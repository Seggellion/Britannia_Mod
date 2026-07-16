package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.InvisibleInAdventureMode;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

public final class ServiceNpcSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode {
    public ServiceNpcSpawnBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isCreative() ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Player player = context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player found ? found : null;
        if (player == null) return Shapes.empty();
        boolean visible = player.isCreative() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
        return visible ? Block.box(0, 0, 0, 16, 16, 16) : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) return null;
        if (player.level().isClientSide) return player.isCreative() ? defaultBlockState() : null;
        return player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)
                ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity) {
            blockEntity.initializeNewPlacement(serverLevel);
        }
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)) {
            return InteractionResult.CONSUME;
        }
        blockEntity.ensureIdentity((ServerLevel) level);
        if (blockEntity.getSpawnPointId() == null) return InteractionResult.CONSUME;

        var provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new ServiceNpcSpawnMenu(
                        containerId,
                        inventory,
                        level.dimension(),
                        pos,
                        blockEntity.getSpawnPointId()
                ),
                Component.translatable("screen.britannia_mod.service_npc_spawn.title")
        );
        serverPlayer.openMenu(provider, buffer -> {
            buffer.writeResourceLocation(level.dimension().location());
            buffer.writeBlockPos(pos);
            buffer.writeUUID(blockEntity.getSpawnPointId());
        });
        if (serverPlayer.containerMenu instanceof ServiceNpcSpawnMenu menu) {
            ServiceNpcSpawnStateS2CPayload.send(serverPlayer, menu, com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError.NONE);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity) {
            blockEntity.recordTrueDestruction(serverLevel);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServiceNpcSpawnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof ServiceNpcSpawnBlockEntity serviceNpcSpawn) {
                serviceNpcSpawn.serverTick();
            }
        };
    }
}
