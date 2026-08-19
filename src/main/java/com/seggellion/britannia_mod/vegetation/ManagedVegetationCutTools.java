package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/** Shared client/server authorization for cutting managed vegetation. */
public final class ManagedVegetationCutTools {
    private ManagedVegetationCutTools() {
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof SwordItem || stack.is(ModTags.Items.GRAIN_HARVEST_BLADES));
    }

    public static boolean canCut(Player player) {
        return player != null && isAuthorized(
                player.isCreative(), player.hasPermissions(2), player.getMainHandItem()
        );
    }

    public static boolean isElevated(Player player) {
        return player != null && (player.isCreative() || player.hasPermissions(2));
    }

    public static boolean shouldDamageHeldItem(Player player) {
        return canCut(player) && !isElevated(player);
    }

    static boolean isAuthorized(boolean creative, boolean permissionLevelTwo, ItemStack heldItem) {
        return creative || permissionLevelTwo || isSword(heldItem);
    }
}
