package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.TrainingDummyBlockEntity;
import com.seggellion.britannia_mod.event.TrainingDummyEventHandler;
import com.seggellion.britannia_mod.training.TrainingWeaponClassifier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-wide, three-high atomic multiblock whose root owns the synchronized animation. */
public final class TrainingDummyBlock extends DecorativeMultiblockBlock implements EntityBlock {
    public TrainingDummyBlock(Properties properties) {
        super(properties, 0, 1, 0, 2, 0, 0, TrainingDummyBlock::cellShape);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new TrainingDummyBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isRoot(state) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    /** Native held-item right-click route. Creative deliberately falls through unchanged. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (player.isCreative()
                || hand != InteractionHand.MAIN_HAND
                || player.isShiftKeyDown()
                || TrainingWeaponClassifier.classify(stack).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide
                && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            TrainingDummyEventHandler.attemptStrike(serverPlayer, serverLevel, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empty-hand right-click route. Creative deliberately falls through unchanged. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isCreative()
                || player.isShiftKeyDown()
                || TrainingWeaponClassifier.classify(player.getMainHandItem()).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            TrainingDummyEventHandler.attemptStrike(serverPlayer, serverLevel, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public boolean triggerHit(ServerLevel level, BlockPos struckPosition, BlockState struckState) {
        if (!hasValidPart(struckState)) {
            return false;
        }
        BlockPos anchor = anchorPosition(struckPosition, struckState);
        if (level.getBlockEntity(anchor) instanceof TrainingDummyBlockEntity dummy) {
            dummy.triggerHitAnimation();
            return true;
        }
        return false;
    }

    private static VoxelShape cellShape(int x, int y, int z) {
        if (y == 0) {
            return Shapes.or(
                    Block.box(2, 0, 4, 14, 16, 12),
                    Block.box(x == 0 ? 8 : 0, 0, 6, x == 0 ? 16 : 8, 5, 10));
        }
        if (y == 1) {
            return Block.box(0, 0, 4, 16, 16, 12);
        }
        return Shapes.or(
                Block.box(0, 0, 6, 16, 12, 10),
                Block.box(x == 0 ? 7 : 0, 12, 6, x == 0 ? 16 : 9, 16, 10));
    }
}
