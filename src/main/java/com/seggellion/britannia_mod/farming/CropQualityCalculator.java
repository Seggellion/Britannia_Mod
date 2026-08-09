package com.seggellion.britannia_mod.farming;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;

public final class CropQualityCalculator {
    public static final String CROP_ID_KEY = "CropId";
    public static final String QUALITY_SCORE_KEY = "QualityScore";
    public static final String LEGACY_QUALITY_KEY = "Quality";
    public static final String COMMODITY_QUALITY_KEY = "CommodityQuality";

    private CropQualityCalculator() {
    }

    public static float fitScore(float actual, float ideal, float tolerance) {
        float safeTolerance = Math.max(0.01f, tolerance);
        float distance = Math.abs(actual - ideal);
        if (distance > safeTolerance) {
            return 0.0f;
        }
        return Math.max(0.05f, 1.0f - (distance / safeTolerance));
    }

    public static float hydrationFit(float actual, FarmingGrowthProfile crop) {
        float hydration = Math.max(0.0f, Math.min(1.0f, actual));
        if (hydration < crop.minHydrationToGrow()) {
            return 0.0f;
        }
        if (hydration > crop.maxHydrationBeforeSeverePenalty()) {
            return 0.0f;
        }
        if (hydration <= crop.hydrationIdeal()) {
            return fitOneSided(hydration, crop.hydrationIdeal(), crop.hydrationUnderTolerance());
        }
        return fitOneSided(hydration, crop.hydrationIdeal(), crop.hydrationOverTolerance());
    }

    private static float fitOneSided(float actual, float ideal, float tolerance) {
        float safeTolerance = Math.max(0.01f, tolerance);
        float distance = Math.abs(actual - ideal);
        if (distance > safeTolerance) {
            return 0.0f;
        }
        return Math.max(0.05f, 1.0f - (distance / safeTolerance));
    }

    public static float weightedNutrientFit(
            FarmingGrowthProfile crop,
            float boneMeal,
            float turquoise,
            float sulphurousAsh,
            float rottenFlesh
    ) {
        if (crop.prefersMaximumNutrients()) {
            return weightedMaximumNutrientFit(crop, boneMeal, turquoise, sulphurousAsh, rottenFlesh);
        }

        float boneMealFit = fitScore(boneMeal, crop.idealBoneMeal(), crop.nutrientTolerance());
        float turquoiseFit = fitScore(turquoise, crop.idealTurquoise(), crop.nutrientTolerance());
        float sulphurousAshFit = fitScore(sulphurousAsh, crop.idealSulphurousAsh(), crop.nutrientTolerance());
        float rottenFleshFit = fitScore(rottenFlesh, crop.idealRottenFlesh(), crop.nutrientTolerance());

        return weightedAverage(crop, boneMealFit, turquoiseFit, sulphurousAshFit, rottenFleshFit);
    }

    private static float weightedMaximumNutrientFit(
            FarmingGrowthProfile crop,
            float boneMeal,
            float turquoise,
            float sulphurousAsh,
            float rottenFlesh
    ) {
        float boneMealFit = fitScore(boneMeal, 1.0f, crop.nutrientTolerance());
        float turquoiseFit = fitScore(turquoise, 1.0f, crop.nutrientTolerance());
        float sulphurousAshFit = fitScore(sulphurousAsh, 1.0f, crop.nutrientTolerance());
        float rottenFleshFit = fitScore(rottenFlesh, 1.0f, crop.nutrientTolerance());

        return weightedAverage(crop, boneMealFit, turquoiseFit, sulphurousAshFit, rottenFleshFit);
    }

    private static float weightedAverage(
            FarmingGrowthProfile crop,
            float boneMealFit,
            float turquoiseFit,
            float sulphurousAshFit,
            float rottenFleshFit
    ) {
        float totalWeight = crop.boneMealWeight() + crop.turquoiseWeight() + crop.sulphurousAshWeight() + crop.rottenFleshWeight();
        if (totalWeight <= 0.0f) {
            return (boneMealFit + turquoiseFit + sulphurousAshFit + rottenFleshFit) / 4.0f;
        }

        return (boneMealFit * crop.boneMealWeight()
                + turquoiseFit * crop.turquoiseWeight()
                + sulphurousAshFit * crop.sulphurousAshWeight()
                + rottenFleshFit * crop.rottenFleshWeight()) / totalWeight;
    }

    public static float growthMultiplier(CropGrowthContext context) {
        if (!context.climateAllowed() || !context.altitudeAllowed() || !context.specialEnvironmentAllowed()) {
            return 0.0f;
        }
        float nutrientGrowthModifier = lerp(0.25f, 1.75f, context.nutrientFit());
        float hydrationGrowthModifier = lerp(0.25f, 1.50f, context.hydrationFit());
        float climateGrowthModifier = lerp(0.50f, 1.50f, context.climateFit());
        float latticeModifier = context.latticeSatisfied() ? 1.0f : 0.0f;
        return nutrientGrowthModifier * hydrationGrowthModifier * climateGrowthModifier * latticeModifier;
    }

