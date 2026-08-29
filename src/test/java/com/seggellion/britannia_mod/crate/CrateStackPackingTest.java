package com.seggellion.britannia_mod.crate;

import static com.seggellion.britannia_mod.crate.CrateVariant.MEDIUM;
import static com.seggellion.britannia_mod.crate.CrateVariant.SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.OptionalInt;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * How a column decides where its crates sit and how much world it claims.
 *
 * <p>Cell count is world occupancy, so it has to be a function of the crate list and nothing else.
 * That is the reason heights are hundredths of a voxel rather than doubles, and the boundary cases
 * below are the ones a floating-point sum would get wrong: a column that exactly fills its top cell
 * must not claim an empty one above it.
 */
class CrateStackPackingTest {

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /* ─── the measured constants ─────────────────────────────── */

    @Test
    void variantHeightsAreTheMeasuredModelExtents() {
        assertEquals(715, SMALL.heightHundredths(), "small crate is 7.15 voxels tall");
        assertEquals(1151, MEDIUM.heightHundredths(), "medium crate is 11.51 voxels tall");
        assertEquals(9, SMALL.slotCount());
        assertEquals(27, MEDIUM.slotCount());
        assertEquals(1600, CrateStackLayout.CELL_HUNDREDTHS);
        assertEquals(4, CrateStackLayout.MAX_CELLS);
        assertEquals(6400, CrateStackLayout.MAX_HEIGHT_HUNDREDTHS);
    }

    /** The large crate is a 2x2x2 multiblock and stays outside compact columns. */
    @Test
    void theLargeCrateIsNotAColumnVariant() {
        assertTrue(CrateVariant.forSlotCount(54).isEmpty(), "the 54-slot crate must not map to a variant");
        assertEquals(SMALL, CrateVariant.forSlotCount(9).orElseThrow());
        assertEquals(MEDIUM, CrateVariant.forSlotCount(27).orElseThrow());
    }

    /* ─── packing ────────────────────────────────────────────── */

