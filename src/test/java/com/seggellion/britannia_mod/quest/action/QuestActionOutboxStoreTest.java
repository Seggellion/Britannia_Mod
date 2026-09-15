package com.seggellion.britannia_mod.quest.action;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M5: the outbox's data layer, in the conventions of
 * {@code QuestRewardDeliveryLedgerStoreTest} -- plain JUnit against {@code load}/{@code save}, no
 * server. The forced flush lives in {@link QuestActionOutbox} and is exercised by the GameTests.
 */
class QuestActionOutboxStoreTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void anEnqueuedEventRoundTripsThroughNbtWithEverySubjectValueAndItsOrder() {
        QuestActionOutboxStore store = new QuestActionOutboxStore();
        QuestActionOutboxEntry fresh = entry(UUID.randomUUID(), 100L);
        QuestActionOutboxEntry retried = entry(UUID.randomUUID(), 200L).withFailure("service_unavailable", 250L);
        for (QuestActionOutboxEntry each : List.of(fresh, retried)) {
            assertEquals(QuestActionOutboxStore.RecordOutcome.CREATED, store.record(each));
        }

        CompoundTag saved = store.save(new CompoundTag(), null);
        assertEquals(QuestActionOutboxStore.SCHEMA_VERSION, saved.getInt("SchemaVersion"));
        QuestActionOutboxStore reloaded = QuestActionOutboxStore.load(saved, null);

        assertFalse(reloaded.isReadOnlyFutureSchema());
        assertEquals(fresh, reloaded.find(fresh.eventUuid()).orElseThrow());
        assertEquals(retried, reloaded.find(retried.eventUuid()).orElseThrow());
        assertEquals(List.of(fresh, retried), reloaded.entriesFor(PLAYER), "insertion order survives");

