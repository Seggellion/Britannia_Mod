package com.seggellion.britannia_mod.client.gui;

/**
 * Scroll geometry for a fixed-height list of uniform rows inside a bounded viewport.
 *
 * <p>Deliberately free of Minecraft types so the bounds arithmetic can be unit tested without a
 * render context. {@link SkillTableScreen} owns the pixels; this owns the maths, so the renderer
 * and the input handler cannot drift apart the way they do when each recomputes row positions from
 * its own copy of the magic numbers.
 *
 * <p>The viewport height is expected to be a whole multiple of the row height, which is what makes
 * the final row fully visible at maximum scroll rather than half-clipped.
 */
public final class SkillListViewport {
    private final int rowHeight;
    private final int viewportHeight;

    public SkillListViewport(int rowHeight, int viewportHeight) {
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("Row height must be positive: " + rowHeight);
        }
        if (viewportHeight < 0) {
            throw new IllegalArgumentException("Viewport height cannot be negative: " + viewportHeight);
        }
        this.rowHeight = rowHeight;
        this.viewportHeight = viewportHeight;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    /** Total pixel height the rows would occupy if nothing clipped them. */
    public int contentHeight(int rowCount) {
        return Math.max(0, rowCount) * rowHeight;
    }

    /** Rows that fit entirely inside the viewport. */
    public int visibleRowCapacity() {
        return viewportHeight / rowHeight;
    }

    /**
     * Largest legal scroll offset. Zero whenever the content fits, which is what stops the list
     * from scrolling into blank space below the last row.
     */
    public int maxScroll(int rowCount) {
        return Math.max(0, contentHeight(rowCount) - viewportHeight);
    }

    public int clampScroll(int scroll, int rowCount) {
        return Math.max(0, Math.min(scroll, maxScroll(rowCount)));
    }

    public boolean canScroll(int rowCount) {
        return maxScroll(rowCount) > 0;
    }

    /** Absolute Y of a row's top edge, given the viewport's own top edge. */
    public int rowTop(int viewportTop, int rowIndex, int scroll) {
        return viewportTop + rowIndex * rowHeight - scroll;
    }

    /** First row index with any pixel inside the viewport. */
    public int firstVisibleRow(int scroll, int rowCount) {
        if (rowCount <= 0) {
            return 0;
        }
        return Math.min(Math.max(0, clampScroll(scroll, rowCount) / rowHeight), rowCount - 1);
    }

    /** Exclusive upper bound of rows with any pixel inside the viewport. */
    public int visibleRowLimit(int scroll, int rowCount) {
        if (rowCount <= 0) {
            return 0;
        }
        int clamped = clampScroll(scroll, rowCount);
        int lastPixel = clamped + viewportHeight;
        return Math.min(rowCount, (lastPixel + rowHeight - 1) / rowHeight);
    }

    /**
     * Row index under a cursor position, or {@code -1} when the cursor is outside the viewport or
     * past the last row. Keeps hover, clicks and tooltips aligned with what the renderer drew.
     */
    public int rowIndexAt(int viewportTop, double mouseY, int scroll, int rowCount) {
        if (rowCount <= 0 || mouseY < viewportTop || mouseY >= viewportTop + viewportHeight) {
            return -1;
        }
        int offset = (int) Math.floor(mouseY - viewportTop) + clampScroll(scroll, rowCount);
        int index = offset / rowHeight;
        return index >= 0 && index < rowCount ? index : -1;
    }
}
