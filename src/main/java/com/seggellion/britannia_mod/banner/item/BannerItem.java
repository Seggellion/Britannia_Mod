package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementService;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.DyeableItem;
import com.seggellion.britannia_mod.dye.api.DyeableStateFailure;
import com.seggellion.britannia_mod.dye.api.DyeableStateRead;
import com.seggellion.britannia_mod.dye.api.DyeableStateUpdate;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.fml.loading.FMLEnvironment;

/** The one shared banner item. Design, textile, colour and mount are typed instance state. */
public final class BannerItem extends Item implements DyeableItem {
    private final BannerItemStateAccess stateAccess;

    public BannerItem(Properties properties, DataComponentType<BannerInstanceState> componentType) {
        super(properties);
        stateAccess = new BannerItemStateAccess(this, componentType, new DyeResolver());
    }

    public BannerItemStateAccess stateAccess() {
        return stateAccess;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return BannerPlacementService.place(context);
    }

    @Override
    public Component getName(ItemStack stack) {
        return configuredName(stack, BannerDataRegistries.current());
    }

    public Component configuredName(ItemStack stack, RegistrySnapshot snapshot) {
        return stateAccess.read(stack)
                .flatMap(state -> snapshot.banners().find(state.bannerDefinitionId()))
                .<Component>map(definition -> Component.translatable(definition.displayNameKey()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        tooltip.addAll(BannerTooltip.lines(
                stateAccess.validate(stack, snapshot, BannerDataRegistries.isAvailable()),
                snapshot,
                !FMLEnvironment.production));
    }

    @Override
    public DyeableStateRead readDyeableState(
            ItemStack stack, RegistrySnapshot snapshot, boolean registryAvailable) {
        BannerStateValidation validation = stateAccess.validate(stack, snapshot, registryAvailable);
        Optional<BannerInstanceState> stored = validation.storedState();
        if (validation.status() == BannerStateStatus.VALID
                || validation.status() == BannerStateStatus.VALID_WITH_DIAGNOSTICS) {
            BannerInstanceState state = stored.orElseThrow();
            Optional<String> diagnostic = validation.issues().stream().findFirst().map(BannerStateIssue::stableId);
            return new DyeableStateRead(Optional.of(state.materialId()), Optional.of(state.resolvedColourId()),
                    state.sourcePigmentId(), true, DyeableStateFailure.NONE, diagnostic);
        }
        BannerStateIssue issue = validation.issues().getFirst();
        return new DyeableStateRead(stored.map(BannerInstanceState::materialId),
                stored.map(BannerInstanceState::resolvedColourId),
                stored.flatMap(BannerInstanceState::sourcePigmentId), false,
                mapFailure(validation.status(), issue.kind()), Optional.of(issue.stableId()));
    }

    @Override
    public DyeableStateUpdate planColourUpdate(
            ItemStack stack,
            ResolvedColourId resolvedColourId,
            Optional<PigmentId> sourcePigmentId,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        BannerColourUpdatePlan plan = stateAccess.planColourUpdate(
                stack, resolvedColourId, sourcePigmentId, snapshot, registryAvailable);
        if (!plan.successful()) {
            BannerStateIssue failure = plan.failure().orElseThrow();
            return new DyeableStateUpdate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), mapFailure(BannerStateStatus.INVALID, failure.kind()),
                    Optional.of(failure.stableId()));
        }
        BannerInstanceState expected = plan.expectedState().orElseThrow();
        BannerInstanceState replacement = plan.replacementState().orElseThrow();
        return new DyeableStateUpdate(Optional.of(expected.materialId()), Optional.of(expected.resolvedColourId()),
                expected.sourcePigmentId(), Optional.of(replacement.resolvedColourId()),
                replacement.sourcePigmentId(), DyeableStateFailure.NONE, Optional.empty());
    }

    @Override
    public boolean applyColourUpdate(ItemStack stack, DyeableStateUpdate update) {
        if (!update.successful()) {
            return false;
        }
        Optional<BannerInstanceState> current = stateAccess.read(stack);
        if (current.isEmpty()) {
            return false;
        }
        BannerInstanceState state = current.orElseThrow();
        if (!update.expectedMaterialId().equals(Optional.of(state.materialId()))
                || !update.expectedColourId().equals(Optional.of(state.resolvedColourId()))
                || !update.expectedSourcePigmentId().equals(state.sourcePigmentId())
                || update.replacementColourId().isEmpty()) {
            return false;
        }
        BannerInstanceState replacement = new BannerInstanceState(
                state.schemaVersion(), state.bannerDefinitionId(), state.materialId(),
                update.replacementColourId().orElseThrow(), update.replacementSourcePigmentId(), state.mountId());
        return stateAccess.applyColourUpdate(stack,
                new BannerColourUpdatePlan(Optional.of(state), Optional.of(replacement), Optional.empty()));
    }

    private static DyeableStateFailure mapFailure(BannerStateStatus status, BannerStateIssueKind issue) {
        if (status == BannerStateStatus.UNCONFIGURED || issue == BannerStateIssueKind.COMPONENT_MISSING) {
            return DyeableStateFailure.UNCONFIGURED;
        }
        if (status == BannerStateStatus.REGISTRY_UNAVAILABLE) {
            return DyeableStateFailure.REGISTRY_UNAVAILABLE;
        }
        return switch (issue) {
            case MATERIAL_MISSING, MATERIAL_DISABLED -> DyeableStateFailure.MATERIAL_UNAVAILABLE;
            case PALETTE_MISSING -> DyeableStateFailure.PALETTE_UNAVAILABLE;
            case RESOLVED_COLOUR_MISSING, REPAIRABLE_MISSING_COLOUR -> DyeableStateFailure.COLOUR_UNAVAILABLE;
            case SOURCE_PIGMENT_MISSING, SOURCE_PIGMENT_DISABLED -> DyeableStateFailure.PIGMENT_UNAVAILABLE;
            case STALE_PLAN -> DyeableStateFailure.STALE_PLAN;
            default -> DyeableStateFailure.INVALID_STATE;
        };
    }
}
