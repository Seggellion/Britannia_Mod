package com.seggellion.britannia_mod.resource.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.shape.planner.SedimentaryLensPlanner;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The sedimentary lens as geometry: deterministic, bounded, bedded, and in one piece.
 *
 * <p>The interesting assertions here are the shape ones. "Deterministic" and "unique cells" are
 * contract checks every planner has to pass; what makes this a lens rather than a renamed seam is
 * that it is much broader than it is thick, that it thins towards its rim instead of merely getting
 * patchier, and that it is a single connected body. Those are asserted directly.
 */
class SedimentaryLensPlannerTest {

    private static final SedimentaryLensPlanner PLANNER = new SedimentaryLensPlanner();

    private static ShapeConfig config(int radius, long seed) {
        return new ShapeConfig(radius, ShapeRotation.XZ, seed);
    }

    private static ShapeConfig config(int radius, long seed, ShapeTuning tuning) {
        return new ShapeConfig(radius, ShapeRotation.XZ, seed, tuning);
    }

    /* ------------------------------------------------------------------ */
    /*  Planner contract                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    void identicalInputsGiveAnIdenticalLens() {
        ShapePlan first = PLANNER.plan(config(10, 0xA5A5A5L));
        ShapePlan second = PLANNER.plan(config(10, 0xA5A5A5L));
        assertEquals(first.offsets(), second.offsets(),
                "the same seed and radius must draw the same bed, cell for cell and in the same order");
    }

    /** A different seed must be a different bed, not a cosmetically different one. */
    @Test
    void aDifferentSeedGivesAMeaningfullyDifferentLens() {
        Set<ShapeOffset> first = new LinkedHashSet<>(PLANNER.plan(config(10, 1L)).offsets());
        Set<ShapeOffset> second = new LinkedHashSet<>(PLANNER.plan(config(10, 2L)).offsets());
        assertNotEquals(first, second);

        Set<ShapeOffset> shared = new HashSet<>(first);
        shared.retainAll(second);
        double overlap = shared.size() / (double) Math.max(first.size(), second.size());
        assertTrue(overlap < 0.92,
                "two seeds produced beds overlapping " + Math.round(overlap * 100)
                        + "% of their cells, which is not meaningful variation");
        assertTrue(overlap > 0.10,
                "two seeds produced beds sharing only " + Math.round(overlap * 100)
                        + "% of their cells; a lens of a given radius should still be recognisably"
                        + " the same kind of object");
    }

