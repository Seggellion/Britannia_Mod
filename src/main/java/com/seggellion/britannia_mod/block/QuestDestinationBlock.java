package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.QuestDestinationBlockEntity;
import com.seggellion.britannia_mod.InvisibleInAdventureMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import javax.annotation.Nullable;

public class QuestDestinationBlock extends Block implements EntityBlock, InvisibleInAdventureMode {

    public QuestDestinationBlock() {
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

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof QuestDestinationBlockEntity dest && player instanceof ServerPlayer sp) {
            // We will need a new payload to open a config screen for this block!
            com.seggellion.britannia_mod.network.payload.QuestDestinationScreenS2CPayload.send(
                sp, pos, dest.getCityName()
            );
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuestDestinationBlockEntity(pos, state); // Make sure to register this in BlockEntityRegistry!
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, t) -> {
            if (t instanceof QuestDestinationBlockEntity e) e.serverTick();
        };
    }
}