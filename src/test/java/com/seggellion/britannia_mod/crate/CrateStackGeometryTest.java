package com.seggellion.britannia_mod.crate;

import static com.seggellion.britannia_mod.crate.CrateVariant.MEDIUM;
import static com.seggellion.britannia_mod.crate.CrateVariant.SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Where a column's crates actually end up, in the picture and in the hitbox.
 *
 * <h2>The nine hundredths</h2>
 *
 * <p>Packing measures a crate by its extent, but a model is drawn from its own origin, and for the
 * medium crate those differ: its art begins at 0.09 rather than 0. Treating the packed base as a
 * model origin would leave a 0.09-voxel seam under every medium crate in a mixed column — small
 * enough to look like a rendering artefact and large enough to see. These tests exist to keep that
 * distinction honest, because it is invisible in the arithmetic and obvious on screen.
 */
class CrateStackGeometryTest {

    /** One hundredth of a voxel, as a fraction of a block, plus room for float conversion. */
    private static final double TOLERANCE = 1.0D / 1600.0D / 4.0D;

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /* ─── the variant contract ───────────────────────────────── */

    @Test
    void everyVariantsExtentIsTheDistanceBetweenItsAuthoredBounds() {
        for (CrateVariant variant : CrateVariant.values()) {
            assertEquals(variant.authoredMaxYHundredths() - variant.authoredMinYHundredths(),
                    variant.heightHundredths(),
                    variant + " packs by a height its own model does not have");
        }
        assertEquals(0, SMALL.authoredMinYHundredths());
        assertEquals(715, SMALL.authoredMaxYHundredths());
        assertEquals(9, MEDIUM.authoredMinYHundredths());
        assertEquals(1160, MEDIUM.authoredMaxYHundredths());
    }

    /* ─── rendered position ──────────────────────────────────── */

    /**
     * The property the whole milestone is judged on: art lands exactly on the crate below.
     *
     * <p>Asserted against the art, not the offset, because the offset is the thing that would be
     * wrong if this were done naively and asserting it would simply restate the bug.
     */
    @Test
    void everyCratesArtStartsWhereTheCrateBelowItEnds() {
        for (CrateVariant[] column : new CrateVariant[][] {
                {SMALL, SMALL},
                {SMALL, SMALL, SMALL},
                {MEDIUM, MEDIUM},
                {SMALL, MEDIUM},
                {MEDIUM, SMALL},
                {SMALL, MEDIUM, SMALL, MEDIUM}}) {

            CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(column);
            int expectedBase = 0;
            for (LogicalCrate crate : stack.crates()) {
                CratePlacement placement = stack.placementOf(crate.id());
                CrateStackSlice.Entry entry = entryFor(stack, crate.id(), placement.firstCell());

                int artBase = entry.artBaseHundredths()
                        + placement.firstCell() * CrateStackLayout.CELL_HUNDREDTHS;
                int artTop = entry.artTopHundredths()
                        + placement.firstCell() * CrateStackLayout.CELL_HUNDREDTHS;

                assertEquals(expectedBase, artBase,
                        "a crate's art must begin where the one below it ended");
                assertEquals(expectedBase + crate.variant().heightHundredths(), artTop,
                        "a crate's art must be exactly as tall as it packs");
                expectedBase = artTop;
            }
        }
    }

