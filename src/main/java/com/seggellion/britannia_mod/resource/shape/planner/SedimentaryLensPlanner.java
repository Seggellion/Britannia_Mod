package com.seggellion.britannia_mod.resource.shape.planner;

import com.seggellion.britannia_mod.resource.shape.ShapeBounds;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapeNoise;
import com.seggellion.britannia_mod.resource.shape.ShapeOffset;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;
import com.seggellion.britannia_mod.resource.shape.ShapePlanner;
import com.seggellion.britannia_mod.resource.shape.ShapeTuning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A sedimentary lens: a broad, thin, gently undulating bed that thins to nothing at its rim.
 *
 * <h2>How this differs from {@code LayeredPlanner}, which is the obvious question</h2>
 * {@code LayeredPlanner} is a seam of fixed thickness with a density falloff — a disc of constant
 * depth that simply gets patchier towards the edge. A real lens is not patchier at the rim; it is
 * <em>thinner</em>, and it ends. That single difference is what makes one read as a mineral seam
 * and the other as a bed of sediment laid down in a basin.
 *
 * <p>Four properties, each doing one job:
 *
 * <ol>
 *   <li><b>Elliptical footprint.</b> The two horizontal radii differ by a seed-derived aspect
 *       ratio, so beds are oval rather than circular, and no two are oriented alike.</li>
 *   <li><b>Lens profile.</b> Local half-thickness follows {@code sqrt(1 - d²)} in normalised
 *       radial distance: full thickness at the centre, tapering to a single cell at the rim. This
 *       is the shape of an actual lens, and it is why the deposit has an edge instead of a cliff.</li>
 *   <li><b>Undulating median plane.</b> The bed's centre drifts vertically with smooth
 *       low-frequency noise, so it follows a gently dipping surface rather than lying flat. Drift
 *       is bounded so it can never separate the bed into layers.</li>
 *   <li><b>Ragged rim.</b> The boundary radius is modulated by smooth noise, so the outline is
 *       irregular without being noisy. Irregularity is applied to the <em>boundary</em>, not to
 *       individual cells, which is the difference between a natural outline and static.</li>
 * </ol>
 *
 * <h2>Why it cannot produce floating scatter</h2>
 * Every accepted column contains an unbroken run of cells from {@code y = 0} to its own median, so
 * every column meets the datum plane. The accepted columns are the interior of a single closed
 * boundary curve, so the datum plane is one connected sheet — and therefore so is the whole body.
 * Gaps are only ever applied strictly above or below the course, so removing one can never detach
 * anything. There is no configuration in which this planner returns an isolated cell, and a
 * connectivity test asserts it rather than trusting the argument.
 *
 * <h2>Determinism and chunk slicing</h2>
 * Every decision is a pure function of {@code (seed, salt, x, z)} or {@code (seed, salt, x, y, z)}
 * through {@link ShapeNoise}. Nothing is sequential, so evaluating any subset of columns in any
 * order gives the same cells, and {@code union(all chunk slices) == complete plan} holds by
 * construction rather than by convention.
 */
public final class SedimentaryLensPlanner implements ShapePlanner {

    /**
     * Below this a lens has no rim to speak of and is indistinguishable from a blob.
     *
     * <p>Six rather than four so that the minimum radius is usable with the default thickness: a
     * shape whose smallest legal radius is rejected by its own breadth rule would be a trap for the
     * first caller who left the tuning out.
     */
    private static final int MINIMUM_RADIUS = 6;

    /** Default total thickness when a resource does not name one. */
    private static final int DEFAULT_THICKNESS = 4;
    private static final double DEFAULT_IRREGULARITY = 0.22;
    private static final double DEFAULT_GAP_CHANCE = 0.25;

    /**
     * A bed must be broader than it is deep, or it is not a bed. The rule is expressed as a ratio
     * so that it scales: a lens 4 thick needs a radius of at least 6.
     */
    private static final double MIN_BREADTH_TO_THICKNESS = 1.5;

    /** Lattice spacings, in blocks, for the two smooth fields. Both well above cell scale. */
    private static final double RIM_SCALE = 11.0;
    private static final double DRIFT_SCALE = 17.0;

    private static final int SALT_ASPECT = 41;
    private static final int SALT_RIM = 42;
    private static final int SALT_DRIFT = 43;
    private static final int SALT_GAP = 44;

    @Override
    public int minimumRadius() {
        return MINIMUM_RADIUS;
    }

    @Override
    public ShapeBounds bounds(ShapeConfig config) {
        // The declared box is the generous one: full radius on both horizontal axes even though the
        // narrow axis will not use all of it, and half the thickness plus the drift limit vertically.
        int halfThickness = halfThickness(config);
        return ShapeBounds.of(config.radius(), halfThickness + driftLimit(halfThickness), config.radius());
    }

