package com.seggellion.britannia_mod.client.gui;

/**
 * A rectangle in scaled screen units, with no client types on it.
 *
 * <p>Rowan farming questline M8. The quest layout records hand back rectangles rather than loose
 * {@code int} pairs so a test can ask the two questions that matter -- "is this inside the
 * parchment?" and "does this overlap that?" -- without re-deriving edges from four accessors and
 * getting the arithmetic subtly wrong.
 *
 * <p>Width and height are clamped at zero. A negative extent is the shape of the defect this
 * milestone exists to remove (see {@link QuestDialogueLayout}'s notes on the legacy
 * {@code DialogueLayout}), so it is not representable here at all: a caller that computes a
 * negative extent gets an empty rectangle, which every containment test then reports honestly
 * instead of silently inverting.
 */
public record ScreenRect(int x, int y, int width, int height) {

    public static final ScreenRect EMPTY = new ScreenRect(0, 0, 0, 0);

    public ScreenRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public static ScreenRect of(int x, int y, int width, int height) {
        return new ScreenRect(x, y, width, height);
    }

    /** From two corners, in either order. */
    public static ScreenRect between(int left, int top, int right, int bottom) {
        return new ScreenRect(
                Math.min(left, right),
                Math.min(top, bottom),
                Math.abs(right - left),
                Math.abs(bottom - top)
        );
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + (width / 2);
    }

    public int centerY() {
        return y + (height / 2);
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /** True when every edge of {@code this} lies inside {@code outer}. Empty rectangles pass. */
    public boolean isInside(ScreenRect outer) {
        if (isEmpty()) return true;
        return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
    }

    /** True when the two rectangles share any area. Touching edges do not overlap. */
    public boolean overlaps(ScreenRect other) {
        if (isEmpty() || other.isEmpty()) return false;
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    public ScreenRect withHeight(int newHeight) {
        return new ScreenRect(x, y, width, newHeight);
    }

    public ScreenRect withY(int newY) {
        return new ScreenRect(x, newY, width, height);
    }

    /** Shrinks by {@code inset} on all four sides, never past zero extent. */
    public ScreenRect inset(int inset) {
        return new ScreenRect(
                x + inset,
                y + inset,
                Math.max(0, width - (inset * 2)),
                Math.max(0, height - (inset * 2))
        );
    }

    /** The row at {@code index} of a vertical stack of {@code rowHeight}-tall rows. */
    public ScreenRect row(int index, int rowHeight, int rowGap) {
        return new ScreenRect(x, y + (index * (rowHeight + rowGap)), width, rowHeight);
    }
}
