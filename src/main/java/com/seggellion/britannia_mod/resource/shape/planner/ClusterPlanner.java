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
 * A rounded blob, densest at its heart and ragged at its rim.
 *
 * <h2>What changed</h2>
 * The geometry is the legacy {@code ClusterVein}'s, kept deliberately: this is the shape copper and
 * verite already have, it was the least broken of the six, and milestone 3 was told not to redesign
 * established gameplay where a minimal deterministic correction suffices. The same squashed
 * distance, the same density falloff, the same air-pocket and rim-trim thinning, at the same rates.
 *
 * <p>What changed is that each cell now decides for itself. The legacy version drew three numbers
 * per cell from the shared level RNG in scan order, so the deposit depended on how many times
 * anything else on the server had drawn first, and the same command twice produced two different
 * veins. Every decision is now {@link ShapeNoise} over the cell's own coordinates, which makes the
 * blob reproducible and — because no cell's fate depends on any other's — sliceable by chunk.
 *
 * <p>One legacy comment is corrected rather than carried: it called {@code ySquash} a flattening
 * factor, but dividing Y by it makes Y contribute <em>less</em> to the distance, so the blob
 * reaches further vertically, not less. The value is unchanged; only the description was wrong.
 */
public final class ClusterPlanner implements ShapePlanner {

    /** Y contributes this much less to the distance, so the blob is taller than it is wide. */
    private static final double Y_SQUASH = 1.8;

    private static final int SALT_DENSITY = 1;
    private static final int SALT_POCKET = 2;
    private static final int SALT_RIM = 3;

    /** Below this the falloff divides by a radius of zero and the blob is a single cell anyway. */
    @Override
    public int minimumRadius() {
        return 1;
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
        List<ShapeOffset> cells = new ArrayList<>();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double squashedY = y / Y_SQUASH;
                    double distance = Math.sqrt((double) x * x + (double) z * z + squashedY * squashedY);
                    if (distance > radius) continue;

                    // Denser at the core, thinner towards the edge.
                    double density = 1.0 - (distance / radius);
                    if (ShapeNoise.unit(seed, SALT_DENSITY, x, y, z) > density) continue;

                    // Small hollows scattered through it.
                    if (ShapeNoise.unit(seed, SALT_POCKET, x, y, z) < 0.10f) continue;

                    // The outer fifth frays rather than ending cleanly.
                    if (distance > radius * 0.8
                            && ShapeNoise.unit(seed, SALT_RIM, x, y, z) < 0.30f) {
                        continue;
                    }
                    cells.add(new ShapeOffset(x, y, z));
                }
            }
        }
        return ShapePlan.of(cells, bounds(config));
    }
}
