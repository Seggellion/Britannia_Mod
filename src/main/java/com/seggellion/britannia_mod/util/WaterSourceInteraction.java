package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.item.PitcherItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Shared, server-authoritative fill transaction for an unlimited water source. */
public final class WaterSourceInteraction {
    private WaterSourceInteraction() {
    }

    public static boolean supports(ItemStack stack) {
        return stack.getItem() instanceof WateringCanItem
                || stack.getItem() instanceof PitcherItem
                || stack.is(Items.BUCKET);
    }

    public static ItemInteractionResult fillFromSource(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!supports(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (stack.getItem() instanceof WateringCanItem) {
                WateringCanItem.setWaterCharges(stack, WateringCanItem.MAX_WATER_CHARGES);
            } else if (stack.getItem() instanceof PitcherItem pitcher) {
                pitcher.fillWithWater(stack);
            } else {
                player.setItemInHand(hand,
                        ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
            }
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
