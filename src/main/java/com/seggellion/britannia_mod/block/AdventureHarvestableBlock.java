package com.seggellion.britannia_mod.block;

import net.minecraft.world.item.ItemStack;

/** Client-visible hint only; the server always revalidates the actual harvest. */
public interface AdventureHarvestableBlock {
    boolean allowsAdventureHarvest(ItemStack tool);
}
