package com.seggellion.britannia_mod.crate;

import static com.seggellion.britannia_mod.crate.CrateVariant.MEDIUM;
import static com.seggellion.britannia_mod.crate.CrateVariant.SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Which crate a player is pointing at.
 *
 * <h2>Why the boundary matters so much</h2>
 *
 * <p>Crates in a column touch exactly, so the plane between two of them is a height both could claim.
 * Left ambiguous, a player aiming at that seam would open one crate or the other depending on
 * floating-point noise in the ray — and would put items into whichever answered that frame. The
 * convention is {@code [base, top)}: the seam belongs to the crate above. The top of the column is
 * the single exception, clamped to the top crate so the exposed lid is always clickable.
 */
class CrateStackTargetResolverTest {

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /* ─── ordinary aim ───────────────────────────────────────── */

    @Test
    void aHitInsideACrateFindsThatCrate() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        int lower = stack.crates().get(0).id();
        int upper = stack.crates().get(1).id();

        assertEquals(lower, at(stack, 0), "the floor of the column is the lower crate");
        assertEquals(lower, at(stack, 300), "3.00 voxels is inside the lower crate");
        assertEquals(lower, at(stack, 714), "one hundredth below the seam is still the lower crate");
        assertEquals(upper, at(stack, 1000), "10.00 voxels is inside the upper crate");
    }

    @Test
    void everyCrateOfAMixedColumnIsReachable() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(MEDIUM, SMALL, MEDIUM);
        int first = stack.crates().get(0).id();
        int second = stack.crates().get(1).id();
        int third = stack.crates().get(2).id();

        assertEquals(first, at(stack, 500));
        assertEquals(second, at(stack, 1400));
        assertEquals(third, at(stack, 2500));
    }

    @Test
    void aColumnOfMediumCratesSplitsAtItsOwnSeam() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(MEDIUM, MEDIUM);
        int lower = stack.crates().get(0).id();
        int upper = stack.crates().get(1).id();

        assertEquals(lower, at(stack, 1150));
        assertEquals(upper, at(stack, 1151), "the seam belongs to the crate above it");
    }

    /* ─── the convention ─────────────────────────────────────── */

    @Test
    void aHitExactlyOnASeamBelongsToTheCrateAbove() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        assertEquals(stack.crates().get(1).id(), at(stack, 715),
                "7.15 is where the lower crate ends and the upper begins");
    }

    @Test
    void aHitOnOrAboveTheLidBelongsToTheTopCrate() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        int top = stack.topCrate().id();

        assertEquals(top, at(stack, 1430), "the exposed lid must be clickable");
        assertEquals(top, at(stack, 1600), "and so must anything the ray reports above it");
    }

    /* ─── crates in continuation cells ───────────────────────── */

    /**
     * A crate above the first cell is found by the same arithmetic.
     *
     * <p>Heights are measured from the column's floor, so it makes no difference which cell the player
     * clicked; that is what keeps a continuation cell from needing to know anything.
     */
    @Test
    void cratesAboveTheFirstCellResolveByTheSameHeights() {
        CrateStackBlockEntity stack =
                CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL, SMALL, SMALL);
        assertEquals(2145, stack.placementOf(stack.crates().get(2).id()).topHundredths());

        assertEquals(stack.crates().get(2).id(), at(stack, 1800),
                "a crate straddling the first boundary");
        assertEquals(stack.crates().get(3).id(), at(stack, 2500),
                "a crate wholly inside the second cell");
        assertEquals(stack.crates().get(4).id(), at(stack, 3300),
                "and the one above it");
    }

    /* ─── the stacking gesture ───────────────────────────────── */

    @Test
    void onlyTheColumnLidCountsAsAStackingGesture() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        assertEquals(1430, stack.totalHeightHundredths());

        assertTrue(isTop(stack, 1430), "the lid is where a crate may be stacked");
        assertTrue(isTop(stack, 1429), "and a hundredth of tolerance for the ray");
        assertTrue(!isTop(stack, 700), "an interior height is not a stacking gesture");
        assertTrue(!isTop(stack, 1400), "nor is a face just below the lid");
    }

    /* ─── nothing there ──────────────────────────────────────── */

    @Test
    void anEmptyColumnHasNothingToAimAt() {
        CrateStackBlockEntity empty = CrateStackTestSupport.emptyStack();
        assertEquals(Optional.empty(), CrateStackTargetResolver.crateAtHeight(empty, 0));
        assertTrue(!CrateStackTargetResolver.isColumnTop(
                empty, net.minecraft.core.BlockPos.ZERO,
                net.minecraft.core.Direction.UP, net.minecraft.world.phys.Vec3.ZERO),
                "an empty column offers no lid to stack onto");
    }

    @Test
    void aCrateThatHasGoneIsNoLongerAimedAt() {
        CrateStackBlockEntity stack = CrateStackTestSupport.stackOf(SMALL, SMALL);
        int upper = stack.crates().get(1).id();
        stack.removeCrate(upper);

        assertEquals(stack.crates().get(0).id(), at(stack, 1000),
                "the height the removed crate held now belongs to what is left");
    }

    private static int at(CrateStackBlockEntity stack, int heightHundredths) {
        return CrateStackTargetResolver.crateAtHeight(stack, heightHundredths).orElseThrow();
    }

    private static boolean isTop(CrateStackBlockEntity stack, int heightHundredths) {
        return Math.abs(heightHundredths - stack.totalHeightHundredths()) <= 1;
    }
}
