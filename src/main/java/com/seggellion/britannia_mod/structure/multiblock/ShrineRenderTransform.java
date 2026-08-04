package com.seggellion.britannia_mod.structure.multiblock;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Pure, documented transform contract shared by renderer bounds and focused tests. */
public final class ShrineRenderTransform {
    public static final double TOLERANCE = 1.0 / 128.0;
    public static final AABB ALL_FACINGS_LOCAL_BOUNDS = new AABB(
            -1.0 - TOLERANCE, -TOLERANCE, -1.0 - TOLERANCE,
            2.0 + TOLERANCE, 1.0 + TOLERANCE, 2.0 + TOLERANCE);
    private static final Map<Direction, Float> ROTATIONS = Map.of(
            Direction.NORTH, 0.0F,
            Direction.EAST, -90.0F,
            Direction.SOUTH, 180.0F,
            Direction.WEST, 90.0F);

    private ShrineRenderTransform() {
    }

    public static float yRotationDegrees(Direction facing) {
        Float rotation = ROTATIONS.get(facing);
        if (rotation == null) {
            throw new IllegalArgumentException("Shrine facing must be horizontal: " + facing);
        }
        return rotation;
    }

    public static AABB worldBounds(BlockPos anchor) {
        return ALL_FACINGS_LOCAL_BOUNDS.move(anchor);
    }
}
