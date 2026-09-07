package com.seggellion.britannia_mod.bowlpreparation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Capacity-aware output delivery that does not use Creative's force-clear insertion fallback. */
public final class BowlPreparationOutput {
    private BowlPreparationOutput() {
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack output) {
        giveOrDrop(player, output, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    static void giveOrDrop(ServerPlayer player, ItemStack output, net.minecraft.world.InteractionHand preferredEmptyHand) {
        ItemStack remaining = output.copy();
        Inventory inventory = player.getInventory();
        // Do not call Inventory.add: its Creative fallback discards an uninsertable remainder.
        for (ItemStack slot : inventory.items) merge(inventory, slot, remaining);
        merge(inventory, player.getOffhandItem(), remaining);
        if (!remaining.isEmpty() && player.getItemInHand(preferredEmptyHand).isEmpty()) {
            int count = Math.min(remaining.getCount(), inventory.getMaxStackSize(remaining));
            player.setItemInHand(preferredEmptyHand, remaining.split(count));
        }
        for (int slot = 0; slot < inventory.items.size() && !remaining.isEmpty(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, remaining.split(Math.min(remaining.getCount(), inventory.getMaxStackSize(remaining))));
            }
        }
        if (!remaining.isEmpty()) player.drop(remaining, false);
        inventory.setChanged();
    }

    private static void merge(Inventory inventory, ItemStack target, ItemStack remaining) {
        if (remaining.isEmpty() || target.isEmpty() || !target.isStackable()
                || !ItemStack.isSameItemSameComponents(target, remaining)) return;
        int count = Math.min(remaining.getCount(), Math.max(0, inventory.getMaxStackSize(target) - target.getCount()));
        target.grow(count);
        remaining.shrink(count);
    }

    static boolean hasSufficientCapacity(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.isDamaged()) {
            return inventory.getFreeSlot() != -1;
        }

        int needed = stack.getCount();
        int perSlotCap = inventory.getMaxStackSize(stack);
        int available = 0;
        for (ItemStack slot : inventory.items) {
            if (slot.isEmpty()) {
                available += perSlotCap;
            } else if (slot.isStackable() && ItemStack.isSameItemSameComponents(slot, stack)) {
                available += Math.max(0, perSlotCap - slot.getCount());
            }
            if (available >= needed) {
                return true;
            }
        }

        // Inventory.add can top up a matching non-empty offhand stack, but never uses an empty
        // offhand slot as the destination for a newly inserted item.
        ItemStack offhand = inventory.offhand.getFirst();
        if (!offhand.isEmpty()
                && offhand.isStackable()
                && ItemStack.isSameItemSameComponents(offhand, stack)) {
            available += Math.max(0, perSlotCap - offhand.getCount());
        }
        return available >= needed;
    }
}
