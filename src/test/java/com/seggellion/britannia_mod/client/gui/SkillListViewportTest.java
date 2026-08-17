package com.seggellion.britannia_mod.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Scroll-bounds contract for the Skills list.
 *
 * <p>The published screen drew every registered skill in an unbounded loop, so any skill past the
 * eleventh was painted through the panel and could not be reached. These cover the arithmetic the
 * replacement relies on, including registries larger than today's.
 */
class SkillListViewportTest {

    /** Matches SkillTableScreen: 12 px rows in a 132 px viewport, so 11 rows fit exactly. */
    private static final SkillListViewport VIEWPORT = new SkillListViewport(12, 132);

    @Test
    void viewportHoldsAWholeNumberOfRowsSoTheLastRowIsNeverHalfDrawn() {
        assertEquals(11, VIEWPORT.visibleRowCapacity());
        assertEquals(0, VIEWPORT.viewportHeight() % VIEWPORT.rowHeight());
    }

    @Test
    void contentHeightFollowsTheRegistrySizeRatherThanAFixedSkillCount() {
        assertEquals(0, VIEWPORT.contentHeight(0));
        assertEquals(132, VIEWPORT.contentHeight(11));
        assertEquals(600, VIEWPORT.contentHeight(50));
    }

    @Test
    void aListThatFitsCannotScroll() {
        for (int rowCount = 0; rowCount <= VIEWPORT.visibleRowCapacity(); rowCount++) {
            assertEquals(0, VIEWPORT.maxScroll(rowCount), "rowCount=" + rowCount);
            assertFalse(VIEWPORT.canScroll(rowCount), "rowCount=" + rowCount);
            assertEquals(0, VIEWPORT.clampScroll(50, rowCount), "rowCount=" + rowCount);
        }
    }

    @Test
    void scrollClampsAtBothEnds() {
        int rowCount = 30;
        assertTrue(VIEWPORT.canScroll(rowCount));
        assertEquals(228, VIEWPORT.maxScroll(rowCount)); // 30*12 - 132
        assertEquals(0, VIEWPORT.clampScroll(-1, rowCount));
        assertEquals(0, VIEWPORT.clampScroll(-9999, rowCount));
        assertEquals(228, VIEWPORT.clampScroll(229, rowCount));
        assertEquals(228, VIEWPORT.clampScroll(9999, rowCount));
        assertEquals(120, VIEWPORT.clampScroll(120, rowCount));
    }

    @Test
    void theFinalRowIsFullyVisibleAtMaximumScroll() {
        int rowCount = 30;
        int top = 100;
        int max = VIEWPORT.maxScroll(rowCount);
        int lastRowTop = VIEWPORT.rowTop(top, rowCount - 1, max);
        assertEquals(top + VIEWPORT.viewportHeight() - VIEWPORT.rowHeight(), lastRowTop);
        assertTrue(lastRowTop + VIEWPORT.rowHeight() <= top + VIEWPORT.viewportHeight(),
                "last row bottom must sit inside the viewport");
    }

    @Test
    void scrollingBackToZeroReturnsExactlyToTheFirstRow() {
        int top = 100;
        assertEquals(top, VIEWPORT.rowTop(top, 0, 0));
        assertEquals(0, VIEWPORT.firstVisibleRow(0, 30));
    }

    @Test
    void visibleWindowTracksTheScrollOffset() {
        int rowCount = 30;
        assertEquals(0, VIEWPORT.firstVisibleRow(0, rowCount));
        assertEquals(11, VIEWPORT.visibleRowLimit(0, rowCount));

        // Scrolled exactly two rows down.
        assertEquals(2, VIEWPORT.firstVisibleRow(24, rowCount));
        assertEquals(13, VIEWPORT.visibleRowLimit(24, rowCount));

        // A partial scroll must include the row peeking in at the bottom edge.
        assertEquals(0, VIEWPORT.firstVisibleRow(6, rowCount));
        assertEquals(12, VIEWPORT.visibleRowLimit(6, rowCount));
    }

    @Test
    void visibleWindowNeverRunsPastTheLastRow() {
        int rowCount = 30;
        int max = VIEWPORT.maxScroll(rowCount);
        assertEquals(rowCount, VIEWPORT.visibleRowLimit(max, rowCount));
        assertTrue(VIEWPORT.firstVisibleRow(max, rowCount) < rowCount);
    }

    @Test
    void emptyRegistryRendersNothingRatherThanThrowing() {
        assertEquals(0, VIEWPORT.firstVisibleRow(0, 0));
        assertEquals(0, VIEWPORT.visibleRowLimit(0, 0));
        assertEquals(-1, VIEWPORT.rowIndexAt(100, 105, 0, 0));
    }

    @Test
    void cursorMapsToTheRowDrawnUnderIt() {
        int top = 100;
        int rowCount = 30;

        assertEquals(0, VIEWPORT.rowIndexAt(top, top, 0, rowCount));
        assertEquals(0, VIEWPORT.rowIndexAt(top, top + 11.9, 0, rowCount));
        assertEquals(1, VIEWPORT.rowIndexAt(top, top + 12, 0, rowCount));

        // After scrolling two rows the same pixel is a different skill.
        assertEquals(2, VIEWPORT.rowIndexAt(top, top, 24, rowCount));
        assertEquals(3, VIEWPORT.rowIndexAt(top, top + 12, 24, rowCount));
    }

    @Test
    void cursorOutsideTheViewportSelectsNothing() {
        int top = 100;
        int rowCount = 30;
        assertEquals(-1, VIEWPORT.rowIndexAt(top, top - 1, 0, rowCount));
        assertEquals(-1, VIEWPORT.rowIndexAt(top, top + VIEWPORT.viewportHeight(), 0, rowCount));
    }

    @Test
    void hoverAgreesWithTheRenderedRowAtEveryScrollOffset() {
        int top = 100;
        int rowCount = 40;
        for (int scroll = 0; scroll <= VIEWPORT.maxScroll(rowCount); scroll++) {
            int limit = VIEWPORT.visibleRowLimit(scroll, rowCount);
            for (int row = VIEWPORT.firstVisibleRow(scroll, rowCount); row < limit; row++) {
                int rowTop = VIEWPORT.rowTop(top, row, scroll);
                // Only assert for rows actually inside the clipped region.
                if (rowTop >= top && rowTop < top + VIEWPORT.viewportHeight()) {
                    assertEquals(row, VIEWPORT.rowIndexAt(top, rowTop, scroll, rowCount),
                            "scroll=" + scroll + " row=" + row);
                }
            }
        }
    }

    @Test
    void aFutureRegistryLargerThanTodaysStaysFullyReachable() {
        int rowCount = 250;
        int max = VIEWPORT.maxScroll(rowCount);
        assertTrue(max > 0);
        assertEquals(rowCount, VIEWPORT.visibleRowLimit(max, rowCount));
        assertEquals(rowCount - 1,
                VIEWPORT.rowIndexAt(0, VIEWPORT.viewportHeight() - 1, max, rowCount));
    }
}
