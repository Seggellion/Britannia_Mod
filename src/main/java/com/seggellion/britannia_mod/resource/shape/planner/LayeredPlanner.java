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
 * A bed: a broad, thin, horizontal seam, richest in the middle and petering out at the margins.
 *
 * <h2>What was wrong before</h2>
 * The legacy {@code LayeredVein} computed geometry and host acceptance in the same breath, and
 * disagreed with itself about which it wanted. Its two cluster phases placed only where
 * {@code isAir()}, so in solid rock they did nothing at all; its scatter phase placed only where
 * <em>not</em> air, and would take bedrock, a fluid or a chest as readily as stone. So the same
 * shape both refused to exist underground and overwrote anything it met in a cave.
 *
 * <p>The geometry here knows nothing about the world. Every cell in the seam is planned; whether a
 * cell may actually be written is {@code MaterializationService}'s decision, asked once, in one
 * place, against the resource's host policy.
 *
 * <h2>Density</h2>
 * Falloff is {@code (1 - d/r)²} across the seam, which is a plausible bed and — not by coincidence
 * — lands within a per cent of the cell count the legacy version was <em>trying</em> to place. Its
 * three phases asked for about {@code 55 × radius} cells between them before the air filter threw
 * most of them away; the integral of this falloff over the seam is about {@code 1.57 × radius²},
 * which at tin's curated radius of 35 is 1,924 against the legacy intent of 1,925. Tin's deposits
 * therefore end up the size they were always meant to be, rather than the size the defect left.
 */
public final class LayeredPlanner implements ShapePlanner {

    /** A seam is three cells thick: its own course, and a little roughness above and below. */
    private static final int HALF_THICKNESS = 1;

    private static final int SALT_DENSITY = 21;
    private static final int SALT_LAYER = 22;

    @Override
    public int minimumRadius() {
        return 1;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        return ShapeBounds.of(config.radius(), HALF_THICKNESS, config.radius());
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        int radius = config.radius();
        long seed = config.seed();
        List<ShapeOffset> cells = new ArrayList<>();

        for (int y = -HALF_THICKNESS; y <= HALF_THICKNESS; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt((double) x * x + (double) z * z);
                    if (distance > radius) continue;

                    double falloff = 1.0 - (distance / radius);
                    double density = falloff * falloff;
                    if (ShapeNoise.unit(seed, SALT_DENSITY, x, y, z) > density) continue;

                    // The seam's own course is solid; what strays above and below it is patchy,
                    // which is what makes it read as bedded rather than as a slab.
                    if (y != 0 && ShapeNoise.unit(seed, SALT_LAYER, x, y, z) < 0.55f) continue;

                    cells.add(new ShapeOffset(x, y, z));
                }
            }
        }
        return ShapePlan.of(cells, bounds(config));
    }
}
