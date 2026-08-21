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
 * A chimney: a vein climbing more or less straight up, wandering a little as it goes.
 *
 * <p>The legacy {@code VerticalVein}'s idea, kept. It climbed {@code radius} steps, drifting up to
 * one cell in X and Z at each, and fattened each step to between two and six cells. All of that is
 * preserved; what changed is where the numbers come from.
 *
 * <h2>Determinism in a shape that is a path</h2>
 * A blob can decide each cell independently, but a path cannot: step ten depends on step nine.
 * So the walk is derived from the seed rather than hashed per cell — every step's drift and width
 * come from {@link ShapeNoise#derive}, keyed by the step index. The walk is still a pure function
 * of {@code (seed, radius)} with no world and no shared RNG in it, so it reproduces exactly; and
 * because the whole path is computed in one pass with no reference to the world, slicing the
 * result by chunk afterwards costs nothing and cannot change it.
 *
 * <p>The legacy version also skipped a step entirely when it happened to land on bedrock, which
 * made the shape depend on the terrain it was being written into. Geometry no longer asks; bedrock
 * is refused at materialisation, where refusing it belongs.
 */
public final class VerticalPlanner implements ShapePlanner {

    private static final int SALT_DRIFT = 41;
    private static final int SALT_WIDTH = 42;
    private static final int SALT_SPREAD = 43;

    /** How far the walk may stray horizontally: one cell per step, plus a cell of spread. */
    private static final int MAX_DRIFT_PER_STEP = 1;
    private static final int MAX_SPREAD = 1;

    @Override
    public int minimumRadius() {
        return 1;
    }

    /**
     * Rotation is not consulted. The legacy implementation accepted the parameter and never read
     * it either; this states so rather than leaving it looking meaningful.
     */
    @Override
    public boolean usesRotation() {
        return false;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        int radius = config.radius();
        int horizontal = radius * MAX_DRIFT_PER_STEP + MAX_SPREAD;
        // The walk climbs one cell per step and never descends, so it reaches from the origin's own
        // course up to radius, and never below it.
        return new ShapeBounds(-horizontal, 0, -horizontal, horizontal, radius, horizontal);
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        int radius = config.radius();
        long seed = config.seed();
        List<ShapeOffset> cells = new ArrayList<>();

        int x = 0;
        int z = 0;
        for (int step = 1; step <= radius; step++) {
            long stepSeed = ShapeNoise.derive(seed, SALT_DRIFT, step);
            x += (int) Long.remainderUnsigned(stepSeed >>> 1, 3) - 1;
            z += (int) Long.remainderUnsigned(stepSeed >>> 20, 3) - 1;

            int width = 2 + ShapeNoise.below(seed, SALT_WIDTH, x, step, z, 5);
            for (int i = 0; i < width; i++) {
                long spread = ShapeNoise.derive(seed, SALT_SPREAD, step * 31 + i);
                int dx = (int) Long.remainderUnsigned(spread >>> 1, 3) - 1;
                int dz = (int) Long.remainderUnsigned(spread >>> 20, 3) - 1;
                cells.add(new ShapeOffset(x + dx, step, z + dz));
            }
        }
        return ShapePlan.of(cells, bounds(config));
    }
}
