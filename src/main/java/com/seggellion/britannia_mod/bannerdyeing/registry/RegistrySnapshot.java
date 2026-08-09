package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** One internally consistent, immutable view of all authored definition data. */
public record RegistrySnapshot(
        DefinitionRegistry<BannerDefinitionId, BannerDefinition> banners,
        DefinitionRegistry<FabricMaterialId, FabricMaterialDefinition> fabricMaterials,
        DefinitionRegistry<PigmentId, PigmentDefinition> pigments,
        DefinitionRegistry<ResourceLocation, MaterialPalette> materialPalettes,
        DefinitionRegistry<MountId, MountDefinition> mounts,
        DefinitionRegistry<PlacementProfileId, PlacementProfile> placementProfiles) {

    public static RegistrySnapshot empty() {
        return new RegistrySnapshot(
                emptyRegistry(), emptyRegistry(), emptyRegistry(),
                emptyRegistry(), emptyRegistry(), emptyRegistry());
    }

    private static <I, T> DefinitionRegistry<I, T> emptyRegistry() {
        return new DefinitionRegistry<>(Map.of(), List.of());
    }
}