    @Test
    void everyCellIsUniqueAndInsideTheDeclaredBounds() {
        for (long seed : new long[] {1L, 7L, 4242L, -99L}) {
            ShapeConfig config = config(12, seed);
            ShapePlan plan = PLANNER.plan(config);
            ShapeBounds bounds = PLANNER.bounds(config);

            assertEquals(plan.offsets().size(), new HashSet<>(plan.offsets()).size(),
                    "seed " + seed + " planned the same cell twice");
            for (ShapeOffset offset : plan.offsets()) {
                assertTrue(bounds.contains(offset),
                        "seed " + seed + " planned " + offset + " outside its declared bounds");
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  What makes it a bed                                                */
    /* ------------------------------------------------------------------ */

    @Test
    void theFootprintIsFarBroaderThanTheBedIsThick() {
        for (long seed : new long[] {3L, 31L, 777L}) {
            ShapePlan plan = PLANNER.plan(config(12, seed, new ShapeTuning(4, 0.22, 0.25)));
            int spanX = span(plan, ShapeOffset::x);
            int spanY = span(plan, ShapeOffset::y);
            int spanZ = span(plan, ShapeOffset::z);
            int breadth = Math.max(spanX, spanZ);

            assertTrue(breadth >= spanY * 3,
                    "seed " + seed + " drew a bed " + breadth + " across and " + spanY
                            + " deep, which is not a lens");
            assertTrue(spanY >= 3, "seed " + seed + " drew a bed only " + spanY + " deep");
        }
    }

    /**
     * Thickest in the middle, thinning outwards: the property that makes it a lens and not a slab.
     *
     * <p>Measured as the <em>mean</em> column height per annulus rather than the maximum. The rim
     * wanders, so the tallest column ten blocks out is whichever one the noise happened to push
     * furthest in — a single favourable sample, not the shape. The average over a ring is the
     * profile.
     */
    @Test
    void theBedThinsTowardsItsRim() {
        // A bed with enough thickness for the profile to have resolution. A five-cell bed taper
        // is only three steps deep, so it is measured separately below.
        ShapePlan plan = PLANNER.plan(config(20, 5150L, new ShapeTuning(8, 0.15, 0.2)));
        double core = meanColumnHeight(plan, 0, 5);
        double middle = meanColumnHeight(plan, 9, 13);
        double rim = meanColumnHeight(plan, 17, 99);

        assertTrue(core > middle + 1.0,
                "mean column is " + core + " at the core and " + middle + " midway out; a lens must taper");
        assertTrue(middle > rim + 1.0,
                "mean column is " + middle + " midway out and " + rim + " at the rim; the taper stops");
        assertTrue(rim < core / 2.0,
                "the outermost ring averages " + rim + " cells against " + core + " at the core;"
                        + " the bed ends in a cliff rather than pinching out");
    }

    /**
     * A thin bed tapers too, in the coarse steps its thickness allows.
     *
     * <p>Silica's own configuration is a thin bed, so this is the case that actually ships. With
     * only two half-cells to work with the profile has three steps rather than a curve, and the
     * assertion says so honestly instead of pretending to a resolution the geometry does not have.
     */
    @Test
    void aThinBedStillTapersInTheStepsItHas() {
        ShapePlan plan = PLANNER.plan(config(14, 5150L, new ShapeTuning(5, 0.15, 0.2)));
        double core = meanColumnHeight(plan, 0, 4);
        double rim = meanColumnHeight(plan, 12, 99);
        assertTrue(core > rim + 1.0,
                "a thin bed averages " + core + " at the core and " + rim + " at the rim, which is"
                        + " not a taper at all");
        assertTrue(rim < 3.0, "a thin bed's rim averages " + rim + " cells");
    }

    /** The footprint is elliptical rather than a perfect disc. */
    @Test
    void theFootprintIsEllipticalRatherThanCircular() {
        int elongated = 0;
        for (long seed = 0; seed < 24; seed++) {
            ShapePlan plan = PLANNER.plan(config(12, seed));
            int spanX = span(plan, ShapeOffset::x);
            int spanZ = span(plan, ShapeOffset::z);
            double ratio = Math.min(spanX, spanZ) / (double) Math.max(spanX, spanZ);
            if (ratio < 0.9) {
                elongated++;
            }
        }
        assertTrue(elongated >= 12,
                "only " + elongated + " of 24 beds were noticeably elliptical; the footprint reads"
                        + " as a disc");
    }

    /**
     * One body, not a field of islands.
     *
     * <p>The single most important assertion in this class. Six-connectivity, no diagonals: cells
     * touching only at a corner do not count as joined, because they do not read as joined either.
     */
    @Test
    void everyLensIsOneConnectedBody() {
        for (long seed : new long[] {1L, 2L, 3L, 88L, 1234L, -7L}) {
            for (int radius : new int[] {6, 8, 12, 20}) {
                ShapePlan plan = PLANNER.plan(config(radius, seed));
                assertEquals(1, connectedComponents(plan.offsets()),
                        "radius " + radius + " seed " + seed + " produced a bed in "
                                + connectedComponents(plan.offsets()) + " disconnected pieces");
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Chunk slicing                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Slicing partitions the plan; it never re-rolls it.
     *
     * <p>Sliced on 16-block boundaries the way a chunk would, in an order chosen to be wrong on
     * purpose, and reassembled. This is the property the whole natural-generation design rests on.
     */
    @Test
    void theUnionOfChunkSlicesIsExactlyTheWholePlan() {
        ShapePlan plan = PLANNER.plan(config(20, 909L));
        Set<ShapeOffset> whole = new LinkedHashSet<>(plan.offsets());

        List<Set<ShapeOffset>> slices = new ArrayList<>();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                int chunkX = cx;
                int chunkZ = cz;
                Set<ShapeOffset> slice = new LinkedHashSet<>();
                for (ShapeOffset offset : plan.offsets()) {
                    if (Math.floorDiv(offset.x(), 16) == chunkX && Math.floorDiv(offset.z(), 16) == chunkZ) {
                        slice.add(offset);
                    }
                }
                slices.add(slice);
            }
        }

        java.util.Collections.reverse(slices);
        Set<ShapeOffset> reassembled = new LinkedHashSet<>();
        int total = 0;
        for (Set<ShapeOffset> slice : slices) {
            total += slice.size();
            reassembled.addAll(slice);
        }

        assertEquals(whole, reassembled, "reassembling the slices did not reproduce the plan");
        assertEquals(whole.size(), total, "the slices overlapped, so a cell belongs to two chunks");
    }

    /* ------------------------------------------------------------------ */
    /*  Configuration validity                                             */
    /* ------------------------------------------------------------------ */

    @Test
    void theSmallestValidLensPlans() {
        ShapePlan plan = PLANNER.plan(config(6, 1L, new ShapeTuning(2, 0.0, 0.0)));
        assertTrue(plan.count() > 0, "the minimum valid configuration planned nothing");
        assertEquals(1, connectedComponents(plan.offsets()));
    }

    /** A large but legal lens plans, and stays inside the platform's cell cap. */
    @Test
    void theLargestSensibleLensStaysWithinThePlanCap() {
        ShapePlan plan = PLANNER.plan(config(28, 1L, new ShapeTuning(4, 0.3, 0.3)));
        assertTrue(plan.count() > 2_000, "a radius-28 lens planned only " + plan.count() + " cells");
        assertTrue(plan.count() <= ShapePlan.MAX_CELLS,
                "a large lens planned " + plan.count() + " cells, over the platform cap");
        assertEquals(1, connectedComponents(plan.offsets()));
    }

    /**
     * A configuration that would blow the cell cap is refused by the shape, before planning.
     *
     * <p>The point is where the refusal comes from. Letting {@code ShapePlan} catch it would mean
     * drawing the whole thing first and failing with a message about a limit rather than about the
     * configuration that broke it.
     */
    @Test
    void aLensBigEnoughToBlowTheCellCapIsRefusedBeforeItIsDrawn() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PLANNER.plan(config(60, 1L, new ShapeTuning(8, 0.3, 0.3))));
        assertTrue(failure.getMessage().contains("over the " + ShapePlan.MAX_CELLS + "-cell limit"),
                failure.getMessage());
    }

    @Test
    void aRadiusBelowTheShapeMinimumIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PLANNER.plan(config(5, 1L)));
    }

