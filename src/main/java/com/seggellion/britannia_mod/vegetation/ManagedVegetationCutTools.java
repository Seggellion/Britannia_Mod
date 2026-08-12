package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/** Shared client/server sword classification for cutting managed vegetation. */
public final class ManagedVegetationCutTools {
    private ManagedVegetationCutTools() {
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof SwordItem || stack.is(ModTags.Items.GRAIN_HARVEST_BLADES));
    }
}
