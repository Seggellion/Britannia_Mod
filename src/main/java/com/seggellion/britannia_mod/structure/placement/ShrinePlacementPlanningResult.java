package com.seggellion.britannia_mod.structure.placement;

import java.util.Optional;

public record ShrinePlacementPlanningResult(
        Optional<ShrinePlacementPlan> plan, ShrinePlacementFailure failure) {
    public static ShrinePlacementPlanningResult success(ShrinePlacementPlan plan) {
        return new ShrinePlacementPlanningResult(Optional.of(plan), ShrinePlacementFailure.NONE);
    }

    public static ShrinePlacementPlanningResult failure(ShrinePlacementFailure failure) {
        return new ShrinePlacementPlanningResult(Optional.empty(), failure);
    }

    public boolean successful() {
        return plan.isPresent() && failure == ShrinePlacementFailure.NONE;
    }
}
