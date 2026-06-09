package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.InvisibleInAdventureMode;
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

public class MerchantSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode {

    public MerchantSpawnBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(1.5F)
                .noOcclusion());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            return RenderShape.MODEL;
        }
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Player player = context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player
                ? (Player) entityContext.getEntity()
                : null;
        if (player == null) return Shapes.empty();
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2));
        return isAdmin ? Block.box(0, 0, 0, 16, 16, 16) : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) return null;
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2));
        return isAdmin ? defaultBlockState() : null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MerchantSpawnBlockEntity spawner && player instanceof ServerPlayer serverPlayer) {
            com.seggellion.britannia_mod.network.payload.MerchantSpawnScreenS2CPayload.send(
                    serverPlayer,
                    pos,
                    spawner.getMerchantType(),
                    spawner.getCityName(),
                    spawner.getTownPersonAmount()
            );
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MerchantSpawnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, blockState, ticker) -> {
            if (ticker instanceof MerchantSpawnBlockEntity spawner) spawner.serverTick();
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MerchantSpawnBlockEntity spawner && level instanceof ServerLevel serverLevel) {
                spawner.onDestroyed(serverLevel);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MerchantSpawnBlockEntity spawner && level instanceof ServerLevel serverLevel) {
                spawner.onDestroyed(serverLevel);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MerchantSpawnBlockEntity spawner && level instanceof ServerLevel serverLevel) {
                spawner.onDestroyed(serverLevel);
            }
        }
        super.onBlockExploded(state, level, pos, explosion);
    }
}
