package com.seggellion.britannia_mod.bowlpreparation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Capacity-aware output delivery that does not use Creative's force-clear insertion fallback. */
final class BowlPreparationOutput {
    private BowlPreparationOutput() {
    }

    static void giveOrDrop(ServerPlayer player, ItemStack output) {
        if (output.isEmpty()) {
            return;
        }
        if (!hasSufficientCapacity(player.getInventory(), output)) {
            player.drop(output.copy(), false);
            return;
        }

        ItemStack remainder = output.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            // Structurally unreachable after the capacity check on the server thread, but this
            // preserves the exact output if a later Inventory implementation changes its rules.
            player.drop(remainder, false);
        }
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
