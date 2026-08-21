package com.seggellion.britannia_mod.resource;

import com.seggellion.britannia_mod.resource.shape.ShapePlanner;
import com.seggellion.britannia_mod.resource.shape.planner.ClusterPlanner;
import com.seggellion.britannia_mod.resource.shape.planner.GeodePlanner;
import com.seggellion.britannia_mod.resource.shape.planner.LayeredPlanner;
import com.seggellion.britannia_mod.resource.shape.planner.SedimentaryLensPlanner;
import com.seggellion.britannia_mod.resource.shape.planner.SnakePlanner;
import com.seggellion.britannia_mod.resource.shape.planner.VerticalLayeredPlanner;
import com.seggellion.britannia_mod.resource.shape.planner.VerticalPlanner;

import java.util.Locale;
import java.util.Optional;

/**
 * The geological shapes a resource definition may name, and the planner that draws each one.
 *
 * <h2>One source for a shape's limits</h2>
 * Milestone 2 kept a table of "hard floor" radii here, measured from the legacy algorithms'
 * crash points. Milestone 3 deleted the algorithms, so the number is no longer a crash boundary —
 * it is the smallest radius at which the geometry means anything, and it belongs to the planner
 * that defines the geometry. {@link #minimumRadius()} delegates, so the enum, the catalogue's
 * validation and the planner itself cannot disagree.
 *
 * <p>A resource's configured range may narrow this, and several do; it may never widen it, because
 * {@link ResourceCatalog} refuses a configured minimum below the planner's own and every planner
 * re-checks its own contract before planning anyway.
 */
public enum ResourceShape {
    CLUSTER(new ClusterPlanner()),
    VERTICAL(new VerticalPlanner()),
    SNAKE(new SnakePlanner()),
    GEODE(new GeodePlanner()),
    LAYERED(new LayeredPlanner()),
    VERTICAL_LAYERED(new VerticalLayeredPlanner()),
    /** Milestone 7: a broad thin bed that thins to nothing at its rim. */
    SEDIMENTARY_LENS(new SedimentaryLensPlanner());

    private final ShapePlanner planner;

    ResourceShape(ShapePlanner planner) {
        this.planner = planner;
    }

    /** The pure planner that draws this shape. Holds no state and touches no world. */
    public ShapePlanner planner() {
        return planner;
    }

    /** The smallest radius this geometry means anything at, defined by the planner. */
    public int minimumRadius() {
        return planner.minimumRadius();
    }

    /** Whether the configured rotation changes this shape's output. */
    public boolean usesRotation() {
        return planner.usesRotation();
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
