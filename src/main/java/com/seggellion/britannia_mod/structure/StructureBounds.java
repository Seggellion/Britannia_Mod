
package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record StructureBounds(BlockPos min, BlockPos max) {

    public static StructureBounds fromPlacement(BlockPos center, Vec3i size, int rotationDeg) {
        Rotation rotation = Rotation.values()[rotationDeg / 90];
        BlockPos adjusted = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        return new StructureBounds(adjusted, adjusted.offset(size.getX(), size.getY(), size.getZ()));
    }

public AABB toAABB() {
    return new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max));
}
}
