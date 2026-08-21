package com.seggellion.britannia_mod.resource.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.ResourceShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 3: the shape planners, exhaustively, without a Minecraft world.
 *
 * <p>That this test class needs no world is the milestone's central structural claim. The six
 * legacy shapes could not have been tested this way at all — they took a {@code ServerLevel},
 * called {@code setBlock} as they went, and drew from the level's shared RNG, so "what does this
 * shape produce" was not a question with an answer. Every property below is now a plain assertion
 * about a pure function.
 */
class ShapePlannerTest {

    private static final long SEED_A = 0x51ED_C0DEL;
    private static final long SEED_B = 0xBEEF_F00DL;

    /** A radius each shape can actually serve, comfortably above its own minimum. */
    private static int workableRadius(ResourceShape shape) {
        return Math.max(6, shape.minimumRadius() + 2);
    }

    private static ShapeConfig config(ResourceShape shape, long seed) {
        return new ShapeConfig(workableRadius(shape), ShapeRotation.XZ, seed);
    }

    /* ------------------------------------------------------------------ */
    /*  Determinism                                                        */
    /* ------------------------------------------------------------------ */

    /** Same seed, same configuration, same result — as a list, not merely as a set. */
    @Test
    void everyShapeIsDeterministicForIdenticalInputs() {
        for (ResourceShape shape : ResourceShape.values()) {
            ShapeConfig config = config(shape, SEED_A);
            List<ShapeOffset> first = shape.planner().plan(config).offsets();
            List<ShapeOffset> second = shape.planner().plan(config).offsets();
            List<ShapeOffset> third = shape.planner().plan(
                    new ShapeConfig(config.radius(), config.rotation(), SEED_A)).offsets();

            assertEquals(first, second, shape + " must plan identically when replanned");
            assertEquals(first, third, shape + " must plan identically from an equal configuration");
            assertFalse(first.isEmpty(), shape + " planned nothing at radius " + config.radius());
        }
    }

    /** A different seed is a different deposit. Otherwise the seed would not be doing anything. */
    @Test
    void everyShapeRespondsToItsSeed() {
        for (ResourceShape shape : ResourceShape.values()) {
            List<ShapeOffset> a = shape.planner().plan(config(shape, SEED_A)).offsets();
            List<ShapeOffset> b = shape.planner().plan(config(shape, SEED_B)).offsets();
            assertNotEquals(a, b, shape + " ignores its seed, so its geometry is not seeded at all");
        }
    }

