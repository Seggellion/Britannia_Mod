package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stackable hedge that derives bottom/middle/top visuals from its vertical neighbors. */
public final class HedgeBushBlock extends DecorativePropBlock {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 2);
    public static final int BOTTOM = 0;
    public static final int MIDDLE = 1;
    public static final int TOP = 2;

    public HedgeBushBlock(Properties properties, VoxelShape outlineShape) {
        super(properties, outlineShape, false);
        registerDefaultState(defaultBlockState().setValue(SEGMENT, BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEGMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(
                SEGMENT, segmentAt(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(this)
                || below.is(BlockTags.DIRT)
                || below.is(Blocks.FARMLAND)
                || below.is(Blocks.MOSS_BLOCK);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.UP || direction == Direction.DOWN) {
            return state.setValue(SEGMENT, segmentAt(level, pos));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private int segmentAt(LevelReader level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        if (!below) {
            return BOTTOM;
        }
        return level.getBlockState(pos.above()).is(this) ? MIDDLE : TOP;
    }
}
