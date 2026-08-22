package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The plain half plaster wall, with one extra presentation state for a perpendicular full wall
 * occupying the far edge of its neighbouring block.
 *
 * <p>The normal corner reaches the boundary between the two blocks. When the full wall is on its
 * far edge, that still leaves eleven model pixels of empty space. {@link #OFFSET_RETURN} selects a
 * dedicated corner model whose main arm continues across that space. No other half-wall family or
 * ordinary corner uses the extended model.
 */
public final class PlasterWallBlankHalfBlock extends PlasterWallHalfBlock {

    public static final BooleanProperty OFFSET_RETURN = BooleanProperty.create("offset_return");
    private static final double RETURN_REACH = 11.0D;

    public PlasterWallBlankHalfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(OFFSET_RETURN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OFFSET_RETURN);
    }

    @Override
    BlockState derive(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState derived = super.derive(state, level, pos);
        return derived.setValue(OFFSET_RETURN,
            derived.getValue(SHAPE) == WallShape.CORNER
                && hasFarEdgePerpendicularWall(level, pos, derived));
    }

    private static boolean hasFarEdgePerpendicularWall(BlockGetter level, BlockPos pos,
                                                       BlockState halfWall) {
        Direction facing = halfWall.getValue(FACING);
        Direction returnSide = halfWall.getValue(BRANCH_RIGHT)
            ? facing.getClockWise() : facing.getCounterClockWise();
        BlockState neighbour = level.getBlockState(pos.relative(returnSide));
        return neighbour.getBlock() instanceof DoubleWallBlock
            && neighbour.getValue(DoubleWallBlock.FACING) == returnSide;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        VoxelShape shape = super.getShape(state, level, pos, context);
        if (!state.getValue(OFFSET_RETURN)) {
            return shape;
        }

        Direction facing = state.getValue(FACING);
        Direction branch = state.getValue(BRANCH_RIGHT)
            ? facing.getClockWise() : facing.getCounterClockWise();
        return Shapes.or(shape, offsetExtension(facing, branch));
    }

    private static VoxelShape offsetExtension(Direction facing, Direction branch) {
        double minX = facing == Direction.WEST ? 0.0D : facing == Direction.EAST ? 9.0D : 0.0D;
        double maxX = facing == Direction.WEST ? 7.0D : facing == Direction.EAST ? 16.0D : 16.0D;
        double minZ = facing == Direction.NORTH ? 0.0D : facing == Direction.SOUTH ? 9.0D : 0.0D;
        double maxZ = facing == Direction.NORTH ? 7.0D : facing == Direction.SOUTH ? 16.0D : 16.0D;

        return switch (branch) {
            case WEST -> Block.box(-RETURN_REACH, 0.0D, minZ, 0.0D, 16.0D, maxZ);
            case EAST -> Block.box(16.0D, 0.0D, minZ, 16.0D + RETURN_REACH, 16.0D, maxZ);
            case NORTH -> Block.box(minX, 0.0D, -RETURN_REACH, maxX, 16.0D, 0.0D);
            case SOUTH -> Block.box(minX, 0.0D, 16.0D, maxX, 16.0D, 16.0D + RETURN_REACH);
            default -> Shapes.empty();
        };
    }
}
