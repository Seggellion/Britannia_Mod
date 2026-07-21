package com.seggellion.britannia_mod.dye.colour;

/** A finite OKLab triple. Values are not clamped because valid transforms may be outside display gamut. */
public record OklabColour(double lightness, double a, double b) {
    public OklabColour {
        if (!Double.isFinite(lightness) || !Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("OKLab components must be finite");
        }
    }
}
