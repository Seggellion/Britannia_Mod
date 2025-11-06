package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.block.entity.ArmoireBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class ArmoireBlock extends BaseEntityBlock {

    public static final MapCodec<ArmoireBlock> CODEC = simpleCodec(ArmoireBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Two-block-wide, three-block-tall collision boxes
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 48.0, 16.0),   // main
            Block.box(-16.0, 0.0, 0.0, 0.0, 48.0, 16.0)   // extends west
    );

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 48.0, 16.0),
            Block.box(16.0, 0.0, 0.0, 32.0, 48.0, 16.0)   // extends east
    );

    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 48.0, 16.0),
            Block.box(0.0, 0.0, 16.0, 16.0, 48.0, 32.0)   // extends north
    );

    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 48.0, 16.0),
            Block.box(0.0, 0.0, -16.0, 16.0, 48.0, 0.0)   // extends south
    );

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.SOUTH, SHAPE_SOUTH);
        SHAPES.put(Direction.EAST,  SHAPE_EAST);
        SHAPES.put(Direction.WEST,  SHAPE_WEST);
    }

    public ArmoireBlock(Properties props) {
        super(props
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

@Override
public RenderShape getRenderShape(BlockState state) {
    return RenderShape.INVISIBLE; // prevents vanilla baked model draw
}


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmoireBlockEntity(pos, state);
    }

    // Shift placement up 1 block so the 3-block model sits properly above ground
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().above(); // one block higher
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        if (level.getBlockState(pos).canBeReplaced(ctx)) {
            return this.defaultBlockState().setValue(FACING, facing);
        }
        return null;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ArmoireBlockEntity armoire) {
            player.openMenu(armoire);
            armoire.startOpen(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!oldState.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArmoireBlockEntity armoire) {
                net.minecraft.world.Containers.dropContents(level, pos, armoire);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(oldState, level, pos, newState, isMoving);
        }
    }
}
