package com.seggellion.britannia_mod.structure.placement;

import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** Classifies whether rollback still owns a target or must preserve an external replacement. */
public final class ShrineRollbackOwnership {
    public enum Action {
        RESTORE_ORIGINAL,
        ALREADY_ORIGINAL,
        PRESERVE_UNRELATED
    }

    private ShrineRollbackOwnership() {
    }

    public static Action classify(
            BlockState currentState, BlockState originalState, BlockState transactionState) {
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(originalState, "originalState");
        Objects.requireNonNull(transactionState, "transactionState");
        if (currentState.equals(originalState)) {
            return Action.ALREADY_ORIGINAL;
        }
        if (currentState.equals(transactionState)) {
            return Action.RESTORE_ORIGINAL;
        }
        return Action.PRESERVE_UNRELATED;
    }
}
