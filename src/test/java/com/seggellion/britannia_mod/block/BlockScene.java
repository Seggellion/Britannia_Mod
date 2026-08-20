package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;

/**
 * A handful of blocks in a map, standing in for a level.
 *
 * <p>The connection rules only ever ask a level for {@code getBlockState}, so this is all the world
 * these suites need - and it keeps them plain unit tests rather than GameTests.
 */
final class BlockScene implements BlockGetter {

    private final Map<BlockPos, BlockState> blocks = new HashMap<>();

    BlockScene put(BlockPos pos, BlockState state) {
        this.blocks.put(pos, state);
        return this;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }
}
