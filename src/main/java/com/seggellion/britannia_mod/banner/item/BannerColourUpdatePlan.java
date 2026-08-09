package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.Objects;
import java.util.Optional;

public record BannerColourUpdatePlan(
        Optional<BannerInstanceState> expectedState,
        Optional<BannerInstanceState> replacementState,
        Optional<BannerStateIssue> failure) {
    public BannerColourUpdatePlan {
        expectedState = Objects.requireNonNull(expectedState, "expectedState");
        replacementState = Objects.requireNonNull(replacementState, "replacementState");
        failure = Objects.requireNonNull(failure, "failure");
    }

    public boolean successful() {
        return replacementState.isPresent() && failure.isEmpty();
    }
}
