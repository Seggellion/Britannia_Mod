package com.seggellion.britannia_mod.block.nudgeable.block_entities;

import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CandelabraBlockEntity extends NudgeableBlockEntity {
    public CandelabraBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.CANDELABRA.get(), pos, state);
    }

}