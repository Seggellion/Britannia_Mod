package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Render anchor for the camera-facing city-moongate portal layers. */
public final class MoongateBlockEntity extends BlockEntity {
    public MoongateBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.MOONGATE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }
}
