package com.seggellion.britannia_mod.features;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.ResourceShape;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a curated vein row must satisfy before any shape algorithm is allowed to see it.
 *
 * <h2>Why this exists</h2>
 * The six shape classes are imperative, mutate the world directly, and trust their arguments
 * completely. Several throw outright on values Rails is free to send: {@link SnakeVein} on a radius
 * of nine or less, {@link GeodeVein} below two, {@link LayeredVein} and {@link VerticalLayeredVein}
 * at zero, and {@link ClusterVein} divides by the radius. Correcting the algorithms is milestone 3;
 * this guarantees malformed input never reaches them, so a bad row is reported and skipped rather
 * than throwing through a half-finished command.
 *
 * <h2>Where the numbers live now</h2>
 * Milestone 1 held its own ore-name-to-shape table and its own per-ore radius bounds, which was a
 * second source of truth for facts the command already had. Milestone 2 removed it:
 *
 * <ul>
 *   <li><b>Which shape an ore uses, and its configured radius range</b>, are read from that
 *       resource's {@link ResourceDefinition.Generation} in {@code resources.json}. Tuning a vein
 *       is now a data edit.</li>
 *   <li><b>The radius at which an algorithm throws</b> stays with the algorithm, in
 *       {@link ResourceShape}. That is not tuning — it is a property of the code — and
 *       {@code ResourceCatalog} refuses a configured minimum that dips below it, so data can
 *       narrow the safe range but never widen it back into the crashing one.</li>
 * </ul>
 *
 * <p>This class is now a validator, not a registry. Milestone 3 folds the remaining rules into the
 * shape codecs when the planners replace the six imperative classes, at which point the whole file
 * goes away.
 */
public final class VeinPlacementValidation {

    /** Beyond the world border a placement is meaningless; reject rather than write into nowhere. */
    public static final int MAX_HORIZONTAL = 30_000_000;

    /** The rotations {@link VerticalLayeredVein} actually understands. */
    private static final java.util.Set<String> ROTATIONS =
            java.util.Set.of("XZ", "YZ", "XY", "ZW");

    /** The default the command has always applied when a row carries no rotation. */
    public static final String DEFAULT_ROTATION = "XZ";

    private VeinPlacementValidation() {
    }

    /** The resource this legacy ore name refers to, if the command can place it at all. */
    public static Optional<ResourceDefinition> resourceFor(String oreType) {
        if (oreType == null) return Optional.empty();
        return ResourceCatalog.instance().byPath(oreType.toLowerCase(Locale.ROOT))
                .filter(definition -> definition.generation().isPresent());
    }

    /** The shape this ore type uses, or empty when the legacy command cannot place it at all. */
    public static Optional<ResourceShape> shapeFor(String oreType) {
        return resourceFor(oreType)
                .flatMap(ResourceDefinition::generation)
                .map(ResourceDefinition.Generation::shape);
    }

    /** Every ore type the legacy command can still place, for operator-facing messages. */
    public static List<String> placeableOreTypes() {
        return ResourceCatalog.instance().generatable().stream()
                .map(ResourceDefinition::path)
                .toList();
    }

    /**
     * Normalises a row's rotation: absent means the historical default, anything unrecognised is
     * a rejection rather than a silent collapse to a single cell.
     */
    public static Optional<String> normaliseRotation(String rotation) {
        if (rotation == null || rotation.isBlank()) return Optional.of(DEFAULT_ROTATION);
        String upper = rotation.trim().toUpperCase(Locale.ROOT);
        return ROTATIONS.contains(upper) ? Optional.of(upper) : Optional.empty();
    }

    /**
     * Why this row must not be placed, or empty when it is safe to hand to its shape.
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
        ResourceDefinition.Generation generation = resource.generation().orElseThrow();
        String shapeName = generation.shape().id();

        if (radius < generation.minRadius()) {
            return Optional.of("radius " + radius + " is below the minimum " + generation.minRadius()
                    + " configured for " + resource.path() + "'s " + shapeName + " shape");
        }
        if (radius > generation.maxRadius()) {
            return Optional.of("radius " + radius + " exceeds the maximum " + generation.maxRadius()
                    + " configured for " + resource.path() + "'s " + shapeName + " shape");
        }
        if (normaliseRotation(rotation).isEmpty()) {
            return Optional.of("unrecognised rotation '" + rotation + "' (expected one of "
                    + ROTATIONS.stream().sorted().toList() + ")");
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
