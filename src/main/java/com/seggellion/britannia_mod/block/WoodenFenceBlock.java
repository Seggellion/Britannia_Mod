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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Edge-mounted fence. Layout is saved physical intent; side flags describe current contacts. */
public class WoodenFenceBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final IntegerProperty LAYOUT_CODE = IntegerProperty.create("layout_code", 0, 32);
    public static final int LEGACY_LAYOUT = 32;
    public static final double THICKNESS = 4.0D;

    public WoodenFenceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LAYOUT_CODE, LEGACY_LAYOUT).setValue(NORTH,false).setValue(EAST,false)
                .setValue(SOUTH,false).setValue(WEST,false));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAYOUT_CODE, NORTH, EAST, SOUTH, WEST);
    }
    /** Read-only decoder: capture a saved legacy mask before connections change. */
    public static int effectiveLayout(BlockState state) {
        int code = state.getValue(LAYOUT_CODE);
        return code == LEGACY_LAYOUT ? connectionMask(state) : code;
    }
    public static BlockState materialize(BlockState state) {
        return state.getValue(LAYOUT_CODE) == LEGACY_LAYOUT
                ? state.setValue(LAYOUT_CODE, connectionMask(state)) : state;
    }
    public static int connectionMask(BlockState state) {
        return (state.getValue(NORTH)?1:0) | (state.getValue(EAST)?2:0)
                | (state.getValue(SOUTH)?4:0) | (state.getValue(WEST)?8:0);
    }
    public static int connectionCount(BlockState state) { return Integer.bitCount(connectionMask(state)); }
    public static BooleanProperty property(Direction direction) {
        return switch(direction) {
            case NORTH -> NORTH; case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST;
            default -> throw new IllegalArgumentException("Horizontal direction required: " + direction);
        };
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return deriveConnections(WoodenFenceGeometry.placement(defaultBlockState(), context),
                context.getLevel(), context.getClickedPos());
    }
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return deriveConnections(state, level, currentPos);
    }
    /** Four guarded cardinal reads, no recursive derivation, neighbor writes or run scans. */
    public BlockState deriveConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        state = materialize(state);
        for (Direction direction : WoodenFenceGeometry.DIRECTIONS) {
            BlockState neighbor = WoodenFenceGeometry.loadedNeighbor(level,pos.relative(direction));
            boolean contact = neighbor != null && neighbor.getBlock() instanceof WoodenFenceBlock
                    && WoodenFenceGeometry.contacts(state, neighbor, direction);
            state = state.setValue(property(direction), contact);
        }
        return state;
    }
    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!level.isClientSide && old.getBlock()!=this) level.scheduleTick(pos,this,1);
    }
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        BlockState resolved = deriveConnections(state,level,pos);
        if (resolved != state) level.setBlock(pos,resolved,Block.UPDATE_ALL);
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WoodenFenceGeometry.outline(state);
    }
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WoodenFenceGeometry.collision(state);
    }
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState source = materialize(state);
        int code = effectiveLayout(source);
        BlockState result = source.setValue(FACING,rotation.rotate(source.getValue(FACING)))
                .setValue(LAYOUT_CODE,(code&16) | WoodenFenceGeometry.transformMask(code&15,rotation::rotate));
        for(Direction d : WoodenFenceGeometry.DIRECTIONS)
            result=result.setValue(property(rotation.rotate(d)),source.getValue(property(d)));
        return result;
    }
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState source=materialize(state);
        if(mirror==Mirror.NONE) return source;
        int code=effectiveLayout(source);
        BlockState result=source.setValue(FACING,mirror.mirror(source.getValue(FACING)))
                .setValue(LAYOUT_CODE,((code^16)&16) | WoodenFenceGeometry.transformMask(code&15,mirror::mirror));
        for(Direction d : WoodenFenceGeometry.DIRECTIONS)
            result=result.setValue(property(mirror.mirror(d)),source.getValue(property(d)));
        return result;
    }
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) { return true; }
}
