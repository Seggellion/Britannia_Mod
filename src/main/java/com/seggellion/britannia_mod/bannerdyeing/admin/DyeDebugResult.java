package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.dye.service.ExplainedDyeResolution;
import java.util.Objects;
import java.util.Optional;

public record DyeDebugResult(
        Optional<ExplainedDyeResolution> resolution,
        Optional<String> resolvedColourNameKey,
        DyeDebugFailure failure,
        String diagnosticId) {
    public DyeDebugResult {
        resolution = Objects.requireNonNull(resolution, "resolution");
        resolvedColourNameKey = Objects.requireNonNull(resolvedColourNameKey, "resolvedColourNameKey");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        if (resolution.isPresent() != (failure == DyeDebugFailure.NONE)
                || resolution.isPresent() != resolvedColourNameKey.isPresent()) {
            throw new IllegalArgumentException("Successful debug result requires resolution and colour name");
        }
    }

    public static DyeDebugResult success(ExplainedDyeResolution result, String colourNameKey) {
        return new DyeDebugResult(Optional.of(result), Optional.of(colourNameKey), DyeDebugFailure.NONE, "");
    }

    public static DyeDebugResult failure(DyeDebugFailure failure, String id) {
        return new DyeDebugResult(Optional.empty(), Optional.empty(), failure, id);
    }

    public boolean successful() {
        return failure == DyeDebugFailure.NONE;
    }
}
