package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class HorizontalTallBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 3);

    // Define explicit voxel boxes to avoid rotation issues
    private static final Map<Direction, VoxelShape> SHAPES_BY_FACING = new EnumMap<>(Direction.class);
    static {
        // Each facing version matches your model orientation (16×20×4)
        // Adjusted so the visible geometry sits on the north side instead of south
        SHAPES_BY_FACING.put(Direction.NORTH, Block.box(0, 0, 0, 16, 20, 4));
        SHAPES_BY_FACING.put(Direction.SOUTH, Block.box(0, 0, 12, 16, 20, 16));
        SHAPES_BY_FACING.put(Direction.WEST,  Block.box(0, 0, 0, 4, 20, 16));
        SHAPES_BY_FACING.put(Direction.EAST,  Block.box(12, 0, 0, 16, 20, 16));
    }

    public HorizontalTallBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(STYLE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(STYLE, 0);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES_BY_FACING.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // identical to shape
        return SHAPES_BY_FACING.get(state.getValue(FACING));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
