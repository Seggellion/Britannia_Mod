package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What one pickup attempt did.
 *
 * <p>The counts are the point. "Exactly once" is an invariant that has to be assertable, not merely
 * intended, so the transaction reports how many world objects it removed and how many inventory
 * items it created rather than leaving tests to infer it.
 */
public record GrabbyPickupResult(
        GrabbyPickupOutcome outcome,
        BlockPos root,
        ItemStack portable,
        int worldObjectsRemoved,
        int inventoryItemsCreated,
        boolean grabSoundPlayed,
        boolean stowSoundPlayed
) {
    public GrabbyPickupResult {
        Objects.requireNonNull(outcome, "outcome");
        portable = portable == null ? ItemStack.EMPTY : portable;
        if (worldObjectsRemoved < 0 || inventoryItemsCreated < 0) {
            throw new IllegalArgumentException("Pickup counts cannot be negative");
        }
        if (worldObjectsRemoved > 1 || inventoryItemsCreated > 1) {
            throw new IllegalArgumentException("A pickup transaction affects at most one object");
        }
        if (stowSoundPlayed && !grabSoundPlayed) {
            throw new IllegalArgumentException("The stow cue cannot precede the grab cue");
        }
    }

    static GrabbyPickupResult refused(GrabbyPickupOutcome outcome, BlockPos root) {
        return new GrabbyPickupResult(outcome, root, ItemStack.EMPTY, 0, 0, false, false);
    }

    public boolean succeeded() {
        return outcome == GrabbyPickupOutcome.SUCCESS;
    }
}
