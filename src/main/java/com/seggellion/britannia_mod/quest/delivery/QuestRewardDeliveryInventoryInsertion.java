package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * All-or-nothing insertion into the main inventory (protocol section 1.8, step 3): either every
 * stack of the delivery fits and all of them are placed, or none is touched and the caller
 * queues the delivery. Nothing is ever dropped on the ground.
 *
 * <p>Deliberately not {@link Inventory#add(ItemStack)}: that method inserts what fits and leaves
 * the remainder behind (a partial grant is exactly what must never happen), and for a player with
 * infinite materials -- creative, which every GameTest mock player is -- it reports success while
 * discarding a stack that did not fit. The plan below follows vanilla's slot rules (top up
 * matching stackable stacks up to {@code Container#getMaxStackSize(ItemStack)}, then empty
 * slots) over a scratch copy of the 36 main slots and commits the copy only when everything fit.
 */
public final class QuestRewardDeliveryInventoryInsertion {
    private QuestRewardDeliveryInventoryInsertion() {}

    /**
     * @param slots        the main inventory slots (never mutated)
     * @param maxStackSize the container's stack ceiling for a stack
     * @param stacks       the stacks to place, each already at most one stack's worth
     * @return the slots after placement, or null when the stacks do not all fit
     */
    public static List<ItemStack> plan(List<ItemStack> slots, ToIntFunction<ItemStack> maxStackSize,
                                       List<ItemStack> stacks) {
        List<ItemStack> scratch = new ArrayList<>(slots.size());
        for (ItemStack slot : slots) scratch.add(slot.copy());

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int remaining = stack.getCount();

            if (stack.isStackable()) {
                for (int index = 0; index < scratch.size() && remaining > 0; index++) {
                    ItemStack occupant = scratch.get(index);
                    if (occupant.isEmpty() || !occupant.isStackable()) continue;
                    if (!ItemStack.isSameItemSameComponents(occupant, stack)) continue;
                    int room = maxStackSize.applyAsInt(occupant) - occupant.getCount();
                    if (room <= 0) continue;
                    int moved = Math.min(room, remaining);
                    occupant.grow(moved);
                    remaining -= moved;
                }
            }
            for (int index = 0; index < scratch.size() && remaining > 0; index++) {
                if (!scratch.get(index).isEmpty()) continue;
                int moved = Math.min(maxStackSize.applyAsInt(stack), remaining);
                scratch.set(index, stack.copyWithCount(moved));
                remaining -= moved;
            }
            if (remaining > 0) return null;
        }
        return scratch;
    }

    /**
     * Places every stack or none. Returns false, with the inventory untouched, when they do not
     * all fit. The caller broadcasts the container changes.
     */
    public static boolean insertAll(Inventory inventory, List<ItemStack> stacks) {
        NonNullList<ItemStack> slots = inventory.items;
        List<ItemStack> planned = plan(slots, inventory::getMaxStackSize, stacks);
        if (planned == null) return false;
        for (int index = 0; index < slots.size(); index++) {
            ItemStack before = slots.get(index);
            ItemStack after = planned.get(index);
            if (before.getCount() == after.getCount() && ItemStack.isSameItemSameComponents(before, after)) continue;
            after.setPopTime(5);
            slots.set(index, after);
        }
        return true;
    }

    /** Whether the stacks would all fit right now, without touching anything. */
    public static boolean fits(Inventory inventory, List<ItemStack> stacks) {
        return plan(inventory.items, inventory::getMaxStackSize, stacks) != null;
    }
}
