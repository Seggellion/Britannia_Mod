package com.seggellion.britannia_mod.block;

import net.minecraft.util.StringRepresentable;

/**
 * How much of a cell an oversized window's art actually fills, vertically.
 *
 * <p>Most window art is authored on the block grid, so every cell it reaches is filled top to
 * bottom. The {@code window_cross_2x2} and {@code window_cross_2x3} frames are authored half a
 * block low, so their outermost rows only reach halfway into a cell; giving those rows a full-face
 * collision would put half a block of invisible wall in plainly empty space.
 */
public enum WindowCollisionSpan implements StringRepresentable {
    /** The art fills the whole cell: y 0..16. */
    FULL("full", 0.0D, 16.0D),
    /** The art only reaches the bottom half of the cell: y 0..8. */
    LOWER("lower", 0.0D, 8.0D),
    /** The art only reaches the top half of the cell: y 8..16. */
    UPPER("upper", 8.0D, 16.0D);

    private final String name;
    private final double minY;
    private final double maxY;

    WindowCollisionSpan(String name, double minY, double maxY) {
        this.name = name;
        this.minY = minY;
        this.maxY = maxY;
    }

    public double minY() {
        return this.minY;
    }

    public double maxY() {
        return this.maxY;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