    /**
     * Planning is order-independent: interleaving two deposits does not disturb either.
     *
     * <p>This is what the legacy shapes could not do. They drew from {@code ServerLevel#getRandom()},
     * a stream shared with everything else on the server, so a vein's geometry depended on how many
     * numbers had been drawn before it — including by an unrelated system on another thread's tick.
     */
    @Test
    void planningOneShapeDoesNotDisturbAnother() {
        for (ResourceShape shape : ResourceShape.values()) {
            List<ShapeOffset> alone = shape.planner().plan(config(shape, SEED_A)).offsets();

            for (ResourceShape other : ResourceShape.values()) {
                other.planner().plan(config(other, SEED_B));
            }
            List<ShapeOffset> afterInterference = shape.planner().plan(config(shape, SEED_A)).offsets();
            assertEquals(alone, afterInterference,
                    shape + " changed after other shapes planned, so it shares mutable state");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Uniqueness and bounds                                              */
    /* ------------------------------------------------------------------ */

    /**
     * No cell is planned twice.
     *
     * <p>Every legacy shape counted one placement per {@code setBlock} call rather than per distinct
     * cell, so a random walk that crossed itself reported more blocks than it made.
     */
    @Test
    void everyShapePlansEachCellAtMostOnce() {
        for (ResourceShape shape : ResourceShape.values()) {
            List<ShapeOffset> offsets = shape.planner().plan(config(shape, SEED_A)).offsets();
            assertEquals(offsets.size(), new HashSet<>(offsets).size(),
                    shape + " planned the same cell more than once");
        }
    }

    /** Cells come back in canonical order, so a plan is comparable rather than merely equal. */
    @Test
    void everyPlanIsCanonicallyOrdered() {
        for (ResourceShape shape : ResourceShape.values()) {
            List<ShapeOffset> offsets = shape.planner().plan(config(shape, SEED_A)).offsets();
            List<ShapeOffset> sorted = new ArrayList<>(offsets);
            sorted.sort(null);
            assertEquals(sorted, offsets, shape + " returned its cells out of canonical order");
        }
    }

    /** Every cell falls inside the bounds the planner declared before it planned. */
    @Test
    void everyPlannedCellIsInsideTheDeclaredBounds() {
        for (ResourceShape shape : ResourceShape.values()) {
            for (long seed : List.of(SEED_A, SEED_B, 0L, -1L)) {
                ShapeConfig config = config(shape, seed);
                ShapeBounds declared = shape.planner().bounds(config);
                for (ShapeOffset offset : shape.planner().plan(config).offsets()) {
                    assertTrue(declared.contains(offset),
                            shape + " planned " + offset + " outside " + declared);
                }
            }
        }
    }

    /** The smallest legal configuration still plans something, and still stays in bounds. */
    @Test
    void everyShapeServesItsOwnMinimumRadius() {
        for (ResourceShape shape : ResourceShape.values()) {
            ShapeConfig smallest =
                    new ShapeConfig(shape.minimumRadius(), ShapeRotation.XZ, SEED_A);
            ShapePlan plan = shape.planner().plan(smallest);
            assertFalse(plan.isEmpty(),
                    shape + " plans nothing at its own declared minimum, so the minimum is wrong");
            for (ShapeOffset offset : plan.offsets()) {
                assertTrue(plan.bounds().contains(offset), shape + " strayed outside its bounds");
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Invalid configuration                                              */
    /* ------------------------------------------------------------------ */

    /**
     * A planner refuses a configuration it cannot serve, before planning.
     *
     * <p>The permanent cure for the crash milestone 1 could only contain from outside. No caller —
     * command, future world generation, or a test written next year — can reach the failure by
     * forgetting to validate first, because the planner validates itself.
     */
    @Test
    void everyPlannerRefusesARadiusBelowItsMinimum() {
        for (ResourceShape shape : ResourceShape.values()) {
            for (int radius : List.of(Integer.MIN_VALUE, -100, -1, 0, shape.minimumRadius() - 1)) {
                if (radius >= shape.minimumRadius()) continue;
                ShapeConfig config = new ShapeConfig(radius, ShapeRotation.XZ, SEED_A);
                IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                        () -> shape.planner().plan(config),
                        shape + " accepted radius " + radius);
                assertTrue(thrown.getMessage().contains("at least"),
                        shape + " must say what it needs: " + thrown.getMessage());
            }
        }
    }

    /**
     * The two historical crash boundaries, named.
     *
     * <p>{@code SnakeVein} computed {@code nextInt(radius - 9)} and {@code GeodeVein}
     * {@code nextInt(radius / 2)}; both threw, and both threw from inside a partially completed
     * command. Neither expression exists any more, and the radii that used to reach them are
     * refused with an explanation instead.
     */
    @Test
    void theHistoricalSnakeAndGeodeCrashRadiiAreRefusedNotThrownFrom() {
        for (int radius : List.of(0, 1, 2, 3)) {
            assertThrows(IllegalArgumentException.class, () -> ResourceShape.SNAKE.planner()
                    .plan(new ShapeConfig(radius, ShapeRotation.XZ, SEED_A)));
        }
        // The legacy floor of 10 was the bug's artefact; four is what the geometry actually needs.
        assertFalse(ResourceShape.SNAKE.planner()
                .plan(new ShapeConfig(4, ShapeRotation.XZ, SEED_A)).isEmpty());

        for (int radius : List.of(0, 1, 2)) {
            assertThrows(IllegalArgumentException.class, () -> ResourceShape.GEODE.planner()
                    .plan(new ShapeConfig(radius, ShapeRotation.XZ, SEED_A)));
        }
        assertFalse(ResourceShape.GEODE.planner()
                .plan(new ShapeConfig(3, ShapeRotation.XZ, SEED_A)).isEmpty());
    }

    /* ------------------------------------------------------------------ */
    /*  Geode geometry                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * The nodule is a crust around a hollow, not a box of scattered points.
     *
     * <p>Pinned as properties rather than as a snapshot of cell coordinates, so the shape can still
     * be balanced later without the test having to be rewritten. What must remain true is that it
     * is radial, that it is hollow, and that it does not stray outside its radius.
     */
    @Test
    void theGeodeIsARadialCrustAroundAHollow() {
        int radius = 8;
        ShapePlan plan = ResourceShape.GEODE.planner()
                .plan(new ShapeConfig(radius, ShapeRotation.XZ, SEED_A));
        assertFalse(plan.isEmpty());

        double closest = Double.MAX_VALUE;
        double furthest = 0.0;
        for (ShapeOffset offset : plan.offsets()) {
            double distance = Math.sqrt((double) offset.x() * offset.x()
                    + (double) offset.y() * offset.y() + (double) offset.z() * offset.z());
            closest = Math.min(closest, distance);
            furthest = Math.max(furthest, distance);
        }

        assertTrue(furthest <= radius,
                "the crust must stay inside its own radius, reached " + furthest);
        assertTrue(closest > 1.0,
                "the nodule must be hollow: nothing should sit at its centre, closest was " + closest);
        assertTrue(furthest - closest < radius,
                "a crust is a shell, not a filled ball: spanned " + closest + " to " + furthest);

        // Nothing at the centre, which is the single clearest difference from the old scatter.
        assertFalse(plan.offsets().contains(new ShapeOffset(0, 0, 0)),
                "the old algorithm scattered through the middle; a nodule is hollow there");
    }

    /** The crust grows with the radius, so the parameter means what it says. */
    @Test
    void theGeodeGrowsWithItsRadius() {
        int small = ResourceShape.GEODE.planner()
                .plan(new ShapeConfig(4, ShapeRotation.XZ, SEED_A)).count();
        int large = ResourceShape.GEODE.planner()
                .plan(new ShapeConfig(10, ShapeRotation.XZ, SEED_A)).count();
        assertTrue(large > small,
                "a bigger radius must be a bigger nodule, got " + small + " then " + large);
    }

    /* ------------------------------------------------------------------ */
    /*  Bedded shapes are world-blind                                      */
    /* ------------------------------------------------------------------ */

    /**
     * The two shapes that used to consult {@code isAir()} are now blind to the world.
     *
     * <p>Structurally: neither planner can see a world, because {@link ShapeConfig} carries none.
     * Behaviourally: they plan a full seam at any seed, where the legacy versions planned nothing
     * at all unless the cells happened to already be cave air. Silver in particular could not
     * generate in rock, which is the worst of the six defects — the resource generated nowhere.
     */
    @Test
    void theBeddedShapesPlanASeamRegardlessOfAnything() {
        for (ResourceShape shape : List.of(ResourceShape.LAYERED, ResourceShape.VERTICAL_LAYERED)) {
            for (long seed : List.of(SEED_A, SEED_B, 0L, 1L, -99L)) {
                ShapePlan plan = shape.planner()
                        .plan(new ShapeConfig(12, ShapeRotation.XZ, seed));
                assertTrue(plan.count() > 50,
                        shape + " planned only " + plan.count() + " cells at radius 12; the legacy "
                                + "air-only filter is exactly what this must no longer do");
            }
        }
    }

    /** The seam is broad and thin, which is what makes it a bed rather than a blob. */
    @Test
    void theHorizontalSeamIsBroaderThanItIsThick() {
        ShapePlan plan = ResourceShape.LAYERED.planner()
                .plan(new ShapeConfig(16, ShapeRotation.XZ, SEED_A));
        ShapeBounds bounds = plan.bounds();
        int width = bounds.maxX() - bounds.minX();
        int thickness = bounds.maxY() - bounds.minY();
        assertTrue(width > thickness * 4,
                "a bed spans far wider than it is deep, was " + width + " by " + thickness);
    }

    /** Rotation turns the standing sheet, and only the shapes that are directional respond. */
    @Test
    void rotationTurnsTheStandingSheetAndIsIgnoredByTheRest() {
        ShapePlanner sheet = ResourceShape.VERTICAL_LAYERED.planner();
        assertTrue(sheet.usesRotation());

        ShapeBounds acrossZ = sheet.bounds(new ShapeConfig(10, ShapeRotation.XZ, SEED_A));
        ShapeBounds acrossX = sheet.bounds(new ShapeConfig(10, ShapeRotation.ZW, SEED_A));
        assertTrue(acrossZ.maxZ() < acrossZ.maxX(), "XZ must be thin on Z");
        assertTrue(acrossX.maxX() < acrossX.maxZ(), "ZW must be thin on X");

        for (ResourceShape shape : List.of(ResourceShape.CLUSTER, ResourceShape.GEODE,
                ResourceShape.LAYERED, ResourceShape.VERTICAL)) {
            assertFalse(shape.usesRotation(), shape + " must declare that rotation does nothing");
            List<ShapeOffset> xz = shape.planner()
                    .plan(new ShapeConfig(8, ShapeRotation.XZ, SEED_A)).offsets();
            List<ShapeOffset> yz = shape.planner()
                    .plan(new ShapeConfig(8, ShapeRotation.YZ, SEED_A)).offsets();
            assertEquals(xz, yz, shape + " says it ignores rotation but does not");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Bounded work                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Every shipped resource's largest configured radius plans a bounded, sane number of cells.
     *
     * <p>Also the milestone's performance guardrail: it records the actual counts, so an accidental
     * explosion in a later change fails here rather than in a world.
     */
    @Test
    void theLargestConfiguredRadiusOfEveryShapeStaysWithinTheCellCap() {
        record Case(ResourceShape shape, int radius) {
        }
        List<Case> largest = List.of(
                new Case(ResourceShape.CLUSTER, 22),
                new Case(ResourceShape.VERTICAL, 128),
                new Case(ResourceShape.SNAKE, 128),
                new Case(ResourceShape.GEODE, 16),
                new Case(ResourceShape.LAYERED, 96),
                new Case(ResourceShape.VERTICAL_LAYERED, 96));

        for (Case largestCase : largest) {
            ShapePlan plan = largestCase.shape().planner()
                    .plan(new ShapeConfig(largestCase.radius(), ShapeRotation.XZ, SEED_A));
            assertTrue(plan.count() <= ShapePlan.MAX_CELLS,
                    largestCase.shape() + " at its configured maximum radius "
                            + largestCase.radius() + " plans " + plan.count()
                            + " cells, past the " + ShapePlan.MAX_CELLS + " cap");
            assertTrue(plan.count() > 0, largestCase.shape() + " planned nothing at its maximum");
        }
    }

    /** A plan that would exceed the cap is refused rather than silently truncated. */
    @Test
    void aPlanBeyondTheCellCapIsRefused() {
        List<ShapeOffset> tooMany = new ArrayList<>();
        for (int i = 0; i <= ShapePlan.MAX_CELLS; i++) {
            tooMany.add(new ShapeOffset(i, 0, 0));
        }
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ShapePlan.of(tooMany, ShapeBounds.cube(ShapePlan.MAX_CELLS + 1)));
        assertTrue(thrown.getMessage().contains("may not plan more than"));
    }

    /** A planner that strayed outside its own declared bounds would be caught building the plan. */
    @Test
    void aPlanOutsideItsDeclaredBoundsIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> ShapePlan.of(List.of(new ShapeOffset(99, 0, 0)), ShapeBounds.cube(2)));
    }

    /* ------------------------------------------------------------------ */
    /*  The noise itself                                                   */
    /* ------------------------------------------------------------------ */

    /** Per-cell noise is a pure function, stable across calls and distinct across salts. */
    @Test
    void perCellNoiseIsPureAndSaltsAreIndependent() {
        assertEquals(ShapeNoise.unit(SEED_A, 1, 3, 4, 5), ShapeNoise.unit(SEED_A, 1, 3, 4, 5));
        assertNotEquals(ShapeNoise.unit(SEED_A, 1, 3, 4, 5), ShapeNoise.unit(SEED_A, 2, 3, 4, 5));
        assertNotEquals(ShapeNoise.unit(SEED_A, 1, 3, 4, 5), ShapeNoise.unit(SEED_B, 1, 3, 4, 5));
        assertNotEquals(ShapeNoise.unit(SEED_A, 1, 3, 4, 5), ShapeNoise.unit(SEED_A, 1, 5, 4, 3));

        Set<Integer> buckets = new HashSet<>();
        for (int x = 0; x < 2000; x++) {
            float value = ShapeNoise.unit(SEED_A, 1, x, 0, 0);
            assertTrue(value >= 0.0f && value < 1.0f, "noise escaped [0,1): " + value);
            buckets.add((int) (value * 10));
        }
        assertEquals(10, buckets.size(), "noise must cover its range, not cluster");
    }

    @Test
    void noiseBoundsAreRespectedAndNonPositiveBoundsAreRefused() {
        for (int i = 0; i < 500; i++) {
            int value = ShapeNoise.below(SEED_A, 1, i, 0, 0, 7);
            assertTrue(value >= 0 && value < 7, "below() escaped its bound: " + value);
        }
        assertThrows(IllegalArgumentException.class, () -> ShapeNoise.below(SEED_A, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShapeNoise.below(SEED_A, 1, 0, 0, 0, -3));
    }
}
