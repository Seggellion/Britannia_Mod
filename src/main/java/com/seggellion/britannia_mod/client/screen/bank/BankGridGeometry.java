package com.seggellion.britannia_mod.client.screen.bank;

import javax.annotation.Nullable;

/**
 * Milestone 9: one rectangular grid of cells -- where each is, and which one the cursor is over.
 *
 * <p>Used three times by the Bank Box: the stored-item grid, the player's main inventory, and the
 * hotbar. They differ only in origin, column count and row count, so they share this rather than
 * three near-identical blocks of arithmetic with three chances to get a rounding wrong.
 *
 * <p>Plain, so hit testing is JUnit-testable (Architecture Decision 0). That matters more here
 * than anywhere else in the epic: Milestone 12's drag engine is built entirely on "which cell is
 * the cursor over", and if that answer is wrong the drag is wrong in ways that look like a
 * rendering bug.
 *
 * <h2>Cell pitch, not cell size</h2>
 * {@link #cellPitch()} is the distance between cell origins; the drawn icon is smaller and
 * centred inside it. Vanilla uses an 18-pixel pitch for a 16-pixel icon, which is where the
 * default comes from -- a grid drawn on a different pitch reads as foreign next to the player's
 * own inventory, which is the one comparison every player has memorised.
 */
public record BankGridGeometry(int originX, int originY, int columns, int rows, int cellPitch) {

    /** Vanilla's slot pitch. A 16-pixel icon with a one-pixel border on each side. */
    public static final int DEFAULT_CELL_PITCH = 18;

    /** The drawn icon inside a cell, centred in the pitch. */
    public static final int ICON_SIZE = 16;

    public BankGridGeometry {
        if (columns <= 0) throw new IllegalArgumentException("columns must be positive");
        if (rows <= 0) throw new IllegalArgumentException("rows must be positive");
        if (cellPitch <= 0) throw new IllegalArgumentException("cellPitch must be positive");
    }

    public static BankGridGeometry of(int originX, int originY, int columns, int rows) {
        return new BankGridGeometry(originX, originY, columns, rows, DEFAULT_CELL_PITCH);
    }

    public int width() {
        return columns * cellPitch;
    }

    public int height() {
        return rows * cellPitch;
    }

    /** How many cells are visible. Not how many items exist -- see {@link BankGridScroll}. */
    public int capacity() {
        return columns * rows;
    }

    public int left() {
        return originX;
    }

    public int top() {
        return originY;
    }

    public int right() {
        return originX + width();
    }

    public int bottom() {
        return originY + height();
    }

    /** Left edge of the cell at {@code index}, counting left-to-right then top-to-bottom. */
    public int cellLeft(int index) {
        return originX + ((index % columns) * cellPitch);
    }

    public int cellTop(int index) {
        return originY + ((index / columns) * cellPitch);
    }

    /** Where the icon is drawn inside cell {@code index} -- centred in the pitch. */
    public int iconLeft(int index) {
        return cellLeft(index) + ((cellPitch - ICON_SIZE) / 2);
    }

    public int iconTop(int index) {
        return cellTop(index) + ((cellPitch - ICON_SIZE) / 2);
    }

    /**
     * The visible cell under the cursor, or {@code null} when the cursor is outside the grid.
     *
     * <p>The right and bottom edges are exclusive, so two grids sharing an edge cannot both claim
     * the same pixel -- the Bank Box stacks three of them, and an ambiguous boundary would make a
     * drag start in one grid and end in another by one pixel of mouse travel.
     */
    @Nullable
    public Integer cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < originX || mouseX >= right()) return null;
        if (mouseY < originY || mouseY >= bottom()) return null;

        int column = (int) ((mouseX - originX) / cellPitch);
        int row = (int) ((mouseY - originY) / cellPitch);
        // Defensive: floating-point mouse coordinates at the exact far edge have been seen to
        // round past the last cell despite the exclusive bounds check above.
        if (column >= columns || row >= rows) return null;
        return (row * columns) + column;
    }

    public boolean contains(double mouseX, double mouseY) {
        return cellIndexAt(mouseX, mouseY) != null;
    }

    /** The same grid moved to a new origin. */
    public BankGridGeometry movedTo(int newOriginX, int newOriginY) {
        return new BankGridGeometry(newOriginX, newOriginY, columns, rows, cellPitch);
    }

    /** The same grid with a different number of visible rows. */
    public BankGridGeometry withRows(int newRows) {
        return new BankGridGeometry(originX, originY, columns, newRows, cellPitch);
    }
}
