package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionRegistry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creates only complete registered shared-banner stacks; it never substitutes missing content. */
public final class BannerItemFactory {
    private final Item bannerItem;
    private final BannerItemStateAccess stateAccess;

    public BannerItemFactory(Item bannerItem, BannerItemStateAccess stateAccess) {
        this.bannerItem = Objects.requireNonNull(bannerItem, "bannerItem");
        this.stateAccess = Objects.requireNonNull(stateAccess, "stateAccess");
    }

    public BannerItemFactoryResult naturalCottonAdminBanner(
            BannerDefinitionId definitionId, RegistrySnapshot snapshot, boolean registryAvailable) {
        return craftedMaterialBanner(definitionId, BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID,
                Optional.empty(), snapshot, registryAvailable);
    }

    public BannerItemFactoryResult craftedMaterialBanner(
            BannerDefinitionId definitionId,
            FabricMaterialId materialId,
            Optional<MountId> selectedMount,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(selectedMount, "selectedMount");
        if (!registryAvailable) {
            return fail(BannerStateIssueKind.REGISTRY_UNAVAILABLE, "registry_snapshot");
        }
        BannerDefinition definition = snapshot.banners().find(definitionId).orElse(null);
        if (definition == null) {
            return BannerItemFactoryResult.failure(missingOrDisabled(snapshot.banners(), definitionId,
                    BannerStateIssueKind.DEFINITION_MISSING, BannerStateIssueKind.DEFINITION_DISABLED));
        }
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(materialId).orElse(null);
        if (material == null) {
            return BannerItemFactoryResult.failure(missingOrDisabled(snapshot.fabricMaterials(), materialId,
                    BannerStateIssueKind.MATERIAL_MISSING, BannerStateIssueKind.MATERIAL_DISABLED));
        }
        MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) {
            return fail(BannerStateIssueKind.PALETTE_MISSING, material.paletteId().toString());
        }
        ResolvedColourId natural = material.naturalColourId();
        if (palette.entries().stream().noneMatch(entry -> entry.id().equals(natural))) {
            return fail(BannerStateIssueKind.RESOLVED_COLOUR_MISSING, natural.toString());
        }
        MountId mountId = selectedMount.orElse(definition.defaultMount());
        return fullySpecifiedBanner(definitionId, materialId, natural, Optional.empty(), mountId,
                snapshot, true);
    }

    public BannerItemFactoryResult fullySpecifiedBanner(
            BannerDefinitionId definitionId,
            FabricMaterialId materialId,
            ResolvedColourId colourId,
            Optional<PigmentId> sourcePigmentId,
            MountId mountId,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        if (!registryAvailable) {
            return fail(BannerStateIssueKind.REGISTRY_UNAVAILABLE, "registry_snapshot");
        }
        BannerInstanceState state = new BannerInstanceState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION, definitionId, materialId, colourId,
                sourcePigmentId, mountId);
        BannerStateValidation validation = stateAccess.validateState(state, snapshot, true);
        if (validation.status() != BannerStateStatus.VALID) {
            return BannerItemFactoryResult.failure(validation.issues().getFirst());
        }
        ItemStack stack = new ItemStack(bannerItem);
        if (!stateAccess.writeCompleteValid(stack, state, snapshot, true)) {
            return fail(BannerStateIssueKind.COMPONENT_DECODE_FAILURE, "banner_instance_state");
        }
        return BannerItemFactoryResult.success(stack);
    }

    private static BannerItemFactoryResult fail(BannerStateIssueKind kind, String stableId) {
        return BannerItemFactoryResult.failure(new BannerStateIssue(kind, stableId));
    }

    private static <I, T> BannerStateIssue missingOrDisabled(
            DefinitionRegistry<I, T> registry, I id, BannerStateIssueKind missing, BannerStateIssueKind disabled) {
        boolean isDisabled = registry.disabledEntries().stream().map(DefinitionEntry::id).anyMatch(id::equals);
        return new BannerStateIssue(isDisabled ? disabled : missing, id.toString());
    }
}
