package com.seggellion.britannia_mod.farming;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowerDefinitionValidator {
    private static final Set<FarmingClimate> CANONICAL_CLIMATES = Set.copyOf(EnumSet.of(
            FarmingClimate.TEMPERATE,
            FarmingClimate.ICE,
            FarmingClimate.FIRE,
            FarmingClimate.WETLAND,
            FarmingClimate.TROPICAL,
            FarmingClimate.ARID,
            FarmingClimate.MAGICAL,
            FarmingClimate.UNDERGROUND
    ));

    private FlowerDefinitionValidator() {
    }

    public static Set<FarmingClimate> canonicalClimates() {
        return CANONICAL_CLIMATES;
    }

    public static ResourceLocation parseNamespacedId(String fieldName, String value) {
        if (value == null || value.isBlank() || !value.contains(":")) {
            throw new IllegalArgumentException(fieldName + " must be a non-blank namespaced ID: " + value);
        }
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " is malformed: " + value, exception);
        }
    }

    static void validateDefinitionValues(
            ResourceLocation id,
            ResourceLocation seedItemId,
            ResourceLocation harvestedItemId,
            int naturalMaximumStage,
            int absoluteMaximumStage,
            List<FlowerPaletteEntry> palette,
            ResourceLocation fallbackColorId,
            FlowerColorLifecycle colorLifecycle
    ) {
        requireNamespaced(id, "Flower species ID");
        requireNamespaced(seedItemId, "Flower seed item ID");
        requireNamespaced(harvestedItemId, "Harvested flower item ID");
        if (naturalMaximumStage < 1 || absoluteMaximumStage < 1) {
            throw new IllegalArgumentException("Flower stage maxima must be at least 1 for " + id);
        }
        if (naturalMaximumStage > absoluteMaximumStage) {
            throw new IllegalArgumentException("Natural maximum stage cannot exceed absolute maximum for " + id);
        }
        if (absoluteMaximumStage > 7) {
            throw new IllegalArgumentException("Initial flower absolute maximum cannot exceed stage 7 for " + id);
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("Flower palette cannot be empty for " + id);
        }
        Set<ResourceLocation> paletteIds = new HashSet<>();
        for (FlowerPaletteEntry entry : palette) {
            if (!paletteIds.add(entry.colorId())) {
                throw new IllegalArgumentException("Duplicate palette color " + entry.colorId() + " for " + id);
            }
        }
        if (!paletteIds.contains(fallbackColorId)) {
            throw new IllegalArgumentException("Fallback color " + fallbackColorId + " is outside the palette for " + id);
        }
        if (colorLifecycle != FlowerColorLifecycle.SELECT_ON_SERVER_PLANTING_AND_STORE) {
            throw new IllegalArgumentException("Flower color selection during load/deserialization is forbidden for " + id);
        }
    }

    static void validateGrowthValues(
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
            float qualitySensitivity
    ) {
        requireUnit("ideal bone meal/nitrogen", idealBoneMeal);
        requireUnit("ideal turquoise/phosphorus", idealTurquoise);
        requireUnit("ideal sulphurous ash/potassium", idealSulphurousAsh);
        requireUnit("ideal rotten flesh/organic matter", idealRottenFlesh);
        requirePositiveUnit("nutrient tolerance", nutrientTolerance);
        requirePositive("bone meal/nitrogen weight", boneMealWeight);
        requirePositive("turquoise/phosphorus weight", turquoiseWeight);
        requirePositive("sulphurous ash/potassium weight", sulphurousAshWeight);
        requirePositive("rotten flesh/organic matter weight", rottenFleshWeight);
        requireUnit("hydration ideal", hydrationIdeal);
        requirePositiveUnit("hydration under-tolerance", hydrationUnderTolerance);
        requirePositiveUnit("hydration over-tolerance", hydrationOverTolerance);
        requireUnit("minimum hydration", minHydrationToGrow);
        requireUnit("maximum hydration", maxHydrationBeforeSeverePenalty);
        if (minHydrationToGrow > hydrationIdeal || hydrationIdeal > maxHydrationBeforeSeverePenalty) {
            throw new IllegalArgumentException("Hydration range must contain the ideal value");
        }
        if (minAltitude < -64 || maxAltitude > 320 || minAltitude > maxAltitude) {
            throw new IllegalArgumentException("Altitude range must be ordered within Minecraft build bounds (-64..320)");
        }
        if (idealMinAltitude < minAltitude || idealMaxAltitude > maxAltitude || idealMinAltitude > idealMaxAltitude) {
            throw new IllegalArgumentException("Ideal altitude range must be ordered inside the tolerated altitude range");
        }
        requireUnit("tolerated climate modifier", regionToleranceOrModifier);
        if (!Float.isFinite(qualitySensitivity) || qualitySensitivity <= 0.0f) {
            throw new IllegalArgumentException("Quality sensitivity must be positive and finite");
        }
        if (idealClimates.isEmpty()) {
            throw new IllegalArgumentException("At least one preferred farming climate is required");
        }
        if (!CANONICAL_CLIMATES.containsAll(idealClimates)
                || !CANONICAL_CLIMATES.containsAll(allowedClimates)
                || !CANONICAL_CLIMATES.containsAll(forbiddenClimates)) {
            throw new IllegalArgumentException("Flower climate mappings must use only canonical FarmingClimate values");
        }
        if (!allowedClimates.containsAll(idealClimates)) {
            throw new IllegalArgumentException("Allowed climates must include every preferred climate");
        }
        Set<FarmingClimate> overlap = EnumSet.copyOf(allowedClimates);
        overlap.retainAll(forbiddenClimates);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Allowed and forbidden climates overlap: " + overlap);
        }
        Set<FarmingClimate> resolved = EnumSet.noneOf(FarmingClimate.class);
        resolved.addAll(allowedClimates);
        resolved.addAll(forbiddenClimates);
        if (!resolved.containsAll(CANONICAL_CLIMATES)) {
            throw new IllegalArgumentException("Every canonical FarmingClimate must resolve as allowed or unsuitable");
        }
    }

    static void validateRegistry(
            Map<ResourceLocation, FlowerColorDefinition> colors,
            Map<ResourceLocation, FlowerDefinition> definitions,
            boolean requireInitialSpecies
    ) {
        Set<ResourceLocation> seenSeeds = new HashSet<>();
        for (FlowerDefinition definition : definitions.values()) {
            if (!seenSeeds.add(definition.seedItemId())) {
                throw new IllegalArgumentException("Duplicate flower seed mapping: " + definition.seedItemId());
            }
            if (requireInitialSpecies && definition.palette().size() < 2) {
                throw new IllegalArgumentException("Initial species requires at least two allowed colors: " + definition.id());
            }
            Set<Integer> tintValues = new HashSet<>();
            for (FlowerPaletteEntry entry : definition.palette()) {
                FlowerColorDefinition color = colors.get(entry.colorId());
                if (color == null) {
                    throw new IllegalArgumentException("Unresolved palette color " + entry.colorId() + " for " + definition.id());
                }
                if (!tintValues.add(color.color().tintValue())) {
                    throw new IllegalArgumentException("Ambiguous duplicate tint value in palette for " + definition.id() + ": " + color.color().hex());
                }
            }
            if (!colors.containsKey(definition.fallbackColorId())) {
                throw new IllegalArgumentException("Unresolved fallback color " + definition.fallbackColorId() + " for " + definition.id());
            }
            if (definition.isPoppy()) {
                if (definition.naturalMaximumStage() != 6 || definition.absoluteMaximumStage() != 7) {
                    throw new IllegalArgumentException("Poppy must have natural maximum 6 and absolute maximum 7");
                }
            } else if (requireInitialSpecies
                    && (definition.naturalMaximumStage() != 7 || definition.absoluteMaximumStage() != 7)) {
                throw new IllegalArgumentException("Initial non-Poppy species must have natural and absolute maximum stage 7: " + definition.id());
            }
        }
    }

    private static void requireNamespaced(ResourceLocation id, String fieldName) {
        if (id.getNamespace().isBlank() || id.getPath().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be namespaced and non-blank");
        }
    }

    private static void requireUnit(String fieldName, float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(fieldName + " must be in the normalized range 0..1: " + value);
        }
    }

    private static void requirePositiveUnit(String fieldName, float value) {
        requireUnit(fieldName, value);
        if (value <= 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requirePositive(String fieldName, float value) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be positive and finite: " + value);
        }
    }
}
