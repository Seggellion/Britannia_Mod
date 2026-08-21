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
 * A nodule: a roughly spherical crust of ore around a hollow interior.
 *
 * <h2>Why this is a rewrite and not a correction</h2>
 * The legacy {@code GeodeVein} was not a geode. It scattered {@code radius * 2} independent points
 * uniformly through a box — no shell, no interior, no centre relationship, and each point placed on
 * its own with no connection to its neighbours. Milestone 0 identified it, and milestone 3 was told
 * explicitly not to carry a misleading algorithm forward under the name.
 *
 * <p>This is the smallest thing that is clearly a geode: cells whose distance from the centre lies
 * within a crust of {@link #crustThickness} at the configured radius, with the crust's inner and
 * outer surfaces deterministically roughened so the nodule is irregular rather than a machined
 * ball, and a few gaps through it. The interior is left alone — that is what makes it hollow, and
 * the host rock simply stays there.
 *
 * <h2>The balancing consequence</h2>
 * The old algorithm's {@code radius} was never a radius. It was a count multiplier and a scatter
 * range, so agapite's curated radius of 55 produced about 110 lone blocks spread across a 55-block
 * box. Read as an actual radius, 55 would describe a crust of roughly half a million cells, which
 * is why this planner declares a low maximum and agapite's configured range was narrowed to a
 * geode-sized one. A curated row still asking for 55 is now refused and reported by name rather
 * than placed — see the milestone report; the curated table needs a matching correction.
 */
public final class GeodePlanner implements ShapePlanner {

    private static final int SALT_OUTER = 11;
    private static final int SALT_INNER = 12;
    private static final int SALT_GAP = 13;

    /** How far the crust reaches inward from the outer surface. */
    private static int crustThickness(int radius) {
        return Math.max(1, Math.round(radius * 0.35f));
    }

    /** Two cells of crust and a cell of hollow is the smallest thing that reads as a nodule. */
    @Override
    public int minimumRadius() {
        return 3;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        return ShapeBounds.cube(config.radius());
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        int radius = config.radius();
        long seed = config.seed();
        int thickness = crustThickness(radius);
        List<ShapeOffset> cells = new ArrayList<>();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt((double) x * x + (double) y * y + (double) z * z);

                    // Both surfaces wobble by up to a cell, independently, so the nodule is lumpy
                    // rather than a perfect shell -- and lumpy the same way every time.
                    double outer = radius - 1.0 + ShapeNoise.unit(seed, SALT_OUTER, x, y, z);
                    double inner = (radius - thickness)
                            + ShapeNoise.unit(seed, SALT_INNER, x, y, z);

                    if (distance > outer || distance < inner) continue;

                    // A little of the crust is missing, as a real nodule's is.
                    if (ShapeNoise.unit(seed, SALT_GAP, x, y, z) < 0.12f) continue;

                    cells.add(new ShapeOffset(x, y, z));
                }
            }
        }
        return ShapePlan.of(cells, bounds(config));
    }
}
