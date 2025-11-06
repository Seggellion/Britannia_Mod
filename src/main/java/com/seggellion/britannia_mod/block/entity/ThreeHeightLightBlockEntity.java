package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;

public class ThreeHeightLightBlockEntity extends BlockEntity {
    public ThreeHeightLightBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.THREE_HEIGHT_LIGHT_BLOCK_ENTITY_TYPE.get(), pos, state);
    }
}
