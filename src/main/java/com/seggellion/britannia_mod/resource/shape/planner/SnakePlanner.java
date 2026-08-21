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
 * A sinuous vein and its tendrils, winding upward.
 *
 * <h2>The crash, fixed where it lives</h2>
 * The legacy {@code SnakeVein} computed a tendril height as {@code random.nextInt(radius - 9) + 10},
 * which throws for any radius of nine or less — and it evaluated that <em>before</em> the branch
 * that would have overridden the value, so the throw was unconditional on the first tendril. A
 * curated row asking for a small gold vein aborted the whole command part-way through, after it had
 * already written blocks.
 *
 * <p>Milestone 1 stopped such rows reaching the algorithm. Milestone 3 removes the expression: the
 * tendril height is now derived within an explicitly bounded range, so there is no arithmetic here
 * that can be handed a non-positive bound. {@link #minimumRadius()} is 4 rather than 10 because
 * four is the smallest radius at which the geometry means anything; the old floor of ten was an
 * artefact of the bug, not a design decision. Curated data may still be stricter, and gold's is.
 *
 * <h2>Determinism</h2>
 * Like the chimney, this is a path, so each walk is derived from the seed by step index rather than
 * hashed per cell. Each of the four veins gets an independent sub-seed, so changing the tendril
 * count would not reshuffle the main vein.
 */
public final class SnakePlanner implements ShapePlanner {

    private static final int TENDRILS = 3;

    private static final int SALT_TENDRIL_ORIGIN = 51;
    private static final int SALT_TENDRIL_HEIGHT = 52;
    private static final int SALT_WALK = 53;
    private static final int SALT_PLACE = 54;

    /** How far a tendril may start from the main vein, on each horizontal axis. */
    private static final int TENDRIL_SPREAD = 10;

    /** Each vein takes at most three steps per cell of height before it gives up climbing. */
    private static final int STEPS_PER_HEIGHT = 3;

    @Override
    public int minimumRadius() {
        return 4;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        int radius = config.radius();
        // Worst case: a walk that drifts one cell horizontally on every one of its steps.
        int horizontal = radius * STEPS_PER_HEIGHT + TENDRIL_SPREAD;
        return new ShapeBounds(-horizontal, 0, -horizontal, horizontal, radius, horizontal);
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        int radius = config.radius();
        long seed = config.seed();
        List<ShapeOffset> cells = new ArrayList<>();

        // The main vein, from the origin, reaching the full configured height.
        walk(cells, seed, 0, 0, 0, radius);

        for (int tendril = 0; tendril < TENDRILS; tendril++) {
            long originSeed = ShapeNoise.derive(seed, SALT_TENDRIL_ORIGIN, tendril);
            int startX = (int) Long.remainderUnsigned(originSeed >>> 1, TENDRIL_SPREAD * 2 + 1)
                    - TENDRIL_SPREAD;
            int startZ = (int) Long.remainderUnsigned(originSeed >>> 20, TENDRIL_SPREAD * 2 + 1)
                    - TENDRIL_SPREAD;

            // The last tendril always reaches full height, as the legacy shape intended; the
            // others take somewhere between half and all of it. Both bounds are positive by
            // construction, whatever the radius, which is the crash's permanent cure.
            int height;
            if (tendril == TENDRILS - 1) {
                height = radius;
            } else {
                int shortest = Math.max(minimumRadius(), radius / 2);
                int span = radius - shortest + 1;
                long heightSeed = ShapeNoise.derive(seed, SALT_TENDRIL_HEIGHT, tendril);
                height = shortest + (int) Long.remainderUnsigned(heightSeed >>> 1, span);
            }
            walk(cells, seed, tendril + 1, startX, startZ, height);
        }
        return ShapePlan.of(cells, bounds(config));
    }

    /** One winding climb, from {@code (startX, 0, startZ)} up to at most {@code height}. */
    private static void walk(
            List<ShapeOffset> cells, long seed, int vein, int startX, int startZ, int height) {
        int x = startX;
        int y = 0;
        int z = startZ;
        int steps = height * STEPS_PER_HEIGHT;

        for (int step = 0; step < steps; step++) {
            long stepSeed = ShapeNoise.derive(seed, SALT_WALK, vein * 8191 + step);
            x += (int) Long.remainderUnsigned(stepSeed >>> 1, 3) - 1;
            y += (int) Long.remainderUnsigned(stepSeed >>> 20, 2);
            z += (int) Long.remainderUnsigned(stepSeed >>> 40, 3) - 1;

            if (y >= height) break;

            // Four cells in five, so the vein reads as broken rather than as a drawn line.
            if (ShapeNoise.unit(seed, SALT_PLACE + vein, x, y, z) < 0.80f) {
                cells.add(new ShapeOffset(x, y, z));
            }
        }
    }
}
