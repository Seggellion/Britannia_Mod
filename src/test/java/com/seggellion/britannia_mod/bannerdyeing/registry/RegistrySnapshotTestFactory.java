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

/** Test-only construction of intentionally invalid snapshots for resolver defensive paths. */
public final class RegistrySnapshotTestFactory {
    private RegistrySnapshotTestFactory() {
    }

    public static RegistrySnapshot colourSnapshot(
            Map<FabricMaterialId, FabricMaterialDefinition> materials,
            Map<PigmentId, PigmentDefinition> pigments,
            Map<ResourceLocation, MaterialPalette> palettes) {
        return new RegistrySnapshot(
                empty(), registry(materials), registry(pigments), registry(palettes), empty(), empty());
    }

    private static <I, T> DefinitionRegistry<I, T> registry(Map<I, T> definitions) {
        java.util.LinkedHashMap<I, DefinitionEntry<I, T>> entries = new java.util.LinkedHashMap<>();
        definitions.forEach((id, definition) -> entries.put(id, new DefinitionEntry<>(id, definition,
                ResourceLocation.parse("britannia_mod:test/" + id.toString().replace(':', '_')))));
        return new DefinitionRegistry<>(entries, List.of());
    }

    private static <I, T> DefinitionRegistry<I, T> empty() {
        return new DefinitionRegistry<>(Map.of(), List.of());
    }
}
