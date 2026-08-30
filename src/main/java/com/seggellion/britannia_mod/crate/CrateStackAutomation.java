package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a hopper touching one cell of a compact column may reach.
 *
 * <h2>Why the block answers and not the block entity</h2>
 *
 * <p>Only a column's root carries a block entity; its continuation cells carry nothing at all. A
 * hopper beside one of those cells would find no container, because {@code HopperBlockEntity} looks
 * for a block entity at the position it is touching. It asks something else first, though:
 * {@code getBlockContainer} checks whether the <em>block</em> is a {@code WorldlyContainerHolder} and
 * hands it the position - which is exactly the question a continuation cell needs to answer, and it
 * can, by looking down to its root.
 *
 * <h2>One crate at a time</h2>
 *
 * <p>A column is several crates a player sees separately, so exposing all of them to one hopper would
 * make automation behave in a way nobody could predict once the column repacked. The face decides
 * which one this hopper is talking to:
 *
 * <ul>
 *   <li><b>Top</b> - the crate at the top of the column, which is the one being poured into.</li>
 *   <li><b>Bottom</b> - the crate at the bottom, which is the one being drained.</li>
 *   <li><b>A side</b> - the crate with the most of itself inside the cell the hopper is touching,
 *       and the lower crate when two share it equally. A side hopper reaches what is physically in
 *       front of it.</li>
 * </ul>
 *
 * <p>The slots are the column's flattened, in order, but only the chosen crate's are ever offered:
 * the indices stay stable for the whole of one hopper operation, and everything else is invisible to
 * it rather than merely unreachable.
 */
public final class CrateStackAutomation implements WorldlyContainer {

    private static final int[] NOTHING = new int[0];

    /** Crates hold ordinary stacks; nothing about a column changes that. */
    private static final int DEFAULT_STACK_LIMIT = 64;

    private final CrateStackBlockEntity stack;

    /** Which cell of the column the hopper is touching, counted from the root. */
    private final int cell;

    /** The crates in order, taken once so an index cannot mean two things mid-operation. */
    private final List<LogicalCrate> order;

    private CrateStackAutomation(CrateStackBlockEntity stack, int cell) {
        this.stack = stack;
        this.cell = cell;
        this.order = List.copyOf(stack.crates());
    }

    /**
     * The view for a hopper touching this position, or null when there is no column behind it.
     *
     * @param cell how far above the column's root this position is
     */
    @Nullable
    public static CrateStackAutomation at(CrateStackBlockEntity stack, int cell) {
        return stack == null || stack.isEmpty() ? null : new CrateStackAutomation(stack, cell);
    }

    /* ─── which crate this face means ────────────────────────── */

    /**
     * The crate a hopper on this face is talking to.
     *
     * <p>Package-visible so the routing can be stated as a rule and tested as one, rather than only
     * observed through a hopper.
     */
    public int crateIndexFor(Direction face) {
        if (order.isEmpty()) {
            return -1;
        }
        return switch (face) {
            case UP -> order.size() - 1;
            case DOWN -> 0;
            default -> mostOfItselfIn(cell);
        };
    }

    /**
     * The crate with the greatest share of itself inside one cell.
     *
     * <p>Ties go to the lower crate. A side hopper has to mean exactly one crate and go on meaning it
     * as the column repacks, and "whichever is mostly there, and the lower one when it is a draw" is
     * the only rule that stays legible when a crate is removed from underneath.
     */
    private int mostOfItselfIn(int which) {
        CrateStackLayout layout = stack.layout();
        int floor = which * CrateStackLayout.CELL_HUNDREDTHS;
        int ceiling = floor + CrateStackLayout.CELL_HUNDREDTHS;
        int best = -1;
        int bestOverlap = 0;
        for (int index = 0; index < order.size(); index++) {
            CratePlacement placement = layout.placementOf(order.get(index).id());
            if (placement == null) {
                continue;
            }
            int overlap = Math.min(placement.topHundredths(), ceiling)
                    - Math.max(placement.baseHundredths(), floor);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                best = index;
            }
        }
        return best;
    }

    /* ─── the container ──────────────────────────────────────── */

    @Override
    public int[] getSlotsForFace(Direction face) {
        int chosen = crateIndexFor(face);
        if (chosen < 0) {
            return NOTHING;
        }
        int first = firstSlotOf(chosen);
        int size = order.get(chosen).variant().slotCount();
        int[] slots = new int[size];
        for (int slot = 0; slot < size; slot++) {
            slots[slot] = first + slot;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack item, @Nullable Direction face) {
        return face == null || offered(slot, face);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack item, Direction face) {
        return offered(slot, face);
    }

    /** Whether this slot is one of the ones this face was offered, and so may be touched through it. */
    private boolean offered(int slot, Direction face) {
        int chosen = crateIndexFor(face);
        if (chosen < 0) {
            return false;
        }
        int first = firstSlotOf(chosen);
        return slot >= first && slot < first + order.get(chosen).variant().slotCount();
    }

    private int firstSlotOf(int crateIndex) {
        int first = 0;
        for (int index = 0; index < crateIndex; index++) {
            first += order.get(index).variant().slotCount();
        }
        return first;
    }

    /** The crate owning a flattened slot, and that slot's index within it. */
    @Nullable
    private LogicalCrateContainer resolve(int slot) {
        int remaining = slot;
        for (LogicalCrate crate : order) {
            int size = crate.variant().slotCount();
            if (remaining < size) {
                return stack.containerFor(crate.id());
            }
            remaining -= size;
        }
        return null;
    }

    private int slotWithin(int slot) {
        int remaining = slot;
        for (LogicalCrate crate : order) {
            int size = crate.variant().slotCount();
            if (remaining < size) {
                return remaining;
            }
            remaining -= size;
        }
        return -1;
    }

    @Override
    public int getContainerSize() {
        int total = 0;
        for (LogicalCrate crate : order) {
            total += crate.variant().slotCount();
        }
        return total;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        LogicalCrateContainer crate = resolve(slot);
        return crate == null ? ItemStack.EMPTY : crate.getItem(slotWithin(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        LogicalCrateContainer crate = resolve(slot);
        return crate == null ? ItemStack.EMPTY : crate.removeItem(slotWithin(slot), count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        LogicalCrateContainer crate = resolve(slot);
        return crate == null ? ItemStack.EMPTY : crate.removeItemNoUpdate(slotWithin(slot));
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        LogicalCrateContainer crate = resolve(slot);
        if (crate != null) {
            crate.setItem(slotWithin(slot), item);
        }
    }

    @Override
    public int getMaxStackSize() {
        return DEFAULT_STACK_LIMIT;
    }

    @Override
    public void setChanged() {
        stack.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        for (LogicalCrate crate : order) {
            stack.containerFor(crate.id()).clearContent();
        }
    }
}
