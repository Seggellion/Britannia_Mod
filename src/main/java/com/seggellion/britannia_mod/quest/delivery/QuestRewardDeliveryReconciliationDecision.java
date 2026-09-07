package com.seggellion.britannia_mod.quest.delivery;

import javax.annotation.Nullable;

/**
 * The restart-reconciliation table of protocol section 1.8, as a pure function so it can be
 * tested without a server: what to do with a delivery given the ledger's state for it and whether
 * the player's persistent marker already names it.
 *
 * <p>The marker is written in the same in-memory player state as the inserted items, so the
 * vanilla player-file write persists both or neither: a present marker is proof the items were
 * persisted, an absent one is proof they were not (or never inserted). The ledger, flushed
 * separately, can therefore lag the player file in exactly one direction -- it may say
 * {@code pending_local} or {@code queued} for an insertion whose player file already landed --
 * and that is the case the table repairs without a second insertion.
 */
public final class QuestRewardDeliveryReconciliationDecision {
    private QuestRewardDeliveryReconciliationDecision() {}

    public enum Action {
        /** No row yet: record {@code pending_local}, then insert. */
        RECORD_AND_INSERT,
        /** The row exists and the items are still owed: insert (again). */
        INSERT,
        /** The marker proves the items landed: repair the row to {@code applied}, then acknowledge. */
        REPAIR_APPLIED_THEN_ACKNOWLEDGE,
        /** The marker proves the items landed but the ledger lost the row: record it as applied, then acknowledge. */
        RECORD_APPLIED_THEN_ACKNOWLEDGE,
        /** Items granted, Rails not yet told. */
        ACKNOWLEDGE_ONLY,
        /** Nothing left to do. */
        NOTHING
    }

    public static Action decide(@Nullable QuestRewardDeliveryLedgerEntry entry, boolean markerPresent) {
        if (entry == null) {
            return markerPresent ? Action.RECORD_APPLIED_THEN_ACKNOWLEDGE : Action.RECORD_AND_INSERT;
        }
        return switch (entry.localState()) {
            case PENDING_LOCAL, QUEUED -> markerPresent
                ? Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE
                : Action.INSERT;
            case APPLIED -> entry.acknowledgementOutstanding() ? Action.ACKNOWLEDGE_ONLY : Action.NOTHING;
            case ACKNOWLEDGED -> Action.NOTHING;
        };
    }
}
