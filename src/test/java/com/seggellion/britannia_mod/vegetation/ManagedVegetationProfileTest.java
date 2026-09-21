package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.farming.FlowerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationProfileTest {
    @Test
    void defaultProfileIsExactSeventyFiveTwentyFiveSplit() {
        ManagedVegetationProfile profile = ManagedVegetationProfile.defaults();

        assertEquals(100, profile.totalWeight());
        assertEquals(75, profile.byId(ManagedVegetationProfile.GRASS_FAMILY_ID).orElseThrow().weight());
        assertEquals(20, profile.byId(ManagedVegetationProfile.FERN_ID).orElseThrow().weight());
        assertEquals(5, profile.byId(ManagedVegetationProfile.FLOWER_ID).orElseThrow().weight());
    }

    @Test
    void controlledRollsMapToStableEntryBoundaries() {
        ManagedVegetationProfile profile = ManagedVegetationProfile.defaults();

        assertEquals(ManagedVegetationProfile.GRASS_FAMILY_ID, profile.selectByRoll(0).id());
        assertEquals(ManagedVegetationProfile.GRASS_FAMILY_ID, profile.selectByRoll(74).id());
        assertEquals(ManagedVegetationProfile.FERN_ID, profile.selectByRoll(75).id());
        assertEquals(ManagedVegetationProfile.FERN_ID, profile.selectByRoll(94).id());
        assertEquals(ManagedVegetationProfile.FLOWER_ID, profile.selectByRoll(95).id());
        assertEquals(ManagedVegetationProfile.FLOWER_ID, profile.selectByRoll(99).id());
        assertThrows(IllegalArgumentException.class, () -> profile.selectByRoll(100));
    }

    @Test
    void swampProfileReplacesOnlyTheFivePercentFlowerSlotWithBloodMoss() {
        ManagedVegetationProfile ordinary = ManagedVegetationProfile.ofWeights(75, 20, 5, false);
        ManagedVegetationProfile swamp = ManagedVegetationProfile.ofWeights(75, 20, 5, true);

        assertEquals(100, ordinary.totalWeight());
        assertEquals(100, swamp.totalWeight());
        assertEquals(75, swamp.byId(ManagedVegetationProfile.GRASS_FAMILY_ID).orElseThrow().weight());
        assertEquals(20, swamp.byId(ManagedVegetationProfile.FERN_ID).orElseThrow().weight());
        assertEquals(5, swamp.byId(ManagedVegetationProfile.BLOOD_MOSS_ID).orElseThrow().weight());
        assertTrue(swamp.byId(ManagedVegetationProfile.FLOWER_ID).isEmpty());
        assertTrue(ordinary.byId(ManagedVegetationProfile.BLOOD_MOSS_ID).isEmpty());
        assertEquals(ManagedVegetationProfile.BLOOD_MOSS_ID, swamp.selectByRoll(95).id());
        assertEquals(ManagedVegetationProfile.BLOOD_MOSS_ID, swamp.selectByRoll(99).id());
    }

    @Test
    void newEntriesDoNotChangeTheSelectionAlgorithm() {
        ResourceLocation shrub = ResourceLocation.fromNamespaceAndPath("britannia_mod", "future_shrub");
        ManagedVegetationProfile profile = new ManagedVegetationProfile(List.of(
                new ManagedVegetationEntry(shrub, 2, ManagedVegetationGrowthStrategy.STATIC_FERN),
                new ManagedVegetationEntry(ManagedVegetationProfile.FERN_ID, 1,
                        ManagedVegetationGrowthStrategy.STATIC_FERN)
        ));

        assertEquals(shrub, profile.selectByRoll(0).id());
        assertEquals(shrub, profile.selectByRoll(1).id());
        assertEquals(ManagedVegetationProfile.FERN_ID, profile.selectByRoll(2).id());
    }

    @Test
    void flowerPoolComesDirectlyFromTheAuthoritativeRegistry() {
        assertEquals(
                new HashSet<>(FlowerRegistry.initial().definitions().keySet()),
                new HashSet<>(ManagedFlowerSpecies.available())
        );
        assertTrue(ManagedFlowerSpecies.available().size() >= 7);
    }

    @Test
    void randomizedDelayAlwaysStaysInsideItsConfiguredBounds() {
        RandomSource random = RandomSource.create(42L);
        for (int iteration = 0; iteration < 1_000; iteration++) {
            int delay = ManagedVegetationConfig.randomBetween(random, 12, 37);
            assertTrue(delay >= 12 && delay <= 37);
        }
        assertEquals(12, ManagedVegetationConfig.randomBetween(random, 12, 12));
    }

    @Test
    void naturalGrowthRollUsesOneInDenominatorSemantics() {
        assertTrue(ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_ENABLED);
        assertEquals(4_096, ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_CHANCE_DENOMINATOR);
        assertEquals(2, ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_SPEED_DIVISOR);
        assertEquals(24_576L, ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(4_096, 2));
        assertEquals(12_288L, ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(4_096, 1));
        RandomSource random = RandomSource.create(42L);
        assertTrue(ManagedVegetationConfig.oneIn(random, 1));
        assertThrows(IllegalArgumentException.class, () -> ManagedVegetationConfig.oneIn(random, 0));
        assertThrows(IllegalArgumentException.class, () -> ManagedVegetationConfig.oneIn(null, 10));
    }

    /**
     * The Patch 18C pace, written down, so the requirement "at least twice as slow" is the
     * assertion rather than something a reader has to recompute from two constants.
     */
    private static final long PATCH_18C_DISCOVERY_DENOMINATOR = 12_288L;
    private static final int PATCH_18C_STAGE_MIN_TICKS = 2_400;
    private static final int PATCH_18C_STAGE_MAX_TICKS = 4_800;

    @Test
    void discoveryIsAtLeastTwiceAsSlowAsPatch18C() {
        long effective = ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(
                ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_CHANCE_DENOMINATOR,
                ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_SPEED_DIVISOR);

        assertTrue(effective >= 2L * PATCH_18C_DISCOVERY_DENOMINATOR,
                "effective discovery denominator " + effective + " must be at least "
                        + (2L * PATCH_18C_DISCOVERY_DENOMINATOR));
        assertEquals(24_576L, effective);
    }

    @Test
    void grassStageIsAtLeastTwiceAsSlowAsPatch18C() {
        long minimum = ManagedVegetationConfig.scaledGrassGrowthTicks(PATCH_18C_STAGE_MIN_TICKS);
        long maximum = ManagedVegetationConfig.scaledGrassGrowthTicks(PATCH_18C_STAGE_MAX_TICKS);

        assertEquals(4_800L, minimum);
        assertEquals(9_600L, maximum);
        assertTrue(minimum >= 2L * PATCH_18C_STAGE_MIN_TICKS);
        assertTrue(maximum >= 2L * PATCH_18C_STAGE_MAX_TICKS);
    }

    /**
     * The trap this hotfix exists to avoid. Production's serverconfig already holds the Patch 18C
     * base values, and a fresh world writes today's defaults; both must land on the same effective
     * pace, or the fix reaches only one of them.
     */
    @Test
    void legacyAndFreshConfigurationsProduceTheSameEffectiveTiming() {
        assertEquals(PATCH_18C_STAGE_MIN_TICKS, ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MIN_TICKS);
        assertEquals(PATCH_18C_STAGE_MAX_TICKS, ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MAX_TICKS);
        assertEquals(
                ManagedVegetationConfig.scaledGrassGrowthTicks(PATCH_18C_STAGE_MIN_TICKS),
                ManagedVegetationConfig.scaledGrassGrowthTicks(
                        ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MIN_TICKS));
        assertEquals(
                ManagedVegetationConfig.scaledGrassGrowthTicks(PATCH_18C_STAGE_MAX_TICKS),
                ManagedVegetationConfig.scaledGrassGrowthTicks(
                        ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MAX_TICKS));

        assertEquals(
                ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(4_096, 2),
                ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(
                        ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_CHANCE_DENOMINATOR,
                        ManagedVegetationConfig.DEFAULT_NATURAL_GROWTH_SPEED_DIVISOR));

        // A stage delay of exactly 4800..9600 is what both of them come to.
        assertEquals(4_800L, ManagedVegetationConfig.scaledGrassGrowthTicks(
                ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MIN_TICKS));
        assertEquals(9_600L, ManagedVegetationConfig.scaledGrassGrowthTicks(
                ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MAX_TICKS));
    }

    /**
     * The widest values the spec itself accepts must not be able to throw or wrap. The discovery
     * denominator now exceeds {@code Integer.MAX_VALUE} at the maximum, and a doubled stage length
     * overflows the int arithmetic in {@code randomBetween}; both are clamped rather than wrapped.
     */
    @Test
    void maximumConfiguredValuesNeitherThrowNorWrapNegative() {
        long widestDiscovery = ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(
                1_000_000, 1_000);
        assertEquals(3_000_000_000L, widestDiscovery);
        assertTrue(widestDiscovery > Integer.MAX_VALUE, "the maximum must exceed the int range");

        int boundedDiscovery = ManagedVegetationConfig.boundedRollDenominator(widestDiscovery);
        assertEquals(Integer.MAX_VALUE, boundedDiscovery);
        assertTrue(boundedDiscovery > 0, "a clamped denominator must never wrap negative");
        // The clamped value must be a legal nextInt bound: this is the call that used to throw.
        assertDoesNotThrow(
                () -> ManagedVegetationConfig.oneIn(RandomSource.create(7L), boundedDiscovery),
                "the widest accepted configuration must not throw inside the grass random tick");

        long widestStage = ManagedVegetationConfig.scaledGrassGrowthTicks(Integer.MAX_VALUE);
        assertEquals(2L * Integer.MAX_VALUE, widestStage);
        int boundedStage = ManagedVegetationConfig.boundedTickCount(widestStage);
        assertEquals(Integer.MAX_VALUE, boundedStage);
        assertTrue(boundedStage > 0, "a clamped stage length must never wrap negative");

        // The smallest accepted values stay exact and stay positive.
        assertEquals(3L, ManagedVegetationConfig.scaledNaturalGrowthChanceDenominator(1, 1));
        assertEquals(2L, ManagedVegetationConfig.scaledGrassGrowthTicks(1));
        assertEquals(1, ManagedVegetationConfig.boundedRollDenominator(1L));
        assertEquals(1, ManagedVegetationConfig.boundedTickCount(1L));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedVegetationConfig.boundedRollDenominator(0L));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedVegetationConfig.boundedTickCount(0L));
    }

    @Test
    void storedTimingBasesAreUnchangedSoLegacyWorldsStayCompatible() {
        assertEquals(1_200, ManagedVegetationConfig.DEFAULT_CUT_REGROW_MIN_TICKS);
        assertEquals(2_400, ManagedVegetationConfig.DEFAULT_CUT_REGROW_MAX_TICKS);
        assertEquals(2_400, ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MIN_TICKS);
        assertEquals(4_800, ManagedVegetationConfig.DEFAULT_GRASS_GROWTH_MAX_TICKS);
        assertEquals(200, ManagedVegetationConfig.DEFAULT_RETRY_TICKS);
        assertEquals(1_200, ManagedVegetationConfig.DEFAULT_FLOWER_STAGE_TICK_MULTIPLIER);
    }
}
