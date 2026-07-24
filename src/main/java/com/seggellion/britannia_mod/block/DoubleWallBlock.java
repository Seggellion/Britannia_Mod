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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DoubleWallBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WallShape> SHAPE = EnumProperty.create("shape", WallShape.class);
    // Added HALF property to track bottom and top blocks
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // VoxelShapes are reduced to 16.0D height. The lower block (0-16) + upper block (16-32) equals your total 32 height.
    private static final VoxelShape EDGE_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 7.0D);
    private static final VoxelShape EDGE_SOUTH = Block.box(0.0D, 0.0D, 9.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape EDGE_WEST  = Block.box(0.0D, 0.0D, 0.0D, 7.0D, 16.0D, 16.0D);
    private static final VoxelShape EDGE_EAST  = Block.box(9.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    private static final VoxelShape STRAIGHT_NORTH = EDGE_NORTH;
    private static final VoxelShape STRAIGHT_SOUTH = EDGE_SOUTH;
    private static final VoxelShape STRAIGHT_EAST  = EDGE_EAST;
    private static final VoxelShape STRAIGHT_WEST  = EDGE_WEST;

    private static final VoxelShape CORNER_NORTH = Shapes.or(EDGE_NORTH, EDGE_WEST);
    private static final VoxelShape CORNER_EAST  = Shapes.or(EDGE_NORTH, EDGE_EAST);
    private static final VoxelShape CORNER_SOUTH = Shapes.or(EDGE_SOUTH, EDGE_EAST);
    private static final VoxelShape CORNER_WEST  = Shapes.or(EDGE_SOUTH, EDGE_WEST);

    private static final VoxelShape T_JUNCTION_NORTH = Shapes.or(EDGE_WEST, EDGE_NORTH, EDGE_EAST);
    private static final VoxelShape T_JUNCTION_EAST  = Shapes.or(EDGE_NORTH, EDGE_EAST, EDGE_SOUTH);
    private static final VoxelShape T_JUNCTION_SOUTH = Shapes.or(EDGE_EAST, EDGE_SOUTH, EDGE_WEST);
    private static final VoxelShape T_JUNCTION_WEST  = Shapes.or(EDGE_SOUTH, EDGE_WEST, EDGE_NORTH);

    public DoubleWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(SHAPE, WallShape.STRAIGHT)
            .setValue(HALF, DoubleBlockHalf.LOWER)); // Set default half
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        
        // Ensure there is space above the targeted block to place the upper half
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            Direction facing = context.getHorizontalDirection().getOpposite();
            WallShape shape = calculateShape(level, pos);
            return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SHAPE, shape)
                .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // Automatically place the UPPER half block directly above when the LOWER is placed
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        
        // If the bottom half breaks, destroy the top. If the top breaks, destroy the bottom.
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        WallShape newShape = calculateShape(level, currentPos);
        return state.setValue(SHAPE, newShape);
    }

    private WallShape calculateShape(LevelAccessor level, BlockPos pos) {
        boolean north = isDoubleWall(level, pos.north());
        boolean south = isDoubleWall(level, pos.south());
        boolean east  = isDoubleWall(level, pos.east());
        boolean west  = isDoubleWall(level, pos.west());

        int connections = (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);

        if (connections >= 3) {
            return WallShape.T_JUNCTION;
        } else if (connections == 2) {
            if ((north && south) || (east && west)) {
                return WallShape.STRAIGHT;
            } else {
                return WallShape.CORNER;
            }
        }
        return WallShape.STRAIGHT;
    }

    private boolean isDoubleWall(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DoubleWallBlock;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        WallShape shape = state.getValue(SHAPE);

        switch (shape) {
            case CORNER:
                switch (facing) {
                    case SOUTH: return CORNER_SOUTH;
                    case EAST:  return CORNER_EAST;
                    case WEST:  return CORNER_WEST;
                    case NORTH:
                    default:    return CORNER_NORTH;
                }
            case T_JUNCTION:
                switch (facing) {
                    case SOUTH: return T_JUNCTION_SOUTH;
                    case EAST:  return T_JUNCTION_EAST;
                    case WEST:  return T_JUNCTION_WEST;
                    case NORTH:
                    default:    return T_JUNCTION_NORTH;
                }
            case STRAIGHT:
            default:
                switch (facing) {
                    case SOUTH: return STRAIGHT_SOUTH;
                    case EAST:  return STRAIGHT_EAST;
                    case WEST:  return STRAIGHT_WEST;
                    case NORTH:
                    default:    return STRAIGHT_NORTH;
                }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}