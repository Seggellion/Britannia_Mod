package com.seggellion.britannia_mod.resource.shape;

import java.util.Locale;
import java.util.Optional;

/**
 * How a directional deposit is oriented.
 *
 * <p>Carried over from the curated Rails rows, which have always spelled these four. Only the
 * planners that are actually directional consult it; the rest declare
 * {@link ShapePlanner#usesRotation()} false, which is milestone 3 making explicit what the legacy
 * code did silently — {@code LayeredVein} took a rotation parameter and never read it, and
 * {@code VerticalLayeredVein}'s rotation {@code switch} had no default, so an unrecognised value
 * left every offset at zero and stacked the whole deposit into one cell.
 */
public enum ShapeRotation {
    /** The historical default: the deposit runs north-south, tight on Z. */
    XZ,
    /** Sideways: a vertical wall running east-west, tight on X. */
    YZ,
    /** Flat: a broad horizontal sheet, tight on Y. */
    XY,
    /** East-west: a vertical wall running along Z, tight on X. */
    ZW;

    public String id() {
        return name();
    }

    public static Optional<ShapeRotation> byId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        for (ShapeRotation rotation : values()) {
            if (rotation.name().equals(wanted)) return Optional.of(rotation);
        }
        return Optional.empty();
    }
}
