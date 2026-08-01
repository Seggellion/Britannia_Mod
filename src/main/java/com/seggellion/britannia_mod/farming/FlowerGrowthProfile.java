package com.seggellion.britannia_mod.farming;

import java.util.Objects;
import java.util.Set;

public record FlowerGrowthProfile(
        int baseGrowthTicks,
        float idealBoneMeal,
        float idealTurquoise,
        float idealSulphurousAsh,
        float idealRottenFlesh,
        float nutrientTolerance,
        float boneMealWeight,
        float turquoiseWeight,
        float sulphurousAshWeight,
        float rottenFleshWeight,
        float hydrationIdeal,
        float hydrationUnderTolerance,
        float hydrationOverTolerance,
        float minHydrationToGrow,
        float maxHydrationBeforeSeverePenalty,
        Set<FarmingClimate> idealClimates,
        Set<FarmingClimate> allowedClimates,
        Set<FarmingClimate> forbiddenClimates,
        int idealMinAltitude,
        int idealMaxAltitude,
        int minAltitude,
        int maxAltitude,
        float regionToleranceOrModifier,
        float qualitySensitivity,
        NutrientPreferenceMode nutrientPreferenceMode
) implements FarmingGrowthProfile {
    public FlowerGrowthProfile {
        if (baseGrowthTicks <= 0) {
            throw new IllegalArgumentException("Flower base growth ticks must be positive: " + baseGrowthTicks);
        }
        idealClimates = Set.copyOf(Objects.requireNonNull(idealClimates, "Ideal climates are required"));
        allowedClimates = Set.copyOf(Objects.requireNonNull(allowedClimates, "Allowed climates are required"));
        forbiddenClimates = Set.copyOf(Objects.requireNonNull(forbiddenClimates, "Forbidden climates are required"));
        Objects.requireNonNull(nutrientPreferenceMode, "Nutrient preference mode is required");
        FlowerDefinitionValidator.validateGrowthValues(
                idealBoneMeal, idealTurquoise, idealSulphurousAsh, idealRottenFlesh,
                nutrientTolerance, boneMealWeight, turquoiseWeight, sulphurousAshWeight, rottenFleshWeight,
                hydrationIdeal, hydrationUnderTolerance, hydrationOverTolerance,
                minHydrationToGrow, maxHydrationBeforeSeverePenalty,
                idealClimates, allowedClimates, forbiddenClimates,
                idealMinAltitude, idealMaxAltitude, minAltitude, maxAltitude,
                regionToleranceOrModifier, qualitySensitivity
        );
    }
}
