package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BannerStateValidation(
        BannerStateStatus status,
        Optional<BannerInstanceState> storedState,
        List<BannerStateIssue> issues) {
    public BannerStateValidation {
        Objects.requireNonNull(status, "status");
        storedState = Objects.requireNonNull(storedState, "storedState");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean validForColourUpdate() {
        return status == BannerStateStatus.VALID || status == BannerStateStatus.VALID_WITH_DIAGNOSTICS;
    }
}
