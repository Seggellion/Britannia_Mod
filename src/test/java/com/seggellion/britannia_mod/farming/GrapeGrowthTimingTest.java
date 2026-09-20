package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Grapes advance by {@code growthProgress += growthMultiplier / baseGrowthTicks} once per random
 * tick, and nothing else. {@code baseGrowthTicks} counts growth callbacks at multiplier 1.0, not
 * server ticks, so it is the whole of the lifecycle length: at the previous value of 8 a well-tended
 * vine matured in roughly two random ticks. The owner-approved value is 16, exactly halving the rate.
 *
 * <p>All arithmetic here, no waiting: the quantity the simulation uses is the callback count, and
 * that is what these assertions count.
 */
class GrapeGrowthTimingTest {

    /** The owner decision this test exists to pin, so it cannot drift back. */
    private static final int APPROVED_GRAPE_BASE_GROWTH_TICKS = 16;

    /** What grapes used before the hotfix, kept so the doubling is asserted against a real number. */
    private static final int PREVIOUS_GRAPE_BASE_GROWTH_TICKS = 8;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void grapeBaseGrowthTicksIsTheApprovedValue() {
        assertEquals(APPROVED_GRAPE_BASE_GROWTH_TICKS, grapes().baseGrowthTicks());
    }

    @Test
    void aFreshCycleTakesExactlyTwiceAsLongAtEveryMultiplier() {
        for (float multiplier : new float[] {0.3283125f, 1.0f, 1.97f, 3.9375f}) {
            // Each callback contributes exactly half of what it used to: that is what halving the
            // rate means, and it holds exactly rather than to a rounding.
            assertEquals(
                    increment(PREVIOUS_GRAPE_BASE_GROWTH_TICKS, multiplier) / 2.0,
                    increment(grapes().baseGrowthTicks(), multiplier),
                    1.0e-9,
                    "the per-callback increment did not halve at multiplier " + multiplier);

            // So the cycle takes exactly twice as many callbacks, as a duration.
            double before = exactCallbacksToMature(PREVIOUS_GRAPE_BASE_GROWTH_TICKS, multiplier, 0.0);
            double after = exactCallbacksToMature(grapes().baseGrowthTicks(), multiplier, 0.0);
            assertEquals(before * 2.0, after, 1.0e-6,
                    "fresh growth at multiplier " + multiplier + " did not take exactly twice as long");

            // A partial callback still has to be paid in full, so the dispatched count is the
            // ceiling of that doubled duration -- never the old count.
            int dispatched = callbacksToMature(grapes().baseGrowthTicks(), multiplier, 0.0f);
            assertEquals((int) Math.ceil(after - 1.0e-6), dispatched,
                    "dispatched callbacks at multiplier " + multiplier + " are not the doubled duration");
            assertTrue(dispatched > callbacksToMature(PREVIOUS_GRAPE_BASE_GROWTH_TICKS, multiplier, 0.0f),
                    "the new base must never dispatch fewer callbacks at multiplier " + multiplier);
        }
    }

    @Test
    void regrowthStillResumesAtAgeFourAndAlsoTakesTwiceAsLong() {
        CropDefinition grapes = grapes();
        int regrowthAge = grapes.clampedPostHarvestRegrowthAge();
        assertEquals(4, regrowthAge, "perennial regrowth must still resume at age 4");

        // regrowAfterHarvest computes progress as regrowthAge / visualAgeCount.
        float regrowthProgress = Math.min(0.99f, regrowthAge / (float) grapes.visualAgeCount());
        assertEquals(0.5f, regrowthProgress, 1.0e-6f, "regrowth must still resume at half progress");
        assertEquals(regrowthAge, FarmingBlockEntityProgress.age(regrowthProgress, grapes),
                "the resumed progress must map back to the resumed age");

        for (float multiplier : new float[] {1.0f, 3.9375f}) {
            double before = exactCallbacksToMature(
                    PREVIOUS_GRAPE_BASE_GROWTH_TICKS, multiplier, regrowthProgress);
            double after = exactCallbacksToMature(grapes.baseGrowthTicks(), multiplier, regrowthProgress);
            assertEquals(before * 2.0, after, 1.0e-6,
                    "regrowth at multiplier " + multiplier + " did not take exactly twice as long");
            assertEquals(before, exactCallbacksToMature(
                            PREVIOUS_GRAPE_BASE_GROWTH_TICKS, multiplier, 0.0) / 2.0, 1.0e-6,
                    "regrowth must still start from half a lifecycle, not a full one");
        }
    }

