package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One logical crate seen as an ordinary {@link Container}.
 *
 * <h2>Why this resolves by id on every call</h2>
 *
 * <p>This view holds a crate id, never a list index and never the {@link LogicalCrate} itself. A
 * player with a crate open is holding one of these, and while it is open a crate below them can be
 * removed — which slides this crate down the column and changes its index. Resolving the id each time
 * means the view keeps pointing at the same inventory through that, and stops resolving entirely once
 * the crate itself is gone, rather than quietly addressing whichever crate inherited the index.
 */
public final class LogicalCrateContainer implements Container {

    private final CrateStackBlockEntity stack;
    private final int crateId;

    public LogicalCrateContainer(CrateStackBlockEntity stack, int crateId) {
        this.stack = Objects.requireNonNull(stack, "stack");
        this.crateId = crateId;
    }

    public int crateId() {
        return crateId;
    }

    /** The crate this view addresses, or null once it has been removed from the column. */
    private LogicalCrate crate() {
        return stack.crateById(crateId);
    }

    private NonNullList<ItemStack> items() {
        LogicalCrate crate = crate();
        return crate == null ? null : crate.items();
    }

    @Override
    public int getContainerSize() {
        LogicalCrate crate = crate();
        // Zero once the crate is gone: a menu asking a departed container for its size gets an honest
        // answer rather than an index into somebody else's inventory.
        return crate == null ? 0 : crate.slotCount();
    }

    @Override
    public boolean isEmpty() {
        LogicalCrate crate = crate();
        return crate == null || crate.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        NonNullList<ItemStack> items = items();
        return items == null || slot < 0 || slot >= items.size() ? ItemStack.EMPTY : items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        NonNullList<ItemStack> items = items();
        if (items == null || slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        NonNullList<ItemStack> items = items();
        if (items == null || slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stackToSet) {
        NonNullList<ItemStack> items = items();
        if (items == null || slot < 0 || slot >= items.size()) {
            return;
        }
        items.set(slot, stackToSet);
        stackToSet.limitSize(getMaxStackSize(stackToSet));
        setChanged();
    }

    @Override
    public void clearContent() {
        NonNullList<ItemStack> items = items();
        if (items != null) {
            items.clear();
            setChanged();
        }
    }

    @Override
    public void setChanged() {
        stack.setChanged();
    }

    /**
     * Whether this view may still be used.
     *
     * <p>Survives a neighbouring crate being removed and survives this crate being repacked to a new
     * height. Fails once the block entity is removed, once the world no longer has that block entity
     * at that position, once this particular crate is gone, and once the player has walked away.
     */
    @Override
    public boolean stillValid(Player player) {
        return stack.isValidContainerFor(crateId, player);
    }

    /**
     * Counts this player against this crate, and sounds only if the crate was shut.
     *
     * <p>Per crate, not per column: a column is one block entity, so a single opener count would make
     * opening any crate report every crate in it as in use — which decides both the chest sounds and
     * whether Grabby Hands will carry away a crate somebody else is looking inside.
     */
    @Override
    public void startOpen(Player player) {
        LogicalCrate crate = crate();
        if (crate == null || player.isSpectator()) {
            return;
        }
        boolean wasShut = crate.openerCount() == 0;
        crate.incrementOpeners();
        if (wasShut) {
            stack.playCrateSound(crateId, ModSounds.CHEST_OPEN.value());
        }
    }

    @Override
    public void stopOpen(Player player) {
        LogicalCrate crate = crate();
        if (crate == null || player.isSpectator()) {
            return;
        }
        crate.decrementOpeners();
        if (crate.openerCount() == 0) {
            stack.playCrateSound(crateId, ModSounds.CHEST_CLOSE.value());
        }
    }
}
