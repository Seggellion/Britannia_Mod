package com.seggellion.britannia_mod.bowlpreparation;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Item adapter for the final fertile-dirt bowl mix. */
public final class FertileDirtMixingItem extends Item {
    public FertileDirtMixingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        InteractionResultHolder<ItemStack> result = HandRecipeInteraction.use(level, player, hand);
        if (result.getResult().consumesAction()) return result;
        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide) {
            BowlMixingMessages.send(player,
                    FertileDirtMixingService.diagnose(held, player.getOffhandItem()));
        }
        return result;
    }
}