    @Test
    void smallCratesPackNoseToTail() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL);
        CrateStackLayout layout = stack.layout();

        assertEquals(0, layout.placements().get(0).baseHundredths());
        assertEquals(715, layout.placements().get(0).topHundredths());
        assertEquals(715, layout.placements().get(1).baseHundredths());
        assertEquals(1430, layout.placements().get(1).topHundredths());
        assertEquals(1430, layout.placements().get(2).baseHundredths());
        assertEquals(2145, layout.placements().get(2).topHundredths());
        assertEquals(2145, layout.totalHundredths());
    }

    @Test
    void mixedVariantsPackInTheOrderTheyWereAdded() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(MEDIUM, SMALL, MEDIUM);
        CrateStackLayout layout = stack.layout();

        assertEquals(0, layout.placements().get(0).baseHundredths());
        assertEquals(1151, layout.placements().get(1).baseHundredths());
        assertEquals(1866, layout.placements().get(2).baseHundredths());
        assertEquals(3017, layout.totalHundredths());
    }

    @Test
    void cellCountsMatchTheWorkedExamples() {
        assertEquals(1, CrateStackTestSupport.stackOf(SMALL).requiredCellCount());
        assertEquals(1, CrateStackTestSupport.stackOf(SMALL, SMALL).requiredCellCount());
        assertEquals(2, CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL).requiredCellCount());
        assertEquals(2, CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL, SMALL).requiredCellCount());
        assertEquals(1, CrateStackTestSupport.stackOf(MEDIUM).requiredCellCount());
        assertEquals(2, CrateStackTestSupport.stackOf(MEDIUM, MEDIUM).requiredCellCount());
        assertEquals(3, CrateStackTestSupport.stackOf(MEDIUM, MEDIUM, MEDIUM).requiredCellCount());
        assertEquals(2, CrateStackTestSupport.stackOf(SMALL, MEDIUM).requiredCellCount());
        assertEquals(2, CrateStackTestSupport.stackOf(MEDIUM, SMALL).requiredCellCount());
        assertEquals(0, CrateStackTestSupport.emptyStack().requiredCellCount());
    }

    /**
     * The arithmetic a {@code double} would get wrong.
     *
     * <p>A column ending exactly on a cell boundary occupies the cell it filled, not the empty one it
     * touches; one hundredth past it needs another.
     */
    @Test
    void cellBoundariesAreExactRatherThanNearlyExact() {
        assertEquals(1, CrateStackLayout.cellsFor(1599));
        assertEquals(1, CrateStackLayout.cellsFor(1600));
        assertEquals(2, CrateStackLayout.cellsFor(1601));
        assertEquals(2, CrateStackLayout.cellsFor(3200));
        assertEquals(3, CrateStackLayout.cellsFor(3201));
        assertEquals(3, CrateStackLayout.cellsFor(4800));
        assertEquals(4, CrateStackLayout.cellsFor(4801));
        assertEquals(4, CrateStackLayout.cellsFor(6400));
        assertEquals(0, CrateStackLayout.cellsFor(0));
    }

    /** A crate cut by a boundary belongs to both cells, and knows where it starts in each. */
    @Test
    void aCrateCrossingABoundaryAppearsInBothCells() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL);
        CratePlacement crossing = stack.layout().placements().get(2);

        assertEquals(1430, crossing.baseHundredths());
        assertEquals(2145, crossing.topHundredths());
        assertTrue(crossing.crossesCellBoundary());
        assertEquals(0, crossing.firstCell());
        assertEquals(1, crossing.lastCell());
        assertEquals(1430, crossing.localBaseHundredths(0));
        assertEquals(-170, crossing.localBaseHundredths(1),
                "a crate continuing from below starts at a negative offset in the upper cell");

        assertEquals(3, stack.layout().placementsInCell(0).size());
        assertEquals(1, stack.layout().placementsInCell(1).size());
    }

    /** A crate finishing exactly on a boundary stays in the cell it filled. */
    @Test
    void aCrateEndingOnABoundaryDoesNotReachIntoTheCellAbove() {
        CratePlacement exact = new CratePlacement(1, 0, CrateStackLayout.CELL_HUNDREDTHS);
        assertEquals(0, exact.firstCell());
        assertEquals(0, exact.lastCell());
        assertFalse(exact.crossesCellBoundary());
    }

    /* ─── identity ───────────────────────────────────────────── */

    @Test
    void idsAreMonotonicAndSurviveRemovalOfEarlierCrates() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        assertEquals(0, a);
        assertEquals(1, b);

        stack.removeCrate(a);
        int c = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();

        assertEquals(2, c, "a removed id must not be handed out again");
        assertEquals(1, b, "the surviving crate keeps the id it was given");
        assertNotNull(stack.crateById(b));
        assertNotNull(stack.crateById(c));
    }

    /* ─── repacking ─────────────────────────────────────────── */

    @Test
    void removingACrateMovesTheOnesAboveItWithoutChangingTheirIdentity() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int c = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        stack.containerFor(c).setItem(0, new ItemStack(Items.DIAMOND, 5));

        assertEquals(1430, stack.placementOf(c).baseHundredths());

        stack.removeCrate(b);

        assertEquals(2, stack.crateCount());
        assertEquals(715, stack.placementOf(c).baseHundredths(), "C should have moved down by one crate");
        assertEquals(0, stack.placementOf(a).baseHundredths());
        assertEquals(1430, stack.totalHeightHundredths());
        assertEquals(Items.DIAMOND, stack.containerFor(c).getItem(0).getItem(),
                "repacking must not disturb what a crate holds");
        assertEquals(5, stack.containerFor(c).getItem(0).getCount());
    }

    @Test
    void removingTheBottomAndTheTopBothLeaveTheRestIntact() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(MEDIUM, Direction.NORTH).orElseThrow();
        int c = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int d = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();

        stack.removeCrate(a);
        assertEquals(0, stack.placementOf(b).baseHundredths());
        assertEquals(1151, stack.placementOf(c).baseHundredths());
        assertEquals(1866, stack.placementOf(d).baseHundredths());

        stack.removeCrate(d);
        assertEquals(2, stack.crateCount());
        assertEquals(1866, stack.totalHeightHundredths());
        assertNotNull(stack.crateById(b));
        assertNotNull(stack.crateById(c));
    }

    @Test
    void removingACrateThatIsNotThereChangesNothing() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        assertTrue(stack.removeCrate(99).isEmpty());
        assertEquals(2, stack.crateCount());
        assertEquals(1430, stack.totalHeightHundredths());
    }

    /* ─── the height cap ─────────────────────────────────────── */

    @Test
    void aColumnFillsToTheCapAndThenRefuses() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        for (int index = 0; index < 8; index++) {
            assertTrue(stack.appendCrate(SMALL, Direction.NORTH).isPresent(),
                    "small crate " + (index + 1) + " should fit within the cap");
        }
        assertEquals(5720, stack.totalHeightHundredths());
        assertEquals(4, stack.requiredCellCount());
        assertFalse(stack.canAppend(SMALL), "a ninth small crate would pass 64 voxels");
    }

    @Test
    void fiveMediumCratesFitAndASixthDoesNot() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        for (int index = 0; index < 5; index++) {
            assertTrue(stack.appendCrate(MEDIUM, Direction.NORTH).isPresent());
        }
        assertEquals(5755, stack.totalHeightHundredths());
        assertEquals(4, stack.requiredCellCount());
        assertTrue(stack.appendCrate(MEDIUM, Direction.NORTH).isEmpty());
    }

    /** A refused append must cost nothing at all, including an identity. */
    @Test
    void aRefusedAppendLeavesTheColumnAndTheIdCounterUntouched() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        for (int index = 0; index < 8; index++) {
            stack.appendCrate(SMALL, Direction.NORTH);
        }
        int crates = stack.crateCount();
        int height = stack.totalHeightHundredths();
        int nextId = stack.nextCrateId();
        int topId = stack.topCrate().id();

        OptionalInt refused = stack.appendCrate(SMALL, Direction.NORTH);

        assertTrue(refused.isEmpty());
        assertEquals(crates, stack.crateCount());
        assertEquals(height, stack.totalHeightHundredths());
        assertEquals(nextId, stack.nextCrateId(), "a refused append must not consume an id");
        assertEquals(topId, stack.topCrate().id());
    }

    /** Every column the cap allows stays inside the cell budget. */
    @Test
    void theHeightCapAndTheCellCapAgree() {
        assertEquals(CrateStackLayout.MAX_CELLS,
                CrateStackLayout.cellsFor(CrateStackLayout.MAX_HEIGHT_HUNDREDTHS));
        assertTrue(CrateStackLayout.withinCap(CrateStackLayout.MAX_HEIGHT_HUNDREDTHS));
        assertFalse(CrateStackLayout.withinCap(CrateStackLayout.MAX_HEIGHT_HUNDREDTHS + 1));
    }
}
