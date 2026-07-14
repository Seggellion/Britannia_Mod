package com.seggellion.britannia_mod.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Function;

public final class CropSeedExtractor {
    private CropSeedExtractor() {
    }

    public static boolean shouldExtract(Player player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND || player.isShiftKeyDown();
    }

    public static InteractionResultHolder<ItemStack> tryExtractSeed(
            Level level,
            Player player,
            InteractionHand hand,
            Function<ItemStack, ItemStack> seedFactory
    ) {
        ItemStack cropStack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ItemStack seedStack = seedFactory.apply(cropStack);
            if (!seedStack.isEmpty()) {
                if (!player.getInventory().add(seedStack)) {
                    player.drop(seedStack, false);
                }

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PUMPKIN_CARVE, SoundSource.PLAYERS, 1.0F, 1.0F);

                if (!player.getAbilities().instabuild) {
                    cropStack.shrink(1);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(cropStack, level.isClientSide());
    }
}
