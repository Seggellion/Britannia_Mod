package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;
import com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.InvisibleInAdventureMode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Explosion;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;


public class TraderSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode {

    public TraderSpawnBlock() {
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
    public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        Player player = c instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player pl ? (Player) ec.getEntity() : null;
        if (player == null) return Shapes.empty();
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        return isAdmin ? Block.box(0, 0, 0, 16, 16, 16) : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return null;
        boolean isAdmin = p.isCreative() || (p instanceof ServerPlayer sp && sp.hasPermissions(2));
        return isAdmin ? defaultBlockState() : null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TraderSpawnBlockEntity spawner && player instanceof ServerPlayer sp) {
            // Send config to client for editing
            com.seggellion.britannia_mod.network.payload.TraderSpawnScreenS2CPayload.send(
                sp, pos,
                spawner.getTraderType(),
                spawner.getCityName(),
                spawner.getTownPersonAmount()
            );
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TraderSpawnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, t) -> {
            if (t instanceof TraderSpawnBlockEntity e) e.serverTick();
        };
    }

@Override
public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
    if (state.getBlock() != newState.getBlock()) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TraderSpawnBlockEntity sp && level instanceof ServerLevel sl) {
                sp.onDestroyed(sl); // despawn + unregister + clear saves
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    } else {
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

@Override
public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TraderSpawnBlockEntity sp && level instanceof ServerLevel sl) {
            sp.onDestroyed(sl); // despawn + unregister + clear saves
        }
    }
    return super.playerWillDestroy(level, pos, state, player);
}


@Override
public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
    if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TraderSpawnBlockEntity sp && level instanceof ServerLevel sl) {
            sp.onDestroyed(sl);
        }
    }
    super.onBlockExploded(state, level, pos, explosion);
}



}
