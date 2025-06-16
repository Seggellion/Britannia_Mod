package com.seggellion.britannia_mod.block.nudgeable.block_entities;

import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ChairBlockEntity extends NudgeableBlockEntity {
    public ChairBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.CHAIR.get(), pos, state);
    }
}