    /** A bed narrower than it is deep is not a bed, and is refused rather than drawn. */
    @Test
    void aLensThatWouldBeDeeperThanItIsBroadIsRefused() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PLANNER.plan(config(8, 1L, new ShapeTuning(8, 0.2, 0.2))));
        assertTrue(failure.getMessage().contains("broader than it is deep"), failure.getMessage());
    }

    @Test
    void tuningOutsideItsSafeRangeIsRefusedByTheConfigurationItself() {
        assertThrows(IllegalArgumentException.class, () -> new ShapeTuning(-1, 0.2, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ShapeTuning(99, 0.2, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ShapeTuning(4, 1.5, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ShapeTuning(4, 0.2, 0.95));
    }

    /** With no tuning at all the planner still draws a coherent bed. */
    @Test
    void aResourceThatNamesNoTuningStillGetsALens() {
        ShapePlan plan = PLANNER.plan(config(10, 42L));
        assertTrue(plan.count() > 100, "the default lens is suspiciously small: " + plan.count());
        assertEquals(1, connectedComponents(plan.offsets()));
        assertTrue(span(plan, ShapeOffset::y) < span(plan, ShapeOffset::x));
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private interface Axis {
        int of(ShapeOffset offset);
    }

    private static int span(ShapePlan plan, Axis axis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (ShapeOffset offset : plan.offsets()) {
            min = Math.min(min, axis.of(offset));
            max = Math.max(max, axis.of(offset));
        }
        return max - min + 1;
    }

    /** Mean height of the columns whose horizontal distance falls in {@code [from, to]}. */
    private static double meanColumnHeight(ShapePlan plan, double from, double to) {
        java.util.Map<Long, int[]> extent = new java.util.HashMap<>();
        for (ShapeOffset offset : plan.offsets()) {
            double d = Math.sqrt((double) offset.x() * offset.x() + (double) offset.z() * offset.z());
            if (d < from || d > to) {
                continue;
            }
            long key = ((long) offset.x() << 32) ^ (offset.z() & 0xFFFFFFFFL);
            int[] span = extent.computeIfAbsent(key, ignored -> new int[] {offset.y(), offset.y()});
            span[0] = Math.min(span[0], offset.y());
            span[1] = Math.max(span[1], offset.y());
        }
        if (extent.isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (int[] span : extent.values()) {
            total += span[1] - span[0] + 1;
        }
        return total / (double) extent.size();
    }

    /** Six-connected components over the planned cells. */
    private static int connectedComponents(List<ShapeOffset> offsets) {
        Set<ShapeOffset> remaining = new HashSet<>(offsets);
        int components = 0;
        while (!remaining.isEmpty()) {
            components++;
            ShapeOffset start = remaining.iterator().next();
            Deque<ShapeOffset> queue = new ArrayDeque<>();
            queue.add(start);
            remaining.remove(start);
            while (!queue.isEmpty()) {
                ShapeOffset cell = queue.poll();
                for (ShapeOffset neighbour : neighbours(cell)) {
                    if (remaining.remove(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
        }
        return components;
    }

    private static List<ShapeOffset> neighbours(ShapeOffset cell) {
        return List.of(
                new ShapeOffset(cell.x() + 1, cell.y(), cell.z()),
                new ShapeOffset(cell.x() - 1, cell.y(), cell.z()),
                new ShapeOffset(cell.x(), cell.y() + 1, cell.z()),
                new ShapeOffset(cell.x(), cell.y() - 1, cell.z()),
                new ShapeOffset(cell.x(), cell.y(), cell.z() + 1),
                new ShapeOffset(cell.x(), cell.y(), cell.z() - 1));
    }
}
