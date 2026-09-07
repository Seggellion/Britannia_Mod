package com.seggellion.britannia_mod.quest.delivery;

/**
 * The Minecraft-side state of one delivery in the durable ledger (protocol section 1.8):
 * {@code pending_local -> applied -> acknowledged}, or {@code queued -> applied} when the
 * inventory was full. Persisted by NAME, never ordinal, so a value may be added later without
 * shifting the meaning of anything already on disk.
 *
 * <p>Note what {@link #QUEUED} does and does not say: the items are owed and sit in the ledger
 * entry, and Rails may already have acknowledged the delivery as {@code queued} (that is recorded
 * beside the state, in {@code acknowledgedOutcome}); the state only moves on once the items are
 * in the inventory. {@link #ACKNOWLEDGED} is terminal: the items are in the inventory, the
 * player marker names the delivery, and Rails has recorded {@code applied} -- or answered with an
 * error no retry could change, which the entry records as its acknowledgement error.
 */
public enum QuestRewardDeliveryLocalState {
    PENDING_LOCAL("pending_local"),
    QUEUED("queued"),
    APPLIED("applied"),
    ACKNOWLEDGED("acknowledged");

    private final String wireName;

    QuestRewardDeliveryLocalState(String wireName) {
        this.wireName = wireName;
    }

    /** The lower-case spelling the protocol document and the log lines use. */
    public String wireName() {
        return wireName;
    }
}
