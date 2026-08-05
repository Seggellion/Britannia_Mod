package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Owner improvement (2026-08-04): visible pagination over the Bank Box grid, so a vault holding
 * 100 items is navigable by click and not only by wheel.
 *
 * <p>Deliberately a <b>presentation</b> of {@link BankGridScroll}, not a replacement: the scroll
 * engine already handles any item count (Rails sends the whole vault in one page --
 * {@code next_cursor} is always null today), and the wheel keeps working. A "page" is one full
 * grid view: with five visible rows that is 45 items, so 100 items are three pages. The page
 * buttons jump a full view; the wheel still moves by row; both drive the same
 * {@code firstVisibleRow}.
 *
 * <p>Plain arithmetic (Architecture Decision 0). The one subtlety is the last page: the scroll
 * clamps to {@code maxFirstRow}, which is rarely a multiple of the view height, so the final
 * snap position shows the last full view rather than a mostly-empty page. A position at that
 * clamp reads as the last page; a wheel position between anchors reads as the page its top row
 * belongs to.
 */
public final class BankGridPager {

    private BankGridPager() {
    }

    /** How many full grid views the content spans. Never below one, even for an empty vault. */
    public static int pageCount(int itemCount, int columns, int visibleRows) {
        int totalRows = BankGridScroll.rowsNeeded(itemCount, columns);
        return Math.max(1, ceilDiv(totalRows, visibleRows));
    }

    /** The 1-based page the current scroll position reads as. */
    public static int currentPage(BankGridScroll scroll, int itemCount, int columns, int visibleRows) {
        int pageCount = pageCount(itemCount, columns, visibleRows);
        int maxFirstRow = BankGridScroll.maxFirstRow(itemCount, columns, visibleRows);
        int first = scroll.firstVisibleRow();
        if (maxFirstRow > 0 && first >= maxFirstRow) return pageCount;
        return Math.min(pageCount, (first / visibleRows) + 1);
    }

    /** One full view forward, clamped -- the ▶ button. */
    public static BankGridScroll nextPage(BankGridScroll scroll, int itemCount, int columns, int visibleRows) {
        return scroll.scrolledBy(visibleRows, itemCount, columns, visibleRows);
    }

    /** One full view back, clamped -- the ◀ button. */
    public static BankGridScroll previousPage(BankGridScroll scroll, int itemCount, int columns, int visibleRows) {
        return scroll.scrolledBy(-visibleRows, itemCount, columns, visibleRows);
    }

    /** Whether the pager controls should exist at all. One page needs no navigation. */
    public static boolean paged(int itemCount, int columns, int visibleRows) {
        return pageCount(itemCount, columns, visibleRows) > 1;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