    /** The worked example from the milestone: a medium crate resting on a small one. */
    @Test
    void aMediumOnASmallIsDrawnFromSevenPointOneFive() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, MEDIUM);
        LogicalCrate medium = stack.topCrate();
        CrateStackSlice.Entry entry = entryFor(stack, medium.id(), 0);

        assertEquals(706, entry.offsetHundredths(),
                "the model moves by the packed base less its own authored minimum");
        assertEquals(715, entry.artBaseHundredths(), "the medium's art must start at 7.15");
        assertEquals(1866, entry.artTopHundredths(), "and end at 18.66");
    }

    /** And the reverse order, where a naive origin would produce an overlap rather than a gap. */
    @Test
    void aSmallOnAMediumIsDrawnFromElevenPointFiveOne() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(MEDIUM, SMALL);
        LogicalCrate small = stack.topCrate();
        CrateStackSlice.Entry entry = entryFor(stack, small.id(), 0);

        assertEquals(1151, entry.offsetHundredths());
        assertEquals(1151, entry.artBaseHundredths(), "the small crate's art must start at 11.51");
        assertEquals(1866, entry.artTopHundredths());
    }

    /* ─── cells ──────────────────────────────────────────────── */

    @Test
    void aCrateCrossingABoundaryIsDrawnFromBothCells() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL);
        LogicalCrate crossing = stack.topCrate();

        CrateStackSlice.Entry lower = entryFor(stack, crossing.id(), 0);
        CrateStackSlice.Entry upper = entryFor(stack, crossing.id(), 1);

        assertEquals(1430, lower.artBaseHundredths(), "seen from the root cell it starts at 14.30");
        assertEquals(-170, upper.artBaseHundredths(),
                "seen from the cell above it started 1.70 voxels below the floor");
        assertEquals(2145 - CrateStackLayout.CELL_HUNDREDTHS, upper.artTopHundredths());
    }

    @Test
    void cellsBeyondTheColumnHoldNothing() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        assertFalse(stack.sliceFor(0).isEmpty());
        assertTrue(stack.sliceFor(1).isEmpty(), "a one-cell column must not claim a second cell");
        assertTrue(CrateStackTestSupport.emptyStack().sliceFor(0).isEmpty());
    }

    /* ─── collision ──────────────────────────────────────────── */

    /**
     * A column's hitbox has to sit where its crates are drawn, and stop where they stop.
     *
     * <p>The shape starts at the floor rather than at the medium crate's authored 0.09, because a
     * shape is clipped to its cell and a crate resting on another has nothing under it to fall
     * through; what matters is that it does not reach past the top crate into open air.
     */
    @Test
    void theCellShapeFollowsTheCratesAndStopsWithThem() {
        CrateStackBlockEntity twoSmall = CrateStackTestSupport.stackOf(SMALL, SMALL);
        VoxelShape shape = CrateStackShapes.cellShape(twoSmall.sliceFor(0));

        assertFalse(shape.isEmpty());
        assertEquals(0.0D, shape.min(Direction.Axis.Y), TOLERANCE, "the column rests on the floor");
        assertEquals(1430.0D / 1600.0D, shape.max(Direction.Axis.Y), TOLERANCE,
                "and stops at the top of the upper crate, not at the top of the block");
        assertTrue(shape.max(Direction.Axis.Y) < 1.0D, "there must be open space above the crates");
    }

    @Test
    void aCrossingCrateContributesToBothCellsAndNeitherOverflows() {
        CrateStackBlockEntity threeSmall = CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL);

        VoxelShape lower = CrateStackShapes.cellShape(threeSmall.sliceFor(0));
        VoxelShape upper = CrateStackShapes.cellShape(threeSmall.sliceFor(1));

        assertEquals(1.0D, lower.max(Direction.Axis.Y), TOLERANCE,
                "the root cell is filled to its ceiling by the crate passing through it");
        assertEquals(0.0D, upper.min(Direction.Axis.Y), TOLERANCE,
                "and the cell above continues from its floor");
        assertEquals(545.0D / 1600.0D, upper.max(Direction.Axis.Y), TOLERANCE,
                "ending 5.45 voxels up, where the third crate ends");
        assertTrue(upper.max(Direction.Axis.Y) < 1.0D);
    }

    /** A mixed column must be one continuous solid, with no seam an entity could fall into. */
    @Test
    void aMixedColumnHasNoGapBetweenItsCrates() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, MEDIUM);
        VoxelShape shape = CrateStackShapes.cellShape(stack.sliceFor(0));

        // The union is continuous from the floor to the cell ceiling: at the contact plane the small
        // crate's top and the medium crate's shape meet with nothing between them.
        assertEquals(0.0D, shape.min(Direction.Axis.Y), TOLERANCE);
        assertEquals(1.0D, shape.max(Direction.Axis.Y), TOLERANCE);
        for (double probe = 0.05D; probe < 0.95D; probe += 0.05D) {
            double sampled = probe;
            assertTrue(shape.toAabbs().stream()
                            .anyMatch(box -> box.minY <= sampled && box.maxY >= sampled),
                    "the column has a hole at Y " + sampled);
        }
    }

    @Test
    void anEmptySliceHasNoShapeAtAll() {
        assertTrue(CrateStackShapes.cellShape(CrateStackSlice.empty()).isEmpty());
    }

    /** Turning a crate must turn its hitbox, or a rotated column is solid where it looks open. */
    @Test
    void shapesFollowEachCratesOwnFacing() {
        VoxelShape north = CrateStackShapes.shapeFor(SMALL, Direction.NORTH);
        VoxelShape east = CrateStackShapes.shapeFor(SMALL, Direction.EAST);

        assertEquals(1.0D - north.max(Direction.Axis.Z), east.min(Direction.Axis.X), 1.0E-6D);
        assertEquals(north.min(Direction.Axis.X), east.min(Direction.Axis.Z), 1.0E-6D);
        assertEquals(north.max(Direction.Axis.Y), east.max(Direction.Axis.Y), 1.0E-6D);
    }

    private static CrateStackSlice.Entry entryFor(
            CrateStackBlockEntity stack, int crateId, int cell) {
        return stack.sliceFor(cell).entries().stream()
                .filter(entry -> entry.crateId() == crateId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("crate " + crateId + " is absent from cell " + cell));
    }
}
