package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.farming.FlowerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

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
        RandomSource random = RandomSource.create(42L);
        assertTrue(ManagedVegetationConfig.oneIn(random, 1));
        assertThrows(IllegalArgumentException.class, () -> ManagedVegetationConfig.oneIn(random, 0));
        assertThrows(IllegalArgumentException.class, () -> ManagedVegetationConfig.oneIn(null, 10));
    }
}
