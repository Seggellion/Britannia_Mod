package com.seggellion.britannia_mod.resource.shape;

/**
 * One planned cell, relative to the deposit origin.
 *
 * <p>Deliberately not {@code BlockPos}. A shape planner is pure geometry and must be testable
 * without Minecraft present at all; using a mod-owned value type makes that structural rather than
 * something a future import could quietly break. {@code PlacementPlanner} converts to world
 * coordinates at the boundary, which is also the point where a deposit stops being a shape and
 * starts being a thing in a world.
 */
public record ShapeOffset(int x, int y, int z) implements Comparable<ShapeOffset> {

    /**
     * Canonical ordering: layer by layer, then row by row.
     *
     * <p>Plans are sorted before they are returned, so a planner that builds its cells by walking a
     * path and a planner that scans a bounding box both produce the same order for the same set.
     * That is what makes "same seed, same result" an assertion about a list rather than about a
     * set, and it means a chunk slice is a contiguous idea rather than an arbitrary one.
     */
    @Override
    public int compareTo(ShapeOffset other) {
        int byY = Integer.compare(y, other.y);
        if (byY != 0) return byY;
        int byX = Integer.compare(x, other.x);
        if (byX != 0) return byX;
        return Integer.compare(z, other.z);
    }
}
