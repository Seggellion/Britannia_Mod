package com.seggellion.britannia_mod.dye.service;

import java.util.Objects;

public record ExplainedDyeResolution(DyeResolutionOutcome outcome, DyeResolutionExplanation explanation) {
    public ExplainedDyeResolution {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(explanation, "explanation");
    }
}
