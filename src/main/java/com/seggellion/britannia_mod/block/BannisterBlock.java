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
 *
 * <h2>Meeting a wall</h2>
 * {@link #WALL_BRANCH} is the one connection a bannister cannot infer from its own kind. A run with
 * railing on both sides and a wall across the block from it needs a short return into that wall,
 * and no arrangement of {@link WallShape} can say so, because the wall is not a peer and never
 * shows up in {@link WallConnection}.
 *
 * <p>The models for that state carry their return {@value #WALL_OVERLAP} voxels past the block
 * boundary, into the wall's own block, so the two meet with no seam whatever depth the wall's art
 * starts at. Collision does not follow it out there - the wall already collides for its own block,
 * and a bannister that reserved space inside its neighbour would stop the player against nothing
 * they can see. Visual and collision shape differ here on purpose.
 */
public class BannisterBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    public static final BooleanProperty BRANCH_RIGHT = BooleanProperty.create("branch_right");

    /**
     * Set when the run has railing on both sides and a wall across the block from it, so the model
     * grows a return into that wall. Only ever set on {@link WallShape#STRAIGHT}: once real
     * bannisters arrive on the perpendicular axis the run is a corner or a junction, and the branch
     * edge belongs to them.
     */
    public static final BooleanProperty WALL_BRANCH = BooleanProperty.create("wall_branch");

    /**
     * How far the wall-facing return reaches past its own block, in voxels, matching
     * {@code bannister_wall_branch.json}. The branch already spans its own block out to 16, so this
     * is pure overlap: enough to be swallowed by any wall whose art reaches its own near face, and
     * short enough not to break out of the far side of a thin one.
     */
    public static final int WALL_OVERLAP = 2;

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
            .setValue(BRANCH_RIGHT, false)
            .setValue(WALL_BRANCH, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, BRANCH_RIGHT, WALL_BRANCH);
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

    /** The whole connection rule, shared by placement and by every neighbour update. */
    BlockState derive(BlockState state, BlockGetter level, BlockPos pos) {
        WallConnection connection = WallConnection.derive(
            level, pos,
            state.getValue(FACING), state.getValue(BRANCH_RIGHT),
            BannisterBlock::isPeer);

        return state.setValue(SHAPE, connection.shape())
                    .setValue(FACING, connection.facing())
                    .setValue(BRANCH_RIGHT, connection.branchRight())
                    .setValue(WALL_BRANCH, wantsWallBranch(level, pos, connection));
    }

    private static boolean isPeer(BlockState neighbour) {
        return neighbour.getBlock() instanceof BannisterBlock;
    }

    /**
     * Whether this block should grow a return into the wall across from it.
     *
     * <p>Three things have to hold, and each is there to keep the return off a railing that did not
     * ask for it:
     *
     * <ul>
     *   <li>the run is straight, so the perpendicular edge is free - a corner or a junction is
     *       already using it for a real bannister;</li>
     *   <li>railing continues on both sides, which is what makes this the middle of a run rather
     *       than a loose end that happens to sit near a wall;</li>
     *   <li>the wall is on the far side of the block from the run. A railing set against a wall
     *       hugs it with {@code facing}, and that one has to stay flat - it is the ordinary way to
     *       fence a landing, and growing a stub into the wall behind every one of them would be
     *       wrong far more often than right.</li>
     * </ul>
     */
    static boolean wantsWallBranch(BlockGetter level, BlockPos pos, WallConnection connection) {
        if (connection.shape() != WallShape.STRAIGHT) {
            return false;
        }
        Direction facing = connection.facing();
        Direction along = facing.getClockWise();
        if (!isPeer(level.getBlockState(pos.relative(along)))
            || !isPeer(level.getBlockState(pos.relative(along.getOpposite())))) {
            return false;
        }
        return DoubleWallBlock.isWallRun(level.getBlockState(pos.relative(facing.getOpposite())));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        if (mirror == Mirror.NONE) {
            return mirrored;
        }
        // BRANCH_RIGHT names an edge either side of FACING, so reflecting swaps it - for the
        // wall-facing return just as much as for a corner or a junction.
        if (state.getValue(SHAPE) == WallShape.STRAIGHT && !state.getValue(WALL_BRANCH)) {
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

    /** True when the state draws a perpendicular run as well as its main one. */
    public static boolean hasBranch(BlockState state) {
        return state.getValue(SHAPE) != WallShape.STRAIGHT || state.getValue(WALL_BRANCH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape shape = edge(facing);

        if (hasBranch(state)) {
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
