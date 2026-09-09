package com.seggellion.britannia_mod.quest.handin;

/**
 * Where one hand-in transaction has got to on this server.
 *
 * <p>The line that matters runs between {@link #REMOVAL_INTENT} and {@link #REMOVED_LOCAL}. Above
 * it nothing has left the player's pack, so abandoning the transaction costs nothing. At or below
 * it the player has given something up, and the protocol now owes them exactly one of two endings:
 * the quest completes, or the items come back through the reward-delivery ledger. A row at or past
 * {@link #REMOVED_LOCAL} is therefore never dropped, never re-removed, and never closed on an
 * answer that proves neither.
 *
 * <p>Persisted by {@code name()}, never by ordinal, so a state added later cannot silently
 * re-interpret an existing ledger the way an inserted enum constant would.
 */
public enum QuestHandinLocalState {

    /** Rails prepared the transaction. Nothing has been resolved, counted or taken. */
    PREPARED,

    /**
     * The requirements resolved, the carried inventory covers them, and this server is about to
     * mutate. Durable <b>before</b> the mutation so a crash inside it leaves a record naming the
     * transaction that was in flight -- but an intent alone never entitles anyone to anything, so
     * finding one on restart is safe whichever side of the mutation the crash landed.
     */
    REMOVAL_INTENT,

    /**
     * The items are gone from the player's own saved file and the concrete proof is durable. From
     * here the only correct action is to confirm with that proof, however many times it takes.
     */
    REMOVED_LOCAL,

    /** A confirmation carrying the stored proof is in flight. Recovered exactly like the state above. */
    CONFIRMING,

    /** Rails finalized the transition and this server applied the completion. */
    CONSUMED,

    /** Rails could not finalize and published the refund as an ordinary pending reward delivery. */
    REFUNDED,

    /**
     * Finished with nothing taken: the player never had the items, or the transaction went away
     * before the mutation. Safe to prune.
     */
    ABANDONED,

    /**
     * Items were removed and Rails' answer proves neither a completion nor a refund -- it does not
     * hold this transaction at all.
     *
     * <p>Deliberately not a way of closing the row. It is preserved, logged and never pruned,
     * because the alternative is deleting the only surviving evidence that a player gave something
     * up for nothing. Nothing automatic acts on it; an operator does.
     */
    STRANDED;

    /** Whether the player has durably given the items up in this state. */
    public boolean itemsRemoved() {
        return this == REMOVED_LOCAL || this == CONFIRMING || this == CONSUMED
                || this == REFUNDED || this == STRANDED;
    }

    /** Whether this server still owes Rails a confirmation carrying the stored proof. */
    public boolean confirmationOutstanding() {
        return this == REMOVED_LOCAL || this == CONFIRMING;
    }

    /** Whether the transaction is finished and needs no further work. */
    public boolean settled() {
        return this == CONSUMED || this == REFUNDED || this == ABANDONED;
    }

    /** Whether the row may be evicted to keep the ledger bounded. Never a stranded one. */
    public boolean prunable() {
        return settled();
    }
}