    public static int calculateQuality(CropDefinition crop, CropGrowthContext context, Player player) {
        return calculateQuality(crop, context, player, 0);
    }

    public static int calculateQuality(CropDefinition crop, CropGrowthContext context, Player player, int rootAgeDays) {
        return calculateQuality(crop, context, rootAgeQualityBonus(crop, rootAgeDays));
    }

    public static int calculateQuality(FarmingGrowthProfile profile, CropGrowthContext context) {
        return calculateQuality(profile, context, 0);
    }

    private static int calculateQuality(FarmingGrowthProfile crop, CropGrowthContext context, int rootAgeQualityBonus) {
        if (!context.climateAllowed() || !context.altitudeAllowed() || !context.specialEnvironmentAllowed()) {
            return 0;
        }

        float conditionScore =
                context.nutrientFit() * 0.45f
                        + context.hydrationFit() * 0.30f
                        + context.climateFit() * 0.25f;

        conditionScore = lerp(conditionScore, conditionScore * conditionScore, Math.max(0.0f, crop.qualitySensitivity() - 1.0f));
        int quality = Math.round(conditionScore * 100.0f);
        quality += rootAgeQualityBonus;

        if (context.nutrientFit() < 0.40f) {
            quality = Math.min(quality, 5);
        }
        if (context.hydrationFit() < 0.40f) {
            quality = Math.min(quality, 6);
        }
        if (context.climateFit() < 0.40f) {
            quality = Math.min(quality, 7);
        }
        if (context.idealGrowth()) {
            quality = 100;
        }

        return Math.max(1, Math.min(100, quality));
    }

    public static int rootAgeQualityBonus(CropDefinition crop, int rootAgeDays) {
        if (crop == null || !crop.usesRootAgeQualityBonus() || rootAgeDays <= 0) {
            return 0;
        }
        float maturity = Math.min(1.0f, rootAgeDays / (float) crop.rootAgeQualityBonusMaturityDays());
        return Math.max(0, Math.min(crop.maxRootAgeQualityBonus(), Math.round(maturity * crop.maxRootAgeQualityBonus())));
    }

    public static void applyQuality(ItemStack stack, CropDefinition crop, int quality) {
        setQuality(stack, quality);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        int clamped = clampQuality(quality);
        tag.putString(CROP_ID_KEY, crop.id());
        tag.putInt(QUALITY_SCORE_KEY, clamped);
        tag.putInt(LEGACY_QUALITY_KEY, clamped);
        tag.putInt(COMMODITY_QUALITY_KEY, clamped);
        tag.putString("QualityLabel", qualityLabel(quality));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void applyQuality(ItemStack stack, ResourceLocation produceId, int quality) {
        setQuality(stack, quality);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        int clamped = clampQuality(quality);
        tag.putString(CROP_ID_KEY, produceId.toString());
        tag.putInt(QUALITY_SCORE_KEY, clamped);
        tag.putInt(LEGACY_QUALITY_KEY, clamped);
        tag.putInt(COMMODITY_QUALITY_KEY, clamped);
        tag.putString("QualityLabel", qualityLabel(quality));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (tag.contains(QUALITY_SCORE_KEY)) {
            return clampQuality(tag.getInt(QUALITY_SCORE_KEY));
        }
        if (tag.contains(LEGACY_QUALITY_KEY)) {
            return clampQuality(tag.getInt(LEGACY_QUALITY_KEY));
        }
        if (tag.contains(COMMODITY_QUALITY_KEY)) {
            return clampQuality(tag.getInt(COMMODITY_QUALITY_KEY));
        }
        return 50;
    }

    public static boolean hasQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains(QUALITY_SCORE_KEY) || tag.contains(LEGACY_QUALITY_KEY) || tag.contains(COMMODITY_QUALITY_KEY);
    }

    public static boolean hasCropQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains(CROP_ID_KEY) && hasQuality(stack);
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        int clamped = clampQuality(quality);
        tag.putInt(QUALITY_SCORE_KEY, clamped);
        tag.putInt(LEGACY_QUALITY_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static Component qualityTooltip(ItemStack stack) {
        return Component.literal("Quality: " + getQuality(stack) + "/100").withStyle(ChatFormatting.GREEN);
    }

    public static String qualityLabel(int quality) {
        if (quality >= 90) return "Exceptional";
        if (quality >= 75) return "Excellent";
        if (quality >= 50) return "Good";
        if (quality >= 25) return "Common";
        return "Poor";
    }

    private static float lerp(float min, float max, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return min + (max - min) * clamped;
    }

    private static int clampQuality(int quality) {
        return Math.max(1, Math.min(100, quality));
    }
}
