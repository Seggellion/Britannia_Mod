package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.service.DyeResolutionFailure;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.Objects;

/** Thin diagnostic projection over the exact resolver used by gameplay. */
public final class DyeResolutionDebugService {
    private final DyeResolver resolver;

    public DyeResolutionDebugService(DyeResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public DyeDebugResult resolve(
            PigmentId pigmentId,
            FabricMaterialId materialId,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return DyeDebugResult.failure(DyeDebugFailure.REGISTRY_UNAVAILABLE, "registry_snapshot");
        }
        if (!snapshot.pigments().contains(pigmentId)) {
            return DyeDebugResult.failure(disabled(snapshot.pigments().disabledEntries(), pigmentId)
                    ? DyeDebugFailure.PIGMENT_DISABLED : DyeDebugFailure.PIGMENT_MISSING, pigmentId.toString());
        }
        if (!snapshot.fabricMaterials().contains(materialId)) {
            return DyeDebugResult.failure(disabled(snapshot.fabricMaterials().disabledEntries(), materialId)
                    ? DyeDebugFailure.MATERIAL_DISABLED : DyeDebugFailure.MATERIAL_MISSING, materialId.toString());
        }
        var material = snapshot.fabricMaterials().require(materialId);
        var palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) {
            return DyeDebugResult.failure(DyeDebugFailure.PALETTE_MISSING, material.paletteId().toString());
        }
        var explained = resolver.explain(pigmentId, materialId, snapshot);
        if (!explained.outcome().successful()) {
            DyeResolutionFailure failure = explained.outcome().failure().orElseThrow();
            return DyeDebugResult.failure(failure == DyeResolutionFailure.NO_COMPATIBLE_COLOUR
                    ? DyeDebugFailure.NO_COMPATIBLE_COLOUR : DyeDebugFailure.RESOLVER_VALIDATION_FAILURE,
                    failure.name());
        }
        var colourId = explained.outcome().result().orElseThrow().resolvedColourId();
        String nameKey = palette.entries().stream()
                .filter(entry -> entry.id().equals(colourId))
                .map(entry -> entry.displayNameKey())
                .findFirst()
                .orElse(null);
        if (nameKey == null) {
            return DyeDebugResult.failure(DyeDebugFailure.RESOLVER_VALIDATION_FAILURE, colourId.toString());
        }
        return DyeDebugResult.success(explained, nameKey);
    }

    private static boolean disabled(java.util.List<? extends DefinitionEntry<?, ?>> entries, Object id) {
        return entries.stream().map(entry -> entry.id()).anyMatch(id::equals);
    }
}
