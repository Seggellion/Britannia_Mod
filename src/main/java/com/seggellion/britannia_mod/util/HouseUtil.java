package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class HouseUtil {

    // Scans a 9x5x9 cube around the sign to find the associated HouseLot
    public static HouseLotBlockEntity findNearbyLot(ServerLevel level, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos checkPos = origin.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(checkPos);
                    if (be instanceof HouseLotBlockEntity lotBE) {
                        return lotBE;
                    }
                }
            }
        }
        return null;
    }
}
