package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Explosion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import javax.annotation.Nullable;

public class QuestGiverSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode {

    public QuestGiverSpawnBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) return RenderShape.MODEL;
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        Player player = c instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player pl ? pl : null;
        if (player == null) return Shapes.empty();
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        return isAdmin ? Block.box(0, 0, 0, 16, 16, 16) : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return null;
        return (p.isCreative() || (p instanceof ServerPlayer sp && sp.hasPermissions(2))) ? defaultBlockState() : null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof QuestGiverSpawnBlockEntity spawner && player instanceof ServerPlayer sp) {
            // Send payload to open UI (You will need to register this payload!)
            com.seggellion.britannia_mod.network.payload.QuestGiverSpawnScreenS2CPayload.send(
                sp, pos, spawner.getNpcName(), spawner.getCityName(), spawner.getCustomApiId(), spawner.getGender()
            );
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuestGiverSpawnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, t) -> {
            if (t instanceof QuestGiverSpawnBlockEntity e) e.serverTick();
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof QuestGiverSpawnBlockEntity sp && level instanceof ServerLevel sl) {
                sp.onDestroyed(sl);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuestGiverSpawnBlockEntity sp && level instanceof ServerLevel sl) {
            sp.onDestroyed(sl);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }
}