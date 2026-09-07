package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.placement.CreativeDecorationPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative plant constrained to ordinary soil while retaining imported custom geometry. */
public final class DecorativePlantBlock extends DecorativePropBlock {
    public DecorativePlantBlock(Properties properties, VoxelShape outlineShape) {
        super(properties, outlineShape, false);
        registerDefaultState(defaultBlockState().setValue(CreativeDecorationPolicy.ORIGIN, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CreativeDecorationPolicy.ORIGIN);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return CreativeDecorationPolicy.remember(super.getStateForPlacement(context), context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return CreativeDecorationPolicy.placedInCreative(state) || below.is(BlockTags.DIRT) || below.is(Blocks.FARMLAND) || below.is(Blocks.MOSS_BLOCK);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
