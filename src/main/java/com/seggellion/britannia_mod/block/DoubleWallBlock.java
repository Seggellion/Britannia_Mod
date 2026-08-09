package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block-tall architectural wall built from edge-aligned segments.
 *
 * <h2>Canonical orientation</h2>
 * {@link #FACING} is the edge the main wall run hugs, and it means the same thing for every
 * {@link WallShape}: {@code facing=north} puts the wall against the block's north face, running
 * east-west, with the decorated side pointing north. That matches every approved {@code _straight}
 * model, which is authored at {@code y=0} with the wall at low z.
 *
 * <p>Corners and junctions add one perpendicular run. {@link #BRANCH_RIGHT} picks which side it is
 * on, relative to {@code FACING}: {@code false} uses the counter-clockwise edge (north -> west),
 * {@code true} uses the clockwise edge (north -> east). The canonical model is always the
 * {north, west} pair authored at {@code y=0}; every other combination is that same model rotated,
 * so no mirrored model files are needed.
 *
 * <p>{@code STRAIGHT} ignores {@code BRANCH_RIGHT}; the blockstate maps both values to one model.
 */
public class DoubleWallBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Which side of {@link #FACING} the perpendicular run sits on. False = counter-clockwise edge. */
    public static final BooleanProperty BRANCH_RIGHT = BooleanProperty.create("branch_right");

    /**
     * Wall depth used by the collision boxes. The approved straight models occupy roughly
     * {@code z 0..7} once their base beams are counted, so the edge strips match the art.
     */
    private static final VoxelShape EDGE_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 7.0D);
    private static final VoxelShape EDGE_SOUTH = Block.box(0.0D, 0.0D, 9.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape EDGE_WEST  = Block.box(0.0D, 0.0D, 0.0D, 7.0D, 16.0D, 16.0D);
    private static final VoxelShape EDGE_EAST  = Block.box(9.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public DoubleWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(SHAPE, WallShape.STRAIGHT)
            .setValue(BRANCH_RIGHT, false)
            .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, BRANCH_RIGHT, HALF);
    }

    /* ─── placement ──────────────────────────────────────────── */

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        // Ensure there is space above the targeted block to place the upper half
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }

        BlockState placed = this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HALF, DoubleBlockHalf.LOWER);

        return deriveConnections(placed, level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // Automatically place the UPPER half block directly above when the LOWER is placed
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);

        // If the bottom half breaks, destroy the top. If the top breaks, destroy the bottom.
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return deriveConnections(state, level, currentPos);
    }

    /* ─── connection derivation ──────────────────────────────── */

    /**
     * Recomputes {@link #FACING}, {@link #SHAPE} and {@link #BRANCH_RIGHT} from the wall neighbours.
     *
     * <p>The old implementation only ever wrote {@code SHAPE} and left {@code FACING} at whatever the
     * player happened to be looking at when they placed the block, which is why auto-detected corners
     * and junctions pointed the wrong way. Facing is now derived alongside the shape, and the values
     * already on the state are used as tie-breakers so a deliberate player choice survives wherever
     * the geometry allows more than one answer.
     */
    private BlockState deriveConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        // Always derive from the lower half's neighbourhood. The two halves would otherwise be able
        // to disagree - a door or any block that only reaches one of them used to leave the lower
        // half a corner and the upper half straight, so collision stopped matching the art.
        BlockPos base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;

        WallConnection connection = WallConnection.derive(
            level, base,
            state.getValue(FACING), state.getValue(BRANCH_RIGHT),
            DoubleWallBlock::connectsTo);

        return state.setValue(SHAPE, connection.shape())
                    .setValue(FACING, connection.facing())
                    .setValue(BRANCH_RIGHT, connection.branchRight());
    }

    /**
     * What a wall run treats as a continuation of itself: another wall, any door (all of this mod's
     * doors extend the vanilla {@code DoorBlock}), or anything a datapack has added to
     * {@link ArchitecturalTags#WALL_CONNECTABLE}.
     */
    private static boolean connectsTo(BlockState neighbour) {
        return neighbour.getBlock() instanceof DoubleWallBlock
            || neighbour.getBlock() instanceof DoorBlock
            || neighbour.is(ArchitecturalTags.WALL_CONNECTABLE);
    }

    /** The perpendicular edge a state currently describes. */
    private static Direction secondaryOf(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(BRANCH_RIGHT) ? facing.getClockWise() : facing.getCounterClockWise();
    }

    /* ─── rotation & mirroring ───────────────────────────────── */

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // BRANCH_RIGHT is relative to FACING, so it survives rotation untouched.
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        if (mirror == Mirror.NONE || state.getValue(SHAPE) == WallShape.STRAIGHT) {
            return mirrored;
        }
        // Reflecting swaps the clockwise and counter-clockwise edges.
        return mirrored.setValue(BRANCH_RIGHT, !state.getValue(BRANCH_RIGHT));
    }

    /* ─── shapes ─────────────────────────────────────────────── */

    private static VoxelShape edge(Direction direction) {
        return switch (direction) {
            case SOUTH -> EDGE_SOUTH;
            case EAST  -> EDGE_EAST;
            case WEST  -> EDGE_WEST;
            default    -> EDGE_NORTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape main = edge(facing);

        if (state.getValue(SHAPE) == WallShape.STRAIGHT) {
            return main;
        }
        return Shapes.or(main, edge(secondaryOf(state)));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
