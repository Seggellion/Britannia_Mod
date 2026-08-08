package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Milestone 9: how far down a grid is scrolled, in whole rows.
 *
 * <p>Rows rather than pixels, deliberately. A pixel offset means partially-visible cells at the
 * top and bottom edge, which look like rendering errors and make hit testing ambiguous -- a
 * half-visible stored item is either clickable or it is not, and neither answer is defensible.
 * The legacy screen scrolled by pixels and needed a scissor rectangle to hide the overflow; a
 * whole-row offset needs neither.
 *
 * <p>Plain and immutable, so every clamp case is JUnit-testable (Architecture Decision 0).
 */
public record BankGridScroll(int firstVisibleRow) {

    public BankGridScroll {
        if (firstVisibleRow < 0) throw new IllegalArgumentException("firstVisibleRow must not be negative");
    }

    public static final BankGridScroll TOP = new BankGridScroll(0);

    /**
     * How many rows the content needs, given how many items it holds.
     *
     * <p>Always at least one, so an empty vault still presents a row of empty cells rather than
     * nothing at all -- design §9.3 wants the grid structure visible even when there is nothing
     * in it.
     */
    public static int rowsNeeded(int itemCount, int columns) {
        if (columns <= 0) throw new IllegalArgumentException("columns must be positive");
        if (itemCount <= 0) return 1;
        return ((itemCount - 1) / columns) + 1;
    }

    /** The largest legal {@link #firstVisibleRow} for this content and viewport. Never negative. */
    public static int maxFirstRow(int itemCount, int columns, int visibleRows) {
        if (visibleRows <= 0) throw new IllegalArgumentException("visibleRows must be positive");
        return Math.max(0, rowsNeeded(itemCount, columns) - visibleRows);
    }

    public static boolean canScroll(int itemCount, int columns, int visibleRows) {
        return maxFirstRow(itemCount, columns, visibleRows) > 0;
    }

    /**
     * Scrolls by {@code rowDelta} rows and clamps to the content.
     *
     * <p>Clamping on every move rather than only on input is what keeps the view valid when the
     * content shrinks underneath it -- a refresh that removes the last row while the player is
     * scrolled to the bottom would otherwise leave them looking at empty space with no way back.
     */
    public BankGridScroll scrolledBy(int rowDelta, int itemCount, int columns, int visibleRows) {
        return at(firstVisibleRow + rowDelta, itemCount, columns, visibleRows);
    }

    /** Clamps an absolute row offset to what the content allows. */
    public static BankGridScroll at(int desiredFirstRow, int itemCount, int columns, int visibleRows) {
        int max = maxFirstRow(itemCount, columns, visibleRows);
        return new BankGridScroll(Math.max(0, Math.min(desiredFirstRow, max)));
    }

    /** Re-clamps after the content changed, keeping the current position where possible. */
    public BankGridScroll reclamped(int itemCount, int columns, int visibleRows) {
        return at(firstVisibleRow, itemCount, columns, visibleRows);
    }

    /**
     * The index into the full item list shown by visible cell {@code cellIndex}, or {@code -1}
     * when that cell falls past the end of the content.
     *
     * <p>The {@code -1} is the whole reason this is a method rather than an addition at the call
     * site: a grid is almost always partly empty, and a caller that forgets to check reads past
     * the end of the list.
     */
    public int itemIndexFor(int cellIndex, int columns, int itemCount) {
        if (cellIndex < 0) return -1;
        int index = (firstVisibleRow * columns) + cellIndex;
        return index < itemCount ? index : -1;
    }
}
