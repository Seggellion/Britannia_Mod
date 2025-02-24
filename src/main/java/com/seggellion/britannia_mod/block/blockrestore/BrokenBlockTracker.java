package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class BrokenBlockTracker {
    private static final Map<BlockPos, BrokenBlockData> BROKEN_BLOCKS = new ConcurrentHashMap<>();

    public static void recordBrokenBlock(BlockPos pos, BlockState originalState, UUID playerUUID) {
        BROKEN_BLOCKS.put(pos, new BrokenBlockData(pos, originalState, System.currentTimeMillis(), playerUUID));
    }

    public static Map<BlockPos, BrokenBlockData> getBrokenBlocks() {
        return BROKEN_BLOCKS;
    }

    public static void removeBlock(BlockPos pos) {
        BROKEN_BLOCKS.remove(pos);
    }
}
