package com.seggellion.britannia_mod.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;

/** Supplied shield artwork with standard shield blocking and the existing smithing lifecycle. */
public final class DecorativeShieldItem extends ShieldItem {
    public DecorativeShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        // Its metal and skill are checked by BlacksmithCrafting, not vanilla plank repair.
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlacksmithItemData.appendTooltip(stack, tooltip);
    }
}
