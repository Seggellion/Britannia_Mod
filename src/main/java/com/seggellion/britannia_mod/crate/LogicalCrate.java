package com.seggellion.britannia_mod.crate;

import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * One crate a player can see and open, inside a column that is a single block entity.
 *
 * <h2>Identity</h2>
 *
 * <p>{@link #id()} is assigned once by the owning stack and never changes again — not when a crate
 * below is removed, not when the column repacks, not across a save. Position cannot serve as
 * identity here: removing a crate slides everything above it down, so a menu, a packet or a Grabby
 * target that remembered "the second crate" would silently retarget a different inventory. Every
 * outward reference to a crate is therefore an id.
 *
 * <p>The item list is this crate's own storage and is never shared with, copied into, or swapped
 * between crates. Repacking recomputes geometry only; it does not touch this object at all.
 */
public final class LogicalCrate {

    private final int id;
    private final CrateVariant variant;
    private final NonNullList<ItemStack> items;
    private final Direction facing;
    private int openerCount;

    public LogicalCrate(int id, CrateVariant variant, Direction facing) {
        this(id, variant, facing, NonNullList.withSize(
                Objects.requireNonNull(variant, "variant").slotCount(), ItemStack.EMPTY));
    }

    /**
     * @param items adopted as this crate's storage, not copied, so a caller must not retain it
     */
    public LogicalCrate(int id, CrateVariant variant, Direction facing, NonNullList<ItemStack> items) {
        this.id = id;
        this.variant = Objects.requireNonNull(variant, "variant");
        this.facing = requireHorizontal(facing);
        this.items = Objects.requireNonNull(items, "items");
        if (items.size() != variant.slotCount()) {
            throw new IllegalArgumentException(
                    "A " + variant.serializedName() + " crate holds " + variant.slotCount()
                            + " slots, not " + items.size());
        }
    }

    public int id() {
        return id;
    }

    public CrateVariant variant() {
        return variant;
    }

    public int heightHundredths() {
        return variant.heightHundredths();
    }

    /**
     * Which way this crate is drawn.
     *
     * <p>Kept per crate rather than per column even though ordinary placement gives a whole column
     * one facing: a crate carries its own facing through promotion from a legacy block, and a column
     * assembled by anything other than the placement rule would otherwise have to invent one.
     */
    public Direction facing() {
        return facing;
    }


    /** This crate's storage. Package-private so only its own container view can reach it. */
    NonNullList<ItemStack> items() {
        return items;
    }

    /**
     * Writes this crate's contents into {@code tag}.
     *
     * <p>The crate saves its own storage rather than handing the list out, so the only code that can
     * reach these stacks is the container view that addresses this one crate. Nothing outside can
     * take the list and hand it to another crate, which is the mistake this whole design has to make
     * unavailable.
     */
    public void saveItemsTo(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    /**
     * Hands this crate's storage out, once, and leaves it empty.
     *
     * <p>Only for a crate that has already been removed from its column. The stacks are moved rather
     * than copied, so from this point exactly one place holds them and nothing can drop them twice;
     * clearing is the half that makes that true.
     */
    public NonNullList<ItemStack> takeContents() {
        NonNullList<ItemStack> taken = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            taken.set(slot, items.get(slot));
        }
        items.clear();
        return taken;
    }

    public int slotCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * How many players currently have this crate open.
     *
     * <p>Deliberately per crate. A column is one block entity, so a single opener count would make
     * opening any crate mark every crate in the column as in use — which later decides chest sounds,
     * whether Grabby Hands will carry a crate, and what simultaneous access looks like. Not saved:
     * vanilla containers recount openers on load, and a count restored from disk is a count that
     * outlives the players it described.
     */
    /**
     * Whether anything in here is itself a container carrying something.
     *
     * <p>Asked rather than answered by reading the items, because the item list is deliberately not
     * exposed: everything that changes a crate goes through the crate. The rule itself is the one an
     * ordinary crate applies - a full container does not go inside another one - and being stored in a
     * column changes nothing about it.
     */
    public boolean holdsNestedContents() {
        for (net.minecraft.world.item.ItemStack held : items) {
            if (!held.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int openerCount() {
        return openerCount;
    }

    public void incrementOpeners() {
        openerCount++;
    }

    public void decrementOpeners() {
        openerCount = Math.max(0, openerCount - 1);
    }

    private static Direction requireHorizontal(Direction facing) {
        Objects.requireNonNull(facing, "facing");
        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("A crate faces horizontally, not " + facing);
        }
        return facing;
    }

    @Override
    public String toString() {
        return "LogicalCrate[id=" + id + ", " + variant.serializedName() + ", " + facing + "]";
    }
}
