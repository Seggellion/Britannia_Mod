package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Half-height plaster wall: 16 voxels tall, 5 deep, hugging one edge of the block.
 *
 * <p>The full-height family is {@link DoubleWallBlock}, which is two blocks tall and carries a
 * 32-voxel model on its lower half. This is the single-block version for knee walls, parapets and
 * balustrade infill, and it shares the same canonical orientation and connection derivation so the
 * two families turn corners identically: {@code facing} is the edge the wall hugs, and
 * {@code branch_right} says which side the perpendicular run is on.
 */
public class PlasterWallHalfBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    public static final BooleanProperty BRANCH_RIGHT = BooleanProperty.create("branch_right");

    /**
     * Matches {@link DoubleWallBlock}: these are the same models cut down to sixteen, so they
     * occupy the same depth once their base beams and the timber posts of the support families are
     * counted. It was five, which left up to a voxel of visible post with nothing behind it.
     */
    private static final double DEPTH = 7.0D;

    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, DEPTH);
    private static final VoxelShape SOUTH = Block.box(0, 0, 16 - DEPTH, 16, 16, 16);
    private static final VoxelShape WEST  = Block.box(0, 0, 0, DEPTH, 16, 16);
    private static final VoxelShape EAST  = Block.box(16 - DEPTH, 0, 0, 16, 16, 16);

    public PlasterWallHalfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SHAPE, WallShape.STRAIGHT)
            .setValue(BRANCH_RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, BRANCH_RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return derive(placed, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return derive(state, level, currentPos);
    }

    /**
     * The whole connection rule, shared by placement and by every neighbour update.
     *
     * <p>Package-visible so the collision suite can drive it without standing up a level.
     */
    BlockState derive(BlockState state, BlockGetter level, BlockPos pos) {
        WallConnection connection = WallConnection.derive(
            level, pos,
            state.getValue(FACING), state.getValue(BRANCH_RIGHT),
            neighbour -> neighbour.getBlock() instanceof PlasterWallHalfBlock
                ? new WallConnection(neighbour.getValue(SHAPE), neighbour.getValue(FACING),
                                     neighbour.getValue(BRANCH_RIGHT))
                : null);

        return state.setValue(SHAPE, connection.shape())
                    .setValue(FACING, connection.facing())
                    .setValue(BRANCH_RIGHT, connection.branchRight());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        if (mirror == Mirror.NONE || state.getValue(SHAPE) == WallShape.STRAIGHT) {
            return mirrored;
        }
        return mirrored.setValue(BRANCH_RIGHT, !state.getValue(BRANCH_RIGHT));
    }

    private static VoxelShape edge(Direction direction) {
        return switch (direction) {
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            default    -> NORTH;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape shape = edge(facing);

        if (state.getValue(SHAPE) != WallShape.STRAIGHT) {
            Direction branch = state.getValue(BRANCH_RIGHT)
                ? facing.getClockWise() : facing.getCounterClockWise();
            shape = Shapes.or(shape, edge(branch));
        }
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
