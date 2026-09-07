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
        return HandRecipeInteraction.use(level, player, hand);
    }
}
