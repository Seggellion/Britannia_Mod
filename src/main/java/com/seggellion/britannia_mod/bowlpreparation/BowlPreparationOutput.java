package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
            // M8 item 8: the drop itself is unchanged; it just no longer happens in silence. The
            // quest reward path already had a "waiting for room in your pack" line and this had
            // nothing, so a full pack looked like the recipe had eaten the output.
            announceDrop(player, output);
            player.drop(output.copy(), false);
            return;
        }

        ItemStack remainder = output.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            // Structurally unreachable after the capacity check on the server thread, but this
            // preserves the exact output if a later Inventory implementation changes its rules.
            announceDrop(player, remainder);
            player.drop(remainder, false);
        }
    }

    /** Names the item that fell, because "your pack is full" alone does not say what was lost. */
    private static void announceDrop(ServerPlayer player, ItemStack output) {
        player.displayClientMessage(
                Component.translatable(QuestScreenText.INVENTORY_FULL_DROPPED, output.getHoverName())
                        .withStyle(ChatFormatting.YELLOW), true);
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
