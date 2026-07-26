package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.item.BannerItemFactoryResult;
import com.seggellion.britannia_mod.banner.item.BannerItemStateAccess;
import com.seggellion.britannia_mod.banner.item.BannerStateIssueKind;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.Objects;
import java.util.Optional;

/** Validates admin arguments and delegates complete state construction to {@link BannerItemFactory}. */
public final class BannerAdminService {
    private final BannerItemFactory factory;
    private final BannerItemStateAccess stateAccess;

    public BannerAdminService(BannerItemFactory factory, BannerItemStateAccess stateAccess) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.stateAccess = Objects.requireNonNull(stateAccess, "stateAccess");
    }

    public BannerAdminResult create(
            BannerDefinitionId definitionId,
            Optional<FabricMaterialId> selectedMaterial,
            Optional<ResolvedColourId> selectedColour,
            Optional<MountId> selectedMount,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(selectedMaterial, "selectedMaterial");
        Objects.requireNonNull(selectedColour, "selectedColour");
        Objects.requireNonNull(selectedMount, "selectedMount");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return fail(BannerAdminFailure.REGISTRY_UNAVAILABLE, "registry_snapshot");
        }

        BannerDefinition definition = snapshot.banners().find(definitionId).orElse(null);
        if (definition == null) {
            return fail(disabled(snapshot.banners().disabledEntries(), definitionId)
                    ? BannerAdminFailure.DEFINITION_DISABLED : BannerAdminFailure.DEFINITION_MISSING,
                    definitionId.toString());
        }
        FabricMaterialId materialId = selectedMaterial.orElse(BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID);
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(materialId).orElse(null);
        if (material == null) {
            return fail(disabled(snapshot.fabricMaterials().disabledEntries(), materialId)
                    ? BannerAdminFailure.MATERIAL_DISABLED : BannerAdminFailure.MATERIAL_MISSING,
                    materialId.toString());
        }
        MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) {
            return fail(BannerAdminFailure.PALETTE_MISSING, material.paletteId().toString());
        }

        ResolvedColourId colourId = selectedColour.orElse(material.naturalColourId());
        if (palette.entries().stream().noneMatch(entry -> entry.id().equals(colourId))) {
            if (selectedColour.isEmpty()) {
                return fail(BannerAdminFailure.NATURAL_COLOUR_MISSING, colourId.toString());
            }
            boolean belongsElsewhere = snapshot.materialPalettes().activeDefinitions().stream()
                    .anyMatch(candidate -> candidate.entries().stream()
                            .anyMatch(entry -> entry.id().equals(colourId)));
            return fail(belongsElsewhere
                    ? BannerAdminFailure.COLOUR_NOT_IN_MATERIAL : BannerAdminFailure.COLOUR_MISSING,
                    colourId.toString());
        }

        MountId mountId = selectedMount.orElse(definition.defaultMount());
        if (!snapshot.mounts().contains(mountId)) {
            return fail(disabled(snapshot.mounts().disabledEntries(), mountId)
                    ? BannerAdminFailure.MOUNT_DISABLED : BannerAdminFailure.MOUNT_MISSING,
                    mountId.toString());
        }
        if (!definition.supportedMounts().contains(mountId)) {
            return fail(BannerAdminFailure.UNSUPPORTED_MOUNT, mountId.toString());
        }

        BannerItemFactoryResult result = factory.fullySpecifiedBanner(
                definitionId, materialId, colourId, Optional.empty(), mountId, snapshot, true);
        if (!result.successful()) {
            var issue = result.failure().orElseThrow();
            return fail(map(issue.kind()), issue.stableId());
        }
        var stack = result.stack().orElseThrow();
        var state = stateAccess.read(stack).orElse(null);
        if (state == null) {
            return fail(BannerAdminFailure.FACTORY_FAILURE, "banner_instance_state");
        }
        return BannerAdminResult.success(stack, state);
    }

    private static BannerAdminFailure map(BannerStateIssueKind kind) {
        return switch (kind) {
            case REGISTRY_UNAVAILABLE -> BannerAdminFailure.REGISTRY_UNAVAILABLE;
            case DEFINITION_MISSING -> BannerAdminFailure.DEFINITION_MISSING;
            case DEFINITION_DISABLED -> BannerAdminFailure.DEFINITION_DISABLED;
            case MATERIAL_MISSING -> BannerAdminFailure.MATERIAL_MISSING;
            case MATERIAL_DISABLED -> BannerAdminFailure.MATERIAL_DISABLED;
            case PALETTE_MISSING -> BannerAdminFailure.PALETTE_MISSING;
            case RESOLVED_COLOUR_MISSING -> BannerAdminFailure.COLOUR_MISSING;
            case MOUNT_MISSING -> BannerAdminFailure.MOUNT_MISSING;
            case MOUNT_DISABLED -> BannerAdminFailure.MOUNT_DISABLED;
            case UNSUPPORTED_MOUNT -> BannerAdminFailure.UNSUPPORTED_MOUNT;
            default -> BannerAdminFailure.INVALID_STATE;
        };
    }

    private static boolean disabled(java.util.List<? extends DefinitionEntry<?, ?>> entries, Object id) {
        return entries.stream().map(DefinitionEntry::id).anyMatch(id::equals);
    }

    private static BannerAdminResult fail(BannerAdminFailure failure, String id) {
        return BannerAdminResult.failure(failure, id);
    }
}
