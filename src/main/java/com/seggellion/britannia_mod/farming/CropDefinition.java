package com.seggellion.britannia_mod.farming;

import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.function.Supplier;

public record CropDefinition(
        String id,
        float minimumFarmingSkill,
        String displayName,
        Supplier<? extends Item> seedItem,
        Supplier<? extends Item> harvestItem,
        int cropTier,
        int baseGrowthTicks,
        int growthStages,
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
        float hydrationTolerance,
        float hydrationUnderTolerance,
        float hydrationOverTolerance,
        float minHydrationToGrow,
        float maxHydrationBeforeSeverePenalty,
        Set<FarmingClimate> idealClimates,
        Set<FarmingClimate> allowedClimates,
        Set<FarmingClimate> forbiddenClimates,
        int minAltitude,
        int maxAltitude,
        float regionToleranceOrModifier,
        boolean requiresLattice,
        boolean treeCrop,
        boolean tallCrop,
        int maxHeight,
        boolean vanillaMapped,
        int minYield,
        int maxYield,
        float farmingSkillModifier,
        float seedReturnChance,
        float qualitySensitivity,
        NutrientPreferenceMode nutrientPreferenceMode,
        CropLifecycle lifecycle,
        CropGrowthHabit growthHabit,
        CropSupportRequirement supportRequirement,
        int postHarvestRegrowthAge,
        boolean rootAgeQualityBonus,
        int rootAgeQualityBonusMaturityDays,
        int maxRootAgeQualityBonus,
        CropHarvestTool harvestTool,
        String notes
) implements FarmingGrowthProfile, FarmingSkillRequirement {
    public CropDefinition {
        FarmingSkillRequirementValidator.validateValue(id, minimumFarmingSkill);
    }

    public int maxGrowthAge() {
        return Math.max(0, growthStages - 1);
    }

    public int visualAgeCount() {
        return Math.max(1, growthStages);
    }

    public boolean isMatureAge(int age) {
        return age >= maxGrowthAge();
    }

    public float tierSkillModifier() {
        return switch (cropTier) {
            case 2 -> 1.10f;
            case 3 -> 1.20f;
            case 4 -> 1.35f;
            default -> 1.00f;
        };
    }

    public boolean isPreferredClimate(FarmingClimate climate) {
        return idealClimates.contains(climate);
    }

    public boolean canGrowInClimate(FarmingClimate climate) {
        FarmingClimate safeClimate = climate == null ? FarmingClimate.TEMPERATE : climate;
        if (forbiddenClimates.contains(safeClimate)) {
            return false;
        }
        return allowedClimates.isEmpty() || allowedClimates.contains(safeClimate);
    }

    public float climateFit(FarmingClimate climate) {
        FarmingClimate safeClimate = climate == null ? FarmingClimate.TEMPERATE : climate;
        if (!canGrowInClimate(safeClimate)) {
            return 0.0f;
        }
        if (idealClimates.contains(safeClimate)) {
            return 1.0f;
        }
        if (!allowedClimates.isEmpty() && allowedClimates.contains(safeClimate)) {
            return 0.65f;
        }
        return regionToleranceOrModifier;
    }

    public boolean canGrowAtAltitude(BlockPos pos) {
        int y = pos.getY();
        return y >= minAltitude && y <= maxAltitude;
    }

    public int tier() {
        return cropTier;
    }

    public int baseGrowthTime() {
        return baseGrowthTicks;
    }

    public int requiredHydration() {
        return Math.max(0, Math.round((hydrationIdeal - hydrationUnderTolerance) * 5.0f));
    }

    public int idealHydration() {
        return Math.max(0, Math.round(hydrationIdeal * 5.0f));
    }

    public float nutrient1Requirement() {
        return idealBoneMeal;
    }

    public float nutrient2Requirement() {
        return idealTurquoise;
    }

    public float nutrient3Requirement() {
        return idealSulphurousAsh;
    }

    public float nutrient4Requirement() {
        return idealRottenFlesh;
    }

    public Set<FarmingClimate> preferredClimates() {
        return idealClimates;
    }

    public float preferredClimateSpeedModifier() {
        return 1.50f;
    }

    public float wrongClimateSpeedModifier() {
        return regionToleranceOrModifier;
    }

    public boolean clearsOnHarvest() {
        return lifecycle == CropLifecycle.ANNUAL;
    }

    public boolean persistsAfterHarvest() {
        return lifecycle != CropLifecycle.ANNUAL;
    }

    public boolean isPerennialCrop() {
        return lifecycle == CropLifecycle.PERENNIAL || lifecycle == CropLifecycle.TRELLIS || lifecycle == CropLifecycle.TREE;
    }

    public boolean requiresSupport() {
        return supportRequirement != CropSupportRequirement.NONE;
    }

    public int clampedPostHarvestRegrowthAge() {
        return Math.max(0, Math.min(maxGrowthAge(), postHarvestRegrowthAge));
    }

    public boolean usesRootAgeQualityBonus() {
        return rootAgeQualityBonus && maxRootAgeQualityBonus > 0 && rootAgeQualityBonusMaturityDays > 0;
    }

    public boolean prefersMaximumNutrients() {
        return nutrientPreferenceMode == NutrientPreferenceMode.MAXIMUM;
    }
}
