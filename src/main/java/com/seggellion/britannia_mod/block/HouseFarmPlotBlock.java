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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A raised planting bed: a brick kerb around the outside with soil recessed inside it.
 *
 * <p>Plots placed next to each other merge. Each side carries a boolean saying whether a plot
 * adjoins it, and the blockstate leaves the kerb off on connected sides, so the soil of both blocks
 * runs together into one bed instead of every block being ringed by its own wall.
 */
public class HouseFarmPlotBlock extends Block {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
        Direction.NORTH, NORTH, Direction.SOUTH, SOUTH,
        Direction.EAST, EAST, Direction.WEST, WEST);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public HouseFarmPlotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(NORTH, false).setValue(SOUTH, false)
            .setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction.getAxis().isVertical()) {
            return state;
        }
        return state.setValue(SIDES.get(direction), neighborState.getBlock() instanceof HouseFarmPlotBlock);
    }

    private BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            boolean joins = level.getBlockState(pos.relative(side.getKey()))
                                 .getBlock() instanceof HouseFarmPlotBlock;
            state = state.setValue(side.getValue(), joins);
        }
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(NORTH, state.getValue(SIDES.get(rotation.rotate(Direction.NORTH))))
                    .setValue(EAST, state.getValue(SIDES.get(rotation.rotate(Direction.EAST))))
                    .setValue(SOUTH, state.getValue(SIDES.get(rotation.rotate(Direction.SOUTH))))
                    .setValue(WEST, state.getValue(SIDES.get(rotation.rotate(Direction.WEST))));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
