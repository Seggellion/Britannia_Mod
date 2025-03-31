package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class BrokenBlockTracker {
    public static void recordBrokenBlock(ServerLevel level, BlockPos pos, BlockState state, UUID playerUUID) {
        BrokenBlockData data = new BrokenBlockData(pos, state, System.currentTimeMillis(), playerUUID);
        BrokenBlockDataStorage.get(level).add(data);
    }

    public static Map<BlockPos, BrokenBlockData> getBrokenBlocks(ServerLevel level) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks();
    }

    public static void removeBlock(ServerLevel level, BlockPos pos) {
        BrokenBlockDataStorage.get(level).remove(pos);
    }
}
