package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public class BrokenBlockData {
    public final BlockPos pos;
    public final BlockState originalState;
    public final long brokenTime;
    public final UUID playerUUID; // Add player UUID to track who broke the block

    public BrokenBlockData(BlockPos pos, BlockState originalState, long brokenTime, UUID playerUUID) {
        this.pos = pos;
        this.originalState = originalState;
        this.brokenTime = brokenTime;
        this.playerUUID = playerUUID;
    }
}
