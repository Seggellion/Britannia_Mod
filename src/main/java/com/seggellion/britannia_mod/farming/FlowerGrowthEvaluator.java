package com.seggellion.britannia_mod.farming;

import java.util.Objects;

/**
 * Adapts persistent flower state to the authoritative farming calculator.
 * This class owns no nutrient, hydration, climate, altitude, quality, or
 * multiplier formula; those remain in {@link CropQualityCalculator} and
 * {@link FarmingGrowthProfile}.
 */
public final class FlowerGrowthEvaluator {
    private FlowerGrowthEvaluator() {
    }

    public static FlowerGrowthEvaluation evaluate(
            FlowerPersistentState state,
            FlowerDefinition definition,
            FarmingClimate currentClimate,
            int altitude
    ) {
        Objects.requireNonNull(state, "Flower state is required");
        Objects.requireNonNull(definition, "Flower definition is required");
        Objects.requireNonNull(currentClimate, "Current farming climate is required");

        FlowerGrowthProfile profile = definition.growthProfile();
        FlowerSoilSnapshot soil = state.soil();
        float nutrientFit = CropQualityCalculator.weightedNutrientFit(
                profile, soil.nitrogen(), soil.phosphorus(), soil.potassium(), soil.organicMatter()
        );
        float hydrationFit = CropQualityCalculator.hydrationFit(
                soil.hydration() / (float) com.seggellion.britannia_mod.block.entity.FarmingBlockEntity.MAX_HYDRATION,
                profile
        );
        boolean climateAllowed = profile.canGrowInClimate(currentClimate);
        boolean altitudeAllowed = profile.canGrowAtAltitude(altitude);
        float climateFit = profile.climateFit(currentClimate);
        boolean idealGrowth = nutrientFit >= 0.95f
                && hydrationFit >= 0.95f
                && climateFit >= 0.95f
                && climateAllowed
                && altitudeAllowed;
        CropGrowthContext context = new CropGrowthContext(
                nutrientFit,
                hydrationFit,
                climateFit,
                currentClimate,
                climateAllowed,
                altitudeAllowed,
                true,
                idealGrowth,
                0.0f
        );

        FlowerGrowthEvaluation.BlockReason blockingReason = blockingReason(soil, context);
        // FarmingBlock's authoritative cadence does not call crop growth at
        // zero hydration. Preserve that outer precondition for flowers.
        float multiplier = blockingReason == FlowerGrowthEvaluation.BlockReason.NONE
                ? CropQualityCalculator.growthMultiplier(context)
                : 0.0f;
        return new FlowerGrowthEvaluation(
                definition,
                soil,
                currentClimate,
                altitude,
                context,
                multiplier,
                state.growthStage(),
                definition.naturalMaximumStage(),
                definition.absoluteMaximumStage(),
                state.growthState().progress(),
                isMature(state, definition),
                blockingReason
        );
    }

    public static FlowerPersistentState advance(
            FlowerPersistentState state,
            FlowerGrowthEvaluation evaluation
    ) {
        Objects.requireNonNull(state, "Flower state is required");
        Objects.requireNonNull(evaluation, "Flower growth evaluation is required");
        if (!state.speciesId().equals(evaluation.definition().id())) {
            throw new IllegalArgumentException("Flower evaluation species does not match persistent identity");
        }
        if (evaluation.mature()) {
            return state;
        }
        FlowerGrowthState current = state.growthState();
        if (evaluation.multiplier() <= 0.0f) {
            return current.blocked()
                    ? state
                    : state.withGrowth(state.growthStage(), new FlowerGrowthState(
                            current.progress(), current.tickProgress(), true
                    ));
        }

        float nextProgress = Math.min(1.0f, current.progress()
                + evaluation.multiplier() / Math.max(1, evaluation.definition().growthProfile().baseGrowthTicks()));
        int nextStage = progressToStage(nextProgress, evaluation.naturalMaximumStage());
        return state.withGrowth(nextStage, new FlowerGrowthState(
                nextProgress,
                current.tickProgress() + 1,
                false
        ));
    }

    /**
     * Converts normalized progress to the flower domain's 1-based stage.
     * Boundary: stage = 1 + floor(progress * naturalMaximum), clamped to
     * 1..naturalMaximum; progress 1.0 is therefore exactly natural maturity.
     */
    public static int progressToStage(float progress, int naturalMaximumStage) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("Flower progress must be finite");
        }
        if (naturalMaximumStage < 1) {
            throw new IllegalArgumentException("Flower natural maximum must be at least 1");
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int stage = 1 + (int) Math.floor(clamped * naturalMaximumStage);
        return Math.max(1, Math.min(naturalMaximumStage, stage));
    }

    public static boolean isMature(FlowerPersistentState state, FlowerDefinition definition) {
        return state.growthStage() > definition.naturalMaximumStage()
                || state.growthState().progress() >= 1.0f
                && state.growthStage() >= definition.naturalMaximumStage();
    }

    public static FlowerPersistentState resetToStageOne(FlowerPersistentState state) {
        Objects.requireNonNull(state, "Flower state is required");
        return state.withGrowth(1, FlowerGrowthState.newlyPlanted());
    }

    private static FlowerGrowthEvaluation.BlockReason blockingReason(
            FlowerSoilSnapshot soil,
            CropGrowthContext context
    ) {
        if (soil.hydration() <= 0) {
            return FlowerGrowthEvaluation.BlockReason.ZERO_HYDRATION;
        }
        if (!context.climateAllowed()) {
            return FlowerGrowthEvaluation.BlockReason.UNSUITABLE_CLIMATE;
        }
        if (!context.altitudeAllowed()) {
            return FlowerGrowthEvaluation.BlockReason.UNSUITABLE_ALTITUDE;
        }
        if (!context.specialEnvironmentAllowed() || !context.latticeSatisfied()) {
            return FlowerGrowthEvaluation.BlockReason.UNSUITABLE_ENVIRONMENT;
        }
        return FlowerGrowthEvaluation.BlockReason.NONE;
    }
}
