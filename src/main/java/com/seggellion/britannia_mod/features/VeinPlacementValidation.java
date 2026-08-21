package com.seggellion.britannia_mod.features;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * External-input checks for a curated vein row, before it becomes a placement.
 *
 * <h2>What this is now</h2>
 * Milestone 1 created this class because the six imperative shapes threw on values Rails was free
 * to send, and something had to stop those values reaching them. Milestone 2 moved the shape table
 * and the radius bounds into the resource catalogue. Milestone 3 finished the job: the shapes are
 * gone, and every rule that was ever about a shape now lives with the shape.
 *
 * <ul>
 *   <li><b>Which shape a resource uses, and its configured radius range</b> — the resource
 *       definition. Asked through {@link PlacementPlanner#reject}, which is the same check the
 *       planner applies to itself, so the command and the planner cannot disagree.</li>
 *   <li><b>The radius a geometry means nothing below</b> — the planner. It refuses its own
 *       configuration before planning, so no caller can reach the failure by forgetting to
 *       validate first.</li>
 *   <li><b>Which cells may actually be written</b> — {@code MaterializationService}, against the
 *       resource's host tag.</li>
 * </ul>
 *
 * <p>What is left here is what genuinely belongs to the <em>row</em> rather than to the resource or
 * the shape: is this a resource the command can place at all, is the rotation a word we know, is
 * the origin somewhere a world can exist. Those are properties of an external message, and they are
 * checked once, where the message arrives.
 *
 * <p>This class survives milestone 3 rather than being deleted with the shapes because the curated
 * Rails row survives it too. When milestone 8 replaces the synchronous fetch with a validated
 * importer, this is the validation that importer inherits, and the file goes with it.
 */
public final class VeinPlacementValidation {

    /** Beyond the world border a placement is meaningless; reject rather than write into nowhere. */
    public static final int MAX_HORIZONTAL = 30_000_000;

    /** The rotation the curated rows have always defaulted to when they carry none. */
    public static final ShapeRotation DEFAULT_ROTATION = ShapeRotation.XZ;

    private VeinPlacementValidation() {
    }

    /** The resource this legacy ore name refers to, if the command can place it at all. */
    public static Optional<ResourceDefinition> resourceFor(String oreType) {
        if (oreType == null) return Optional.empty();
        return ResourceCatalog.instance().byPath(oreType.toLowerCase(Locale.ROOT))
                .filter(definition -> definition.generation().isPresent());
    }

    /** Every ore type the legacy command can still place, for operator-facing messages. */
    public static List<String> placeableOreTypes() {
        return ResourceCatalog.instance().generatable().stream()
                .map(ResourceDefinition::path)
                .toList();
    }

    /**
     * Normalises a row's rotation: absent means the historical default, anything unrecognised is a
     * rejection rather than a silent collapse.
     *
     * <p>The legacy {@code VerticalLayeredVein} had a rotation {@code switch} with no default, so an
     * unrecognised value left every offset at zero and stacked the whole deposit into one cell.
     * {@link ShapeRotation} is an enum, so that failure no longer has anywhere to happen — but a row
     * can still carry a word we do not know, and saying so is better than assuming one.
     */
    public static Optional<ShapeRotation> normaliseRotation(String rotation) {
        if (rotation == null || rotation.isBlank()) return Optional.of(DEFAULT_ROTATION);
        return ShapeRotation.byId(rotation);
    }

    /**
     * Why this row must not be placed, or empty when it is safe to plan.
     *
     * @return a short operator-facing reason, suitable for a command message and a log line
     */
    public static Optional<String> reject(
            String oreType,
            int radius,
            String rotation,
            int x,
            int y,
            int z,
            int minBuildHeight,
            int maxBuildHeight) {

        ResourceDefinition resource = resourceFor(oreType).orElse(null);
        if (resource == null) {
            return Optional.of("unknown or unplaceable ore type '" + oreType
                    + "' (placeable: " + String.join(", ", placeableOreTypes()) + ")");
        }
        // Radius is the resource's and the shape's business, not this class's. Asking the planner
        // means there is exactly one answer, and it is the one placement will actually apply.
        Optional<PlacementPlanner.Rejection> radiusRejection =
                PlacementPlanner.reject(resource, radius);
        if (radiusRejection.isPresent()) {
            return Optional.of(radiusRejection.get().reason());
        }
        if (normaliseRotation(rotation).isEmpty()) {
            return Optional.of("unrecognised rotation '" + rotation + "' (expected one of "
                    + java.util.Arrays.stream(ShapeRotation.values()).map(ShapeRotation::id).toList() + ")");
        }
        if (y < minBuildHeight || y > maxBuildHeight) {
            return Optional.of("y " + y + " is outside the build range ["
                    + minBuildHeight + ", " + maxBuildHeight + "]");
        }
        if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL) {
            return Optional.of("coordinates (" + x + ", " + z + ") lie beyond the world limit");
        }
        return Optional.empty();
    }
}
