package com.seggellion.britannia_mod.block;

import java.util.UUID;

import javax.annotation.Nullable;

import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.util.HouseUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * DoorBlock that queries its {@link LockableDoorBlockEntity} for the structure
 * UUID and only opens if the player holds a matching {@link HouseKeyItem} or
 * the house is public.
 */
public class LockableDoorBlock extends DoorBlock implements EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();

    public LockableDoorBlock(BlockSetType type, Properties props) {
        super(type, props);
    }

    /* -----------------------------------------------------------------------
     * Block-Entity hook
     * -------------------------------------------------------------------- */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LockableDoorBlockEntity(pos, state);
    }

    /* -----------------------------------------------------------------------
     * Redstone, and why the lock outranks it
     *
     * DoorBlock reads a neighbour signal in three places and each one can put a door in the
     * OPEN state without anybody being asked: neighborChanged while the door stands, and
     * getStateForPlacement when it is first put down. (onExplosionHit is the third, and it is
     * already closed to us -- both housing door set types declare canOpenByWindCharge false,
     * which BritanniaBlockSetTypesRespectTheLockTest pins so it stays that way.)
     *
     * A lock that a button on the outside wall can step around is not a lock, and the fix does
     * not belong in the structures: a house should be able to have a pressure plate inside its
     * own doorway without that being a security hole. So the rule lives here, once, for every
     * housing door: while the door is locked, a signal may set POWERED but never OPEN.
     *
     * POWERED is still tracked truthfully rather than frozen, so that the moment the door is
     * unlocked it behaves like the wiring around it says it should.
     * -------------------------------------------------------------------- */

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        if (isLocked(level, pos, state)) {
            boolean signal = hasRedstoneSignal(level, lowerHalf(pos, state));
            if (state.getValue(POWERED) != signal || state.getValue(OPEN)) {
                // Flag 2 is what vanilla uses here: tell the clients, do not cascade another
                // round of neighbour updates. Writing the same state twice is a no-op, so this
                // cannot loop.
                level.setBlock(pos, state.setValue(POWERED, signal).setValue(OPEN, false), 2);
            }
            return;
        }
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        // A housing door is locked from the moment it exists -- the block entity defaults that
        // way -- so it must not appear already standing open because it was placed in a powered
        // spot. POWERED is left as vanilla computed it.
        return state == null ? null : state.setValue(OPEN, false);
    }

    /**
     * Bring a door back into line with a lock that has just changed.
     *
     * <p>Called by {@link LockableDoorBlockEntity} whenever the flag moves, because the door
     * that matters is the one that is already standing open on a pressure plate when its house
     * turns private. Vanilla would leave it open: POWERED is true, so nothing re-evaluates, and
     * the house is locked with its door hanging wide. Locking closes it. Unlocking hands it back
     * to whatever the wiring currently says.
     *
     * <p>Both halves are written, so it does not matter which one calls in.
     */
    public static void enforceLock(@Nullable Level level, BlockPos pos, boolean locked) {
        if (!(level instanceof ServerLevel server)) return;

        BlockState state = server.getBlockState(pos);
        if (!(state.getBlock() instanceof LockableDoorBlock)) return;

        BlockPos lower = lowerHalf(pos, state);
        boolean signal = hasRedstoneSignal(server, lower);
        boolean open = !locked && signal;

        applyToHalf(server, lower, open, signal);
        applyToHalf(server, lower.above(), open, signal);
    }

    private static void applyToHalf(Level level, BlockPos pos, boolean open, boolean powered) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LockableDoorBlock)) return;
        if (state.getValue(OPEN) == open && state.getValue(POWERED) == powered) return;
        level.setBlock(pos, state.setValue(OPEN, open).setValue(POWERED, powered), 2);
    }

    /** The same pair of positions vanilla consults for a door. */
    private static boolean hasRedstoneSignal(Level level, BlockPos lowerHalf) {
        return level.hasNeighborSignal(lowerHalf) || level.hasNeighborSignal(lowerHalf.above());
    }

    private static BlockPos lowerHalf(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /**
     * Whether this door is locked, read from the lower half the way every other caller reads it.
     *
     * <p>A housing door with no block entity is treated as locked. That is the direction to fail
     * in for a rule about who may open something, and it matches the block entity, whose own
     * default is locked.
     */
    private static boolean isLocked(Level level, BlockPos pos, BlockState state) {
        return !(level.getBlockEntity(lowerHalf(pos, state)) instanceof LockableDoorBlockEntity door)
                || door.isLocked();
    }


    /* -----------------------------------------------------------------------
     * Right-click interaction
     * -------------------------------------------------------------------- */
 @Override
protected InteractionResult useWithoutItem(BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           BlockHitResult hit) {

    if (level.isClientSide) return InteractionResult.SUCCESS;

    BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    BlockEntity be = level.getBlockEntity(basePos);
    if (!(be instanceof LockableDoorBlockEntity doorBE)) return InteractionResult.PASS;

    HouseLotBlockEntity lot = HouseUtil.findLot(level, pos);
    if (lot == null || !lot.isPrivate()) {
        return tryOpenDoor(state, level, pos, player, doorBE);
    }

    UUID lockId = doorBE.getStructureLockId();
    if (lockId != null) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof HouseKeyItem keyItem) {
            if (keyItem.matches(held, lockId)) {
                doorBE.toggleLock();


                SoundEvent snd = doorBE.isLocked()
                                ? ModSounds.DOOR_LOCK.get()
                                : ModSounds.DOOR_LOCK.get();

                level.playSound(null, pos, snd, SoundSource.BLOCKS, 1.0F, 1.0F);

                player.displayClientMessage(
                    Component.literal(doorBE.isLocked() ? "You have locked the door." : "You have unlocked the door."),
                    true
                );
                return InteractionResult.CONSUME;
            } else {
                player.displayClientMessage(Component.literal("This is not the key for this lock."), true);
                return InteractionResult.CONSUME;
            }
        }
    }

    if (doorBE.isLocked()) {
        player.displayClientMessage(Component.literal("This door is locked."), true);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.75f, 0.4f);
        return InteractionResult.CONSUME;
    }

    return tryOpenDoor(state, level, pos, player, doorBE);
}

    /* -----------------------------------------------------------------------
     * Helpers
     * -------------------------------------------------------------------- */
private InteractionResult tryOpenDoor(BlockState state, Level level, BlockPos pos, Player player, LockableDoorBlockEntity doorBE) {
    if (doorBE.isLocked()) {
        player.displayClientMessage(Component.literal("This door is locked."), true);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.75f, 0.4f);
        return InteractionResult.CONSUME;
    }

    boolean open = state.getValue(OPEN);
    level.setBlock(pos, state.setValue(OPEN, !open), 10);
    level.playSound(null, pos,
            open ? type().doorClose() : type().doorOpen(),
            SoundSource.BLOCKS,
            1.0F, 1.0F);

    return InteractionResult.SUCCESS;
}






}
