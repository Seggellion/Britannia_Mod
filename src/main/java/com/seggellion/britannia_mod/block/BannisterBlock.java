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
 * Edge-mounted bannister: 3 voxels thick, spanning the full 16-voxel edge, 18 voxels tall.
 *
 * <p>Turns corners the same way the walls do - {@code facing} is the edge the main run hugs and
 * {@code branch_right} picks the side the perpendicular run sits on - so two bannister runs meeting
 * at right angles mitre together instead of leaving an open end.
 *
 * <h2>The 18th voxel</h2>
 * The visual model is 18 voxels tall, so its handrail stands 2 voxels proud of the block. That is
 * the established convention in this mod for architectural trim - {@code plaster_wall_joist_edge}
 * overhangs its block by 2 voxels and every {@link DoubleWallBlock} model is 32 voxels tall - so
 * this is a single block rather than a two-part multiblock.
 *
 * <p>Collision and selection stop at 16. A {@code VoxelShape} taller than the block's own cube is
 * clipped by the broadphase anyway, so extending it would only produce collision that does not
 * match anything the player can see. The trade-off is deliberate: a solid block placed directly
 * above will overlap the top 2 voxels of the handrail, exactly as it does with the joist edge.
 */
public class BannisterBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    public static final BooleanProperty BRANCH_RIGHT = BooleanProperty.create("branch_right");

    /** Matches the model: 3 voxels deep, hugging the edge named by {@link #FACING}. */
    private static final double THICKNESS = 3.0D;

    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, THICKNESS);
    private static final VoxelShape SOUTH = Block.box(0, 0, 16 - THICKNESS, 16, 16, 16);
    private static final VoxelShape WEST  = Block.box(0, 0, 0, THICKNESS, 16, 16);
    private static final VoxelShape EAST  = Block.box(16 - THICKNESS, 0, 0, 16, 16, 16);

    public BannisterBlock(BlockBehaviour.Properties properties) {
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

    private BlockState derive(BlockState state, LevelAccessor level, BlockPos pos) {
        WallConnection connection = WallConnection.derive(
            level, pos,
            state.getValue(FACING), state.getValue(BRANCH_RIGHT),
            neighbour -> neighbour.getBlock() instanceof BannisterBlock);

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

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
