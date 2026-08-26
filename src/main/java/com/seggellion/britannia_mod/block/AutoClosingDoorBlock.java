package com.seggellion.britannia_mod.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Britannia's common RunUO-style automatically closing door.
 *
 * <p>The logical server schedules one block tick on the bottom block whenever the door changes
 * from closed to open. Closing clears that tick, and reopening clears then replaces it. Clearing
 * before scheduling matters because Minecraft de-duplicates scheduled ticks by block and position;
 * it also prevents an older opening cycle from closing a newly reopened door early.
 *
 * <p>No per-door ticker or world scan is used. Native scheduled ticks are saved with their chunk
 * and only run while that position is ticking.
 */
public class AutoClosingDoorBlock extends DoorBlock {
    public static final int AUTO_CLOSE_DELAY_TICKS = 20 * 20;
    public static final int AUTO_CLOSE_RETRY_TICKS = 10 * 20;

    public AutoClosingDoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        boolean wasOpen = state.getValue(OPEN);
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        if (result.consumesAction()) {
            automaticCloseAfterTransition(level, pos, state, wasOpen,
                    level.getBlockState(pos).getOptionalValue(OPEN).orElse(wasOpen));
        }
        return result;
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos,
                        boolean open) {
        boolean wasOpen = state.getValue(OPEN);
        super.setOpen(entity, level, state, pos, open);
        boolean isOpen = level.getBlockState(pos).getOptionalValue(OPEN).orElse(wasOpen);
        automaticCloseAfterTransition(level, pos, state, wasOpen, isOpen);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        boolean wasOpen = state.getValue(OPEN);
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        boolean isOpen = level.getBlockState(pos).getOptionalValue(OPEN).orElse(wasOpen);
        automaticCloseAfterTransition(level, pos, state, wasOpen, isOpen);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(OPEN)) {
            automaticCloseAfterTransition(level, pos, state, false, true);
        }
    }

    /**
     * Records an opening/closing transition performed by a subclass's custom interaction path.
     */
    protected final void automaticCloseAfterTransition(Level level, BlockPos pos,
                                                       BlockState stateBefore, boolean wasOpen,
                                                       boolean isOpen) {
        if (!(level instanceof ServerLevel server) || wasOpen == isOpen) {
            return;
        }

        BlockPos basePos = automaticCloseBasePos(pos, stateBefore);
        clearAutomaticClose(server, basePos);
        if (isOpen) {
            server.scheduleTick(basePos, this, AUTO_CLOSE_DELAY_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this) || !state.getOptionalValue(OPEN).orElse(false)) {
            return;
        }

        if (isPoweredForAutomaticClose(level, pos) || !isClearToClose(level, pos)) {
            restartAutomaticClose(level, pos, AUTO_CLOSE_RETRY_TICKS);
            return;
        }

        closeAutomatically(level, pos, state);
    }

    /** Returns the canonical scheduled-tick position for this door. */
    protected BlockPos automaticCloseBasePos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** Number of vertically stacked blocks whose closed collision shapes form this door. */
    protected int automaticCloseHeight() {
        return 2;
    }

    /**
     * Closes a clear, unpowered door. Vanilla owns state synchronization, sound, game event, and
     * client updates for ordinary two-block doors.
     */
    protected void closeAutomatically(ServerLevel level, BlockPos basePos, BlockState baseState) {
        super.setOpen(null, level, baseState, basePos, false);
    }

    private boolean isPoweredForAutomaticClose(ServerLevel level, BlockPos basePos) {
        for (int y = 0; y < automaticCloseHeight(); y++) {
            BlockPos partPos = basePos.above(y);
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(this)
                    && (partState.getOptionalValue(POWERED).orElse(false)
                    || level.hasNeighborSignal(partPos))) {
                return true;
            }
        }
        return false;
    }

    private boolean isClearToClose(ServerLevel level, BlockPos basePos) {
        for (int y = 0; y < automaticCloseHeight(); y++) {
            BlockPos partPos = basePos.above(y);
            BlockState partState = level.getBlockState(partPos);
            if (!partState.is(this)) {
                return false;
            }

            BlockState closedState = partState.setValue(OPEN, false);
            if (!level.isUnobstructed(closedState, partPos, CollisionContext.empty())) {
                return false;
            }
        }
        return true;
    }

    private void restartAutomaticClose(ServerLevel level, BlockPos basePos, int delayTicks) {
        clearAutomaticClose(level, basePos);
        level.scheduleTick(basePos, this, delayTicks);
    }

    private static void clearAutomaticClose(ServerLevel level, BlockPos basePos) {
        // The position currently contains a door, so clearing this one-block area cannot remove a
        // scheduled tick belonging to some other block at the same position.
        level.getBlockTicks().clearArea(new BoundingBox(basePos));
    }
}
