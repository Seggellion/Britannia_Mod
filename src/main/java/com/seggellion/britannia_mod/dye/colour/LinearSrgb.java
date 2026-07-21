package com.seggellion.britannia_mod.dye.colour;

/** Linear-light sRGB channels. Canonical parsed input produces values in {@code [0, 1]}. */
public record LinearSrgb(double red, double green, double blue) {
    public LinearSrgb {
        if (!Double.isFinite(red) || !Double.isFinite(green) || !Double.isFinite(blue)) {
            throw new IllegalArgumentException("Linear sRGB channels must be finite");
        }
    }
}
