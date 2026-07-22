package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable display-only registry projection. It is never consulted by server gameplay. */
public record BannerRenderDataSnapshot(
        Map<BannerDefinitionId, BannerRenderDefinition> banners,
        Map<FabricMaterialId, BannerRenderMaterial> materials,
        Map<MountId, BannerRenderMount> mounts) {
    public BannerRenderDataSnapshot {
        banners = immutableById(Objects.requireNonNull(banners, "banners"));
        materials = immutableById(Objects.requireNonNull(materials, "materials"));
        mounts = immutableById(Objects.requireNonNull(mounts, "mounts"));
    }

    public static BannerRenderDataSnapshot empty() {
        return new BannerRenderDataSnapshot(Map.of(), Map.of(), Map.of());
    }

    public static BannerRenderDataSnapshot fromRegistry(RegistrySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<BannerDefinitionId, BannerRenderDefinition> banners = new LinkedHashMap<>();
        snapshot.banners().activeDefinitions().forEach(definition -> banners.put(definition.id(),
                new BannerRenderDefinition(definition.id(), definition.assets(), definition.contentStatus(),
                        definition.dimensions(), definition.supportedOrientations(), definition.supportedMounts())));

        LinkedHashMap<FabricMaterialId, BannerRenderMaterial> materials = new LinkedHashMap<>();
        snapshot.fabricMaterials().activeDefinitions().forEach(material -> {
            MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
            if (palette == null) {
                return;
            }
            LinkedHashMap<ResolvedColourId, Integer> colours = new LinkedHashMap<>();
            palette.entries().forEach(entry -> colours.put(entry.id(), parseSrgb(entry.displaySrgb())));
            materials.put(material.id(), new BannerRenderMaterial(
                    material.id(), material.naturalColourId(), material.paletteId(), colours));
        });

        LinkedHashMap<MountId, BannerRenderMount> mounts = new LinkedHashMap<>();
        snapshot.mounts().activeDefinitions().forEach(mount -> mounts.put(mount.id(),
                new BannerRenderMount(mount.id(), mount.geometry(), mount.texture())));
        return new BannerRenderDataSnapshot(banners, materials, mounts);
    }

    public List<BannerRenderDefinition> orderedBanners() {
        return List.copyOf(banners.values());
    }

    public List<BannerRenderMaterial> orderedMaterials() {
        return List.copyOf(materials.values());
    }

    public List<BannerRenderMount> orderedMounts() {
        return List.copyOf(mounts.values());
    }

    private static int parseSrgb(String value) {
        return Integer.parseInt(value.substring(1), 16) & 0xFFFFFF;
    }

    private static <K, V> Map<K, V> immutableById(Map<K, V> input) {
        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        input.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Object::toString)))
                .forEach(entry -> {
                    if (result.put(Objects.requireNonNull(entry.getKey(), "id"),
                            Objects.requireNonNull(entry.getValue(), "value")) != null) {
                        throw new IllegalArgumentException("Duplicate render-data ID " + entry.getKey());
                    }
                });
        return Collections.unmodifiableMap(result);
    }
}
