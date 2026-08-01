package com.seggellion.britannia_mod.farming;

/** Complete server-side flower evaluation snapshot for growth and diagnostics. */
public record FlowerGrowthEvaluation(
        FlowerDefinition definition,
        FlowerSoilSnapshot soil,
        FarmingClimate currentClimate,
        int altitude,
        CropGrowthContext farmingContext,
        float multiplier,
        int currentStage,
        int naturalMaximumStage,
        int absoluteMaximumStage,
        float progress,
        boolean mature,
        BlockReason blockingReason
) {
    public enum BlockReason {
        NONE,
        ZERO_HYDRATION,
        UNSUITABLE_CLIMATE,
        UNSUITABLE_ALTITUDE,
        UNSUITABLE_ENVIRONMENT
    }
}
