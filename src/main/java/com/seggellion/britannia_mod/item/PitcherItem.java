package com.seggellion.britannia_mod.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/** Placeable empty pitcher that can carry water in its item data. */
public class PitcherItem extends BlockItem {
    private static final String KEY_FILLED = "FilledWithWater";

    public PitcherItem(Block block, Properties properties) {
        super(block, properties);
    }

    public void fillWithWater(ItemStack stack) {
        // Get or create the custom data component
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putBoolean(KEY_FILLED, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public boolean isFilled(ItemStack stack) {
        // Safely check if the tag exists and has the boolean key
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.getBoolean(KEY_FILLED);
    }
}
