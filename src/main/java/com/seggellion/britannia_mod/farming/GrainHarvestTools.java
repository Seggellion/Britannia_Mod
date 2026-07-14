package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.world.item.ItemStack;

public final class GrainHarvestTools {
    private GrainHarvestTools() {
    }

    public static boolean isGrainCrop(String cropId) {
        return switch (cropId) {
            case "wheat", "rye", "oats", "barley", "mustard", "beans", "flax", "nightshade" -> true;
            default -> false;
        };
    }

    public static boolean isGrainHarvestBlade(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModTags.Items.GRAIN_HARVEST_BLADES);
    }
}
