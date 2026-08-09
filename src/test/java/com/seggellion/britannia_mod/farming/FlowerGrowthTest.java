package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerGrowthTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void speciesProfilesExposeValidatedClimateRangesAndInitialCropScaleTimings() {
        Map<net.minecraft.resources.ResourceLocation, Integer> expectedTicks = new LinkedHashMap<>();
        expectedTicks.put(FlowerRegistry.POPPY, 5);
        expectedTicks.put(FlowerRegistry.SNOWDROP, 7);
        expectedTicks.put(FlowerRegistry.LILY, 8);
        expectedTicks.put(FlowerRegistry.FOXGLOVE, 9);
        expectedTicks.put(FlowerRegistry.CAMPION, 6);
        expectedTicks.put(FlowerRegistry.HYACINTH, 7);
        expectedTicks.put(FlowerRegistry.ORFLUER, 9);

        for (FlowerDefinition definition : registry.definitions().values()) {
            FlowerGrowthProfile profile = definition.growthProfile();
            assertEquals(expectedTicks.get(definition.id()), profile.baseGrowthTicks(), definition.id().toString());
            assertTrue(profile.baseGrowthTicks() >= 4 && profile.baseGrowthTicks() <= 10);
            assertTrue(profile.minHydrationToGrow() <= profile.hydrationIdeal());
            assertTrue(profile.hydrationIdeal() <= profile.maxHydrationBeforeSeverePenalty());
            assertTrue(profile.minAltitude() <= profile.idealMinAltitude());
            assertTrue(profile.idealMinAltitude() <= profile.idealMaxAltitude());
            assertTrue(profile.idealMaxAltitude() <= profile.maxAltitude());
            assertTrue(profile.allowedClimates().containsAll(profile.idealClimates()));
            assertFalse(profile.allowedClimates().stream().anyMatch(profile.forbiddenClimates()::contains));
        }
    }

    @Test
    void idealConditionsAdvanceEverySpeciesToItsNaturalMaximumAndStop() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            FlowerPersistentState state = idealState(definition);
            int evaluations = 0;
            while (!FlowerGrowthEvaluator.isMature(state, definition) && evaluations++ < 50) {
                FlowerGrowthEvaluation evaluation = idealEvaluation(state, definition);
                assertTrue(evaluation.multiplier() > 0.0f, definition.id().toString());
                state = FlowerGrowthEvaluator.advance(state, evaluation);
            }

            assertTrue(FlowerGrowthEvaluator.isMature(state, definition), definition.id().toString());
            assertEquals(definition.naturalMaximumStage(), state.growthStage(), definition.id().toString());
            assertEquals(1.0f, state.growthState().progress(), 0.0001f);
            FlowerPersistentState stopped = FlowerGrowthEvaluator.advance(state, idealEvaluation(state, definition));
            assertEquals(state, stopped, definition.id().toString());
        }
    }

    @Test
    void poppyAutomaticGrowthClampsToSixWhileOtherSpeciesReachSeven() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerPersistentState poppyState = growToMaturity(idealState(poppy), poppy);
        assertEquals(6, poppyState.growthStage());
        assertNotEquals(7, poppyState.growthStage());

        FlowerPersistentState manuallyRaised = poppyState.withGrowth(
                7, new FlowerGrowthState(0.75f, poppyState.growthState().tickProgress(), false)
        );
        assertTrue(FlowerGrowthEvaluator.isMature(manuallyRaised, poppy));
        assertEquals(manuallyRaised, FlowerGrowthEvaluator.advance(
                manuallyRaised, idealEvaluation(manuallyRaised, poppy)
        ));

        registry.definitions().values().stream().filter(definition -> !definition.isPoppy()).forEach(definition ->
                assertEquals(7, growToMaturity(idealState(definition), definition).growthStage(), definition.id().toString())
        );
    }

    @Test
    void zeroHydrationAndUnsuitableWorldConditionsPauseWithoutResetAndResume() {
        FlowerDefinition lily = registry.byId(FlowerRegistry.LILY).orElseThrow();
        FlowerPersistentState intermediate = idealState(lily).withGrowth(3, new FlowerGrowthState(0.37f, 4, false));
        FlowerPersistentState dry = intermediate.withSoil(intermediate.soil().withHydration(0));
        FlowerGrowthEvaluation dryEvaluation = FlowerGrowthEvaluator.evaluate(
                dry, lily, lily.growthProfile().idealClimates().iterator().next(), idealAltitude(lily)
        );
        assertEquals(FlowerGrowthEvaluation.BlockReason.ZERO_HYDRATION, dryEvaluation.blockingReason());
        FlowerPersistentState blocked = FlowerGrowthEvaluator.advance(dry, dryEvaluation);
        assertEquals(0.37f, blocked.growthState().progress(), 0.0001f);
        assertEquals(3, blocked.growthStage());
        assertTrue(blocked.growthState().blocked());
        assertIdentityPreserved(intermediate, blocked, false);

        FarmingClimate unsuitable = lily.growthProfile().forbiddenClimates().iterator().next();
        FlowerGrowthEvaluation climateEvaluation = FlowerGrowthEvaluator.evaluate(
                intermediate, lily, unsuitable, idealAltitude(lily)
        );
        assertEquals(0.0f, climateEvaluation.multiplier(), 0.0001f);
        assertEquals(FlowerGrowthEvaluation.BlockReason.UNSUITABLE_CLIMATE, climateEvaluation.blockingReason());

        FlowerGrowthEvaluation altitudeEvaluation = FlowerGrowthEvaluator.evaluate(
                intermediate, lily, lily.growthProfile().idealClimates().iterator().next(), lily.growthProfile().minAltitude() - 1
        );
        assertEquals(FlowerGrowthEvaluation.BlockReason.UNSUITABLE_ALTITUDE, altitudeEvaluation.blockingReason());

        FlowerPersistentState restored = blocked.withSoil(intermediate.soil());
        FlowerPersistentState resumed = FlowerGrowthEvaluator.advance(restored, idealEvaluation(restored, lily));
        assertTrue(resumed.growthState().progress() > blocked.growthState().progress());
        assertFalse(resumed.growthState().blocked());
        assertIdentityPreserved(intermediate, resumed, false);
    }

    @Test
    void toleratedClimateIsSlowerAndUnsuitableNutrientsReduceSharedMultiplier() {
        FlowerDefinition snowdrop = registry.byId(FlowerRegistry.SNOWDROP).orElseThrow();
        FlowerPersistentState ideal = idealState(snowdrop);
        FarmingClimate preferred = snowdrop.growthProfile().idealClimates().iterator().next();
        FarmingClimate tolerated = snowdrop.growthProfile().allowedClimates().stream()
                .filter(climate -> !snowdrop.growthProfile().idealClimates().contains(climate))
                .findFirst().orElseThrow();
        float preferredMultiplier = FlowerGrowthEvaluator.evaluate(ideal, snowdrop, preferred, idealAltitude(snowdrop)).multiplier();
        float toleratedMultiplier = FlowerGrowthEvaluator.evaluate(ideal, snowdrop, tolerated, idealAltitude(snowdrop)).multiplier();
        assertTrue(toleratedMultiplier > 0.0f);
        assertTrue(toleratedMultiplier < preferredMultiplier);

        FlowerSoilSnapshot poorSoil = new FlowerSoilSnapshot(
                ideal.soil().hydration(), ideal.soil().fertilizerLevel(),
                0.0f, 0.0f, 0.0f, 0.0f,
                ideal.soil().origin(), ideal.soil().communityRestoration(),
                ideal.soil().communitySeedableUntilGameTime()
        );
        float poorMultiplier = FlowerGrowthEvaluator.evaluate(
                ideal.withSoil(poorSoil), snowdrop, preferred, idealAltitude(snowdrop)
        ).multiplier();
        assertTrue(poorMultiplier > 0.0f);
        assertTrue(poorMultiplier < preferredMultiplier);
    }

    @Test
    void oneBasedStageMappingHasExplicitBoundariesAndNeverSelectsPoppySeven() {
        assertEquals(1, FlowerGrowthEvaluator.progressToStage(0.0f, 7));
        assertEquals(1, FlowerGrowthEvaluator.progressToStage(Math.nextDown(1.0f / 7.0f), 7));
        assertEquals(2, FlowerGrowthEvaluator.progressToStage(1.0f / 7.0f, 7));
        assertEquals(7, FlowerGrowthEvaluator.progressToStage(6.0f / 7.0f, 7));
        assertEquals(7, FlowerGrowthEvaluator.progressToStage(1.0f, 7));
        assertEquals(1, FlowerGrowthEvaluator.progressToStage(-1.0f, 7));
        assertEquals(7, FlowerGrowthEvaluator.progressToStage(2.0f, 7));
        assertEquals(6, FlowerGrowthEvaluator.progressToStage(1.0f, 6));
    }

    @Test
    void perennialResetReasonsPreserveIdentitySoilCommunityAndExactTint() {
        FlowerDefinition orfluer = registry.byId(FlowerRegistry.ORFLUER).orElseThrow();
        FlowerSoilSnapshot community = FlowerSoilSnapshot.communitySoil(
                4, 2, 0.61f, 0.62f, 0.63f, 0.64f, 998877L
        );
        FlowerPersistentState mature = state(orfluer, community).withGrowth(
                7, new FlowerGrowthState(1.0f, 21, true)
        );

        for (FlowerResetReason reason : FlowerResetReason.values()) {
            FlowerPersistentState reset = FlowerGrowthEvaluator.resetToStageOne(mature);
            assertEquals(1, reset.growthStage(), reason.name());
            assertEquals(FlowerGrowthState.newlyPlanted(), reset.growthState(), reason.name());
            assertIdentityPreserved(mature, reset, true);
            assertEquals(998877L, reset.soil().communitySeedableUntilGameTime());
            assertEquals(FlowerCommunityRestoration.currentRepositoryState(),
                    reset.soil().communityRestoration().orElseThrow());
        }
    }

    @Test
    void persistenceRoundTripsIntermediateMatureBlockedResetAndPoppySix() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerDefinition campion = registry.byId(FlowerRegistry.CAMPION).orElseThrow();
        FlowerPersistentState[] states = {
                state(campion, idealSoil(campion)),
                state(campion, idealSoil(campion)).withGrowth(4, new FlowerGrowthState(0.50f, 5, false)),
                state(campion, idealSoil(campion)).withGrowth(7, new FlowerGrowthState(1.0f, 12, false)),
                state(campion, idealSoil(campion)).withGrowth(3, new FlowerGrowthState(0.40f, 4, true)),
                FlowerGrowthEvaluator.resetToStageOne(state(campion, idealSoil(campion)).withGrowth(7, new FlowerGrowthState(1.0f, 12, false))),
                state(poppy, idealSoil(poppy)).withGrowth(6, new FlowerGrowthState(1.0f, 9, false))
        };
        for (FlowerPersistentState state : states) {
            assertEquals(state, FlowerPersistentState.fromTag(state.toTag(), registry));
        }

        FlowerPersistentState provenanceIsHistorical = state(campion, idealSoil(campion));
        FlowerGrowthEvaluation current = FlowerGrowthEvaluator.evaluate(
                provenanceIsHistorical, campion, FarmingClimate.WETLAND, idealAltitude(campion)
        );
        assertEquals(FarmingClimate.TEMPERATE, provenanceIsHistorical.regionProvenance().plantingClimate());
        assertEquals(FarmingClimate.WETLAND, current.currentClimate());
    }

    @Test
    void updateTagsGiveTwoObserversIdenticalChangedGrowthState() {
        FlowerDefinition foxglove = registry.byId(FlowerRegistry.FOXGLOVE).orElseThrow();
        FlowerPersistentState changed = state(foxglove, idealSoil(foxglove)).withGrowth(
                5, new FlowerGrowthState(0.72f, 8, true)
        );
        CompoundTag update = FlowerBlockEntity.clientStateTag(changed);
        FlowerPersistentState first = FlowerPersistentState.fromTag(update, registry);
        FlowerPersistentState second = FlowerPersistentState.fromTag(update.copy(), registry);
        assertEquals(first, second);
        assertEquals(5, first.growthStage());
        assertEquals(0.72f, first.growthState().progress(), 0.0001f);
        assertTrue(first.growthState().blocked());
        assertEquals(changed.color(), first.color());
        assertEquals(changed.soil().hydration(), first.soil().hydration());
    }

    @Test
    void sharedWeatherRulesAndExistingCropMultiplierRemainUnchanged() {
        assertFalse(FarmingBlock.hydrationDecays(0, 0.0f));
        assertTrue(FarmingBlock.hydrationDecays(3, 0.0999f));
        assertFalse(FarmingBlock.hydrationDecays(3, 0.10f));
        assertEquals(2, FarmingBlock.hydrationAfterRain(0, true));
        assertEquals(2, FarmingBlock.hydrationAfterRain(1, true));
        assertEquals(3, FarmingBlock.hydrationAfterRain(3, true));
        assertEquals(1, FarmingBlock.hydrationAfterRain(1, false));

        assertEquals(0.875f, CropQualityCalculator.growthMultiplier(CropGrowthContext.neutral(0.0f)), 0.0001f);
    }

    @Test
    void implementationRoutesThroughSharedCalculatorAndDoesNotSelectColour() throws IOException {
        String evaluator = source("farming/FlowerGrowthEvaluator.java");
        String flowerBlock = source("block/FlowerBlock.java");
        String flowerEntity = source("block/entity/FlowerBlockEntity.java");
        assertTrue(evaluator.contains("CropQualityCalculator.weightedNutrientFit"));
        assertTrue(evaluator.contains("CropQualityCalculator.hydrationFit"));
        assertTrue(evaluator.contains("CropQualityCalculator.growthMultiplier"));
        assertFalse(evaluator.contains("WeightedFlowerColorSelector"));
        assertFalse(evaluator.contains("FlowerColorSelector"));
        assertTrue(flowerBlock.contains("randomTick("));
        assertTrue(flowerBlock.contains("FarmingBlock.shouldDecayHydration"));
        assertTrue(flowerBlock.contains("FarmingBlock.hydrationAfterRain"));
        assertTrue(flowerEntity.contains("resetToStageOne(FlowerResetReason reason)"));
        assertFalse(flowerEntity.contains("WeightedFlowerColorSelector"));
    }

    private FlowerPersistentState growToMaturity(FlowerPersistentState state, FlowerDefinition definition) {
        for (int count = 0; count < 50 && !FlowerGrowthEvaluator.isMature(state, definition); count++) {
            state = FlowerGrowthEvaluator.advance(state, idealEvaluation(state, definition));
        }
        return state;
    }

    private FlowerGrowthEvaluation idealEvaluation(FlowerPersistentState state, FlowerDefinition definition) {
        return FlowerGrowthEvaluator.evaluate(
                state, definition, definition.growthProfile().idealClimates().iterator().next(), idealAltitude(definition)
        );
    }

    private FlowerPersistentState idealState(FlowerDefinition definition) {
        return state(definition, idealSoil(definition));
    }

    private FlowerSoilSnapshot idealSoil(FlowerDefinition definition) {
        FlowerGrowthProfile profile = definition.growthProfile();
        int hydration = Math.max(1, Math.min(5, Math.round(profile.hydrationIdeal() * 5.0f)));
        return FlowerSoilSnapshot.privateSoil(
                hydration, 0,
                profile.idealBoneMeal(), profile.idealTurquoise(),
                profile.idealSulphurousAsh(), profile.idealRottenFlesh()
        );
    }

    private FlowerPersistentState state(FlowerDefinition definition, FlowerSoilSnapshot soil) {
        return new FlowerPersistentState(
                FlowerPersistentState.CURRENT_DATA_VERSION,
                definition.id(),
                registry.fallbackColor(definition),
                1,
                FlowerPlantingOrigin.PLAYER,
                false,
                Optional.of(UUID.fromString("4eaf6428-3fb5-4099-9dc7-c4a5eb619132")),
                new FlowerRegionProvenance("Britain", FarmingClimate.TEMPERATE),
                new FlowerQuality(50),
                soil,
                FlowerGrowthState.newlyPlanted()
        );
    }

    private static int idealAltitude(FlowerDefinition definition) {
        FlowerGrowthProfile profile = definition.growthProfile();
        return profile.idealMinAltitude() + (profile.idealMaxAltitude() - profile.idealMinAltitude()) / 2;
    }

    private static void assertIdentityPreserved(
            FlowerPersistentState expected,
            FlowerPersistentState actual,
            boolean includeSoil
    ) {
        assertEquals(expected.dataVersion(), actual.dataVersion());
        assertEquals(expected.speciesId(), actual.speciesId());
        assertEquals(expected.color().tintValue(), actual.color().tintValue());
        assertEquals(expected.plantingOrigin(), actual.plantingOrigin());
        assertEquals(expected.protectedFlower(), actual.protectedFlower());
        assertEquals(expected.planterUuid(), actual.planterUuid());
        assertEquals(expected.regionProvenance(), actual.regionProvenance());
        assertEquals(expected.quality(), actual.quality());
        if (includeSoil) {
            assertEquals(expected.soil(), actual.soil());
        } else {
            assertEquals(expected.soil().origin(), actual.soil().origin());
            assertEquals(expected.soil().communityRestoration(), actual.soil().communityRestoration());
        }
    }

    private static String source(String relative) throws IOException {
        return Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8
        );
    }
}
