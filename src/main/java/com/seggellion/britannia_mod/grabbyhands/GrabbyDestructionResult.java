package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * What one destruction attempt did.
 *
 * <p>The counts exist so "exactly once" is assertable rather than merely intended.
 */
public record GrabbyDestructionResult(
        GrabbyDestructionOutcome outcome,
        BlockPos position,
        int objectsDestroyed,
        boolean effectPlayed,
        boolean toolDamaged
) {
    public GrabbyDestructionResult {
        Objects.requireNonNull(outcome, "outcome");
        if (objectsDestroyed < 0 || objectsDestroyed > 1) {
            throw new IllegalArgumentException("A destruction transaction affects at most one object");
        }
        if (outcome.consumedObject() != (objectsDestroyed == 1)) {
            throw new IllegalArgumentException(
                    "Outcome " + outcome + " disagrees with a destroyed count of " + objectsDestroyed);
        }
        if (effectPlayed && objectsDestroyed == 0) {
            throw new IllegalArgumentException("Destruction feedback must not fire without a destruction");
        }
    }

    static GrabbyDestructionResult refused(GrabbyDestructionOutcome outcome, BlockPos position) {
        return new GrabbyDestructionResult(outcome, position, 0, false, false);
    }

    public boolean succeeded() {
        return outcome == GrabbyDestructionOutcome.SUCCESS;
    }
}
