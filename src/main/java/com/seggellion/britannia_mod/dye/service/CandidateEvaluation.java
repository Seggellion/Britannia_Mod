package com.seggellion.britannia_mod.dye.service;

import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Objects;

public record CandidateEvaluation(
        ResolvedColourId colourId,
        double distance,
        List<String> sharedColourFamilyTags,
        int priority,
        boolean selected) {
    public CandidateEvaluation {
        Objects.requireNonNull(colourId, "colourId");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        sharedColourFamilyTags = List.copyOf(Objects.requireNonNull(
                sharedColourFamilyTags, "sharedColourFamilyTags"));
    }

    public boolean sharesColourFamilyTag() {
        return !sharedColourFamilyTags.isEmpty();
    }
}
