package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import java.util.Properties;

import net.minecraft.core.BlockPos;

public class CandelabraBlock extends Block {
    public CandelabraBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState oldState, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(oldState, world, pos, newState, isMoving);

        // Force lighting update
        if (!oldState.is(newState.getBlock())) {
            world.sendBlockUpdated(pos, oldState, Blocks.AIR.defaultBlockState(), 3);
            world.getChunk(pos).setUnsaved(true); // Ensure chunk marks as dirty
            world.getLightEngine().checkBlock(pos);
        }
    }
}
