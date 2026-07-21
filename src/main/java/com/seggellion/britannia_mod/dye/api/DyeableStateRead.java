package com.seggellion.britannia_mod.dye.api;

import java.util.Objects;
import java.util.Optional;

/** Banner-independent authoritative colour view for any future textile item. */
public record DyeableStateRead(
        Optional<FabricMaterialId> materialId,
        Optional<ResolvedColourId> resolvedColourId,
        Optional<PigmentId> sourcePigmentId,
        boolean pigmentApplicationAllowed,
        DyeableStateFailure failure,
        Optional<String> diagnosticId) {
    public DyeableStateRead {
        materialId = Objects.requireNonNull(materialId, "materialId");
        resolvedColourId = Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        sourcePigmentId = Objects.requireNonNull(sourcePigmentId, "sourcePigmentId");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public boolean successful() {
        return failure == DyeableStateFailure.NONE;
    }
}
