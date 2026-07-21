package com.seggellion.britannia_mod.banner.placement;

import java.util.Optional;

public record BannerPlacementPlanningResult(Optional<BannerPlacementPlan> plan, BannerPlacementFailure failure) {
    public static BannerPlacementPlanningResult success(BannerPlacementPlan plan) {
        return new BannerPlacementPlanningResult(Optional.of(plan), BannerPlacementFailure.NONE);
    }

    public static BannerPlacementPlanningResult failure(BannerPlacementFailure failure) {
        return new BannerPlacementPlanningResult(Optional.empty(), failure);
    }

    public boolean successful() {
        return plan.isPresent() && failure == BannerPlacementFailure.NONE;
    }
}
