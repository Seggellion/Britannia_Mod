package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-block-high sandstone slope with a practical stepped collision approximation. */
public final class SandstoneBattlementBlock extends DoubleWallBlock {
    private static final VoxelShape LOWER_SLOPE = Shapes.or(
        Block.box(0, 0, 0, 16, 4, 2),
        Block.box(0, 0, 2, 16, 8, 4),
        Block.box(0, 0, 4, 16, 12, 6),
        Block.box(0, 0, 6, 16, 16, 16)).optimize();
    private static final VoxelShape UPPER_SLOPE = Shapes.or(
        Block.box(0, 0, 8, 16, 4, 10),
        Block.box(0, 0, 10, 16, 8, 12),
        Block.box(0, 0, 12, 16, 12, 14),
        Block.box(0, 0, 14, 16, 16, 16)).optimize();
    private static final VoxelShape LOWER_CORNER_LEFT = cornerSlope(DoubleBlockHalf.LOWER, false);
    private static final VoxelShape UPPER_CORNER_LEFT = cornerSlope(DoubleBlockHalf.UPPER, false);
    private static final VoxelShape LOWER_CORNER_RIGHT = cornerSlope(DoubleBlockHalf.LOWER, true);
    private static final VoxelShape UPPER_CORNER_RIGHT = cornerSlope(DoubleBlockHalf.UPPER, true);

    public SandstoneBattlementBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static VoxelShape cornerSlope(DoubleBlockHalf half, boolean branchRight) {
        VoxelShape shape = Shapes.empty();
        for (int x = 0; x < 16; x += 2) {
            for (int z = 0; z < 16; z += 2) {
                int inwardX = branchRight ? 16 - x : x + 2;
                int inwardZ = z + 2;
                int globalHeight = Math.min(32, 2 * Math.min(inwardX, inwardZ));
                int localHeight = half == DoubleBlockHalf.LOWER
                    ? Math.min(16, globalHeight)
                    : Math.max(0, globalHeight - 16);
                if (localHeight > 0) {
                    shape = Shapes.or(shape, Block.box(x, 0, z, x + 2, localHeight, z + 2));
                }
            }
        }
        return shape.optimize();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        DoubleBlockHalf half = state.getValue(HALF);
        VoxelShape canonical;
        if (state.getValue(SHAPE) == WallShape.CORNER) {
            canonical = half == DoubleBlockHalf.LOWER
                ? (state.getValue(BRANCH_RIGHT) ? LOWER_CORNER_RIGHT : LOWER_CORNER_LEFT)
                : (state.getValue(BRANCH_RIGHT) ? UPPER_CORNER_RIGHT : UPPER_CORNER_LEFT);
        } else {
            canonical = half == DoubleBlockHalf.LOWER ? LOWER_SLOPE : UPPER_SLOPE;
        }
        return HorizontalShape.rotateFromNorth(canonical, state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
