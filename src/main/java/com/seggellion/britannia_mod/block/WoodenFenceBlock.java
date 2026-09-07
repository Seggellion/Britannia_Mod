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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wall-connected, edge-mounted timber fence derived from the bannister construction language.
 *
 * <p>The four side flags are authoritative, server-derived connection state. {@link #FACING}
 * remains the intentional edge for isolated pieces; connected pieces derive it from topology.
 * Linear runs use the occupied edge of a junction, never a neighbour's previous facing. Unlike a vanilla fence, the collision and models occupy 4-voxel edge strips
 * instead of meeting at the centre of the block.
 */
public class WoodenFenceBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    /** One voxel broader than the bannister's 3-voxel construction. */
    public static final double THICKNESS = 4.0D;

    private static final VoxelShape EDGE_NORTH = Block.box(0, 0, 0, 16, 16, THICKNESS);
    private static final VoxelShape EDGE_EAST = Block.box(16 - THICKNESS, 0, 0, 16, 16, 16);
    private static final VoxelShape EDGE_SOUTH = Block.box(0, 0, 16 - THICKNESS, 16, 16, 16);
    private static final VoxelShape EDGE_WEST = Block.box(0, 0, 0, THICKNESS, 16, 16);

    public WoodenFenceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(NORTH, false).setValue(EAST, false)
            .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return deriveConnections(placed, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction.getAxis().isVertical()) {
            return state;
        }
        return deriveConnections(state, level, currentPos);
    }

    public BlockState deriveConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean north = connectsAt(level, pos.north());
        boolean east = connectsAt(level, pos.east());
        boolean south = connectsAt(level, pos.south());
        boolean west = connectsAt(level, pos.west());

        state = state.setValue(NORTH, north).setValue(EAST, east)
                     .setValue(SOUTH, south).setValue(WEST, west);

        int count = count(north, east, south, west);
        if (count == 0) return state;
        if (count == 4) return state.setValue(FACING, Direction.NORTH);

        boolean eastWestRun = east || west;
        boolean northSouthRun = north || south;
        if (eastWestRun && !northSouthRun) {
            return state.setValue(FACING,
                resolvedRunEdge(level, pos, Direction.Axis.X));
        }
        if (northSouthRun && !eastWestRun) {
            return state.setValue(FACING,
                resolvedRunEdge(level, pos, Direction.Axis.Z));
        }

        // Mixed-axis topology is anchored on an outside edge. This is deterministic, so corners
        // and T pieces survive reload and do not depend on which neighbour happened to update last.
        if (count == 2) {
            return state.setValue(FACING, !north ? Direction.NORTH : Direction.SOUTH);
        }
        if (!north) return state.setValue(FACING, Direction.NORTH);
        if (!east) return state.setValue(FACING, Direction.EAST);
        if (!south) return state.setValue(FACING, Direction.SOUTH);
        return state.setValue(FACING, Direction.WEST);
    }

    private static boolean connectsAt(LevelAccessor level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).getBlock() instanceof WoodenFenceBlock;
    }

    /** All pieces in one loaded linear run choose the same junction, independent of history. */
    private static Direction resolvedRunEdge(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        Direction negative = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        for (Direction step : new Direction[]{negative, negative.getOpposite()}) {
            BlockPos cursor = pos.relative(step);
            while (connectsAt(level, cursor)) {
                boolean n = connectsAt(level, cursor.north()), e = connectsAt(level, cursor.east());
                boolean s = connectsAt(level, cursor.south()), w = connectsAt(level, cursor.west());
                if ((axis == Direction.Axis.X && (n || s)) || (axis == Direction.Axis.Z && (e || w))) {
                    // Match the existing model's outside strips, not its incidental facing field.
                    int count = count(n, e, s, w);
                    Direction first = count == 4 ? Direction.NORTH
                            : !n ? Direction.NORTH : !e ? Direction.EAST : !s ? Direction.SOUTH : Direction.WEST;
                    if (count == 2) return axis == Direction.Axis.X
                            ? (!n ? Direction.NORTH : Direction.SOUTH) : (!w ? Direction.WEST : Direction.EAST);
                    return first.getAxis() != axis ? first : first.getCounterClockWise();
                }
                cursor = cursor.relative(step);
            }
        }
        return axis == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && old.getBlock() != this) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        BlockState resolved = deriveConnections(state, level, pos);
        if (resolved != state) level.setBlock(pos, resolved, Block.UPDATE_ALL);
    }

    public static int connectionCount(BlockState state) {
        return count(state.getValue(NORTH), state.getValue(EAST),
            state.getValue(SOUTH), state.getValue(WEST));
    }

    private static int count(boolean north, boolean east, boolean south, boolean west) {
        return (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);
    }

    private static boolean isOppositePair(BlockState state) {
        return state.getValue(NORTH) && state.getValue(SOUTH)
            || state.getValue(EAST) && state.getValue(WEST);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Horizontal direction required: " + direction);
        };
    }

    private static VoxelShape edge(Direction direction) {
        return switch (direction) {
            case EAST -> EDGE_EAST;
            case SOUTH -> EDGE_SOUTH;
            case WEST -> EDGE_WEST;
            default -> EDGE_NORTH;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        int connections = connectionCount(state);
        Direction facing = state.getValue(FACING);
        if (connections < 2 || connections == 2 && isOppositePair(state)) {
            return edge(facing);
        }
        if (connections == 2) {
            // The two unconnected sides are the outside edges occupied by the L model.
            VoxelShape shape = Shapes.empty();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!state.getValue(property(direction))) {
                    shape = Shapes.or(shape, edge(direction));
                }
            }
            return shape;
        }
        if (connections == 3) {
            Direction missing = Direction.NORTH;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!state.getValue(property(direction))) {
                    missing = direction;
                    break;
                }
            }
            return Shapes.or(edge(missing), edge(missing.getCounterClockWise()));
        }
        return Shapes.or(edge(facing), edge(facing.getCounterClockWise()));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        // Preserve the authored footprint and selection bounds; only collision reaches 1.5 blocks.
        VoxelShape collision = Shapes.empty();
        for (var box : getShape(state, level, pos, context).toAabbs())
            collision = Shapes.or(collision, Shapes.box(box.minX, box.minY, box.minZ, box.maxX, 1.5D, box.maxZ));
        return collision;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        boolean north = state.getValue(NORTH);
        boolean east = state.getValue(EAST);
        boolean south = state.getValue(SOUTH);
        boolean west = state.getValue(WEST);
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
            .setValue(property(rotation.rotate(Direction.NORTH)), north)
            .setValue(property(rotation.rotate(Direction.EAST)), east)
            .setValue(property(rotation.rotate(Direction.SOUTH)), south)
            .setValue(property(rotation.rotate(Direction.WEST)), west);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        boolean north = state.getValue(NORTH);
        boolean east = state.getValue(EAST);
        boolean south = state.getValue(SOUTH);
        boolean west = state.getValue(WEST);
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(property(mirror.mirror(Direction.NORTH)), north)
            .setValue(property(mirror.mirror(Direction.EAST)), east)
            .setValue(property(mirror.mirror(Direction.SOUTH)), south)
            .setValue(property(mirror.mirror(Direction.WEST)), west);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
