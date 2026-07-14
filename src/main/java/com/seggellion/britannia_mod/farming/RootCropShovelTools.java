package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.world.item.ItemStack;

public final class RootCropShovelTools {
    private RootCropShovelTools() {
    }

    public static boolean isRootCrop(String cropId) {
        return switch (cropId) {
            case "mandrake", "garlic", "potato", "yam" -> true;
            default -> false;
        };
    }

    public static boolean isRootCropShovel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModTags.Items.ROOT_CROP_SHOVELS);
    }
}
