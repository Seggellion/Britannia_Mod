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

    public static RegistrySnapshot pigmentSnapshot(
            Map<PigmentId, PigmentDefinition> active,
            List<PigmentDefinition> disabled) {
        java.util.LinkedHashMap<PigmentId, DefinitionEntry<PigmentId, PigmentDefinition>> activeEntries =
                new java.util.LinkedHashMap<>();
        active.forEach((id, definition) -> activeEntries.put(id, entry(id, definition, "active")));
        List<DefinitionEntry<PigmentId, PigmentDefinition>> disabledEntries = disabled.stream()
                .map(definition -> entry(definition.id(), definition, "disabled"))
                .toList();
        return new RegistrySnapshot(
                empty(), empty(), new DefinitionRegistry<>(activeEntries, disabledEntries),
                empty(), empty(), empty());
    }

    private static <I, T> DefinitionEntry<I, T> entry(I id, T definition, String kind) {
        return new DefinitionEntry<>(id, definition,
                ResourceLocation.parse("britannia_mod:test/" + kind + "/" + id.toString().replace(':', '_')));
    }

    private static <I, T> DefinitionRegistry<I, T> registry(Map<I, T> definitions) {
        java.util.LinkedHashMap<I, DefinitionEntry<I, T>> entries = new java.util.LinkedHashMap<>();
        definitions.forEach((id, definition) -> entries.put(id, entry(id, definition, "active")));
        return new DefinitionRegistry<>(entries, List.of());
    }

    private static <I, T> DefinitionRegistry<I, T> empty() {
        return new DefinitionRegistry<>(Map.of(), List.of());
    }
}