        QuestActionEvent event = reloaded.find(fresh.eventUuid()).orElseThrow().event();
        assertEquals(QuestAction.CROP_HARVEST, event.action());
        assertEquals("minecraft:overworld", event.dimensionKey());
        assertEquals(List.of("plot_key", "crop_id", "crop_cycle_uuid", "planter_uuid", "community_plot", "yield"),
            List.copyOf(event.subject().names()), "the subject's field order is part of the encoded bytes");
        assertEquals("true", event.subject().comparable("community_plot"), "a flag survives as a flag");
        assertEquals("3", event.subject().comparable("yield"), "a number survives as a number");
        assertEquals("9005", event.target().questStateId());
        assertEquals("crop_harvested", event.target().triggerKey());
    }

    @Test
    void anUnreadableRowIsQuarantinedAndTheReadableOnesStillLoad() {
        QuestActionOutboxStore store = new QuestActionOutboxStore();
        QuestActionOutboxEntry good = entry(UUID.randomUUID(), 10L);
        store.record(good);
        CompoundTag saved = store.save(new CompoundTag(), null);

        ListTag rows = saved.getList("Entries", Tag.TAG_COMPOUND);
        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("Action", "not_a_contract_action");
        corrupt.putString("KeepMe", "raw");
        rows.add(corrupt);

        QuestActionOutboxStore reloaded = QuestActionOutboxStore.load(saved, null);

        assertEquals(1, reloaded.size(), "the readable row still loads");
        assertEquals(good, reloaded.find(good.eventUuid()).orElseThrow());
        assertEquals(1, reloaded.unreadable().size(), "the unreadable row is quarantined, not dropped");
        assertEquals("raw", reloaded.unreadable().get(0).rawTag().getString("KeepMe"));

        CompoundTag resaved = reloaded.save(new CompoundTag(), null);
        assertEquals(2, resaved.getList("Entries", Tag.TAG_COMPOUND).size(),
            "a quarantined row is written back verbatim so a later version can still read it");
    }

    @Test
    void aSchemaFromTheFutureMakesTheStoreReadOnlyAndPreservesTheFileVerbatim() {
        CompoundTag fromTheFuture = new CompoundTag();
        fromTheFuture.putInt("SchemaVersion", QuestActionOutboxStore.SCHEMA_VERSION + 1);
        ListTag rows = new ListTag();
        CompoundTag row = new CompoundTag();
        row.putString("SomethingNew", "value");
        rows.add(row);
        fromTheFuture.put("Entries", rows);

        QuestActionOutboxStore store = QuestActionOutboxStore.load(fromTheFuture, null);

        assertTrue(store.isReadOnlyFutureSchema());
        assertEquals(0, store.size(), "nothing from a schema this build cannot read is treated as live");
        assertEquals(QuestActionOutboxStore.RecordOutcome.READ_ONLY_SCHEMA, store.record(entry(UUID.randomUUID(), 1L)));
        assertFalse(store.remove(UUID.randomUUID()), "a read-only store never edits the file");
        assertEquals(fromTheFuture, store.save(new CompoundTag(), null),
            "the newer file is written back exactly as found");
    }

    @Test
    void aMissingOrMalformedSchemaVersionIsAlsoReadOnly() {
        assertTrue(QuestActionOutboxStore.load(new CompoundTag(), null).isReadOnlyFutureSchema());

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("SchemaVersion", QuestActionOutboxStore.SCHEMA_VERSION);
        malformed.put("Entries", StringTag.valueOf("not a list"));
        assertTrue(QuestActionOutboxStore.load(malformed, null).isReadOnlyFutureSchema());
    }

    @Test
    void thePerPlayerBoundDropsTheOldestRowAndNeverGrowsPastSixtyFour() {
        QuestActionOutboxStore store = new QuestActionOutboxStore();
        UUID oldest = UUID.randomUUID();
        store.record(entry(oldest, 1L));
        for (int index = 1; index < QuestActionOutboxStore.MAX_ENTRIES_PER_PLAYER; index++) {
            store.record(entry(UUID.randomUUID(), 1L + index));
        }
        assertEquals(QuestActionOutboxStore.MAX_ENTRIES_PER_PLAYER, store.entriesFor(PLAYER).size());

        UUID newest = UUID.randomUUID();
        assertEquals(QuestActionOutboxStore.RecordOutcome.CREATED, store.record(entry(newest, 999L)));

        assertEquals(QuestActionOutboxStore.MAX_ENTRIES_PER_PLAYER, store.entriesFor(PLAYER).size(),
            "the bound holds");
        assertTrue(store.find(oldest).isEmpty(), "the oldest row is the one that goes");
        assertTrue(store.find(newest).isPresent(), "the newest event is always kept");
    }

    @Test
    void theBoundIsPerPlayerSoOneFarmerCannotEvictAnother() {
        QuestActionOutboxStore store = new QuestActionOutboxStore();
        UUID other = UUID.randomUUID();
        UUID othersOnlyRow = UUID.randomUUID();
        store.record(entry(othersOnlyRow, other, 1L));
        for (int index = 0; index <= QuestActionOutboxStore.MAX_ENTRIES_PER_PLAYER + 5; index++) {
            store.record(entry(UUID.randomUUID(), PLAYER, 10L + index));
        }

        assertTrue(store.find(othersOnlyRow).isPresent(), "another player's row is never a bound candidate");
        assertEquals(1, store.entriesFor(other).size());
        assertEquals(QuestActionOutboxStore.MAX_ENTRIES_PER_PLAYER, store.entriesFor(PLAYER).size());
    }

    @Test
    void theStoreAnswersWhetherAnObjectiveIsAlreadyOwedAndForgetsItOnceRemoved() {
        QuestActionOutboxStore store = new QuestActionOutboxStore();
        QuestActionOutboxEntry pending = entry(UUID.randomUUID(), 1L);
        store.record(pending);

        assertTrue(store.hasOpenEntryFor(PLAYER, "9005", "crop_harvested"));
        assertFalse(store.hasOpenEntryFor(PLAYER, "9005", "crop_planted"), "a different objective is not owed");
        assertFalse(store.hasOpenEntryFor(UUID.randomUUID(), "9005", "crop_harvested"),
            "another player's objective is not owed");

        assertTrue(store.remove(pending.eventUuid()));
        assertFalse(store.hasOpenEntryFor(PLAYER, "9005", "crop_harvested"),
            "a terminal result frees the objective to be reported again");
    }

    @Test
    void aFailedAttemptBacksOffAndLoginSchedulesItImmediatelyAgain() {
        QuestActionOutboxEntry fresh = entry(UUID.randomUUID(), 0L);
        assertTrue(fresh.dueAt(0L), "a fresh row is due at once");

        QuestActionOutboxEntry failedOnce = fresh.withFailure("service_unavailable", 1_000L);
        assertEquals(1, failedOnce.attempts());
        assertEquals("service_unavailable", failedOnce.lastError());
        assertFalse(failedOnce.dueAt(1_000L), "a failed row waits");
        assertFalse(failedOnce.dueAt(10_999L), "it waits the whole ten seconds");
        assertTrue(failedOnce.dueAt(11_000L), "and is due when they have passed");

        QuestActionOutboxEntry failedTwice = failedOnce.withFailure("timeout", 11_000L);
        assertEquals(2, failedTwice.attempts());
        assertTrue(failedTwice.dueAt(41_000L), "the second wait is thirty seconds");

        assertTrue(failedTwice.dueNow().dueAt(0L), "login and server start override the schedule");
        assertEquals(failedTwice.attempts(), failedTwice.dueNow().attempts(),
            "rescheduling does not forget how many attempts have failed");
    }

    private static QuestActionOutboxEntry entry(UUID eventUuid, long createdAt) {
        return entry(eventUuid, PLAYER, createdAt);
    }

    private static QuestActionOutboxEntry entry(UUID eventUuid, UUID playerUuid, long createdAt) {
        QuestActionEvent event = new QuestActionEvent(eventUuid, playerUuid, QuestAction.CROP_HARVEST,
            "2026-09-06T21:40:00Z", "minecraft:overworld", 1203, 64, -488,
            QuestActionEvents.cropHarvestSubject("minecraft:overworld:1203:64:-488", "carrot",
                UUID.fromString(QuestActionContractFixtures.CROP_CYCLE_UUID), playerUuid, true, 3),
            QuestActionEvent.Target.of("9005", "crop_harvested"));
        return QuestActionOutboxEntry.queued(event, createdAt);
    }
}
