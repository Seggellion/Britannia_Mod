package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Counting and removing exactly what a hand-in asked for, from carried inventory only (protocol
 * section 1.5.1).
 *
 * <h2>What "carried" means, exhaustively</h2>
 * The 36 main and hotbar slots, and the off-hand. Not armour, not the ender chest, not an open
 * container, not a house or bank, not a dropped entity, not a nearby inventory. That list is short
 * because it is a rule and not an optimisation: a hand-in the player can satisfy from a chest three
 * rooms away is one they did not knowingly give up.
 *
 * <h2>All or nothing</h2>
 * {@link #plan} counts the whole requirement set without touching a stack. Only a plan that covers
 * every requirement reaches {@link #removeAll}, so a player short by one unit loses nothing at all.
 * Requirements are consumed in order against a scratch tally, which is what makes two requirements
 * naming the same item behave correctly: the second sees what the first would leave, and each keeps
 * its own proof entry rather than being merged into one Rails would refuse.
 *
 * <p>Stacks are matched on item identity alone. A requirement names an item and a count, so a
 * renamed or damaged stack of the same item is the same item; components are deliberately not part
 * of the test, because nothing in the contract lets an author ask for one.
 */
public final class QuestHandinInventory {

    /** Main inventory and hotbar. Vanilla indexes these 0..35 on {@link Inventory}. */
    public static final int MAIN_SLOT_COUNT = 36;

    /** {@link Inventory}'s off-hand slot index. */
    public static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

    private QuestHandinInventory() {}

    /** One stack's contribution to one requirement. Indices address the carried view, not the player. */
    public record SlotTake(int carriedIndex, int requirementIndex, int count) {}

    public sealed interface Outcome permits Planned, Insufficient {}

    /** Everything is present; these takes remove exactly the requirement set and nothing else. */
    public record Planned(List<SlotTake> takes) implements Outcome {
        public Planned {
            takes = List.copyOf(takes);
        }
    }

    /** The player is short. Nothing was touched, and this is what they still owe. */
    public record Insufficient(List<QuestItemHandinProtocol.MissingItem> missing) implements Outcome {
        public Insufficient {
            missing = List.copyOf(missing);
        }
    }

    /**
     * A snapshot of the carried slots in the order this contract reads them: main inventory and
     * hotbar, then the off-hand.
     *
     * <p>Copies, not references, and that is load-bearing twice over. {@link #removeAll} compares
     * the live stack against this snapshot to prove nothing moved -- a comparison that is vacuously
     * true if both sides are the same object. And {@link #restoreAll} rebuilds an emptied slot from
     * it, which it could not do if shrinking the live stack had emptied the snapshot too.
     */
    public static List<ItemStack> carried(Inventory inventory) {
        List<ItemStack> snapshot = new ArrayList<>(MAIN_SLOT_COUNT + 1);
        for (int slot = 0; slot < MAIN_SLOT_COUNT; slot++) {
            snapshot.add(inventory.getItem(slot).copy());
        }
        snapshot.add(inventory.getItem(OFFHAND_SLOT).copy());
        return snapshot;
    }

    /** Maps a carried-view index back to the real {@link Inventory} slot. */
    public static int inventorySlot(int carriedIndex) {
        return carriedIndex < MAIN_SLOT_COUNT ? carriedIndex : OFFHAND_SLOT;
    }

    /**
     * Plans the removal without mutating anything.
     *
     * @param carried the carried slots, in {@link #carried} order
     * @param plan    the resolved requirements, one entry per requirement, in requirement order
     */
    public static Outcome plan(List<ItemStack> carried, List<QuestHandinRemoval> plan) {
        int[] available = new int[carried.size()];
        for (int index = 0; index < carried.size(); index++) {
            ItemStack stack = carried.get(index);
            available[index] = stack == null || stack.isEmpty() ? 0 : stack.getCount();
        }

        List<SlotTake> takes = new ArrayList<>();
        Map<String, Integer> shortfall = new LinkedHashMap<>();
        for (QuestHandinRemoval removal : plan) {
            Optional<Item> wanted = QuestHandinRequirementResolver.item(removal);
            if (wanted.isEmpty()) {
                // An id that resolved at planning time but names nothing now. Treated as a
                // shortfall rather than a crash: the player is told what is missing, and no stack
                // has been touched.
                shortfall.merge(removal.itemId(), removal.count(), Integer::sum);
                continue;
            }
            int owed = removal.count();
            for (int index = 0; index < carried.size() && owed > 0; index++) {
                if (available[index] <= 0) continue;
                ItemStack stack = carried.get(index);
                if (stack == null || stack.isEmpty() || stack.getItem() != wanted.get()) continue;
                int taken = Math.min(available[index], owed);
                available[index] -= taken;
                owed -= taken;
                takes.add(new SlotTake(index, removal.requirementIndex(), taken));
            }
            if (owed > 0) shortfall.merge(removal.itemId(), owed, Integer::sum);
        }

        if (shortfall.isEmpty()) return new Planned(takes);
        // Only the shortfall is reported, never the whole requirement: a player who already has
        // some of what was asked for is told what is left to find, not asked to start again.
        List<QuestItemHandinProtocol.MissingItem> missing = new ArrayList<>(shortfall.size());
        shortfall.forEach((itemId, count) ->
                missing.add(new QuestItemHandinProtocol.MissingItem(itemId, count)));
        return new Insufficient(missing);
    }

    /**
     * Commits a plan, re-verifying every stack immediately beforehand.
     *
     * <p>The re-verification is not paranoia about this method's own callers -- it runs on the
     * server thread with no yield between the check and the shrink -- it is about the gap between
     * the plan and the commit, which in this flow spans a Rails round trip in the general case. If
     * anything moved, nothing is removed.
     *
     * @return true when every take was applied; false with the inventory untouched otherwise
     */
    public static boolean removeAll(Inventory inventory, List<ItemStack> carried, List<SlotTake> takes) {
        int[] needed = new int[carried.size()];
        for (SlotTake take : takes) needed[take.carriedIndex()] += take.count();
        for (int index = 0; index < needed.length; index++) {
            if (needed[index] == 0) continue;
            ItemStack live = inventory.getItem(inventorySlot(index));
            ItemStack planned = carried.get(index);
            if (live.isEmpty() || planned == null || planned.isEmpty()) return false;
            if (live.getItem() != planned.getItem()) return false;
            if (live.getCount() < needed[index]) return false;
        }
        for (int index = 0; index < needed.length; index++) {
            if (needed[index] == 0) continue;
            inventory.getItem(inventorySlot(index)).shrink(needed[index]);
        }
        return true;
    }

    /**
     * Puts back exactly what {@link #removeAll} took, for the one caller that must undo it: a
     * forced player save this server could not confirm.
     *
     * <p>Rebuilds an emptied slot rather than growing it. {@code ItemStack#shrink} to zero leaves a
     * stack that reports itself empty and cannot be grown back, so a naive {@code grow} would
     * silently restore nothing -- the same trap the coin sweep's own restore documents.
     */
    public static void restoreAll(Inventory inventory, List<ItemStack> carried, List<SlotTake> takes) {
        int[] taken = new int[carried.size()];
        for (SlotTake take : takes) taken[take.carriedIndex()] += take.count();
        for (int index = 0; index < taken.length; index++) {
            if (taken[index] == 0) continue;
            int slot = inventorySlot(index);
            ItemStack live = inventory.getItem(slot);
            if (live.isEmpty()) {
                ItemStack planned = carried.get(index);
                if (planned == null || planned.isEmpty()) continue;
                inventory.setItem(slot, planned.copyWithCount(taken[index]));
            } else {
                live.grow(taken[index]);
            }
        }
    }
}