    @Test
    void oneCallbackAdvancesGrowthExactlyOnce() {
        CropDefinition grapes = grapes();
        float multiplier = 1.0f;
        float expectedIncrement = multiplier / grapes.baseGrowthTicks();

        float afterOne = advance(0.0f, grapes.baseGrowthTicks(), multiplier);
        assertEquals(expectedIncrement, afterOne, 1.0e-6f,
                "a single growth callback must add exactly multiplier / baseGrowthTicks");
        assertEquals(1.0f / 16.0f, afterOne, 1.0e-6f);
        assertEquals(afterOne * 2.0f, advance(afterOne, grapes.baseGrowthTicks(), multiplier), 1.0e-6f,
                "a second callback must add the same increment again, never a larger one");

        // A callback that never happens cannot move anything. That an ordinary server tick does not
        // produce one, and that a random tick produces exactly one, is asserted live in
        // GrapeArborGameTests.growthAdvancesOncePerRandomTickAndNeverOnAPlainServerTick.
        assertEquals(16, callbacksToMature(grapes.baseGrowthTicks(), multiplier, 0.0f),
                "at multiplier 1.0 a fresh grape cycle is exactly baseGrowthTicks callbacks");
    }

    @Test
    void theAgeBandsAndMatureBoundaryAreUnchangedByTheNewBase() {
        CropDefinition grapes = grapes();
        assertEquals(7, grapes.maxGrowthAge());
        assertEquals(8, grapes.visualAgeCount());
        int[] expectedAgeByEighth = {0, 1, 2, 3, 4, 5, 6, 7};
        for (int eighth = 0; eighth < expectedAgeByEighth.length; eighth++) {
            float progress = eighth / 8.0f;
            assertEquals(expectedAgeByEighth[eighth], FarmingBlockEntityProgress.age(progress, grapes),
                    "progress " + progress + " changed which age it lands in");
        }
        assertEquals(7, FarmingBlockEntityProgress.age(1.0f, grapes));
    }

    @Test
    void noOtherCropsGrowthTicksChanged() {
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (CropDefinition crop : CropRegistry.all()) {
            actual.put(crop.id(), crop.baseGrowthTicks());
        }

        Map<String, Integer> approved = approvedBaseGrowthTicks();
        assertEquals(approved.keySet(), actual.keySet(), "the crop roster changed; update the approved map");

        Map<String, String> drifted = new LinkedHashMap<>();
        actual.forEach((id, value) -> {
            Integer expected = approved.get(id);
            if (!expected.equals(value)) {
                drifted.put(id, expected + " -> " + value);
            }
        });
        assertTrue(drifted.isEmpty(), "base growth values moved for " + drifted);
        assertEquals(APPROVED_GRAPE_BASE_GROWTH_TICKS, actual.get("grapes"));
    }

    /**
     * Callbacks needed to reach progress 1.0, counted the way {@code tickGrowth} counts them: the
     * increment is clamped at 1.0, so this is a ceiling, not a division.
     */
    private static int callbacksToMature(int baseGrowthTicks, float multiplier, float startProgress) {
        float progress = startProgress;
        int callbacks = 0;
        while (progress < 1.0f) {
            progress = advance(progress, baseGrowthTicks, multiplier);
            callbacks++;
            assertTrue(callbacks < 100_000, "growth never completed; increment was non-positive");
        }
        return callbacks;
    }

