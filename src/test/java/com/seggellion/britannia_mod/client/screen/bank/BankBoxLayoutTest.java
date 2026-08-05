package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 9's required coverage: the Bank Box's structure at supported resolutions and GUI
 * scales.
 *
 * <p>As in {@code BankDialogueLayoutTest}, GUI scale is not a parameter -- Minecraft divides by it
 * before a screen sees a coordinate, so a scaled size already encodes it. The constants name the
 * real window-and-scale pairs each size stands for.
 */
class BankBoxLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    private static final int W_1920_SCALE_2 = 960;
    private static final int H_1080_SCALE_2 = 540;
    private static final int W_1280_SCALE_2 = 640;
    private static final int W_1280_SCALE_3 = 426;
    private static final int H_1280_SCALE_3 = 240;
    private static final int W_1024_SCALE_4 = 256;
    private static final int H_768_SCALE_4 = 192;

    private static BankBoxLayout layout(int width, int height, int items) {
        return BankBoxLayout.calculate(width, height, FONT_LINE_HEIGHT, items);
    }

    private static void assertNothingOverlaps(BankBoxLayout l) {
        assertTrue(l.bankGrid().bottom() <= l.inventoryGrid().top(),
                "bank grid ran into the inventory grid");
        assertTrue(l.inventoryGrid().bottom() <= l.hotbar().top(),
                "inventory grid ran into the hotbar");
        assertTrue(l.weightY() < l.bankGrid().top(), "weight line ran into the bank grid");
        assertTrue(l.titleY() < l.weightY(), "title ran into the weight line");
        assertTrue(l.statusY() >= l.hotbar().bottom(), "status ran into the hotbar");
        if (l.chestVisible()) {
            // The grid sits inside the velvet interior band, never over the lid, walls or base.
            int interiorTop = l.chestTop() + l.chestLidHeight();
            int interiorBottom = interiorTop + l.chestInteriorHeight();
            assertTrue(l.bankGrid().left() > l.chestLeft(), "grid touches the chest's left wall");
            assertTrue(l.bankGrid().right() < l.chestRight(), "grid touches the chest's right wall");
            assertTrue(l.bankGrid().top() >= interiorTop, "grid ran up into the lid");
            assertTrue(l.bankGrid().bottom() <= interiorBottom, "grid ran down into the base");
            // Title and weight are written on the lid, inside it.
            assertTrue(l.titleY() >= l.chestTop(), "title above the chest");
            assertTrue(l.weightY() < interiorTop, "weight ran off the lid into the interior");
            // The pack is not part of the vault: it starts below the chest's base.
            assertTrue(l.inventoryLabelY() >= l.chestBottom(), "pack label ran into the chest");
        }
    }

    private static void assertWithinScreen(BankBoxLayout l, int width, int height) {
        assertTrue(l.panelLeft() >= 0, "panel off the left edge");
        assertTrue(l.panelRight() <= width, "panel off the right edge: " + l.panelRight() + " > " + width);
        assertTrue(l.panelTop() >= 0, "panel off the top edge");
        assertTrue(l.bankGrid().right() <= l.panelRight(), "bank grid past the panel");
        assertTrue(l.hotbar().right() <= l.panelRight(), "hotbar past the panel");
        if (l.chestVisible()) {
            assertTrue(l.chestLeft() >= l.panelLeft(), "chest off the panel's left edge");
            assertTrue(l.chestRight() <= l.panelRight(), "chest off the panel's right edge");
        }
    }

    // ---------- The size matrix ----------

    @Test
    void holdsItsStructureAcrossEveryPlausibleScreenSize() {
        // Brute force rather than samples: the compact/wide switch and the row-count clamp both
        // have edges no hand-picked list would land on.
        for (int width = 180; width <= 1200; width += 4) {
            for (int height = 200; height <= 700; height += 20) {
                BankBoxLayout l = layout(width, height, 40);
                assertNothingOverlaps(l);
                assertWithinScreen(l, width, height);
            }
        }
    }

    @Test
    void keepsAllNineColumnsAtEverySize() {
        // Design §9.3: nine columns, matching Minecraft convention. This must never adapt away.
        for (int width : new int[]{W_1920_SCALE_2, W_1280_SCALE_2, W_1280_SCALE_3, W_1024_SCALE_4, 180}) {
            BankBoxLayout l = layout(width, 400, 40);
            assertEquals(BankBoxLayout.COLUMNS, l.bankGrid().columns(), "bank grid at " + width);
            assertEquals(BankBoxLayout.COLUMNS, l.inventoryGrid().columns(), "inventory at " + width);
            assertEquals(BankBoxLayout.COLUMNS, l.hotbar().columns(), "hotbar at " + width);
        }
    }

    @Test
    void alwaysShowsThePlayersOwnThreeRowsAndHotbar() {
        BankBoxLayout l = layout(W_1024_SCALE_4, H_768_SCALE_4, 0);
        assertEquals(BankBoxLayout.INVENTORY_ROWS, l.inventoryGrid().rows());
        assertEquals(BankBoxLayout.HOTBAR_ROWS, l.hotbar().rows());
        assertEquals(27, l.inventoryGrid().capacity());
        assertEquals(9, l.hotbar().capacity());
    }

    // ---------- The bank grid stays multi-row ----------

    @Test
    void neverDropsBelowTwoBankRowsEvenWhenTheScreenIsTiny() {
        // Design §9.3 permits the row count to adapt but requires the grid stay multi-row.
        for (int height = 120; height <= 300; height += 4) {
            BankBoxLayout l = layout(W_1024_SCALE_4, height, 100);
            assertTrue(l.bankGrid().rows() >= BankBoxLayout.MIN_BANK_ROWS,
                    "only " + l.bankGrid().rows() + " bank rows at height " + height);
        }
    }

    @Test
    void growsTheBankGridWhenThereIsRoomAndContentToFill() {
        BankBoxLayout tall = layout(W_1920_SCALE_2, H_1080_SCALE_2, 100);
        BankBoxLayout short_ = layout(W_1920_SCALE_2, H_1280_SCALE_3, 100);
        assertEquals(BankBoxLayout.MAX_BANK_ROWS, tall.bankGrid().rows());
        assertTrue(short_.bankGrid().rows() <= tall.bankGrid().rows(), "a shorter screen should not gain rows");
    }

    @Test
    void doesNotGrowTheGridPastWhatTheContentNeeds() {
        // Five empty rows for three items is wasted space that crowds everything below it.
        BankBoxLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 3);
        assertEquals(BankBoxLayout.MIN_BANK_ROWS, l.bankGrid().rows());
    }

    @Test
    void showsTheGridStructureEvenForAnEmptyVault() {
        BankBoxLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 0);
        assertTrue(l.bankGrid().rows() >= BankBoxLayout.MIN_BANK_ROWS);
        assertTrue(l.bankGrid().capacity() > 0);
    }

    // ---------- The chest ----------

    @Test
    void showsTheChestExactlyWhenItFits() {
        int threshold = (BankBoxLayout.MARGIN * 2) + BankBoxLayout.CHEST_WIDTH;
        assertFalse(layout(threshold - 1, 400, 40).chestVisible(),
                "one pixel short of fitting must fall back to the plain layout");
        assertTrue(layout(threshold, 400, 40).chestVisible(),
                "the chest must appear the moment it fits");
    }

    @Test
    void thePlainFallbackCarriesNoChestRectangles() {
        BankBoxLayout l = layout(200, 400, 40);
        assertFalse(l.chestVisible());
        assertEquals(0, l.chestWidth());
        assertEquals(0, l.chestLidHeight());
        assertEquals(0, l.chestInteriorHeight());
        assertEquals(0, l.chestBaseHeight());
    }

    @Test
    void theLidGrowsTowardItsNaturalProportionWhenSpaceAllows() {
        BankBoxLayout tall = layout(W_1920_SCALE_2, H_1080_SCALE_2, 100);
        assertEquals(BankBoxLayout.CHEST_LID_NATURAL, tall.chestLidHeight(),
                "a roomy screen should draw the lid at its natural proportion");

        BankBoxLayout tight = layout(W_1920_SCALE_2, 300, 100);
        assertTrue(tight.chestLidHeight() < BankBoxLayout.CHEST_LID_NATURAL,
                "a tight screen should compress the lid");
        assertTrue(tight.chestLidHeight() >= (FONT_LINE_HEIGHT + 2) * 3 + 8,
                "but never below the three text rows written on it -- title, weight, pager");
    }

    @Test
    void theInteriorBandIsExactlyTheGridPlusItsPadding() {
        BankBoxLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 100);
        int expected = BankBoxLayout.CHEST_INTERIOR_PAD_TOP
                + (l.bankGrid().rows() * BankGridGeometry.DEFAULT_CELL_PITCH)
                + BankBoxLayout.CHEST_INTERIOR_PAD_BOTTOM;
        assertEquals(expected, l.chestInteriorHeight(),
                "the velvet band must stretch to exactly what the grid needs -- it is the one slice that scales");
    }

    @Test
    void theGridIsCentredInTheChest() {
        BankBoxLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 40);
        int leftInset = l.bankGrid().left() - l.chestLeft();
        int rightInset = l.chestRight() - l.bankGrid().right();
        assertTrue(Math.abs(leftInset - rightInset) <= 1, "grid off-centre in the chest: " + leftInset + " vs " + rightInset);
    }

    // ---------- Controls reflow ----------

    @Test
    void putsControlsBesideTheGridsWhenThereIsWidth() {
        BankBoxLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 40);
        assertFalse(l.compact());
        assertTrue(l.amountBoxX() >= l.bankGrid().right(), "control column should clear the grids");
    }

    @Test
    void wrapsControlsBeneathTheGridsWhenNarrow() {
        BankBoxLayout l = layout(W_1024_SCALE_4, 400, 40);
        assertTrue(l.compact());
        assertTrue(l.amountBoxY() >= l.hotbar().bottom(), "controls should sit below the hotbar when compact");
        assertEquals(l.contentLeft(), l.amountBoxX());
    }

    @Test
    void keepsTheThreeDenominationButtonsSeparatedAndInsideThePanel() {
        // Side by side across the content width when compact; stacked as full-width rows in the
        // wide column, where a third of 108px cannot hold "Copper" without truncating it.
        for (int width : new int[]{W_1920_SCALE_2, W_1280_SCALE_2, W_1280_SCALE_3, W_1024_SCALE_4}) {
            BankBoxLayout l = layout(width, 400, 40);
            if (l.compact()) {
                for (int i = 0; i < 2; i++) {
                    assertTrue(l.denominationX(i) + l.denominationWidth() <= l.denominationX(i + 1),
                            "denomination buttons overlap at width " + width);
                }
            } else {
                assertEquals(BankBoxLayout.CONTROL_COLUMN_WIDTH, l.denominationWidth(),
                        "wide-mode denominations are full column rows at width " + width);
                for (int i = 0; i < 2; i++) {
                    assertTrue(l.denominationY(i) + BankBoxLayout.ROW_HEIGHT <= l.denominationY(i + 1),
                            "stacked denomination buttons overlap at width " + width);
                }
            }
            assertTrue(l.denominationX(2) + l.denominationWidth() <= l.panelRight(),
                    "denomination buttons past the panel at width " + width);
            assertTrue(l.denominationWidth() > 0, "zero-width denomination button at " + width);
        }
    }

    @Test
    void theWideControlColumnNeverOutgrowsThePanel() {
        // Five rows since the Withdraw button retired (drag-to-withdraw owner decision) -- Back
        // is the column's last row now. The panel's height is set by the grids, so the column
        // must fit under it at the tightest wide screens too.
        for (int width = 180; width <= 1200; width += 4) {
            for (int height = 200; height <= 700; height += 20) {
                BankBoxLayout l = layout(width, height, 40);
                if (l.compact()) continue;
                assertTrue(l.backY() + BankBoxLayout.ROW_HEIGHT <= l.panelBottom(),
                        "control column past the panel at " + width + "x" + height);
                assertTrue(l.denominationY(2) + BankBoxLayout.ROW_HEIGHT <= l.backY(),
                        "denominations ran into Back at " + width + "x" + height);
            }
        }
    }

    @Test
    void backIsTheOnlyActionAndFillsItsRowInBothArrangements() {
        // The Withdraw button retired with the drag; a half-width Back beside an empty hole
        // would advertise the absence. Full width in both arrangements.
        BankBoxLayout wide = layout(W_1920_SCALE_2, H_1080_SCALE_2, 40);
        assertEquals(BankBoxLayout.CONTROL_COLUMN_WIDTH, wide.actionWidth(), "wide: the full column");
        assertTrue(wide.backY() > wide.denominationY(2), "wide: below the denominations");

        BankBoxLayout compact = layout(W_1024_SCALE_4, 400, 40);
        assertTrue(compact.actionWidth() > (compact.panelWidth() - (BankBoxLayout.MARGIN * 2)) / 2,
                "compact: wider than the old half-row share");
        assertTrue(compact.backX() + compact.actionWidth() <= compact.panelRight(), "compact: inside the panel");
    }

    // ---------- Grid geometry ----------

    @Test
    void locatesEveryCellInsideItsOwnGrid() {
        BankGridGeometry grid = BankGridGeometry.of(100, 50, 9, 5);
        for (int index = 0; index < grid.capacity(); index++) {
            int left = grid.cellLeft(index);
            int top = grid.cellTop(index);
            assertTrue(left >= grid.left() && left < grid.right(), "cell " + index + " x out of range");
            assertTrue(top >= grid.top() && top < grid.bottom(), "cell " + index + " y out of range");
            assertEquals(Integer.valueOf(index), grid.cellIndexAt(left + 1, top + 1), "round trip for cell " + index);
        }
    }

    @Test
    void reportsNoCellOutsideTheGrid() {
        BankGridGeometry grid = BankGridGeometry.of(100, 50, 9, 5);
        assertNull(grid.cellIndexAt(99, 60));
        assertNull(grid.cellIndexAt(60, 49));
        assertNull(grid.cellIndexAt(grid.right(), 60));
        assertNull(grid.cellIndexAt(60, grid.bottom()));
        assertNotNull(grid.cellIndexAt(grid.right() - 1, grid.bottom() - 1));
    }

    @Test
    void adjacentGridsNeverBothClaimTheSamePixel() {
        // The Bank Box stacks three grids. An inclusive edge would let one pixel of mouse travel
        // start a drag in one grid and end it in another.
        BankGridGeometry upper = BankGridGeometry.of(0, 0, 9, 2);
        BankGridGeometry lower = BankGridGeometry.of(0, upper.bottom(), 9, 2);
        double sharedEdge = upper.bottom();
        assertNull(upper.cellIndexAt(5, sharedEdge));
        assertNotNull(lower.cellIndexAt(5, sharedEdge));
    }

    // ---------- Scrolling ----------

    @Test
    void computesRowsNeededFromTheItemCount() {
        assertEquals(1, BankGridScroll.rowsNeeded(0, 9), "an empty vault still shows one row of cells");
        assertEquals(1, BankGridScroll.rowsNeeded(9, 9));
        assertEquals(2, BankGridScroll.rowsNeeded(10, 9));
        assertEquals(3, BankGridScroll.rowsNeeded(19, 9));
    }

    @Test
    void doesNotScrollWhenEverythingFits() {
        assertFalse(BankGridScroll.canScroll(20, 9, 5));
        assertEquals(0, BankGridScroll.maxFirstRow(20, 9, 5));
    }

    @Test
    void clampsScrollingAtBothEnds() {
        BankGridScroll scroll = BankGridScroll.TOP;
        assertEquals(0, scroll.scrolledBy(-5, 100, 9, 5).firstVisibleRow(), "cannot scroll above the top");

        int max = BankGridScroll.maxFirstRow(100, 9, 5);
        assertEquals(max, scroll.scrolledBy(999, 100, 9, 5).firstVisibleRow(), "cannot scroll past the bottom");
    }

    @Test
    void reclampsWhenTheContentShrinksUnderneathTheView() {
        // A refresh that removes rows while the player is scrolled to the bottom must not leave
        // them staring at empty space with no way back.
        BankGridScroll atBottom = BankGridScroll.at(999, 100, 9, 5);
        assertTrue(atBottom.firstVisibleRow() > 0);
        assertEquals(0, atBottom.reclamped(5, 9, 5).firstVisibleRow());
    }

    @Test
    void mapsVisibleCellsOntoTheItemListAndReportsCellsPastTheEnd() {
        BankGridScroll scroll = new BankGridScroll(1);
        assertEquals(9, scroll.itemIndexFor(0, 9, 20), "first visible cell after one scrolled row");
        assertEquals(19, scroll.itemIndexFor(10, 9, 20));
        assertEquals(-1, scroll.itemIndexFor(11, 9, 20), "a cell past the end must report -1");
        assertEquals(-1, scroll.itemIndexFor(-1, 9, 20));
    }
}
