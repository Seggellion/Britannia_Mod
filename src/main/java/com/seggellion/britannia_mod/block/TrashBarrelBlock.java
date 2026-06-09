package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.block.entity.TrashBarrelBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TrashBarrelBlock extends BaseEntityBlock {
    // 1. Define the codec for this specific block
    public static final MapCodec<TrashBarrelBlock> CODEC = simpleCodec(TrashBarrelBlock::new);

    public TrashBarrelBlock(Properties properties) {
        super(properties);
    }

    // 2. Override the codec method to return your custom codec
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrashBarrelBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Ensures the block renders properly instead of being invisible
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TrashBarrelBlockEntity trashBarrel) {
                // Opens the UI defined in the block entity
                player.openMenu(trashBarrel);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // We only tick on the server side
        return level.isClientSide ? null : createTickerHelper(type, BlockEntityRegistry.TRASH_BARREL_BE.get(), TrashBarrelBlockEntity::tick);
    }
}