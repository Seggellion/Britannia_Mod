package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import java.util.Optional;

/** Pure facing-aware placement mathematics shared by future anchor and part code. */
public final class StructureTransform {
    private StructureTransform() {
    }

    public record WorldPosition(int x, int y, int z) {
        public WorldPosition plus(int dx, int dy, int dz) {
            return new WorldPosition(x + dx, y + dy, z + dz);
        }
    }

    public enum HorizontalFacing {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int xStep;
        private final int zStep;

        HorizontalFacing(int xStep, int zStep) {
            this.xStep = xStep;
            this.zStep = zStep;
        }

        public int xStep() {
            return xStep;
        }

        public int zStep() {
            return zStep;
        }

        public HorizontalFacing counterClockwise() {
            return switch (this) {
                case NORTH -> WEST;
                case WEST -> SOUTH;
                case SOUTH -> EAST;
                case EAST -> NORTH;
            };
        }

        public HorizontalFacing opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
            };
        }
    }

    /**
     * FACING points outward. The anchor is lower front-left, local X is viewer-right, local Y is
     * up, and local Z is away from the viewer. Therefore right is counter-clockwise from FACING
     * and away is opposite FACING.
     */
    public static WorldPosition worldPosition(
            WorldPosition anchor, HorizontalFacing facing, LocalOffset local) {
        HorizontalFacing right = facing.counterClockwise();
        HorizontalFacing away = facing.opposite();
        return anchor.plus(
                right.xStep() * local.x() + away.xStep() * local.z(),
                local.y(),
                right.zStep() * local.x() + away.zStep() * local.z());
    }

    public static WorldPosition anchorPosition(
            WorldPosition partPosition, HorizontalFacing facing, LocalOffset local) {
        HorizontalFacing right = facing.counterClockwise();
        HorizontalFacing away = facing.opposite();
        return partPosition.plus(
                -(right.xStep() * local.x() + away.xStep() * local.z()),
                -local.y(),
                -(right.zStep() * local.x() + away.zStep() * local.z()));
    }

    public static Optional<LocalOffset> localOffset(
            WorldPosition anchor, HorizontalFacing facing, WorldPosition worldPosition) {
        int dx = worldPosition.x() - anchor.x();
        int dy = worldPosition.y() - anchor.y();
        int dz = worldPosition.z() - anchor.z();
        HorizontalFacing right = facing.counterClockwise();
        HorizontalFacing away = facing.opposite();
        int localX = dx * right.xStep() + dz * right.zStep();
        int localZ = dx * away.xStep() + dz * away.zStep();
        LocalOffset result = new LocalOffset(localX, dy, localZ);
        return worldPosition(anchor, facing, result).equals(worldPosition)
                ? Optional.of(result)
                : Optional.empty();
    }
}
