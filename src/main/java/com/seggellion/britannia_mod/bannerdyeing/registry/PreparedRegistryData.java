package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Immutable output of the asynchronous resource-read and structural-decode stage. */
public record PreparedRegistryData(
        int resourcesDiscovered,
        int resourcesDecoded,
        List<DefinitionEntry<BannerDefinitionId, BannerDefinition>> banners,
        List<DefinitionEntry<FabricMaterialId, FabricMaterialDefinition>> fabricMaterials,
        List<DefinitionEntry<PigmentId, PigmentDefinition>> pigments,
        List<DefinitionEntry<ResourceLocation, MaterialPalette>> materialPalettes,
        List<DefinitionEntry<MountId, MountDefinition>> mounts,
        List<DefinitionEntry<PlacementProfileId, PlacementProfile>> placementProfiles,
        List<ValidationIssue> structuralIssues) {

    public PreparedRegistryData {
        banners = List.copyOf(banners);
        fabricMaterials = List.copyOf(fabricMaterials);
        pigments = List.copyOf(pigments);
        materialPalettes = List.copyOf(materialPalettes);
        mounts = List.copyOf(mounts);
        placementProfiles = List.copyOf(placementProfiles);
        structuralIssues = structuralIssues.stream().sorted(ValidationIssue.ORDER).toList();
    }
}
