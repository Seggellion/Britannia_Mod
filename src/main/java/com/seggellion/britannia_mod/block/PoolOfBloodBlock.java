package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One no-collision blood decoration with all eight purchased-pack visual variants. */
public final class PoolOfBloodBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<PoolOfBloodBlock> CODEC = simpleCodec(PoolOfBloodBlock::new);
    public static final int VARIANT_COUNT = 8;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANT_COUNT - 1);
    private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 1, 16);

    public PoolOfBloodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(VARIANT, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().random;
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VARIANT, random.nextInt(VARIANT_COUNT));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()) || player.isSpectator()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(VARIANT,
                    (state.getValue(VARIANT) + 1) % VARIANT_COUNT), Block.UPDATE_ALL);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
