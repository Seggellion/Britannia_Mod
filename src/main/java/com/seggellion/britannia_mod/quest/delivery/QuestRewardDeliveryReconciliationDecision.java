package com.seggellion.britannia_mod.quest.delivery;

import com.seggellion.britannia_mod.player.PlayerDataStore;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * The restart-reconciliation table of protocol section 1.8, as a pure function so it can be
 * tested without a server: what to do with a delivery given the ledger's state for it and whether
 * the player's persistent marker already names it.
 *
 * <p>EITHER DURABLE RECORD PROVES THE ITEMS LANDED, and the ABSENCE of a record proves nothing on
 * its own. Both the marker and the permanent proof are written in the same in-memory player state
 * as the inserted items, so the vanilla player-file write persists them with the items or not at
 * all: whichever one names the uuid, the items were persisted. The converse does not hold for the
 * marker, which is bounded per player and may be absent after eviction for a delivery that was
 * genuinely granted.
 *
 * <p>THE TWO PLAYER-SIDE RECORDS ARE CONSULTED ONLY WHERE THE LEDGER HAS NOT ALREADY SETTLED IT.
 * They decide the answer when the row is absent, {@code pending_local} or {@code queued}; for an
 * {@code applied} or {@code acknowledged} row the ledger is authoritative and neither boolean is
 * read at all, so the absence of both is NOT a universal "the items are still owed". The ledger,
 * flushed separately, can lag the player file in exactly one direction -- it may say
 * {@code pending_local} or {@code queued} for an insertion whose player file already landed -- and
 * that is the case the table repairs without a second insertion.
 *
 * <p>D-0102 ADDED THE THIRD INPUT, and it is the only one that is permanent. The ledger row and
 * the marker are both bounded per player, so for an old uuid BOTH can be absent while the items
 * were in fact granted - and Rails can re-hand that uuid indefinitely, because
 * {@code Publish#find_existing} has no state filter. Read with two inputs this table answered
 * {@code RECORD_AND_INSERT} for that case and granted the items twice.
 * {@code PlayerDataStore.APPLIED_DELIVERY_PROOF} never evicts, so when it names the uuid the
 * answer is never an insertion, whatever the two bounded records say.
 */
public final class QuestRewardDeliveryReconciliationDecision {
    private QuestRewardDeliveryReconciliationDecision() {}

    public enum Action {
        /** No row yet: record {@code pending_local}, then insert. */
        RECORD_AND_INSERT,
        /** The row exists and the items are still owed: insert (again). */
        INSERT,
        /** A durable record proves the items landed: repair the row to {@code applied}, then acknowledge. */
        REPAIR_APPLIED_THEN_ACKNOWLEDGE,
        /** A durable record proves the items landed but the ledger lost the row: record it as applied, then acknowledge. */
        RECORD_APPLIED_THEN_ACKNOWLEDGE,
        /** Items granted, Rails not yet told. */
        ACKNOWLEDGE_ONLY,
        /** Nothing left to do. */
        NOTHING
    }

    /**
     * The form production uses: it reads BOTH durable records from the player's own mod data, so a
     * caller cannot consult one and forget the other.
     *
     * <p>READ-ONLY, like the rest of this class. An earlier version migrated pre-D-0102 markers here,
     * which made the decision path write to the player NBT and contradicted both this class's own
     * documentation and the task's acceptance criterion. Migration is a persistence step and belongs
     * to the caller: {@code QuestRewardDeliveryService} runs
     * {@code PlayerDataStore.migrateLegacyMarkersIn} before it decides, while the legacy marker is
     * still present to migrate.
     *
     * <p>The two-boolean form below stays for the table tests, which enumerate states directly.
     */
    public static Action decideForPlayerData(@Nullable QuestRewardDeliveryLedgerEntry entry,
                                             CompoundTag playerModData, UUID deliveryUuid) {
        boolean marker = PlayerDataStore.hasAppliedDeliveryIn(playerModData, deliveryUuid);
        boolean proof = PlayerDataStore.hasDeliveryProofIn(playerModData, deliveryUuid);
        return decide(entry, marker, proof);
    }

    public static Action decide(@Nullable QuestRewardDeliveryLedgerEntry entry, boolean markerPresent,
                               boolean proofPresent) {
        // Either durable record proves the items landed. The proof is the one that is still there
        // after the ledger row was pruned and the marker evicted, which is the whole point of it.
        boolean granted = markerPresent || proofPresent;
        if (entry == null) {
            return granted ? Action.RECORD_APPLIED_THEN_ACKNOWLEDGE : Action.RECORD_AND_INSERT;
        }
        return switch (entry.localState()) {
            case PENDING_LOCAL, QUEUED -> granted
                ? Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE
                : Action.INSERT;
            case APPLIED -> entry.acknowledgementOutstanding() ? Action.ACKNOWLEDGE_ONLY : Action.NOTHING;
            case ACKNOWLEDGED -> Action.NOTHING;
        };
    }
}
