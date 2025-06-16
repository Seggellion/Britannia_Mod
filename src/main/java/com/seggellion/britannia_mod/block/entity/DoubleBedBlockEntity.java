package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DoubleBedBlockEntity extends BlockEntity {
    public DoubleBedBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DOUBLE_BED.get(), pos, state);
    }

    /** Tint the quilt; reuse the block’s dye colour (red for now). */
    public DyeColor getColor() { return DyeColor.RED; }
}

