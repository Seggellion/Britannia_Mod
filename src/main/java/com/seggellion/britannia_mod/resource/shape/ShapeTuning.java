package com.seggellion.britannia_mod.resource.shape;

/**
 * Optional geometric knobs a shape may read, for the shapes that genuinely have any.
 *
 * <h2>Why this is one record and not a config type per shape</h2>
 * Five of the seven planners are fully described by a radius. The sedimentary lens is the first
 * that is not: a bed's thickness is independent of its footprint — that is what makes it a bed —
 * and how ragged its rim is and how porous its margins are are real geological choices rather than
 * derivable from the radius. Those three things want to live in data.
 *
 * <p>Giving every shape its own configuration type would mean a parallel hierarchy, a parsing
 * branch per shape, and a cast at every call site, for one shape's benefit. Carrying three numbers
 * that most planners ignore costs nothing and keeps {@link ShapeConfig} a single type. When a
 * second shape wants something genuinely different, that is the moment to reconsider — not now.
 *
 * <p>{@link #DEFAULT} means "the planner's own judgement". A planner that reads tuning must behave
 * sensibly under it, so a resource that names no tuning still gets a coherent deposit.
 *
 * @param thickness    total vertical extent in blocks at the thickest point; {@code 0} means default
 * @param irregularity how far the rim wanders, {@code 0} for a clean ellipse, {@code 1} for very ragged
 * @param gapChance    probability that a cell away from the bed's core is absent
 */
public record ShapeTuning(int thickness, double irregularity, double gapChance) {

    /** No opinion: every planner falls back to its own defaults. */
    public static final ShapeTuning DEFAULT = new ShapeTuning(0, -1.0, -1.0);

    /** The widest a bed may be told to be. Beyond this the plan stops being a bed. */
    public static final int MAX_THICKNESS = 16;

    public ShapeTuning {
        if (thickness < 0 || thickness > MAX_THICKNESS) {
            throw new IllegalArgumentException(
                    "shape thickness must be between 0 and " + MAX_THICKNESS + ", was " + thickness);
        }
        if (irregularity != -1.0 && (irregularity < 0.0 || irregularity > 1.0)) {
            throw new IllegalArgumentException(
                    "shape irregularity must be between 0 and 1, was " + irregularity);
        }
        if (gapChance != -1.0 && (gapChance < 0.0 || gapChance > 0.9)) {
            // Above 0.9 the margins stop being porous and start being absent, which produces the
            // disconnected scatter this whole shape exists to avoid.
            throw new IllegalArgumentException(
                    "shape gap chance must be between 0 and 0.9, was " + gapChance);
        }
    }

    public boolean hasThickness() {
        return thickness > 0;
    }

    public int thicknessOr(int fallback) {
        return hasThickness() ? thickness : fallback;
    }

    public double irregularityOr(double fallback) {
        return irregularity < 0.0 ? fallback : irregularity;
    }

    public double gapChanceOr(double fallback) {
        return gapChance < 0.0 ? fallback : gapChance;
    }
}
