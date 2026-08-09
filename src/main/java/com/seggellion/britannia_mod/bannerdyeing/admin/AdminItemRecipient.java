package com.seggellion.britannia_mod.bannerdyeing.admin;

import net.minecraft.world.item.ItemStack;

/** Minimal delivery boundary so give/drop behavior can be tested without a live server. */
public interface AdminItemRecipient {
    String name();

    boolean insert(ItemStack stack);

    boolean dropRemainder(ItemStack stack);
}
