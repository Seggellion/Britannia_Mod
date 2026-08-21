package com.seggellion.britannia_mod.resource.shape;

/**
 * A geological shape, as pure geometry.
 *
 * <h2>The contract</h2>
 * {@code plan(config)} answers: given a deterministic seed and a configuration, which cells belong
 * to this deposit, relative to its origin?
 *
 * <p>A planner may not:
 * <ul>
 *   <li>touch a level, a chunk, or any block state;</li>
 *   <li>draw from shared runtime randomness — {@code ServerLevel#getRandom()} and friends;</li>
 *   <li>write anything anywhere;</li>
 *   <li>decide whether a cell is an acceptable host, contains a fluid, holds a block entity, or is
 *       protected. Those are {@code MaterializationService}'s questions.</li>
 * </ul>
 *
 * <p>It must:
 * <ul>
 *   <li>return identical cells for identical {@code (radius, rotation, seed)};</li>
 *   <li>return each cell at most once;</li>
 *   <li>keep every cell inside the bounds it declared;</li>
 *   <li>reject a configuration it cannot serve, before planning rather than during.</li>
 * </ul>
 *
 * <p>The last point is the permanent fix for the crash milestone 1 contained externally. A planner
 * that would divide by zero or ask for a random number below a non-positive bound refuses the
 * configuration itself, so no caller — command, future world generation, or test — can reach the
 * failure by forgetting to validate first.
 */
public interface ShapePlanner {

    /** The smallest radius this geometry means anything at, and below which planning is refused. */
    int minimumRadius();

    /** Whether {@link ShapeConfig#rotation()} changes this shape's output. */
    default boolean usesRotation() {
        return false;
    }

    /** The box the plan will fall inside, known before planning. */
    ShapeBounds bounds(ShapeConfig config);

    /** The deposit's cells, relative to its origin. */
    ShapePlan plan(ShapeConfig config);

    /**
     * Shared configuration check. Called by every planner before it does anything, so the refusal
     * message is identical wherever it comes from.
     */
    default void validate(ShapeConfig config) {
        if (config.radius() < minimumRadius()) {
            throw new IllegalArgumentException(getClass().getSimpleName()
                    + " needs a radius of at least " + minimumRadius() + ", was given " + config.radius());
        }
    }
}
