package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

public class ChairBlock extends HorizontalDirectionalBlock {
private static final Logger LOGGER = LogUtils.getLogger();

    
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ChairBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

public static final MapCodec<ChairBlock> CODEC = simpleCodec(ChairBlock::new);

@Override
protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
}

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide()) {
        List<LivingSeatEntity> existing = level.getEntitiesOfClass(
            LivingSeatEntity.class,
            new AABB(pos),
            entity -> true
        );

        LivingSeatEntity seat = existing.isEmpty()
            ? EntityRegistry.SEAT_ENTITY.get().spawn((ServerLevel) level, pos, MobSpawnType.TRIGGERED)
            : existing.get(0);

        if (seat != null && !player.isPassenger()) {
            player.startRiding(seat);
        }
    }

    return InteractionResult.SUCCESS;
}


@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    LOGGER.info("🔧 useItemOn triggered - Held item: {}, Block: {}", stack.getItem(), state.getBlock());

    if (!level.isClientSide()
        && stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())
        && player.isCreative()
        && !player.isSpectator()) {

        LOGGER.info("✅ Rotating chair block at {}", pos);

        Direction current = state.getValue(FACING);
        Direction next = current.getClockWise();
        level.setBlock(pos, state.setValue(FACING, next), 3);

        return ItemInteractionResult.SUCCESS;
    }

    LOGGER.info("⏭️ Interaction passed to default.");
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
}



}