    /** Callbacks to maturity as a real duration, before a partial one is rounded up to a whole. */
    private static double exactCallbacksToMature(int baseGrowthTicks, float multiplier, double startProgress) {
        return (1.0 - startProgress) / increment(baseGrowthTicks, multiplier);
    }

    private static double increment(int baseGrowthTicks, float multiplier) {
        return (double) multiplier / Math.max(1, baseGrowthTicks);
    }

    /** The exact increment from {@code FarmingBlockEntity.tickGrowth}. */
    private static float advance(float progress, int baseGrowthTicks, float multiplier) {
        return Math.min(1.0f, progress + (multiplier / Math.max(1, baseGrowthTicks)));
    }

    private static CropDefinition grapes() {
        return CropRegistry.byId("grapes").orElseThrow();
    }

    /** Mirrors {@code FarmingBlockEntity.progressToAge} without needing a block entity. */
    private static final class FarmingBlockEntityProgress {
        private static int age(float progress, CropDefinition crop) {
            return com.seggellion.britannia_mod.block.entity.FarmingBlockEntity.progressToAge(progress, crop);
        }
    }

    /**
     * Every crop's base growth value, transcribed by hand from the registry as it stood when this
     * hotfix was written. Literal on purpose: read back from {@code CropRegistry} it would assert
     * nothing, and a retuning that leaks sideways into another crop is exactly what it guards.
     */
    private static Map<String, Integer> approvedBaseGrowthTicks() {
        Map<String, Integer> approved = new LinkedHashMap<>();
        approved.put("squash", 5);
        approved.put("carrot", 5);
        approved.put("corn", 7);
        approved.put("cabbage", 6);
        approved.put("lettuce", 4);
        approved.put("yellow_onion", 5);
        approved.put("green_onion", 4);
        approved.put("pumpkin", 7);
        approved.put("potato", 5);
        approved.put("watermelon", 7);
        approved.put("vanilla_potato", 5);
        approved.put("wheat", 5);
        approved.put("rye", 5);
        approved.put("barley", 5);
        approved.put("oats", 5);
        approved.put("mustard", 5);
        approved.put("beans", 6);
        approved.put("rice", 7);
        approved.put("tomato", 6);
        approved.put("garlic", 7);
        approved.put("ginseng", 9);
        approved.put("mandrake", 10);
        approved.put("nightshade", 9);
        approved.put("brown_mushroom", 6);
        approved.put("red_mushroom", 6);
        approved.put("vanilla_pumpkin", 6);
        approved.put("vanilla_melon", 6);
        approved.put("pineapple", 8);
        approved.put("strawberry", 6);
        approved.put("blueberry", 7);
        approved.put("raspberry", 6);
        approved.put("cranberry", 7);
        approved.put("blackberry", 6);
        approved.put("huckleberry", 7);
        approved.put("mulberry", 6);
        approved.put("elderberry", 6);
        approved.put("cherries", 10);
        approved.put("cotton", 8);
        approved.put("flax", 6);
        approved.put("hemp", 6);
        approved.put("hops", 7);
        approved.put("snow_peas", 5);
        approved.put("peas", 6);
        approved.put("turnips", 5);
        approved.put("apple", 10);
        approved.put("pear", 10);
        approved.put("peach", 10);
        approved.put("lemon", 10);
        approved.put("lime", 10);
        approved.put("orange", 10);
        approved.put("olive", 10);
        approved.put("plum", 9);
        approved.put("bell_peppers", 6);
        approved.put("cucumbers", 6);
        approved.put("honeydew", 7);
        approved.put("cantaloupe", 7);
        approved.put("banana", 9);
        approved.put("broccoli", 6);
        approved.put("cauliflower", 7);
        approved.put("rhubarb", 6);
        approved.put("celery", 6);
        approved.put("tobacco", 7);
        approved.put("radish", 4);
        approved.put("parsnip", 5);
        approved.put("yam", 6);
        approved.put("rutabaga", 6);
        approved.put("grapes", 16);  // the only value this hotfix changes: was 8
        return approved;
    }
}
