package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

/** Central validation for Farming progression definition metadata and planting-item resolution. */
public final class FarmingSkillRequirementValidator {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int EXPECTED_CROPS = 67;
    public static final int EXPECTED_FLOWERS = 7;
    public static final int EXPECTED_SPECIES = EXPECTED_CROPS + EXPECTED_FLOWERS;
    public static final String APPROVED_SOURCE = "FARMING_SKILL_PROGRESSION_PROPOSAL.md";

    private FarmingSkillRequirementValidator() {
    }

    public static void validateValue(String speciesId, float value) {
        if (speciesId == null || speciesId.isBlank()) {
            throw new IllegalArgumentException("Farming requirement species ID is missing; expected source: "
                    + APPROVED_SOURCE);
        }
        if (!Float.isFinite(value) || value < 0.0f || value > 100.0f) {
            throw new IllegalArgumentException("Invalid minimum Farming skill for species " + speciesId
                    + ": " + value + " (expected finite 0..100 from " + APPROVED_SOURCE + ")");
        }
    }

    /** Called once from common setup after deferred item registration is available. */
    public static ValidationSummary validateRegisteredDefinitions() {
        List<SpeciesRegistration> registrations = registeredSpecies();
        ValidationSummary summary = validateRegistrations(registrations, true);

        for (SpeciesRegistration registration : registrations) {
            FarmingSkillRequirementResolver.ResolvedRequirement resolved =
                    FarmingSkillRequirementResolver.resolve(registration.plantingItemId())
                            .orElseThrow(() -> new IllegalStateException("Planting item "
                                    + registration.plantingItemId() + " does not resolve to species "
                                    + registration.speciesId() + "; expected source: " + APPROVED_SOURCE));
            if (!resolved.speciesId().equals(registration.speciesId())) {
                throw new IllegalStateException("Planting item " + registration.plantingItemId()
                        + " resolves to " + resolved.speciesId() + " instead of " + registration.speciesId()
                        + "; expected source: " + APPROVED_SOURCE);
            }
            if (Float.compare(resolved.minimumFarmingSkill(), registration.requirement().minimumFarmingSkill()) != 0) {
                throw new IllegalStateException("Conflicting minimum Farming skill for species "
                        + registration.speciesId() + " via planting item " + registration.plantingItemId()
                        + ": " + resolved.minimumFarmingSkill() + " versus "
                        + registration.requirement().minimumFarmingSkill());
            }
        }

        validateFruitTreeOverlays();
        requireSentinel(registrations, "britannia_mod:poppy", 20.0f);
        if (Float.compare(FlowerInteractionService.POPPY_STAGE_SEVEN_SKILL, 100.0f) != 0) {
            throw new IllegalStateException("Poppy stage-7 mastery must remain Farming 100, independently of "
                    + "ordinary Poppy requirement 20");
        }
        LOGGER.info("Validated Farming skill requirements: {} species, {} crops, {} flowers, {} planting items",
                summary.species(), summary.crops(), summary.flowers(), summary.plantingItems());
        return summary;
    }

