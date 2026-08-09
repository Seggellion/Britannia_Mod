package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerStateIssueKind;
import com.seggellion.britannia_mod.banner.item.BannerStateValidation;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.DyeableStateUpdate;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.service.DyeResolutionFailure;
import com.seggellion.britannia_mod.dye.service.DyeResolutionOutcome;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Performs every preview check without mutating either held stack. */
public final class DyePreviewValidationService {
    private final DyeResolver resolver;

    public DyePreviewValidationService(DyeResolver resolver) {
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
    }

    public DyePreviewPlan plan(
            ItemStack mainHand,
            Item registeredTub,
            ItemStack offHand,
            BannerItem registeredBanner,
            RegistrySnapshot snapshot,
            boolean registryAvailable,
            DataComponentType<DyeTubState> tubComponent) {
        if (!mainHand.is(registeredTub) || offHand.getItem() != registeredBanner) {
            return DyePreviewPlan.failure(DyePreviewFailure.INVALID_HAND_CONTRACT);
        }
        Optional<BannerInstanceState> stored = registeredBanner.stateAccess().read(offHand);
        if (stored.isEmpty()) {
            return DyePreviewPlan.failure(DyePreviewFailure.BANNER_UNCONFIGURED);
        }
        DyeTubState tubState = DyeTubStateAccess.read(mainHand, tubComponent);
        if (tubState.pigmentId().isEmpty()) {
            return DyePreviewPlan.failure(DyePreviewFailure.TUB_EMPTY);
        }
        if (tubState.remainingUses().filter(uses -> uses == 0).isPresent()) {
            return DyePreviewPlan.failure(DyePreviewFailure.TUB_DEPLETED);
        }
        if (!registryAvailable) {
            return DyePreviewPlan.failure(DyePreviewFailure.REGISTRY_UNAVAILABLE);
        }

        PigmentId pigmentId = tubState.pigmentId().orElseThrow();
        PigmentDefinition pigment = snapshot.pigments().find(pigmentId).orElse(null);
        if (pigment == null) {
            boolean disabled = snapshot.pigments().disabledEntries().stream()
                    .map(DefinitionEntry::id).anyMatch(pigmentId::equals);
            return DyePreviewPlan.failure(disabled
                    ? DyePreviewFailure.PIGMENT_DISABLED : DyePreviewFailure.PIGMENT_MISSING);
        }

        BannerStateValidation validation = registeredBanner.stateAccess().validate(offHand, snapshot, true);
        if (!validation.validForColourUpdate()) {
            return DyePreviewPlan.failure(mapBannerFailure(validation));
        }
        BannerInstanceState state = validation.storedState().orElseThrow();
        BannerDefinition definition = snapshot.banners().find(state.bannerDefinitionId()).orElse(null);
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(state.materialId()).orElse(null);
        MaterialPalette palette = material == null ? null : snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        MountDefinition mount = snapshot.mounts().find(state.mountId()).orElse(null);
        if (definition == null || material == null || palette == null || mount == null) {
            return DyePreviewPlan.failure(DyePreviewFailure.BANNER_INVALID);
        }

        DyeResolutionOutcome resolved = resolver.resolve(pigmentId, state.materialId(), snapshot);
        if (!resolved.successful()) {
            return DyePreviewPlan.failure(resolved.failure().orElseThrow() == DyeResolutionFailure.NO_COMPATIBLE_COLOUR
                    ? DyePreviewFailure.NO_COMPATIBLE_COLOUR : DyePreviewFailure.RESOLVER_FAILURE);
        }
        DyeResult result = resolved.result().orElseThrow();
        DyeableStateUpdate update = registeredBanner.planColourUpdate(
                offHand, result.resolvedColourId(), Optional.of(pigmentId), snapshot, true);
        if (!update.successful()) {
            return DyePreviewPlan.failure(DyePreviewFailure.DYEABLE_REJECTED);
        }

        MaterialPaletteEntry current = palette.entries().stream()
                .filter(entry -> entry.id().equals(state.resolvedColourId())).findFirst().orElse(null);
        MaterialPaletteEntry replacement = palette.entries().stream()
                .filter(entry -> entry.id().equals(result.resolvedColourId())).findFirst().orElse(null);
        if (current == null || replacement == null) {
            return DyePreviewPlan.failure(DyePreviewFailure.BANNER_INVALID);
        }
        Optional<String> currentPigmentName = state.sourcePigmentId().map(id -> snapshot.pigments().find(id)
                .map(PigmentDefinition::displayNameKey)
                .orElse("screen.britannia_mod.dye_preview.unavailable"));
        DyePreviewDisplayData display = new DyePreviewDisplayData(
                definition.displayNameKey(), material.displayNameKey(), mount.displayNameKey(),
                current.displayNameKey(), currentPigmentName, pigment.displayNameKey(), replacement.displayNameKey(),
                result.matchType(), result.perceptualDistance(), rgb(current.displaySrgb()), rgb(replacement.displaySrgb()),
                definition.contentStatus() != BannerContentStatus.COMPLETE, definition.dimensions().provisional());
        return DyePreviewPlan.success(state, tubState, pigmentId, result, display);
    }

    private static DyePreviewFailure mapBannerFailure(BannerStateValidation validation) {
        BannerStateIssueKind kind = validation.issues().isEmpty()
                ? BannerStateIssueKind.COMPONENT_DECODE_FAILURE : validation.issues().getFirst().kind();
        return switch (kind) {
            case COMPONENT_MISSING -> DyePreviewFailure.BANNER_UNCONFIGURED;
            case REGISTRY_UNAVAILABLE -> DyePreviewFailure.REGISTRY_UNAVAILABLE;
            case DEFINITION_MISSING -> DyePreviewFailure.DEFINITION_MISSING;
            case DEFINITION_DISABLED -> DyePreviewFailure.DEFINITION_DISABLED;
            case MATERIAL_MISSING -> DyePreviewFailure.MATERIAL_MISSING;
            case MATERIAL_DISABLED -> DyePreviewFailure.MATERIAL_DISABLED;
            case PALETTE_MISSING -> DyePreviewFailure.PALETTE_MISSING;
            case MOUNT_MISSING -> DyePreviewFailure.MOUNT_MISSING;
            case MOUNT_DISABLED -> DyePreviewFailure.MOUNT_DISABLED;
            default -> DyePreviewFailure.BANNER_INVALID;
        };
    }

    private static int rgb(String canonicalSrgb) {
        return Integer.parseInt(canonicalSrgb.substring(1), 16);
    }
}
