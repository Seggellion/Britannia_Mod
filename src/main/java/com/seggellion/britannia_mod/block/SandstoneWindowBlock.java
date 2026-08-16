package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-block sandstone wall whose modelled apertures are also open to selection and collision. */
public final class SandstoneWindowBlock extends DoubleWallBlock {
    private static final double DEPTH = 7.0D;
    private static final double JAMB = 3.0D;
    private static final double SILL = 4.0D;
    private static final double LINTEL_START = 12.0D;

    private static final VoxelShape STRAIGHT_LOWER = Shapes.or(
        Block.box(0, 0, 0, 16, SILL, DEPTH),
        Block.box(0, SILL, 0, JAMB, 16, DEPTH),
        Block.box(16 - JAMB, SILL, 0, 16, 16, DEPTH));
    private static final VoxelShape STRAIGHT_UPPER = Shapes.or(
        Block.box(0, 0, 0, JAMB, LINTEL_START, DEPTH),
        Block.box(16 - JAMB, 0, 0, 16, LINTEL_START, DEPTH),
        Block.box(0, LINTEL_START, 0, 16, 16, DEPTH));

    private static final VoxelShape WEST_RETURN_LOWER = returnWindow(false, DoubleBlockHalf.LOWER);
    private static final VoxelShape WEST_RETURN_UPPER = returnWindow(false, DoubleBlockHalf.UPPER);
    private static final VoxelShape EAST_RETURN_LOWER = returnWindow(true, DoubleBlockHalf.LOWER);
    private static final VoxelShape EAST_RETURN_UPPER = returnWindow(true, DoubleBlockHalf.UPPER);
    private static final VoxelShape WEST_CORNER_LOWER = cornerWindow(false, DoubleBlockHalf.LOWER);
    private static final VoxelShape WEST_CORNER_UPPER = cornerWindow(false, DoubleBlockHalf.UPPER);
    private static final VoxelShape EAST_CORNER_LOWER = cornerWindow(true, DoubleBlockHalf.LOWER);
    private static final VoxelShape EAST_CORNER_UPPER = cornerWindow(true, DoubleBlockHalf.UPPER);

    public SandstoneWindowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static VoxelShape returnWindow(boolean east, DoubleBlockHalf half) {
        double x0 = east ? 16.0D - DEPTH : 0.0D;
        double x1 = east ? 16.0D : DEPTH;
        if (half == DoubleBlockHalf.LOWER) {
            return Shapes.or(
                Block.box(x0, 0, DEPTH, x1, SILL, 16),
                Block.box(x0, SILL, DEPTH, x1, 16, 9),
                Block.box(x0, SILL, 14, x1, 16, 16));
        }
        return Shapes.or(
            Block.box(x0, 0, DEPTH, x1, LINTEL_START, 9),
            Block.box(x0, 0, 14, x1, LINTEL_START, 16),
            Block.box(x0, LINTEL_START, DEPTH, x1, 16, 16));
    }

    private static VoxelShape cornerWindow(boolean east, DoubleBlockHalf half) {
        double x0 = east ? 9.0D : 0.0D;
        double x1 = east ? 16.0D : DEPTH;
        double armStart = east ? 0.0D : DEPTH;
        double armEnd = east ? 9.0D : 16.0D;
        VoxelShape pillar = Block.box(x0, 0, 0, x1, 16, DEPTH);
        VoxelShape branch = half == DoubleBlockHalf.LOWER
            ? (east ? EAST_RETURN_LOWER : WEST_RETURN_LOWER)
            : (east ? EAST_RETURN_UPPER : WEST_RETURN_UPPER);
        VoxelShape northArm;
        if (half == DoubleBlockHalf.LOWER) {
            northArm = Shapes.or(
                Block.box(armStart, 0, 0, armEnd, SILL, DEPTH),
                Block.box(armStart, SILL, 0, armStart + 2, 16, DEPTH),
                Block.box(armEnd - 2, SILL, 0, armEnd, 16, DEPTH));
        } else {
            northArm = Shapes.or(
                Block.box(armStart, 0, 0, armStart + 2, LINTEL_START, DEPTH),
                Block.box(armEnd - 2, 0, 0, armEnd, LINTEL_START, DEPTH),
                Block.box(armStart, LINTEL_START, 0, armEnd, 16, DEPTH));
        }
        return Shapes.or(pillar, northArm, branch).optimize();
    }

    private static VoxelShape canonicalShape(BlockState state) {
        DoubleBlockHalf half = state.getValue(HALF);
        VoxelShape main = half == DoubleBlockHalf.LOWER ? STRAIGHT_LOWER : STRAIGHT_UPPER;
        if (state.getValue(SHAPE) == WallShape.STRAIGHT) {
            return main;
        }

        boolean east = state.getValue(BRANCH_RIGHT);
        if (state.getValue(SHAPE) == WallShape.CORNER) {
            return half == DoubleBlockHalf.LOWER
                ? (east ? EAST_CORNER_LOWER : WEST_CORNER_LOWER)
                : (east ? EAST_CORNER_UPPER : WEST_CORNER_UPPER);
        }
        VoxelShape branch = half == DoubleBlockHalf.LOWER
            ? (east ? EAST_RETURN_LOWER : WEST_RETURN_LOWER)
            : (east ? EAST_RETURN_UPPER : WEST_RETURN_UPPER);
        return Shapes.or(main, branch).optimize();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HorizontalShape.rotateFromNorth(canonicalShape(state), state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
