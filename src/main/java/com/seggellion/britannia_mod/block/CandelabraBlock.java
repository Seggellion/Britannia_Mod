package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;
import com.mojang.serialization.MapCodec;

public class CandelabraBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<CandelabraBlock> CODEC = simpleCodec(CandelabraBlock::new);

    public CandelabraBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    /* orient on placement */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                    .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /* add the property to the block‑state definition */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /* keep your lighting fix */
    @Override
    public void onRemove(BlockState oldState, Level level,
                         BlockPos pos, BlockState newState, boolean isMoving) {

        super.onRemove(oldState, level, pos, newState, isMoving);

        if (!oldState.is(newState.getBlock())) {
            level.sendBlockUpdated(pos, oldState, Blocks.AIR.defaultBlockState(), 3);
            level.getChunk(pos).setUnsaved(true);
            level.getLightEngine().checkBlock(pos);
        }
    }
}