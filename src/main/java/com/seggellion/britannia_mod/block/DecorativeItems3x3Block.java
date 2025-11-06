package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class DecorativeItems3x3Block extends Block {

    public static final MapCodec<DecorativeItems3x3Block> CODEC = simpleCodec(DecorativeItems3x3Block::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

private static final VoxelShape SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 48.0, 48.0, 8.0);
private static final VoxelShape SHAPE_SOUTH = Block.box(-32.0, 0.0, 8.0, 16.0, 48.0, 16.0);
private static final VoxelShape SHAPE_WEST  = Block.box(8.0, 0.0, -32.0, 16.0, 48.0, 16.0);
private static final VoxelShape SHAPE_EAST  = Block.box(0.0, 0.0, 0.0, 8.0, 48.0, 48.0);


    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.SOUTH, SHAPE_SOUTH);
        SHAPES.put(Direction.WEST,  SHAPE_WEST);
        SHAPES.put(Direction.EAST,  SHAPE_EAST);
    }

    public DecorativeItems3x3Block(Properties props) {
        super(props
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Faces opposite of player
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_NORTH);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // uses baked model JSON
    }
}
