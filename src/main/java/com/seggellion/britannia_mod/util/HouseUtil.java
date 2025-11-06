package com.seggellion.britannia_mod.util;

import javax.annotation.Nullable;

import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helpers for locating the {@link HouseLotBlockEntity} that owns a block.
 */
public class HouseUtil {

    /** Original routine (unchanged) – scans a 9 × 5 × 9 cube. */
    public static @Nullable HouseLotBlockEntity findNearbyLot(ServerLevel level, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(check);
                    if (be instanceof HouseLotBlockEntity lot) return lot;
                }
            }
        }
        return null;
    }

    /** Convenience wrapper so callers don’t need to cast Level → ServerLevel. */
    public static @Nullable HouseLotBlockEntity findLot(Level level, BlockPos pos) {
        return (level instanceof ServerLevel server)
               ? findNearbyLot(server, pos)
               : null;
    }
}
