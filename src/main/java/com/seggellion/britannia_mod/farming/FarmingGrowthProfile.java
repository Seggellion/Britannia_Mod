package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Shared read-only inputs for the authoritative farming fit and quality formulas.
 * Implementations provide data only; {@link CropQualityCalculator} owns the math.
 */
public interface FarmingGrowthProfile {
    float idealBoneMeal();

    float idealTurquoise();

    float idealSulphurousAsh();

    float idealRottenFlesh();

    float nutrientTolerance();

    float boneMealWeight();

    float turquoiseWeight();

    float sulphurousAshWeight();

    float rottenFleshWeight();

    float hydrationIdeal();

    float hydrationUnderTolerance();

    float hydrationOverTolerance();

    float minHydrationToGrow();

    float maxHydrationBeforeSeverePenalty();

    Set<FarmingClimate> idealClimates();

    Set<FarmingClimate> allowedClimates();

    Set<FarmingClimate> forbiddenClimates();

    int minAltitude();

    int maxAltitude();

    float regionToleranceOrModifier();

    float qualitySensitivity();

    NutrientPreferenceMode nutrientPreferenceMode();

    default boolean prefersMaximumNutrients() {
        return nutrientPreferenceMode() == NutrientPreferenceMode.MAXIMUM;
    }

    default boolean isPreferredClimate(FarmingClimate climate) {
        return idealClimates().contains(climate);
    }

    default boolean canGrowInClimate(FarmingClimate climate) {
        FarmingClimate safeClimate = climate == null ? FarmingClimate.TEMPERATE : climate;
        if (forbiddenClimates().contains(safeClimate)) {
            return false;
        }
        return allowedClimates().isEmpty() || allowedClimates().contains(safeClimate);
    }

    default float climateFit(FarmingClimate climate) {
        FarmingClimate safeClimate = climate == null ? FarmingClimate.TEMPERATE : climate;
        if (!canGrowInClimate(safeClimate)) {
            return 0.0f;
        }
        if (idealClimates().contains(safeClimate)) {
            return 1.0f;
        }
        if (!allowedClimates().isEmpty() && allowedClimates().contains(safeClimate)) {
            return 0.65f;
        }
        return regionToleranceOrModifier();
    }

    default boolean canGrowAtAltitude(int y) {
        return y >= minAltitude() && y <= maxAltitude();
    }

    default boolean canGrowAtAltitude(BlockPos pos) {
        return canGrowAtAltitude(pos.getY());
    }
}