    public static List<SpeciesRegistration> registeredSpecies() {
        List<SpeciesRegistration> registrations = new ArrayList<>(EXPECTED_SPECIES);
        for (CropDefinition definition : CropRegistry.all()) {
            Item plantingItem;
            try {
                plantingItem = Objects.requireNonNull(definition.seedItem().get(), "seed supplier returned null");
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Cannot resolve planting item for species " + definition.id()
                        + "; expected source: " + APPROVED_SOURCE, exception);
            }
            ResourceLocation plantingItemId = BuiltInRegistries.ITEM.getKey(plantingItem);
            registrations.add(new SpeciesRegistration(
                    definition.id(), plantingItemId, definition, DefinitionType.CROP
            ));
        }
        for (FlowerDefinition definition : FlowerRegistry.initial().definitions().values()) {
            registrations.add(new SpeciesRegistration(
                    definition.id().toString(), definition.seedItemId(), definition, DefinitionType.FLOWER
            ));
        }
        return List.copyOf(registrations);
    }

    public static ValidationSummary validateRegistrations(
            Collection<SpeciesRegistration> registrations,
            boolean requireInitialRoster
    ) {
        Objects.requireNonNull(registrations, "Farming species registrations are required");
        Map<String, SpeciesRegistration> bySpecies = new LinkedHashMap<>();
        Map<ResourceLocation, SpeciesRegistration> byItem = new LinkedHashMap<>();
        int crops = 0;
        int flowers = 0;

        for (SpeciesRegistration registration : registrations) {
            if (registration == null) {
                throw new IllegalArgumentException("Missing farming species registration; expected source: "
                        + APPROVED_SOURCE);
            }
            if (registration.requirement() == null) {
                throw new IllegalArgumentException("Species " + registration.speciesId()
                        + " has no minimum Farming requirement; expected source: " + APPROVED_SOURCE);
            }
            if (registration.plantingItemId() == null) {
                throw new IllegalArgumentException("Species " + registration.speciesId()
                        + " has no planting-item ID; expected source: " + APPROVED_SOURCE);
            }
            validateValue(registration.speciesId(), registration.requirement().minimumFarmingSkill());

            SpeciesRegistration previousSpecies = bySpecies.putIfAbsent(registration.speciesId(), registration);
            if (previousSpecies != null) {
                float previous = previousSpecies.requirement().minimumFarmingSkill();
                float current = registration.requirement().minimumFarmingSkill();
                String conflict = Float.compare(previous, current) == 0 ? "duplicate registry ID" : "conflicting values";
                throw new IllegalArgumentException("Species " + registration.speciesId() + " has " + conflict
                        + ": " + previous + " and " + current + "; expected source: " + APPROVED_SOURCE);
            }

            SpeciesRegistration previousItem = byItem.putIfAbsent(registration.plantingItemId(), registration);
            if (previousItem != null) {
                throw new IllegalArgumentException("Planting item " + registration.plantingItemId()
                        + " maps to multiple species: " + previousItem.speciesId() + " and "
                        + registration.speciesId() + "; expected source: " + APPROVED_SOURCE);
            }
            switch (registration.definitionType()) {
                case CROP -> crops++;
                case FLOWER -> flowers++;
            }
        }

        if (requireInitialRoster
                && (bySpecies.size() != EXPECTED_SPECIES || crops != EXPECTED_CROPS
                || flowers != EXPECTED_FLOWERS || byItem.size() != EXPECTED_SPECIES)) {
            throw new IllegalStateException("Incomplete Farming progression definitions: species=" + bySpecies.size()
                    + ", crops=" + crops + ", flowers=" + flowers + ", plantingItems=" + byItem.size()
                    + " (expected 74/67/7/74 from " + APPROVED_SOURCE + ")");
        }
        return new ValidationSummary(bySpecies.size(), crops, flowers, byItem.size());
    }

    public static void validateApprovedRoster(
            Collection<SpeciesRegistration> registrations,
            Set<String> catalogSpecies,
            Map<String, Float> approvedRequirements
    ) {
        ValidationSummary summary = validateRegistrations(registrations, true);
        Map<String, SpeciesRegistration> implemented = new LinkedHashMap<>();
        for (SpeciesRegistration registration : registrations) {
            implemented.put(registration.speciesId(), registration);
        }
        requireSameSpecies("catalog", catalogSpecies, "runtime definitions", implemented.keySet());
        requireSameSpecies("approved proposal", approvedRequirements.keySet(), "runtime definitions", implemented.keySet());
        for (Map.Entry<String, Float> approved : approvedRequirements.entrySet()) {
            float actual = implemented.get(approved.getKey()).requirement().minimumFarmingSkill();
            if (Float.compare(approved.getValue(), actual) != 0) {
                throw new IllegalArgumentException("Proposal mismatch for species " + approved.getKey()
                        + ": implementation=" + actual + ", approved=" + approved.getValue()
                        + "; expected source: " + APPROVED_SOURCE);
            }
        }
        if (summary.plantingItems() != EXPECTED_SPECIES) {
            throw new IllegalStateException("Expected 74 planting-item mappings, found " + summary.plantingItems());
        }
    }

    private static void requireSameSpecies(String expectedName, Set<String> expected, String actualName, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalArgumentException("Farming species mismatch between " + expectedName + " and "
                    + actualName + ": missing=" + missing + ", extra=" + extra
                    + "; expected source: " + APPROVED_SOURCE);
        }
    }

    private static void validateFruitTreeOverlays() {
        for (FruitTreeDefinition tree : FruitTreeRegistry.all()) {
            CropDefinition crop = CropRegistry.byId(tree.id()).orElseThrow(() -> new IllegalStateException(
                    "Fruit-tree overlay " + tree.id() + " has no authoritative CropDefinition requirement"));
            if (tree.seedOrSaplingItem().get() != crop.seedItem().get()) {
                throw new IllegalStateException("Fruit-tree overlay " + tree.id()
                        + " planting item conflicts with its authoritative CropDefinition");
            }
        }
    }

    private static void requireSentinel(
            Collection<SpeciesRegistration> registrations,
            String speciesId,
            float expected
    ) {
        SpeciesRegistration registration = registrations.stream()
                .filter(candidate -> candidate.speciesId().equals(speciesId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing sentinel species " + speciesId));
        float actual = registration.requirement().minimumFarmingSkill();
        if (Float.compare(actual, expected) != 0) {
            throw new IllegalStateException("Invalid sentinel requirement for " + speciesId + ": " + actual
                    + " (expected " + expected + " from " + APPROVED_SOURCE + ")");
        }
    }

    public enum DefinitionType {
        CROP,
        FLOWER
    }

    public record SpeciesRegistration(
            String speciesId,
            ResourceLocation plantingItemId,
            FarmingSkillRequirement requirement,
            DefinitionType definitionType
    ) {
    }

    public record ValidationSummary(int species, int crops, int flowers, int plantingItems) {
    }
}
