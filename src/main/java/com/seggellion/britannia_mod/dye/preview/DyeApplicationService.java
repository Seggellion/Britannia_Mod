package com.seggellion.britannia_mod.dye.preview;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.DyeableItem;
import com.seggellion.britannia_mod.dye.api.DyeableStateRead;
import com.seggellion.britannia_mod.dye.api.DyeableStateUpdate;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.service.DyeResolutionOutcome;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Revalidates a consumed preview and applies its two planned component changes atomically. */
public final class DyeApplicationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DyeResolver resolver;

    public DyeApplicationService(DyeResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public DyeApplicationResultCode apply(
            DyePreviewSession session,
            ItemStack mainHand,
            ItemStack offHand,
            Item registeredTub,
            DyeableItem dyeableItem,
            RegistrySnapshot currentSnapshot,
            boolean registryAvailable,
            DataComponentType<DyeTubState> tubComponent) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(registeredTub, "registeredTub");
        Objects.requireNonNull(dyeableItem, "dyeableItem");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(tubComponent, "tubComponent");

        DyeApplicationResultCode mainMismatch = stackMismatch(
                session.expectedMainStack(), mainHand, tubComponent, true);
        if (mainMismatch != null) {
            return mainMismatch;
        }
        DyeApplicationResultCode offMismatch = stackMismatch(
                session.expectedOffStack(), offHand, tubComponent, false);
        if (offMismatch != null) {
            return offMismatch;
        }
        if (mainHand.getItem() != registeredTub || mainHand.getItem() != session.expectedMainItem()) {
            return DyeApplicationResultCode.MAIN_HAND_CHANGED;
        }
        if (offHand.getItem() != session.expectedOffItem()) {
            return DyeApplicationResultCode.OFF_HAND_CHANGED;
        }
        if (!registryAvailable || currentSnapshot != session.registrySnapshot()) {
            return DyeApplicationResultCode.REGISTRY_CHANGED;
        }

        DyeTubState currentTub = DyeTubStateAccess.read(mainHand, tubComponent);
        if (!currentTub.equals(session.tubState())) {
            return DyeApplicationResultCode.TUB_STATE_CHANGED;
        }
        if (currentTub.pigmentId().isEmpty()
                || !currentTub.pigmentId().orElseThrow().equals(session.pigmentId())) {
            return DyeApplicationResultCode.TUB_STATE_CHANGED;
        }
        if (currentTub.remainingUses().filter(uses -> uses == 0).isPresent()) {
            return DyeApplicationResultCode.TUB_DEPLETED;
        }

        DyeableStateRead currentBanner = dyeableItem.readDyeableState(offHand, currentSnapshot, true);
        if (!currentBanner.successful()
                || !currentBanner.materialId().equals(Optional.of(session.bannerState().materialId()))
                || !currentBanner.resolvedColourId().equals(Optional.of(session.bannerState().resolvedColourId()))
                || !currentBanner.sourcePigmentId().equals(session.bannerState().sourcePigmentId())) {
            return DyeApplicationResultCode.BANNER_STATE_CHANGED;
        }

        DyeResolutionOutcome resolution = resolver.resolve(
                session.pigmentId(), session.bannerState().materialId(), currentSnapshot);
        if (!resolution.successful()) {
            return DyeApplicationResultCode.RESOLVER_RESULT_CHANGED;
        }
        DyeResult result = resolution.result().orElseThrow();
        if (!result.equals(session.resolvedResult())) {
            return DyeApplicationResultCode.RESOLVER_RESULT_CHANGED;
        }
        if (currentBanner.resolvedColourId().equals(Optional.of(result.resolvedColourId()))
                && currentBanner.sourcePigmentId().equals(Optional.of(session.pigmentId()))) {
            return DyeApplicationResultCode.ALREADY_DYED;
        }

        DyeableStateUpdate bannerUpdate = dyeableItem.planColourUpdate(
                offHand, result.resolvedColourId(), Optional.of(session.pigmentId()), currentSnapshot, true);
        if (!bannerUpdate.successful()) {
            return DyeApplicationResultCode.BANNER_UPDATE_INVALID;
        }
        DyeTubState replacementTub = currentTub.remainingUses().isPresent()
                ? new DyeTubState(currentTub.schemaVersion(), currentTub.pigmentId(),
                        Optional.of(currentTub.remainingUses().orElseThrow() - 1))
                : currentTub;

        ItemStack bannerBackup = offHand.copy();
        ItemStack tubBackup = mainHand.copy();
        try {
            if (!dyeableItem.applyColourUpdate(offHand, bannerUpdate)) {
                restoreApprovedComponents(offHand, bannerBackup, mainHand, tubBackup, tubComponent);
                return DyeApplicationResultCode.BANNER_UPDATE_INVALID;
            }
            if (!replacementTub.equals(currentTub)) {
                DyeTubStateAccess.write(mainHand, tubComponent, replacementTub);
            }
            return DyeApplicationResultCode.SUCCESS;
        } catch (RuntimeException exception) {
            restoreApprovedComponents(offHand, bannerBackup, mainHand, tubBackup, tubComponent);
            LOGGER.error("Unexpected dye application failure player={} session={} pigment={} material={}",
                    session.playerId(), session.sessionId(), session.pigmentId(), session.bannerState().materialId(),
                    exception);
            return DyeApplicationResultCode.UNEXPECTED_APPLY_FAILURE;
        }
    }

    private static DyeApplicationResultCode stackMismatch(
            ItemStack expected, ItemStack actual, DataComponentType<DyeTubState> tubComponent, boolean main) {
        if (ItemStack.matches(expected, actual)) {
            return null;
        }
        if (expected.getItem() != actual.getItem() || expected.getCount() != actual.getCount()) {
            return main ? DyeApplicationResultCode.MAIN_HAND_CHANGED : DyeApplicationResultCode.OFF_HAND_CHANGED;
        }
        if (main && !DyeTubStateAccess.read(expected, tubComponent).equals(DyeTubStateAccess.read(actual, tubComponent))) {
            if (DyeTubStateAccess.read(actual, tubComponent).remainingUses().filter(uses -> uses == 0).isPresent()) {
                return DyeApplicationResultCode.TUB_DEPLETED;
            }
            return DyeApplicationResultCode.TUB_STATE_CHANGED;
        }
        return main ? DyeApplicationResultCode.MAIN_HAND_CHANGED : DyeApplicationResultCode.BANNER_STATE_CHANGED;
    }

    private static void restoreApprovedComponents(
            ItemStack offHand, ItemStack bannerBackup, ItemStack mainHand, ItemStack tubBackup,
            DataComponentType<DyeTubState> tubComponent) {
        try {
            offHand.applyComponentsAndValidate(bannerBackup.getComponentsPatch());
            DyeTubStateAccess.write(mainHand, tubComponent, DyeTubStateAccess.read(tubBackup, tubComponent));
        } catch (RuntimeException ignored) {
            // The caller logs the unexpected apply failure; rollback remains best effort at this narrow boundary.
        }
    }
}
