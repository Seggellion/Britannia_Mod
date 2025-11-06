package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public class TallDecorative3Block extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 3 blocks high = 48 voxels
    // Box units are 1/16 of a block, so height 3.0D covers 48 voxels
    private static final VoxelShape SHAPE_NORTH = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 3.0D, 1.0D);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 3.0D, 1.0D);
    private static final VoxelShape SHAPE_WEST  = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 3.0D, 1.0D);
    private static final VoxelShape SHAPE_EAST  = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 3.0D, 1.0D);

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.SOUTH, SHAPE_SOUTH);
        SHAPES.put(Direction.WEST,  SHAPE_WEST);
        SHAPES.put(Direction.EAST,  SHAPE_EAST);
    }

    public TallDecorative3Block(BlockBehaviour.Properties props) {
        super(props
            .noOcclusion()
            .strength(1.5F)
            .sound(SoundType.STONE)
            .isViewBlocking((s, r, p) -> false));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
        return RenderShape.MODEL;
    }
}
