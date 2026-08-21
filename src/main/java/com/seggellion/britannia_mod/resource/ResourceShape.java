package com.seggellion.britannia_mod.resource;

import java.util.Locale;
import java.util.Optional;

/**
 * The geological shapes a resource definition may name, and the radius each one cannot survive.
 *
 * <h2>Hard floor versus configured range</h2>
 * A shape's <b>hard floor</b> is a property of its algorithm, not a design choice: below it the
 * implementation throws. {@code SnakeVein} computes {@code random.nextInt(radius - 9)}, so nine is
 * fatal; {@code GeodeVein} computes {@code random.nextInt(radius / 2)}, so one is fatal; the
 * layered shapes and the cluster reach {@code nextInt(radius * 2)} or divide by the radius, so
 * zero is fatal for all of them. Those numbers belong with the algorithm and are stated here.
 *
 * <p>A resource's <b>configured range</b> is tuning, and belongs in its definition — one ore may
 * reasonably be capped smaller than another using the same shape. {@link ResourceCatalog}
 * validates the configured minimum against the hard floor, so data can narrow the safe range but
 * never widen it back into the crashing one.
 *
 * <p>Moved here from {@code VeinPlacementValidation} at milestone 2 so that the resource package
 * owns shape identity and {@code features} depends on it rather than the other way round.
 * Milestone 3 replaces the six imperative implementations with deterministic pure planners; when
 * it does, this enum becomes the planner registry's key and the hard floors disappear with the
 * algorithms that needed them.
 */
public enum ResourceShape {
    CLUSTER(1),
    VERTICAL(1),
    /** {@code nextInt(radius - 9)} throws at or below 9. */
    SNAKE(10),
    /** {@code nextInt(radius / 2)} throws below 2. */
    GEODE(2),
    LAYERED(1),
    VERTICAL_LAYERED(1);

    private final int hardFloorRadius;

    ResourceShape(int hardFloorRadius) {
        this.hardFloorRadius = hardFloorRadius;
    }

    /** The smallest radius this algorithm can be handed without throwing. */
    public int hardFloorRadius() {
        return hardFloorRadius;
    }

    /** The id used in data, e.g. {@code vertical_layered}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ResourceShape> byId(String id) {
        if (id == null) return Optional.empty();
        for (ResourceShape shape : values()) {
            if (shape.id().equals(id.toLowerCase(Locale.ROOT))) return Optional.of(shape);
        }
        return Optional.empty();
    }
}
