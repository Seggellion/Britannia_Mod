package com.seggellion.britannia_mod.resource.shape.planner;

import com.seggellion.britannia_mod.resource.shape.ShapeBounds;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapeNoise;
import com.seggellion.britannia_mod.resource.shape.ShapeOffset;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;
import com.seggellion.britannia_mod.resource.shape.ShapePlanner;

import java.util.ArrayList;
import java.util.List;

/**
 * A sheet standing on edge: a thin vertical seam, richest at its heart.
 *
 * <h2>The defect this replaces</h2>
 * Every one of the legacy {@code VerticalLayeredVein}'s three phases was guarded by
 * {@code isAir()}. Silver is the only resource that uses this shape, which means silver could only
 * ever materialise into cave air — run against solid rock, the shape placed nothing at all and
 * still reported success. That is the single worst of the six defects: the resource did not merely
 * generate badly, it generated nowhere.
 *
 * <p>This planner does not know what {@code isAir} means. It plans the seam; whether each cell is
 * an acceptable host is asked once, later, by {@code MaterializationService}, against the
 * resource's configured host tag.
 *
 * <h2>Rotation</h2>
 * The legacy {@code switch} had no {@code default}, so any rotation string it did not recognise
 * left all three offsets at zero and stacked the entire deposit into the origin cell. Rotation is
 * now an enum, so there is no unrecognised value to fall through, and each of the four spellings
 * picks which axis the sheet is thin along:
 *
 * <ul>
 *   <li>{@code XZ} — the sheet spans X and Y, thin on Z. The historical default.</li>
 *   <li>{@code YZ} — spans Y and Z, thin on X.</li>
 *   <li>{@code ZW} — spans Y and Z, thin on X. The curated rows' east-west spelling.</li>
 *   <li>{@code XY} — spans X and Z, thin on Y: a flat sheet rather than a standing one.</li>
 * </ul>
 *
 * <p>Density is {@code (1 - d/r)²} across the sheet, the same falloff the horizontal seam uses and
 * for the same reason: it reproduces the cell count the legacy phases were asking for before the
 * air filter discarded them.
 */
public final class VerticalLayeredPlanner implements ShapePlanner {

    private static final int HALF_THICKNESS = 1;

    private static final int SALT_DENSITY = 31;
    private static final int SALT_LAYER = 32;

    @Override
    public int minimumRadius() {
        return 1;
    }

    @Override
    public boolean usesRotation() {
        return true;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        int radius = config.radius();
        return switch (config.rotation()) {
            case XZ -> ShapeBounds.of(radius, radius, HALF_THICKNESS);
            case YZ, ZW -> ShapeBounds.of(HALF_THICKNESS, radius, radius);
            case XY -> ShapeBounds.of(radius, HALF_THICKNESS, radius);
        };
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        int radius = config.radius();
        long seed = config.seed();
        ShapeBounds bounds = bounds(config);
        List<ShapeOffset> cells = new ArrayList<>();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    // Distance is measured across the sheet's two long axes; the thin axis is the
                    // one whose bound is HALF_THICKNESS, and it is the seam's depth, not its reach.
                    int alongA;
                    int alongB;
                    int across;
                    switch (config.rotation()) {
                        case XZ -> { alongA = x; alongB = y; across = z; }
                        case YZ, ZW -> { alongA = y; alongB = z; across = x; }
                        case XY -> { alongA = x; alongB = z; across = y; }
                        default -> throw new IllegalStateException("unreachable");
                    }

                    double distance = Math.sqrt((double) alongA * alongA + (double) alongB * alongB);
                    if (distance > radius) continue;

                    double falloff = 1.0 - (distance / radius);
                    double density = falloff * falloff;
                    if (ShapeNoise.unit(seed, SALT_DENSITY, x, y, z) > density) continue;

                    if (across != 0 && ShapeNoise.unit(seed, SALT_LAYER, x, y, z) < 0.55f) continue;

                    cells.add(new ShapeOffset(x, y, z));
                }
            }
        }
        return ShapePlan.of(cells, bounds);
    }
}
