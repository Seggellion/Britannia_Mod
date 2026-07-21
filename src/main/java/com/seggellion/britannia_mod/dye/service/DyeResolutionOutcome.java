package com.seggellion.britannia_mod.dye.service;

import java.util.Objects;
import java.util.Optional;

/** Lightweight success-or-failure value for expected content resolution outcomes. */
public record DyeResolutionOutcome(
        Optional<DyeResult> result,
        Optional<DyeResolutionFailure> failure,
        String message) {
    public DyeResolutionOutcome {
        result = Objects.requireNonNull(result, "result");
        failure = Objects.requireNonNull(failure, "failure");
        message = Objects.requireNonNull(message, "message");
        if (result.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Exactly one of result or failure must be present");
        }
    }

    public static DyeResolutionOutcome success(DyeResult result) {
        return new DyeResolutionOutcome(Optional.of(result), Optional.empty(), "");
    }

    public static DyeResolutionOutcome failure(DyeResolutionFailure failure, String message) {
        return new DyeResolutionOutcome(Optional.empty(), Optional.of(failure), message);
    }

    public boolean successful() {
        return result.isPresent();
    }
}
