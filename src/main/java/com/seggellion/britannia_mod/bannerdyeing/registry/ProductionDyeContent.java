package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Milestone 5 production-development content boundary, separate from generic registry fixtures. */
public final class ProductionDyeContent {
    public static final List<String> REQUIRED_MATERIAL_PATHS = List.of("cotton", "wool", "linen", "silk");

    private static final Set<FabricMaterialId> REQUIRED_MATERIALS = REQUIRED_MATERIAL_PATHS.stream()
            .map(path -> FabricMaterialId.parse("britannia_mod:" + path))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private ProductionDyeContent() {
    }

    public static void requireComplete(RegistrySnapshot snapshot) {
        Set<FabricMaterialId> missing = new LinkedHashSet<>(REQUIRED_MATERIALS);
        missing.removeAll(snapshot.fabricMaterials().activeEntries().keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production dye content is missing required fabric materials: " + missing);
        }
        for (FabricMaterialId materialId : REQUIRED_MATERIALS) {
            var material = snapshot.fabricMaterials().require(materialId);
            var palette = snapshot.materialPalettes().find(material.paletteId()).orElseThrow(() ->
                    new IllegalStateException("Required material has no active palette: " + materialId));
            if (!palette.materialId().equals(materialId)) {
                throw new IllegalStateException("Required palette owner mismatch for " + materialId);
            }
            if (palette.entries().stream().noneMatch(entry -> entry.id().equals(material.naturalColourId()))) {
                throw new IllegalStateException("Required material has no natural colour: " + materialId);
            }
        }
    }

    public static Set<FabricMaterialId> requiredMaterials() {
        return REQUIRED_MATERIALS;
    }
}
