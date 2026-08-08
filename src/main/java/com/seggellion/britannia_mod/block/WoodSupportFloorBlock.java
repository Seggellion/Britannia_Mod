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
 * Floor joist edge piece: a full-height joist run along one block edge with a thin deck covering
 * the rest of the block.
 *
 * <p>Uses the same canonical orientation and connection derivation as {@link DoubleWallBlock}, so
 * the two families turn corners the same way: {@code facing} is the edge the joist run hugs, and
 * {@code branch_right} says which side the perpendicular run is on.
 *
 * <p>This block previously had no {@code getShape} at all, so its collision and selection were a
 * full cube while the model is a joist plus a 3-voxel deck. Both now follow the art.
 */
public class WoodSupportFloorBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    public static final BooleanProperty BRANCH_RIGHT = BooleanProperty.create("branch_right");

    /**
     * True when all four horizontal neighbours are filled, so no joist end is exposed. The visible
     * joist retracts to just the deck: an interior floor tile has nothing to show an edge to, and
     * leaving the joist there pushed geometry into the neighbour and read as a lattice of beams
     * across what should be a flat floor.
     */
    public static final BooleanProperty ENCLOSED = BooleanProperty.create("enclosed");

    /** How far the joist run reaches back from the face it hugs. Matches the model. */
    private static final double JOIST_DEPTH = 7.0D;
    /** The deck is the top 3 voxels of everything the joists do not occupy. */
    private static final double DECK_TOP = 13.0D;

    private static final VoxelShape JOIST_NORTH = Block.box(0, 0, 0, 16, 16, JOIST_DEPTH);
    private static final VoxelShape JOIST_SOUTH = Block.box(0, 0, 16 - JOIST_DEPTH, 16, 16, 16);
    private static final VoxelShape JOIST_WEST  = Block.box(0, 0, 0, JOIST_DEPTH, 16, 16);
    private static final VoxelShape JOIST_EAST  = Block.box(16 - JOIST_DEPTH, 0, 0, 16, 16, 16);

    /** The deck spans the whole block; the joists simply stand proud of it. */
    private static final VoxelShape DECK = Block.box(0, DECK_TOP, 0, 16, 16, 16);

    public WoodSupportFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SHAPE, WallShape.STRAIGHT)
            .setValue(BRANCH_RIGHT, false)
            .setValue(ENCLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, BRANCH_RIGHT, ENCLOSED);
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

    private BlockState derive(BlockState state, LevelAccessor level, BlockPos pos) {
        WallConnection connection = WallConnection.derive(
            level, pos,
            state.getValue(FACING), state.getValue(BRANCH_RIGHT),
            neighbour -> neighbour.getBlock() instanceof WoodSupportFloorBlock);

        return state.setValue(SHAPE, connection.shape())
                    .setValue(FACING, connection.facing())
                    .setValue(BRANCH_RIGHT, connection.branchRight())
                    .setValue(ENCLOSED, isEnclosed(level, pos));
    }

    /**
     * True when every horizontal neighbour on this plane is filled - another joist floor, or any
     * block presenting a solid face toward us. Nothing can see a joist end here, so it retracts.
     */
    private static boolean isEnclosed(LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbour = level.getBlockState(neighbourPos);
            boolean filled = neighbour.getBlock() instanceof WoodSupportFloorBlock
                || neighbour.isFaceSturdy(level, neighbourPos, direction.getOpposite());
            if (!filled) {
                return false;
            }
        }
        return true;
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

    private static VoxelShape joist(Direction direction) {
        return switch (direction) {
            case SOUTH -> JOIST_SOUTH;
            case EAST  -> JOIST_EAST;
            case WEST  -> JOIST_WEST;
            default    -> JOIST_NORTH;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(ENCLOSED)) {
            // Retracted: the deck alone, matching the enclosed model.
            return DECK;
        }

        Direction facing = state.getValue(FACING);
        VoxelShape shape = Shapes.or(DECK, joist(facing));

        if (state.getValue(SHAPE) != WallShape.STRAIGHT) {
            Direction branch = state.getValue(BRANCH_RIGHT)
                ? facing.getClockWise() : facing.getCounterClockWise();
            shape = Shapes.or(shape, joist(branch));
        }
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
