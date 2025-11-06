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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
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



    private boolean hasMatchingKey(Player player, UUID lockId) {
        // Check both hands first
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof HouseKeyItem keyItem &&
                keyItem.matches(held, lockId)) {
                return true;
            }
        }

        // Then fallback to inventory check
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof HouseKeyItem keyItem &&
                keyItem.matches(stack, lockId)) {
                return true;
            }
        }

        return false;
}



}