    @Override
    public void validate(ShapeConfig config) {
        ShapePlanner.super.validate(config);
        int thickness = thickness(config);
        if (thickness < 1) {
            throw new IllegalArgumentException(
                    "A sedimentary lens needs a thickness of at least 1, was " + thickness);
        }
        if (config.radius() < thickness * MIN_BREADTH_TO_THICKNESS) {
            throw new IllegalArgumentException(
                    "A sedimentary lens must be broader than it is deep: radius " + config.radius()
                            + " with thickness " + thickness + " needs a radius of at least "
                            + (int) Math.ceil(thickness * MIN_BREADTH_TO_THICKNESS));
        }
        // An upper bound on the plan, refused before any work is done rather than discovered by
        // ShapePlan's cap after the whole thing has been drawn. Every factor left out -- the
        // elliptical aspect, the taper, the gaps -- can only reduce the true count, so this never
        // admits a configuration that would then blow the cap. It is roughly two and a half times
        // pessimistic, which in practice means a slightly smaller usable radius rather than a risk.
        int halfThickness = halfThickness(config);
        long ceiling = Math.round(Math.PI * config.radius() * (double) config.radius()
                * (2L * halfThickness + driftLimit(halfThickness) + 1));
        if (ceiling > ShapePlan.MAX_CELLS) {
            throw new IllegalArgumentException(
                    "A sedimentary lens of radius " + config.radius() + " and thickness " + thickness
                            + " could plan up to " + ceiling + " cells, over the " + ShapePlan.MAX_CELLS
                            + "-cell limit; reduce the radius or the thickness");
        }
    }

