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

/**
 * DoorBlock that queries its {@link LockableDoorBlockEntity} for the structure
 * UUID and only opens if the player holds a matching {@link HouseKeyItem} or
 * the house is public.
 */
public class LockableDoorBlock extends DoorBlock implements EntityBlock {

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

        // Client side just returns success so the hand animation plays.
        if (level.isClientSide) return InteractionResult.SUCCESS;

        /* The BE is stored only on the lower half to avoid duplicates. */
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockEntity be = level.getBlockEntity(basePos);
        if (!(be instanceof LockableDoorBlockEntity doorBE)) return InteractionResult.PASS;

        /* 1)  If the house is public, behave like a normal door. */
        HouseLotBlockEntity lot = HouseUtil.findLot(level, pos);
        if (lot == null || !lot.isPrivate()) {
            return toggleDoor(state, level, pos);
        }

        /* 2)  Private house – need a key that matches the structure UUID. */
        @Nullable UUID lockId = doorBE.getStructureLockId();
        if (lockId != null && hasMatchingKey(player, lockId)) {
            return toggleDoor(state, level, pos);
        }

        /* 3)  Failed attempt: play a dull metal clunk and consume the click. */
        level.playSound(null, pos,
                        SoundEvents.IRON_TRAPDOOR_CLOSE,
                        SoundSource.BLOCKS,
                        0.75f, 0.4f);
        return InteractionResult.CONSUME;
    }

    /* -----------------------------------------------------------------------
     * Helpers
     * -------------------------------------------------------------------- */
    private InteractionResult toggleDoor(BlockState state, Level level, BlockPos pos) {
        boolean open = state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, !open), 10);
        level.playSound(null, pos,
                open ? type().doorClose() : type().doorOpen(),
                SoundSource.BLOCKS,
                1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private boolean hasMatchingKey(Player player, UUID lockId) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof HouseKeyItem keyItem &&
                keyItem.matches(stack, lockId)) {
                return true;
            }
        }
        return false;
    }
}
