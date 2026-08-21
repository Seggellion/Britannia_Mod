package com.seggellion.britannia_mod.features;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a curated vein row must satisfy before any shape algorithm is allowed to see it.
 *
 * <h2>Why this exists at milestone 1</h2>
 * The six shape classes are imperative, mutate the world directly, and trust their arguments
 * completely. Several of them throw outright on values Rails is free to send:
 *
 * <ul>
 *   <li>{@link SnakeVein} computes {@code random.nextInt(radius - 9)}, which throws
 *       {@code IllegalArgumentException} for any radius of 9 or less. It is evaluated <em>before</em>
 *       the full-height override on the next line, so the throw is unconditional on the first
 *       tendril — a single bad gold row aborts the whole command part-way through, after it has
 *       already written blocks.</li>
 *   <li>{@link GeodeVein} computes {@code random.nextInt(radius / 2)}, which throws for any radius
 *       below 2.</li>
 *   <li>{@link LayeredVein} and {@link VerticalLayeredVein} compute {@code random.nextInt(radius * 2)},
 *       which throws at radius 0 and for negative radii.</li>
 *   <li>{@link ClusterVein} divides by {@code radius}, producing NaN comparisons at radius 0.</li>
 *   <li>{@link VerticalLayeredVein}'s rotation {@code switch} has no default, so an unrecognised
 *       rotation silently leaves every offset at zero and stacks the entire deposit in one cell.</li>
 * </ul>
 *
 * <p>Correcting the algorithms is milestone 3, which replaces them with deterministic pure
 * planners. This milestone only has to guarantee that malformed input cannot reach them — so the
 * rules live here, ahead of the call, and a rejected row is reported and skipped rather than
 * throwing through a half-finished command.
 *
 * <p>Pure Java on purpose, exactly like {@code MineableCatalog}: no Minecraft types, so a plain
 * JUnit test drives every boundary without booting the game. Build-height limits are passed in by
 * the caller rather than read from a level here.
 *
 * <h2>Known duplication</h2>
 * The ore-name-to-shape table below repeats the {@code switch} in {@code PopulateOresCommand}.
 * That duplication is deliberate and temporary: milestone 2 moves the mapping into the canonical
 * resource definition, at which point both copies collapse into the definition's shape field.
 */
public final class VeinPlacementValidation {

    /** The shapes the legacy command can actually select, and what each one needs to be safe. */
    public enum Shape {
        /** Cubic scan, so its cost grows with the cube of the radius; capped tighter than the rest. */
        CLUSTER(1, 32),
        VERTICAL(1, 128),
        /** {@code nextInt(radius - 9)} throws at or below 9. */
        SNAKE(10, 128),
        /** {@code nextInt(radius / 2)} throws below 2. */
        GEODE(2, 128),
        LAYERED(1, 128),
        VERTICAL_LAYERED(1, 128);

        private final int minimumRadius;
        private final int maximumRadius;

        Shape(int minimumRadius, int maximumRadius) {
            this.minimumRadius = minimumRadius;
            this.maximumRadius = maximumRadius;
        }

        public int minimumRadius() {
            return minimumRadius;
        }

        public int maximumRadius() {
            return maximumRadius;
        }
    }

    /**
     * Ore types the legacy command can place, and the shape each uses.
     *
     * <p>Coal is deliberately absent. It was placed as {@code minecraft:coal_ore}, a block the
     * Mining catalogue does not govern, so it broke with vanilla drops, no skill requirement and
     * no restoration — a managed generation route manufacturing unmanaged economic material.
     * Milestone 1 withdraws the route; coal returns when it has a real resource definition.
     */
    private static final Map<String, Shape> SHAPES = Map.of(
            "copper", Shape.CLUSTER,
            "verite", Shape.CLUSTER,
            "iron", Shape.VERTICAL,
            "valorite", Shape.VERTICAL,
            "shadow_iron", Shape.VERTICAL,
            "gold", Shape.SNAKE,
            "agapite", Shape.GEODE,
            "silver", Shape.VERTICAL_LAYERED,
            "tin", Shape.LAYERED);

    /** The rotations {@link VerticalLayeredVein} actually understands. */
    private static final java.util.Set<String> ROTATIONS =
            java.util.Set.of("XZ", "YZ", "XY", "ZW");

    /** The default the command has always applied when a row carries no rotation. */
    public static final String DEFAULT_ROTATION = "XZ";

    /** Beyond the world border a placement is meaningless; reject rather than write into nowhere. */
    public static final int MAX_HORIZONTAL = 30_000_000;

    private VeinPlacementValidation() {
    }

    /** The shape this ore type uses, or empty when the legacy command cannot place it at all. */
    public static Optional<Shape> shapeFor(String oreType) {
        if (oreType == null) return Optional.empty();
        return Optional.ofNullable(SHAPES.get(oreType.toLowerCase(Locale.ROOT)));
    }

    /** Every ore type the legacy command can still place, for operator-facing messages. */
    public static java.util.List<String> placeableOreTypes() {
        return SHAPES.keySet().stream().sorted().toList();
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

        Shape shape = shapeFor(oreType).orElse(null);
        if (shape == null) {
            return Optional.of("unknown or unplaceable ore type '" + oreType
                    + "' (placeable: " + String.join(", ", placeableOreTypes()) + ")");
        }
        if (radius < shape.minimumRadius()) {
            return Optional.of("radius " + radius + " is below the minimum " + shape.minimumRadius()
                    + " that the " + shape.name().toLowerCase(Locale.ROOT) + " shape can produce");
        }
        if (radius > shape.maximumRadius()) {
            return Optional.of("radius " + radius + " exceeds the maximum " + shape.maximumRadius()
                    + " allowed for the " + shape.name().toLowerCase(Locale.ROOT) + " shape");
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
