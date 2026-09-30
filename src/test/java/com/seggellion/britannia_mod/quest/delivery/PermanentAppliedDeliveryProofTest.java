package com.seggellion.britannia_mod.quest.delivery;

import com.seggellion.britannia_mod.player.PlayerDataStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciliationDecision.Action;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-0102: a delivery whose items were granted can never be granted again, however long ago it was.
 *
 * <p>The defect this proves closed. Rails can re-hand an acknowledged {@code delivery_uuid}
 * indefinitely - {@code QuestRewardDeliveries::Publish#find_existing} is a bare {@code find_by} with
 * no state filter, and {@code publish.rb} records having "handed the shard a delivery_uuid its ledger
 * had already applied". The shard's two ORIGINAL records are both bounded per player at 256: the
 * ledger prunes acknowledged rows, and the marker list evicts oldest-first with no liveness test. Once
 * both had aged out, {@code decide} answered {@code RECORD_AND_INSERT} and the items were granted a
 * second time.
 *
 * <p>These tests run against the mod compound and the pure decision function, with no server, which is
 * what lets the neoforge/unit gate prove the property rather than a game test asserting it in passing.
 */
class PermanentAppliedDeliveryProofTest {

    /** One more than the bounded marker list can hold, so the oldest entry is certain to be gone. */
    private static final int MORE_THAN_THE_BOUND = PlayerDataStore.MAX_APPLIED_DELIVERY_MARKERS + 50;

    // ---- the decisive test ----------------------------------------------------------------------

    @Test
    void anAlreadyGrantedUuidCannotInsertAgainAfterBothBoundedRecordsAreGone() {
        CompoundTag data = new CompoundTag();
        UUID old = UUID.randomUUID();

        // It was applied: both the bounded marker and the permanent proof are written together, which
        // is what QuestRewardDeliveryService does in one in-memory step before the player-file write.
        PlayerDataStore.markDeliveryAppliedIn(data, old);
        PlayerDataStore.recordDeliveryProofIn(data, old);
        assertTrue(PlayerDataStore.hasDeliveryProofIn(data, old));

        // The player then earns more than the bound. The marker list evicts oldest-first.
        for (int i = 0; i < MORE_THAN_THE_BOUND; i++) {
            UUID later = UUID.randomUUID();
            PlayerDataStore.markDeliveryAppliedIn(data, later);
            PlayerDataStore.recordDeliveryProofIn(data, later);
        }

        assertFalse(markerNames(data, old),
            "precondition: the bounded marker must have evicted the old uuid, or this test proves nothing");
        assertTrue(PlayerDataStore.hasDeliveryProofIn(data, old),
            "the permanent proof must NOT evict");

        // And the ledger row was pruned too, because it was acknowledged: entry == null.
        assertEquals(Action.RECORD_APPLIED_THEN_ACKNOWLEDGE,
            QuestRewardDeliveryReconciliationDecision.decide(null, false, true),
            "an already granted uuid must never insert again");
        assertNotEquals(Action.RECORD_AND_INSERT,
            QuestRewardDeliveryReconciliationDecision.decide(null, false, true));
    }

    @Test
    void withoutTheProofThatSameStateInsertsAgainWhichIsTheDefect() {
        // The two-input answer, kept here so the defect is documented by a test rather than by prose.
        assertEquals(Action.RECORD_AND_INSERT,
            QuestRewardDeliveryReconciliationDecision.decide(null, false, false));
    }

    // ---- the proof is genuinely unbounded -------------------------------------------------------

    @Test
    void theProofHasNoCapNoExpiryAndIsNeverPruned() {
        CompoundTag data = new CompoundTag();
        for (int i = 0; i < MORE_THAN_THE_BOUND; i++) {
            PlayerDataStore.recordDeliveryProofIn(data, UUID.randomUUID());
        }
        assertEquals(MORE_THAN_THE_BOUND, PlayerDataStore.deliveryProofCount(data),
            "every recorded delivery must still be there; a cap here would reopen the defect");
        assertTrue(PlayerDataStore.deliveryProofCount(data) > PlayerDataStore.MAX_APPLIED_DELIVERY_MARKERS,
            "the proof must outgrow the bounded marker list");
    }

    @Test
    void recordingTheSameUuidTwiceIsIdempotent() {
        CompoundTag data = new CompoundTag();
        UUID one = UUID.randomUUID();
        PlayerDataStore.recordDeliveryProofIn(data, one);
        PlayerDataStore.recordDeliveryProofIn(data, one);
        assertEquals(1, PlayerDataStore.deliveryProofCount(data));
    }

    @Test
    void theRepresentationIsCompactFourIntsPerDelivery() {
        CompoundTag data = new CompoundTag();
        PlayerDataStore.recordDeliveryProofIn(data, UUID.randomUUID());
        PlayerDataStore.recordDeliveryProofIn(data, UUID.randomUUID());
        assertTrue(data.contains(PlayerDataStore.APPLIED_DELIVERY_PROOF, Tag.TAG_INT_ARRAY),
            "an int array, not a list of strings: this list is unbounded and lives in the player file");
        assertEquals(8, data.getIntArray(PlayerDataStore.APPLIED_DELIVERY_PROOF).length);
    }

    // ---- crash ordering, both directions --------------------------------------------------------

    @Test
    void proofPresentWithTheLedgerLaggingRepairsWithoutInserting() {
        // Crash after the player file landed, before the ledger was flushed.
        assertEquals(Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE,
            QuestRewardDeliveryReconciliationDecision.decide(pendingLocal(), false, true));
    }

    @Test
    void noProofAndNoMarkerStillInsertsExactlyOnceBecauseTheItemsNeverLanded() {
        // Crash that lost the player-file write: the items and both player-side records went with it.
        assertEquals(Action.INSERT,
            QuestRewardDeliveryReconciliationDecision.decide(pendingLocal(), false, false));
    }

    @Test
    void anAcknowledgedRowNeverInsertsWhateverThePlayerRecordsSay() {
        for (boolean marker : List.of(true, false)) {
            for (boolean proof : List.of(true, false)) {
                assertEquals(Action.NOTHING,
                    QuestRewardDeliveryReconciliationDecision.decide(acknowledged(), marker, proof));
            }
        }
    }

    // ---- queued is not applied ------------------------------------------------------------------

    @Test
    void queuingRecordsNoProofBecauseReceivingIsNotApplying() {
        // A queued delivery is owned by the shard and NOT in the inventory. With no proof and no
        // marker the table must still retry the insertion rather than treat it as granted.
        assertEquals(Action.INSERT,
            QuestRewardDeliveryReconciliationDecision.decide(queued(), false, false));
    }

    @Test
    void aQueuedDeliveryThatLaterAppliesIsThenProvenAndNeverInsertsAgain() {
        CompoundTag data = new CompoundTag();
        UUID uuid = UUID.randomUUID();

        // While queued, nothing was recorded for it.
        assertFalse(PlayerDataStore.hasDeliveryProofIn(data, uuid));
        assertEquals(Action.INSERT,
            QuestRewardDeliveryReconciliationDecision.decide(queued(), false,
                PlayerDataStore.hasDeliveryProofIn(data, uuid)));

        // The retry lands: the proof is recorded at the moment of application.
        PlayerDataStore.markDeliveryAppliedIn(data, uuid);
        PlayerDataStore.recordDeliveryProofIn(data, uuid);
        assertEquals(Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE,
            QuestRewardDeliveryReconciliationDecision.decide(queued(), false,
                PlayerDataStore.hasDeliveryProofIn(data, uuid)));
    }

    // ---- compatibility with player data written before this change ------------------------------

    @Test
    void oldPlayerDataCarryingOnlyTheBoundedMarkerStillLoadsAndIsStillRecognised() {
        CompoundTag data = new CompoundTag();
        UUID legacy = UUID.randomUUID();
        PlayerDataStore.markDeliveryAppliedIn(data, legacy);          // no proof key at all

        assertFalse(data.contains(PlayerDataStore.APPLIED_DELIVERY_PROOF, Tag.TAG_INT_ARRAY),
            "old data has no proof array, and reading it must not invent one");
        assertEquals(0, PlayerDataStore.deliveryProofCount(data));
        assertFalse(PlayerDataStore.hasDeliveryProofIn(data, legacy));

        // It is still refused, because the bounded marker is still consulted.
        assertTrue(markerNames(data, legacy));
        assertEquals(Action.RECORD_APPLIED_THEN_ACKNOWLEDGE,
            QuestRewardDeliveryReconciliationDecision.decide(null, true, false));
    }

    @Test
    void aProofArrayOfAnImpossibleLengthIsUnreadableRatherThanPartlyRead() {
        CompoundTag data = new CompoundTag();
        data.putIntArray(PlayerDataStore.APPLIED_DELIVERY_PROOF, new int[]{1, 2, 3});
        assertEquals(0, PlayerDataStore.deliveryProofCount(data),
            "three ints are not a whole uuid; pairing them would invent one nobody recorded");
        assertFalse(PlayerDataStore.hasDeliveryProofIn(data, UUID.randomUUID()));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static boolean markerNames(CompoundTag data, UUID uuid) {
        String want = uuid.toString();
        for (Tag tag : data.getList(PlayerDataStore.APPLIED_DELIVERY_UUIDS, Tag.TAG_STRING)) {
            if (want.equals(tag.getAsString())) return true;
        }
        return false;
    }

    private static QuestRewardDeliveryLedgerEntry pendingLocal() {
        QuestRewardDelivery delivery = new QuestRewardDelivery(UUID.randomUUID(), 41L, "9001", "k",
            List.of(new QuestRewardDeliveryItem("britannia_mod:gold_coin", 2, false)), "pending", "");
        return QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, UUID.randomUUID(), 1L);
    }

    private static QuestRewardDeliveryLedgerEntry queued() {
        return pendingLocal().withQueued(2L);
    }

    private static QuestRewardDeliveryLedgerEntry acknowledged() {
        return pendingLocal().withApplied(2L).withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 3L);
    }
}
