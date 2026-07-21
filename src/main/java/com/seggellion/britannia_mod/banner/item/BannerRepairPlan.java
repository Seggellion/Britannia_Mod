package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.Objects;
import java.util.Optional;

public record BannerRepairPlan(
        Optional<BannerInstanceState> expectedState,
        Optional<BannerInstanceState> replacementState,
        Optional<BannerRepairReason> reason,
        boolean historicalPigmentRetained,
        Optional<BannerStateIssue> failure) {
    public BannerRepairPlan {
        expectedState = Objects.requireNonNull(expectedState, "expectedState");
        replacementState = Objects.requireNonNull(replacementState, "replacementState");
        reason = Objects.requireNonNull(reason, "reason");
        failure = Objects.requireNonNull(failure, "failure");
    }

    public boolean successful() {
        return replacementState.isPresent() && reason.isPresent() && failure.isEmpty();
    }
}
