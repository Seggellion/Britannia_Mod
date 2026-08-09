package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.source.PigmentSourceService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure, generation-free suggestions; callers supply the current immutable snapshot for every request. */
public final class BannerAdminSuggestions {
    private final PigmentSourceService pigmentSource;

    public BannerAdminSuggestions(PigmentSourceService pigmentSource) {
        this.pigmentSource = Objects.requireNonNull(pigmentSource, "pigmentSource");
    }

    public List<String> definitions(RegistrySnapshot snapshot, boolean available) {
        if (!available) {
            return List.of();
        }
        return snapshot.banners().activeDefinitions().stream()
                .map(definition -> definition.id().toString()).sorted().toList();
    }

    public List<String> materials(RegistrySnapshot snapshot, boolean available) {
        if (!available) {
            return List.of();
        }
        return snapshot.fabricMaterials().activeDefinitions().stream()
                .map(material -> material.id().toString()).sorted().toList();
    }

    public List<String> colours(
            Optional<FabricMaterialId> materialId, RegistrySnapshot snapshot, boolean available) {
        if (!available || materialId.isEmpty()) {
            return List.of();
        }
        return snapshot.fabricMaterials().find(materialId.orElseThrow())
                .flatMap(material -> snapshot.materialPalettes().find(material.paletteId()))
                .map(palette -> palette.entries().stream()
                        .map(entry -> entry.id().toString()).sorted().toList())
                .orElse(List.of());
    }

    public List<String> mounts(
            Optional<BannerDefinitionId> definitionId, RegistrySnapshot snapshot, boolean available) {
        if (!available) {
            return List.of();
        }
        if (definitionId.isPresent()) {
            return snapshot.banners().find(definitionId.orElseThrow())
                    .map(definition -> definition.supportedMounts().stream()
                            .filter(snapshot.mounts()::contains)
                            .map(Object::toString).sorted().toList())
                    .orElse(List.of());
        }
        return snapshot.mounts().activeDefinitions().stream()
                .map(mount -> mount.id().toString()).sorted().toList();
    }

    public List<String> pigments(RegistrySnapshot snapshot, boolean available) {
        if (!available) {
            return List.of();
        }
        var result = pigmentSource.listAvailablePigments(snapshot, true);
        return result.successful()
                ? result.entries().stream().map(entry -> entry.pigmentId().toString()).toList()
                : List.of();
    }
}
