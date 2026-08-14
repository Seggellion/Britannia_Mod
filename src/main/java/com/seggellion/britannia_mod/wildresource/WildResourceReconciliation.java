package com.seggellion.britannia_mod.wildresource;

import net.minecraft.world.level.ChunkPos;

/** Pure persisted-ledger transition used by loaded-chunk world reconciliation. */
public final class WildResourceReconciliation {
    private WildResourceReconciliation() {
    }

    public static Outcome reconcile(
            WildResourceSavedData data,
            WildResourceNode node,
            WildResourceEntry entry,
            WildResourceEntry.ExistingNodeState state,
            long nextAttempt
    ) {
        if (entry == null) {
            data.removeNode(node.position());
            return Outcome.UNKNOWN_REMOVED;
        }
        if (state == WildResourceEntry.ExistingNodeState.VALID) {
            return Outcome.PRESERVED;
        }
        if (state == WildResourceEntry.ExistingNodeState.DEFER) {
            return Outcome.DEFERRED;
        }
        data.removeNode(node.position());
        data.scheduleAttempt(new ChunkPos(node.position()), entry.id(), nextAttempt);
        return state == WildResourceEntry.ExistingNodeState.OWNED_INVALID
                ? Outcome.REMOVE_OWNED_BLOCK
                : Outcome.LEDGER_REMOVED;
    }

    public enum Outcome {
        PRESERVED,
        DEFERRED,
        REMOVE_OWNED_BLOCK,
        LEDGER_REMOVED,
        UNKNOWN_REMOVED
    }
}
