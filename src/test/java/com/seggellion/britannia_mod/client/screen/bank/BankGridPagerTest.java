package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner's 100-item case throughout: 9 columns, 5 visible rows, so 100 items are 12 rows and
 * three pages (45 + 45 + 10).
 */
class BankGridPagerTest {

    private static final int COLUMNS = 9;
    private static final int VISIBLE = 5;
    private static final int HUNDRED = 100;

    @Test
    void aHundredItemsAreThreePages() {
        assertEquals(3, BankGridPager.pageCount(HUNDRED, COLUMNS, VISIBLE));
        assertTrue(BankGridPager.paged(HUNDRED, COLUMNS, VISIBLE));
    }

    @Test
    void aVaultThatFitsOneViewIsOnePageAndUnpaged() {
        assertEquals(1, BankGridPager.pageCount(45, COLUMNS, VISIBLE));
        assertFalse(BankGridPager.paged(45, COLUMNS, VISIBLE));
        assertEquals(1, BankGridPager.pageCount(0, COLUMNS, VISIBLE), "an empty vault is still one page");
        assertFalse(BankGridPager.paged(0, COLUMNS, VISIBLE));
    }

    @Test
    void nextWalksTheThreePagesAndClampsAtTheEnd() {
        BankGridScroll scroll = BankGridScroll.TOP;
        assertEquals(1, BankGridPager.currentPage(scroll, HUNDRED, COLUMNS, VISIBLE));

        scroll = BankGridPager.nextPage(scroll, HUNDRED, COLUMNS, VISIBLE);
        assertEquals(5, scroll.firstVisibleRow());
        assertEquals(2, BankGridPager.currentPage(scroll, HUNDRED, COLUMNS, VISIBLE));

        scroll = BankGridPager.nextPage(scroll, HUNDRED, COLUMNS, VISIBLE);
        // 12 rows, 5 visible: the last snap clamps to row 7 so the view stays full.
        assertEquals(7, scroll.firstVisibleRow());
        assertEquals(3, BankGridPager.currentPage(scroll, HUNDRED, COLUMNS, VISIBLE));

        BankGridScroll clamped = BankGridPager.nextPage(scroll, HUNDRED, COLUMNS, VISIBLE);
        assertEquals(7, clamped.firstVisibleRow(), "next on the last page stays put");
    }

    @Test
    void previousWalksBackAndClampsAtTheTop() {
        BankGridScroll last = BankGridScroll.at(999, HUNDRED, COLUMNS, VISIBLE);
        BankGridScroll middle = BankGridPager.previousPage(last, HUNDRED, COLUMNS, VISIBLE);
        // From the clamped row 7, one view back lands on row 2 -- still page 1's territory by
        // the top-row rule, which is fine: the wheel and the buttons share one position space.
        assertEquals(2, middle.firstVisibleRow());

        BankGridScroll top = BankGridPager.previousPage(middle, HUNDRED, COLUMNS, VISIBLE);
        assertEquals(0, top.firstVisibleRow());
        assertEquals(0, BankGridPager.previousPage(top, HUNDRED, COLUMNS, VISIBLE).firstVisibleRow(),
                "previous on the first page stays put");
    }

    @Test
    void aWheelPositionBetweenAnchorsReadsAsItsTopRowsPage() {
        assertEquals(1, BankGridPager.currentPage(new BankGridScroll(4), HUNDRED, COLUMNS, VISIBLE));
        assertEquals(2, BankGridPager.currentPage(new BankGridScroll(5), HUNDRED, COLUMNS, VISIBLE));
        // The clamp row itself always reads as the last page, however it was reached.
        assertEquals(3, BankGridPager.currentPage(new BankGridScroll(7), HUNDRED, COLUMNS, VISIBLE));
    }

    @Test
    void aRefreshThatShrinksTheVaultReclampsIntoASmallerBook() {
        // 100 items scrolled to the end, then a refresh leaves 20: the scroll reclamps (existing
        // behaviour) and the pager reads the new truth.
        BankGridScroll atEnd = BankGridScroll.at(999, HUNDRED, COLUMNS, VISIBLE);
        BankGridScroll reclamped = atEnd.reclamped(20, COLUMNS, VISIBLE);
        assertEquals(1, BankGridPager.pageCount(20, COLUMNS, VISIBLE));
        assertEquals(1, BankGridPager.currentPage(reclamped, 20, COLUMNS, VISIBLE));
    }
}
