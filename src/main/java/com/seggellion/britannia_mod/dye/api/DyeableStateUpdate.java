package com.seggellion.britannia_mod.dye.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable validation plan; applying it is a separate, explicit mutation boundary. */
public record DyeableStateUpdate(
        Optional<FabricMaterialId> expectedMaterialId,
        Optional<ResolvedColourId> expectedColourId,
        Optional<PigmentId> expectedSourcePigmentId,
        Optional<ResolvedColourId> replacementColourId,
        Optional<PigmentId> replacementSourcePigmentId,
        DyeableStateFailure failure,
        Optional<String> diagnosticId) {
    public DyeableStateUpdate {
        expectedMaterialId = Objects.requireNonNull(expectedMaterialId, "expectedMaterialId");
        expectedColourId = Objects.requireNonNull(expectedColourId, "expectedColourId");
        expectedSourcePigmentId = Objects.requireNonNull(expectedSourcePigmentId, "expectedSourcePigmentId");
        replacementColourId = Objects.requireNonNull(replacementColourId, "replacementColourId");
        replacementSourcePigmentId = Objects.requireNonNull(replacementSourcePigmentId, "replacementSourcePigmentId");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public boolean successful() {
        return failure == DyeableStateFailure.NONE;
    }
}
