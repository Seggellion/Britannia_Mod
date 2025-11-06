package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.MonsterSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.InvisibleInAdventureMode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;



public class MonsterSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode {

    public MonsterSpawnBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5F)
            .noOcclusion()); // transparent to sight for non-admins
    }


    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            return RenderShape.MODEL;  // will show your block’s JSON model/texture
        }
        return RenderShape.INVISIBLE;  // no rendering for others
    }

    // No collision for anyone.
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    // Only show an outline to admins (creative or permission level >=2). Others get no hitbox ⇒ effectively hidden.
    @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        Player player = c instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof Player pl ? (Player) ec.getEntity() : null;
        if (player == null) return Shapes.empty();
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        return isAdmin ? Block.box(0, 0, 0, 16, 1, 16) : Shapes.empty();
    }

    // Prevent placement by non-admins.
    @Nullable @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return null;
        boolean isAdmin = p.isCreative() || (p instanceof ServerPlayer sp && sp.hasPermissions(2));
        return isAdmin ? defaultBlockState() : null;
    }

    // Open config screen for admins only; otherwise do nothing.
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME; // ignore
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MonsterSpawnBlockEntity spawner && player instanceof ServerPlayer sp) {
            // send current config to that player and open client screen
            com.seggellion.britannia_mod.network.payload.MonsterSpawnScreenS2CPayload.send(
                sp, pos, spawner.getEntityId(), spawner.getSpawnRadius(),
                spawner.getRandomMinTicks(), spawner.getRandomMaxTicks(), spawner.isNightOnly(), spawner.getMaxEntities(), spawner.getActiveEntityTypes((ServerLevel) level)
            );
        }
        return InteractionResult.CONSUME;
    }

    // Block entity hookup
    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonsterSpawnBlockEntity(pos, state);
    }

    @Nullable @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, t) -> {
            if (t instanceof MonsterSpawnBlockEntity e) e.serverTick();
        };
    }
}
