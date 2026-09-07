package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M3: the ledger's data layer, in the conventions of
 * {@code BlessedDeliveryReceiptStoreTest} -- plain JUnit against {@code load}/{@code save}, no
 * server. The forced flush lives in {@link QuestRewardDeliveryLedger} and is exercised by the
 * GameTests.
 */
class QuestRewardDeliveryLedgerStoreTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void aRecordedEntryRoundTripsThroughNbtWithEveryField() {
        QuestRewardDeliveryLedgerStore store = new QuestRewardDeliveryLedgerStore();
        QuestRewardDeliveryLedgerEntry pending = entry(UUID.randomUUID(), 100L);
        QuestRewardDeliveryLedgerEntry applied = entry(UUID.randomUUID(), 200L).withApplied(250L)
            .withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 260L);
        QuestRewardDeliveryLedgerEntry queued = entry(UUID.randomUUID(), 300L).withQueued(310L)
            .withAcknowledged(QuestRewardDeliveryProtocol.Outcome.QUEUED, 320L);
        QuestRewardDeliveryLedgerEntry failed = entry(UUID.randomUUID(), 400L).withApplied(410L)
            .withTerminalError("conflicting_delivery_result", 420L);
        for (QuestRewardDeliveryLedgerEntry each : List.of(pending, applied, queued, failed)) {
            assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED, store.record(each));
        }

        CompoundTag saved = store.save(new CompoundTag(), null);
        assertEquals(QuestRewardDeliveryLedgerStore.SCHEMA_VERSION, saved.getInt("SchemaVersion"));
        QuestRewardDeliveryLedgerStore reloaded = QuestRewardDeliveryLedgerStore.load(saved, null);

        assertFalse(reloaded.isReadOnlyFutureSchema());
        assertEquals(pending, reloaded.find(pending.deliveryUuid()).orElseThrow());
        assertEquals(applied, reloaded.find(applied.deliveryUuid()).orElseThrow());
        assertEquals(queued, reloaded.find(queued.deliveryUuid()).orElseThrow());
        assertEquals(failed, reloaded.find(failed.deliveryUuid()).orElseThrow());
        assertEquals(List.of(pending, applied, queued, failed), reloaded.entriesFor(PLAYER), "insertion order survives");
        assertEquals(List.of(pending, queued), reloaded.openEntriesFor(PLAYER));

        QuestRewardDeliveryLedgerEntry reloadedQueued = reloaded.find(queued.deliveryUuid()).orElseThrow();
        assertEquals(QuestRewardDeliveryLocalState.QUEUED, reloadedQueued.localState());
        assertEquals("queued", reloadedQueued.acknowledgedOutcome());
        assertTrue(reloadedQueued.itemsOwed());
        assertFalse(reloadedQueued.acknowledgementOutstanding(), "Rails already holds queued");
        assertEquals(List.of(new QuestRewardDeliveryItem("britannia_mod:britannia_shovel", 1, false),
            new QuestRewardDeliveryItem("britannia_mod:gold_coin", 5, null)), reloadedQueued.items());
    }

    @Test
    void theStateMachineAnswersWhatIsStillOwedAndWhatIsStillUnacknowledged() {
        QuestRewardDeliveryLedgerEntry pending = entry(UUID.randomUUID(), 1L);
        assertTrue(pending.itemsOwed());
        assertFalse(pending.acknowledgementOutstanding());
        assertTrue(pending.open());

        QuestRewardDeliveryLedgerEntry queued = pending.withQueued(2L);
        assertTrue(queued.itemsOwed());
        assertTrue(queued.acknowledgementOutstanding());
        assertEquals(1, queued.attempts());

        QuestRewardDeliveryLedgerEntry queuedAcknowledged = queued.withAcknowledged(QuestRewardDeliveryProtocol.Outcome.QUEUED, 3L);
        assertEquals(QuestRewardDeliveryLocalState.QUEUED, queuedAcknowledged.localState());
        assertTrue(queuedAcknowledged.itemsOwed());
        assertFalse(queuedAcknowledged.acknowledgementOutstanding());
        assertTrue(queuedAcknowledged.open());

        QuestRewardDeliveryLedgerEntry applied = queuedAcknowledged.withApplied(4L);
        assertFalse(applied.itemsOwed());
        assertTrue(applied.itemsGranted());
        assertTrue(applied.acknowledgementOutstanding(), "queued -> applied is the upgrade Rails still needs");
        assertEquals(4L, applied.appliedAtEpochMillis());

        QuestRewardDeliveryLedgerEntry acknowledged = applied.withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 5L);
        assertEquals(QuestRewardDeliveryLocalState.ACKNOWLEDGED, acknowledged.localState());
        assertFalse(acknowledged.open());
        assertEquals(5L, acknowledged.acknowledgedAtEpochMillis());

        QuestRewardDeliveryLedgerEntry terminal = applied.withTerminalError("delivery_not_found", 6L);
        assertEquals(QuestRewardDeliveryLocalState.ACKNOWLEDGED, terminal.localState());
        assertEquals("delivery_not_found", terminal.acknowledgementError());
        assertFalse(terminal.open(), "a terminal answer is never retried");
    }

    @Test
    void recordNeverOverwritesAndUpdateNeverFabricates() {
        QuestRewardDeliveryLedgerStore store = new QuestRewardDeliveryLedgerStore();
        QuestRewardDeliveryLedgerEntry first = entry(UUID.randomUUID(), 1L);
        assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED, store.record(first));
        assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.ALREADY_PRESENT, store.record(first.withApplied(9L)));
        assertEquals(first, store.find(first.deliveryUuid()).orElseThrow(), "the stored row is untouched by a replay");

        assertTrue(store.update(UUID.randomUUID(), e -> e.withApplied(2L)).isEmpty());
        assertTrue(store.update(first.deliveryUuid(), e -> e).isEmpty(), "an unchanged row is not a change");
        assertEquals(QuestRewardDeliveryLocalState.APPLIED,
            store.update(first.deliveryUuid(), e -> e.withApplied(2L)).orElseThrow().localState());
        assertTrue(store.isDirty());
    }

    @Test
    void aCorruptRowIsQuarantinedVerbatimAndTheRestStillLoads() {
        QuestRewardDeliveryLedgerStore store = new QuestRewardDeliveryLedgerStore();
        QuestRewardDeliveryLedgerEntry good = entry(UUID.randomUUID(), 1L);
        store.record(good);
        CompoundTag saved = store.save(new CompoundTag(), null);

        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("LocalState", "SOMETHING_FROM_THE_FUTURE");
        corrupt.putString("Note", "keep me");
        saved.getList("Entries", Tag.TAG_COMPOUND).add(corrupt);
        CompoundTag duplicate = good.toNbt();
        saved.getList("Entries", Tag.TAG_COMPOUND).add(duplicate);

        QuestRewardDeliveryLedgerStore reloaded = QuestRewardDeliveryLedgerStore.load(saved, null);
        assertFalse(reloaded.isReadOnlyFutureSchema());
        assertEquals(good, reloaded.find(good.deliveryUuid()).orElseThrow());
        assertEquals(2, reloaded.unreadable().size());
        assertEquals("keep me", reloaded.unreadable().get(0).rawTag().getString("Note"));

        ListTag resaved = reloaded.save(new CompoundTag(), null).getList("Entries", Tag.TAG_COMPOUND);
        assertEquals(3, resaved.size(), "quarantined rows are re-saved, never dropped");
    }

    @Test
    void aFutureSchemaIsReadOnlyAndRefusesEveryMutation() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", QuestRewardDeliveryLedgerStore.SCHEMA_VERSION + 1);
        ListTag entries = new ListTag();
        entries.add(entry(UUID.randomUUID(), 1L).toNbt());
        future.put("Entries", entries);

        QuestRewardDeliveryLedgerStore store = QuestRewardDeliveryLedgerStore.load(future, null);
        assertTrue(store.isReadOnlyFutureSchema());
        assertEquals(1, store.unreadable().size());
        assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.READ_ONLY_SCHEMA, store.record(entry(UUID.randomUUID(), 2L)));
        assertTrue(store.update(UUID.randomUUID(), e -> e.withApplied(3L)).isEmpty());
        assertEquals(future, store.save(new CompoundTag(), null), "the future file is written back untouched");

        CompoundTag missing = new CompoundTag();
        assertTrue(QuestRewardDeliveryLedgerStore.load(missing, null).isReadOnlyFutureSchema());
    }

    @Test
    void thePerPlayerBoundPrunesOnlyAcknowledgedRowsOldestFirst() {
        QuestRewardDeliveryLedgerStore store = new QuestRewardDeliveryLedgerStore();
        UUID other = UUID.randomUUID();
        QuestRewardDeliveryLedgerEntry oldestAcknowledged = entry(UUID.randomUUID(), 1L).withApplied(1L)
            .withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 1L);
        QuestRewardDeliveryLedgerEntry oldestLive = entry(UUID.randomUUID(), 2L);
        store.record(oldestAcknowledged);
        store.record(oldestLive);
        QuestRewardDeliveryLedgerEntry othersRow = new QuestRewardDeliveryLedgerEntry(UUID.randomUUID(), other, 41L, "9001",
            "k", List.of(new QuestRewardDeliveryItem("britannia_mod:gold_coin", 1, false)),
            QuestRewardDeliveryLocalState.ACKNOWLEDGED, 0, 3L, 3L, 3L, "applied", "");
        store.record(othersRow);
        for (int i = 0; i < QuestRewardDeliveryLedgerStore.MAX_ENTRIES_PER_PLAYER - 2; i++) {
            store.record(entry(UUID.randomUUID(), 10L + i).withApplied(10L + i)
                .withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 10L + i));
        }
        assertEquals(QuestRewardDeliveryLedgerStore.MAX_ENTRIES_PER_PLAYER, store.entriesFor(PLAYER).size());

        QuestRewardDeliveryLedgerEntry newest = entry(UUID.randomUUID(), 999L);
        assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED, store.record(newest));

        assertEquals(QuestRewardDeliveryLedgerStore.MAX_ENTRIES_PER_PLAYER, store.entriesFor(PLAYER).size());
        assertTrue(store.find(oldestAcknowledged.deliveryUuid()).isEmpty(), "the oldest acknowledged row was pruned");
        assertEquals(oldestLive, store.find(oldestLive.deliveryUuid()).orElseThrow(), "a live row survives the bound");
        assertEquals(newest, store.find(newest.deliveryUuid()).orElseThrow());
        assertEquals(othersRow, store.find(othersRow.deliveryUuid()).orElseThrow(), "another player's rows are not this player's bound");
    }

    @Test
    void aBoundFullOfLiveRowsIsExceededRatherThanLosingOne() {
        QuestRewardDeliveryLedgerStore store = new QuestRewardDeliveryLedgerStore();
        for (int i = 0; i < QuestRewardDeliveryLedgerStore.MAX_ENTRIES_PER_PLAYER; i++) {
            store.record(entry(UUID.randomUUID(), 10L + i).withQueued(10L + i));
        }
        assertEquals(QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED, store.record(entry(UUID.randomUUID(), 999L)));
        assertEquals(QuestRewardDeliveryLedgerStore.MAX_ENTRIES_PER_PLAYER + 1, store.entriesFor(PLAYER).size());
    }

    @Test
    void theEntryRefusesShapesTheContractRefuses() {
        UUID uuid = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new QuestRewardDeliveryLedgerEntry(
            uuid, PLAYER, 41L, "9001", "k", List.of(), QuestRewardDeliveryLocalState.PENDING_LOCAL, 0, 1L, 0L, 0L, "", ""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new QuestRewardDeliveryLedgerEntry(
            uuid, PLAYER, 41L, "9001", "k", List.of(new QuestRewardDeliveryItem("britannia_mod:gold_coin", 1, false)),
            QuestRewardDeliveryLocalState.PENDING_LOCAL, -1, 1L, 0L, 0L, "", ""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new QuestRewardDeliveryItem("britannia_mod:gold_coin", 0, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new QuestRewardDeliveryItem("britannia_mod:gold_coin", 1025, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new QuestRewardDeliveryItem(" ", 1, false));
    }

    private static QuestRewardDeliveryLedgerEntry entry(UUID deliveryUuid, long recordedAt) {
        QuestRewardDelivery delivery = new QuestRewardDelivery(deliveryUuid, 41L, "9001", "1757200000:1201:choice:accept",
            List.of(new QuestRewardDeliveryItem("britannia_mod:britannia_shovel", 1, false),
                new QuestRewardDeliveryItem("britannia_mod:gold_coin", 5, null)),
            "pending", "2026-09-06T21:10:00Z");
        return QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, PLAYER, recordedAt);
    }
}
