package com.seggellion.britannia_mod.resource.shape;

import java.util.Collection;

/**
 * The box a plan's cells are guaranteed to lie within, relative to the deposit origin.
 *
 * <p>Declared by the planner before it plans, so a caller can reason about where a deposit reaches
 * without generating it — which is what lets {@code PlacementPlanner} decide whether a deposit
 * touches a given chunk cheaply, and what a future deposit ledger will persist instead of every
 * standing cell.
 */
public record ShapeBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public ShapeBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted shape bounds");
        }
    }

    /** A cube reaching {@code radius} in every direction. */
    public static ShapeBounds cube(int radius) {
        return new ShapeBounds(-radius, -radius, -radius, radius, radius, radius);
    }

    public static ShapeBounds of(int xRadius, int yRadius, int zRadius) {
        return new ShapeBounds(-xRadius, -yRadius, -zRadius, xRadius, yRadius, zRadius);
    }

    public boolean contains(ShapeOffset offset) {
        return offset.x() >= minX && offset.x() <= maxX
                && offset.y() >= minY && offset.y() <= maxY
                && offset.z() >= minZ && offset.z() <= maxZ;
    }

    /** The smallest bounds enclosing everything given, or a point at the origin when empty. */
    public static ShapeBounds enclosing(Collection<ShapeOffset> offsets) {
        if (offsets.isEmpty()) return new ShapeBounds(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ShapeOffset offset : offsets) {
            minX = Math.min(minX, offset.x());
            minY = Math.min(minY, offset.y());
            minZ = Math.min(minZ, offset.z());
            maxX = Math.max(maxX, offset.x());
            maxY = Math.max(maxY, offset.y());
            maxZ = Math.max(maxZ, offset.z());
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Number of cells in the box, saturating rather than overflowing. */
    public long volume() {
        long x = (long) maxX - minX + 1;
        long y = (long) maxY - minY + 1;
        long z = (long) maxZ - minZ + 1;
        return x * y * z;
    }
}