    @Override
    public ShapePlan plan(ShapeConfig config) {
        validate(config);
        long seed = config.seed();
        int radius = config.radius();
        int halfThickness = halfThickness(config);
        double irregularity = config.tuning().irregularityOr(DEFAULT_IRREGULARITY);
        double gapChance = config.tuning().gapChanceOr(DEFAULT_GAP_CHANCE);

        // The narrow axis, between 62% and 100% of the configured radius. Which axis is narrow is
        // itself seed-derived, so beds are not all elongated the same way.
        double aspect = 0.62 + 0.38 * ShapeNoise.unit(seed, SALT_ASPECT, 0, 0, 0);
        boolean narrowIsX = ShapeNoise.unit(seed, SALT_ASPECT, 1, 0, 0) < 0.5f;
        double radiusX = narrowIsX ? radius * aspect : radius;
        double radiusZ = narrowIsX ? radius : radius * aspect;

        int drift = driftLimit(halfThickness);
        List<ShapeOffset> cells = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double normalised = Math.sqrt(
                        square(x / radiusX) + square(z / radiusZ));

                // The rim wanders smoothly rather than per cell, which is what keeps the outline
                // irregular but continuous.
                double rim = 1.0 + irregularity * ShapeNoise.smoothSigned2(seed, SALT_RIM, x, z, RIM_SCALE);
                if (normalised > rim) continue;

                // Lens profile: thickest at the centre, one cell at the rim.
                //
                // Rounded rather than floored. Flooring looks equivalent and is not: the taper is
                // within a whisker of 1.0 across the whole middle of the bed, so floor(2 * 0.993)
                // is 1 and only the single cell at the exact origin ever reaches full thickness.
                // That produces a bed of almost uniform thinness with a pinprick in the centre,
                // which is not a lens. Rounding gives the profile it is supposed to have.
                double taper = Math.sqrt(Math.max(0.0, 1.0 - square(normalised / rim)));
                int columnHalf = (int) Math.round(halfThickness * taper);

                // The bed's median plane dips gently across the deposit -- but the dip is scaled by
                // the same taper as the thickness, so the rim pinches out on a level datum while
                // the body undulates. That is both what a lens does and what makes the next line
                // safe.
                int median = (int) Math.round(
                        drift * taper * ShapeNoise.smoothSigned2(seed, SALT_DRIFT, x, z, DRIFT_SCALE));

                // The course: every cell between the datum plane and this column's median, always
                // present. Because every column therefore contains y=0, and the accepted columns
                // are the interior of one closed boundary curve, the whole plan is a single
                // connected body -- by construction, not by luck. At the rim the median is zero, so
                // this is exactly one cell and the bed really does thin to nothing.
                int courseLow = Math.min(0, median);
                int courseHigh = Math.max(0, median);
                for (int y = courseLow; y <= courseHigh; y++) {
                    cells.add(new ShapeOffset(x, y, z));
                }

                for (int dy = 1; dy <= columnHalf; dy++) {
                    // Porosity only in the body, never in the course, and only once the column is
                    // thick enough that removing a cell cannot detach anything.
                    addIfKept(cells, seed, gapChance, x, median + dy, z, columnHalf);
                    addIfKept(cells, seed, gapChance, x, median - dy, z, columnHalf);
                }
            }
        }
        return ShapePlan.of(largestBody(cells), bounds(config));
    }

    /**
     * Keep the main body and discard any satellite the rim noise pinched off.
     *
     * <p>Every column reaches the datum plane, so the body is connected exactly when the accepted
     * columns form one region in plan view — and a smoothly wandering rim can occasionally cut a
     * small lobe loose, most often at small radii where the wander is large relative to the
     * footprint. Rather than tuning the noise until it stops happening and hoping, the plan drops
     * the stragglers. That is also the honest geology: an outlier blob eight blocks from the bed is
     * not part of the bed.
     *
     * <p>Deterministic and order-independent: components are found by flood fill over a set, the
     * largest wins, and ties are broken by the lowest cell in canonical order rather than by
     * whichever the iterator happened to reach first.
     */
    private static List<ShapeOffset> largestBody(List<ShapeOffset> cells) {
        Set<ShapeOffset> remaining = new HashSet<>(cells);
        List<ShapeOffset> best = List.of();
        ShapeOffset bestAnchor = null;

        for (ShapeOffset cell : cells) {
            if (!remaining.remove(cell)) continue;
            List<ShapeOffset> component = new ArrayList<>();
            ShapeOffset anchor = cell;
            Deque<ShapeOffset> queue = new ArrayDeque<>();
            queue.add(cell);
            component.add(cell);
            while (!queue.isEmpty()) {
                ShapeOffset current = queue.poll();
                for (ShapeOffset neighbour : neighbours(current)) {
                    if (remaining.remove(neighbour)) {
                        queue.add(neighbour);
                        component.add(neighbour);
                        if (compare(neighbour, anchor) < 0) {
                            anchor = neighbour;
                        }
                    }
                }
            }
            if (component.size() > best.size()
                    || (component.size() == best.size() && compare(anchor, bestAnchor) < 0)) {
                best = component;
                bestAnchor = anchor;
            }
        }
        // Restore the canonical planning order, so the plan is byte-identical run to run.
        List<ShapeOffset> ordered = new ArrayList<>(best.size());
        Set<ShapeOffset> kept = new HashSet<>(best);
        for (ShapeOffset cell : cells) {
            if (kept.contains(cell)) {
                ordered.add(cell);
            }
        }
        return ordered;
    }

    private static int compare(ShapeOffset a, ShapeOffset b) {
        if (b == null) return -1;
        if (a.x() != b.x()) return Integer.compare(a.x(), b.x());
        if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
        return Integer.compare(a.z(), b.z());
    }

    private static List<ShapeOffset> neighbours(ShapeOffset cell) {
        return List.of(
                new ShapeOffset(cell.x() + 1, cell.y(), cell.z()),
                new ShapeOffset(cell.x() - 1, cell.y(), cell.z()),
                new ShapeOffset(cell.x(), cell.y() + 1, cell.z()),
                new ShapeOffset(cell.x(), cell.y() - 1, cell.z()),
                new ShapeOffset(cell.x(), cell.y(), cell.z() + 1),
                new ShapeOffset(cell.x(), cell.y(), cell.z() - 1));
    }

    private static void addIfKept(
            List<ShapeOffset> cells, long seed, double gapChance, int x, int y, int z, int columnHalf) {
        if (columnHalf >= 1 && ShapeNoise.unit(seed, SALT_GAP, x, y, z) < gapChance) {
            return;
        }
        cells.add(new ShapeOffset(x, y, z));
    }

    private static int thickness(ShapeConfig config) {
        ShapeTuning tuning = config.tuning();
        return tuning.thicknessOr(DEFAULT_THICKNESS);
    }

    private static int halfThickness(ShapeConfig config) {
        return Math.max(0, thickness(config) / 2);
    }

    /**
     * How far the median plane may wander from the origin.
     *
     * <p>Tied to the thickness rather than configured separately: a bed that drifts further than it
     * is thick has stopped being one bed, and there is no configuration in which that is wanted.
     */
    private static int driftLimit(int halfThickness) {
        // Half the half-thickness, not all of it. The course between the datum plane and the median
        // is real bed, so every block of drift adds a block to the column -- and at the rim, where
        // the profile is supposed to be pinching out, that inflation is exactly where it is least
        // wanted. Half is enough to read as a dipping bed and small enough not to fight the taper.
        return Math.max(1, halfThickness / 2);
    }

    private static double square(double value) {
        return value * value;
    }
}
