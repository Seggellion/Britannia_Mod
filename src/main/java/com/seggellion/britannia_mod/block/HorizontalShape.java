package com.seggellion.britannia_mod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Package-local helpers for rotating model-authored north-facing voxel shapes. */
final class HorizontalShape {
    private HorizontalShape() {
    }

    static VoxelShape rotateFromNorth(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) {
            return shape;
        }

        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            AABB box = switch (facing) {
                case EAST -> new AABB(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX);
                case SOUTH -> new AABB(1.0D - maxX, minY, 1.0D - maxZ,
                                       1.0D - minX, maxY, 1.0D - minZ);
                case WEST -> new AABB(minZ, minY, 1.0D - maxX, maxZ, maxY, 1.0D - minX);
                default -> new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            };
            result[0] = Shapes.or(result[0], Shapes.create(box));
        });
        return result[0].optimize();
    }
}
