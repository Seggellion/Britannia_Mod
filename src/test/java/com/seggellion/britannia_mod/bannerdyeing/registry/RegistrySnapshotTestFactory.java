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

    public static RegistrySnapshot withoutBanner(RegistrySnapshot source, BannerDefinitionId id) {
        return new RegistrySnapshot(without(source.banners(), id), source.fabricMaterials(), source.pigments(),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withDisabledBanner(RegistrySnapshot source, BannerDefinitionId id) {
        return new RegistrySnapshot(disable(source.banners(), id), source.fabricMaterials(), source.pigments(),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withoutMaterial(RegistrySnapshot source, FabricMaterialId id) {
        return new RegistrySnapshot(source.banners(), without(source.fabricMaterials(), id), source.pigments(),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withDisabledMaterial(RegistrySnapshot source, FabricMaterialId id) {
        return new RegistrySnapshot(source.banners(), disable(source.fabricMaterials(), id), source.pigments(),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withoutPalette(RegistrySnapshot source, ResourceLocation id) {
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), source.pigments(),
                without(source.materialPalettes(), id), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withoutPigment(RegistrySnapshot source, PigmentId id) {
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), without(source.pigments(), id),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withDisabledPigment(RegistrySnapshot source, PigmentId id) {
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), disable(source.pigments(), id),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot withoutMount(RegistrySnapshot source, MountId id) {
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), source.pigments(),
                source.materialPalettes(), without(source.mounts(), id), source.placementProfiles());
    }

    public static RegistrySnapshot withDisabledMount(RegistrySnapshot source, MountId id) {
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), source.pigments(),
                source.materialPalettes(), disable(source.mounts(), id), source.placementProfiles());
    }

    public static RegistrySnapshot replacePalette(RegistrySnapshot source, MaterialPalette replacement) {
        Map<ResourceLocation, MaterialPalette> palettes = definitions(source.materialPalettes());
        palettes.put(replacement.id(), replacement);
        return new RegistrySnapshot(source.banners(), source.fabricMaterials(), source.pigments(), registry(palettes),
                source.mounts(), source.placementProfiles());
    }

    public static RegistrySnapshot replaceBanner(RegistrySnapshot source, BannerDefinition replacement) {
        Map<BannerDefinitionId, BannerDefinition> banners = definitions(source.banners());
        banners.put(replacement.id(), replacement);
        return new RegistrySnapshot(registry(banners), source.fabricMaterials(), source.pigments(),
                source.materialPalettes(), source.mounts(), source.placementProfiles());
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

    private static <I, T> DefinitionRegistry<I, T> without(DefinitionRegistry<I, T> source, I id) {
        Map<I, T> remaining = definitions(source);
        remaining.remove(id);
        return registry(remaining);
    }

    private static <I, T> DefinitionRegistry<I, T> disable(DefinitionRegistry<I, T> source, I id) {
        java.util.LinkedHashMap<I, DefinitionEntry<I, T>> active = new java.util.LinkedHashMap<>(source.activeEntries());
        DefinitionEntry<I, T> removed = active.remove(id);
        if (removed == null) {
            return source;
        }
        List<DefinitionEntry<I, T>> disabled = new java.util.ArrayList<>(source.disabledEntries());
        disabled.add(entry(id, removed.definition(), "disabled"));
        return new DefinitionRegistry<>(active, disabled);
    }

    private static <I, T> Map<I, T> definitions(DefinitionRegistry<I, T> source) {
        Map<I, T> definitions = new java.util.LinkedHashMap<>();
        source.activeEntries().forEach((id, entry) -> definitions.put(id, entry.definition()));
        return definitions;
    }

    private static <I, T> DefinitionRegistry<I, T> empty() {
        return new DefinitionRegistry<>(Map.of(), List.of());
    }
}
