package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.CandelabraBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public class CandelabraBlock extends HorizontalDirectionalBlock implements EntityBlock, INudgeable {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<CandelabraBlock> CODEC = simpleCodec(CandelabraBlock::new);

    public CandelabraBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        //return RenderShape.ENTITYBLOCK_ANIMATED;
                    return RenderShape.MODEL;
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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CandelabraBlockEntity(pos, state);
    }
}