package com.seggellion.britannia_mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Misc static helpers reused by several block-entities.
 */
public final class Util {

    private Util() {}   // no instantiation

    /**
     * Searches in a cube around {@code center} up to {@code radius} blocks away
     * for a spot that has solid ground below and air above, with no entities
     * occupying the space. Returns {@code null} if nothing suitable is found.
     */
    public static BlockPos findGround(ServerLevel level, BlockPos center, int radius) {
        for (int attempts = 0; attempts < 50; attempts++) {
            int offX = (int) ((level.random.nextDouble() - 0.5) * radius * 2);
            int offZ = (int) ((level.random.nextDouble() - 0.5) * radius * 2);
            int baseY = center.getY();

            for (int dy = -5; dy <= 5; dy++) {
                BlockPos pos = new BlockPos(center.getX() + offX,
                                            baseY + dy,
                                            center.getZ() + offZ);

                if (level.getBlockState(pos.below()).isSolid()
                    && level.getBlockState(pos).isAir()
                    && level.getEntitiesOfClass(Entity.class,
                           new AABB(pos).inflate(0.2)).isEmpty()) {
                    return pos;
                }
            }
        }
        return null;
    }
}
