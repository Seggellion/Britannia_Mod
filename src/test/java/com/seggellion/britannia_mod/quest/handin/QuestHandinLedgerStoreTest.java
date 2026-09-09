package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger's own durability, without a server.
 *
 * <p>{@code save}/{@code load} take a {@code HolderLookup.Provider} this store never uses, so the
 * whole round trip is exercised by passing {@code null} -- the same seam the blessed-receipt and
 * delivery-ledger tests use.
 *
 * <p>The retention cases are the ones worth reading. This ledger refuses rather than evicts, which
 * is the opposite trade from the delivery ledger and for the opposite reason: dropping a delivery
 * row risks granting an item twice, while dropping a hand-in row risks a player having given one up
 * for nothing.
 */
class QuestHandinLedgerStoreTest {

    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER_PLAYER = UUID.fromString("8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b");

    @Test
    void aRemovedTransactionSurvivesTheRoundTripWithItsProofIntact() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID handin = UUID.randomUUID();
        store.record(removed(handin, List.of(
                QuestHandinRemoval.literal(0, "britannia_mod:dung", 1),
                QuestHandinRemoval.resolved(1, "britannia_mod:carrots", 2,
                        "awarded_crop_harvest_item", "carrot"))));

        QuestHandinLedgerStore reloaded = QuestHandinLedgerStore.load(store.save(new CompoundTag(), null), null);

        QuestHandinLedgerEntry entry = reloaded.find(handin).orElseThrow();
        assertEquals(QuestHandinLocalState.REMOVED_LOCAL, entry.localState());
        assertEquals(2, entry.proof().size());
        assertEquals("britannia_mod:dung", entry.proof().get(0).itemId());
        assertFalse(entry.proof().get(0).answersResolver());
        assertEquals("britannia_mod:carrots", entry.proof().get(1).itemId());
        assertEquals("awarded_crop_harvest_item", entry.proof().get(1).resolver());
        assertEquals("carrot", entry.proof().get(1).flagValue());
        assertEquals(2, entry.proof().get(1).count());
    }

    @Test
    void theReloadedProofEncodesToTheSameBytesTheFirstAttemptSent() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID handin = UUID.randomUUID();
        QuestHandinLedgerEntry original = removed(handin, List.of(
                QuestHandinRemoval.resolved(0, "britannia_mod:carrots", 1,
                        "awarded_crop_harvest_item", "carrot")));
        store.record(original);

        QuestHandinLedgerEntry reloaded = QuestHandinLedgerStore
                .load(store.save(new CompoundTag(), null), null).find(handin).orElseThrow();

        assertEquals(text(QuestItemHandinProtocol.encodeConfirmation(original.confirmation())),
                text(QuestItemHandinProtocol.encodeConfirmation(reloaded.confirmation())),
                "a retry after a restart must post the bytes the lost attempt posted");
    }

    @Test
    void schemaVersionIsWrittenAndAFutureOneMakesTheStoreReadOnlyWithoutClobberingIt() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        store.record(prepared(UUID.randomUUID()));
        CompoundTag saved = store.save(new CompoundTag(), null);
        assertEquals(QuestHandinLedgerStore.SCHEMA_VERSION, saved.getInt("SchemaVersion"));

        CompoundTag future = saved.copy();
        future.putInt("SchemaVersion", QuestHandinLedgerStore.SCHEMA_VERSION + 1);
        QuestHandinLedgerStore readOnly = QuestHandinLedgerStore.load(future, null);

        assertTrue(readOnly.isReadOnlyFutureSchema());
        assertEquals(QuestHandinLedgerStore.RecordOutcome.READ_ONLY_SCHEMA,
                readOnly.record(prepared(UUID.randomUUID())));
        assertTrue(readOnly.update(UUID.randomUUID(), entry -> entry).isEmpty());
        assertEquals(QuestHandinLedgerStore.SCHEMA_VERSION + 1,
                readOnly.save(new CompoundTag(), null).getInt("SchemaVersion"),
                "a file from a newer build is handed back untouched, never rewritten");
    }

    @Test
    void aMissingSchemaVersionIsTreatedAsUnreadableRatherThanAsVersionZero() {
        CompoundTag noVersion = new CompoundTag();
        noVersion.put("Entries", new ListTag());
        assertTrue(QuestHandinLedgerStore.load(noVersion, null).isReadOnlyFutureSchema());
    }

    @Test
    void aCorruptRowIsQuarantinedVerbatimAndItsNeighboursStillLoad() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID good = UUID.randomUUID();
        store.record(removed(good, List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1))));
        CompoundTag saved = store.save(new CompoundTag(), null);
        ListTag entries = saved.getList("Entries", CompoundTag.TAG_COMPOUND);
        CompoundTag broken = new CompoundTag();
        broken.putString("HandinUuid", "not-a-uuid");
        broken.putString("Marker", "keep me");
        entries.add(broken);

        QuestHandinLedgerStore reloaded = QuestHandinLedgerStore.load(saved, null);

        assertTrue(reloaded.find(good).isPresent(), "one bad row must not hide the others");
        assertEquals(1, reloaded.unreadable().size());
        assertEquals("keep me", reloaded.unreadable().get(0).rawTag().getString("Marker"));
        assertTrue(reloaded.save(new CompoundTag(), null)
                        .getList("Entries", CompoundTag.TAG_COMPOUND).size() == 2,
                "a row this build cannot read is written back rather than deleted");
    }

    @Test
    void aRowClaimingARemovalWithNoProofIsRefusedRatherThanLoaded() {
        assertThrows(IllegalArgumentException.class, () -> new QuestHandinLedgerEntry(
                UUID.randomUUID(), PLAYER, "", "", "", List.of(), QuestHandinLocalState.REMOVED_LOCAL,
                UUID.randomUUID(), 0, 1L, 1L, 0L, ""),
                "a removal Rails could never be asked to account for must not be representable");
    }

    @Test
    void aStateThisBuildDoesNotKnowIsQuarantinedRatherThanGuessedAt() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        store.record(prepared(UUID.randomUUID()));
        CompoundTag saved = store.save(new CompoundTag(), null);
        saved.getList("Entries", CompoundTag.TAG_COMPOUND).getCompound(0)
                .putString("LocalState", "SOMETHING_LATER");

        QuestHandinLedgerStore reloaded = QuestHandinLedgerStore.load(saved, null);

        assertEquals(0, reloaded.size());
        assertEquals(1, reloaded.unreadable().size());
    }

    @Test
    void recordingTheSameTransactionTwiceKeepsTheFirstRow() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID handin = UUID.randomUUID();
        assertEquals(QuestHandinLedgerStore.RecordOutcome.CREATED, store.record(prepared(handin)));
        assertEquals(QuestHandinLedgerStore.RecordOutcome.ALREADY_PRESENT, store.record(prepared(handin)));
        assertEquals(1, store.size());
    }

    @Test
    void settledRowsArePrunedToKeepTheLedgerBounded() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        for (int index = 0; index < QuestHandinLedgerStore.MAX_ENTRIES_PER_PLAYER + 20; index++) {
            settle(store, QuestHandinLocalState.CONSUMED);
        }
        assertTrue(store.countFor(PLAYER) <= QuestHandinLedgerStore.MAX_ENTRIES_PER_PLAYER,
                "settled rows are what the bound is allowed to take");
    }

    /**
     * A completed transaction cannot forget what it took. The invariant is on the record itself, so
     * there is no code path -- production, recovery or test -- that can write a row saying the items
     * are gone with nothing to account for them.
     */
    @Test
    void aSettledStateThatMeansTheItemsAreGoneStillNeedsItsProof() {
        QuestHandinLedgerEntry preparedOnly = prepared(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> preparedOnly.withSettled(QuestHandinLocalState.CONSUMED, 2L));
        assertThrows(IllegalArgumentException.class,
                () -> preparedOnly.withSettled(QuestHandinLocalState.REFUNDED, 2L));
        assertThrows(IllegalArgumentException.class,
                () -> preparedOnly.withStranded("handin_rejected", 2L));
        // Abandoning one that never took anything is the transition a prepared row is allowed.
        assertEquals(QuestHandinLocalState.ABANDONED,
                preparedOnly.withSettled(QuestHandinLocalState.ABANDONED, 2L).localState());
    }

    @Test
    void aPlayerFullOfLiveRowsIsRefusedANewOneRatherThanLosingAnOldOne() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        for (int index = 0; index < QuestHandinLedgerStore.MAX_ENTRIES_PER_PLAYER; index++) {
            store.record(removed(UUID.randomUUID(),
                    List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1))));
        }

        assertEquals(QuestHandinLedgerStore.RecordOutcome.AT_CAPACITY,
                store.record(prepared(UUID.randomUUID())),
                "refusing a new transaction is what stops a removal with nowhere to be recorded");
        assertEquals(QuestHandinLedgerStore.MAX_ENTRIES_PER_PLAYER, store.countFor(PLAYER));
        // Another player is unaffected: the bound is per player, not per server.
        assertEquals(QuestHandinLedgerStore.RecordOutcome.CREATED,
                store.record(QuestHandinLedgerEntry.prepared(UUID.randomUUID(), OTHER_PLAYER,
                        "41", "9001", "hand_over", UUID.randomUUID(), 1L)));
    }

    @Test
    void aStrandedRowIsNeverPruned() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID stranded = UUID.randomUUID();
        store.record(removed(stranded, List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1))));
        store.update(stranded, entry -> entry.withStranded("handin_rejected", 3L));
        for (int index = 0; index < QuestHandinLedgerStore.MAX_ENTRIES_PER_PLAYER + 20; index++) {
            settle(store, QuestHandinLocalState.CONSUMED);
        }

        assertTrue(store.find(stranded).isPresent(),
                "the only surviving evidence that a player gave something up is not a pruning candidate");
    }

    @Test
    void anUpdateThatChangesNothingReportsNothingSoNoFlushIsForced() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID handin = UUID.randomUUID();
        store.record(prepared(handin));
        assertTrue(store.update(handin, entry -> entry).isEmpty());
        assertTrue(store.update(UUID.randomUUID(), entry -> entry).isEmpty());
        assertTrue(store.update(handin, entry -> entry.withSettled(QuestHandinLocalState.ABANDONED, 5L))
                .isPresent());
    }

    @Test
    void openEntriesExcludeTheSettledOnes() {
        QuestHandinLedgerStore store = new QuestHandinLedgerStore();
        UUID live = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        store.record(removed(live, List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1))));
        store.record(removed(done, List.of(QuestHandinRemoval.literal(0, "britannia_mod:dirt", 1))));
        store.update(done, entry -> entry.withSettled(QuestHandinLocalState.CONSUMED, 6L));

        assertEquals(List.of(live), store.openEntriesFor(PLAYER).stream()
                .map(QuestHandinLedgerEntry::handinUuid).toList());
        assertEquals(2, store.entriesFor(PLAYER).size());
    }

    @Test
    void aMalformedEntriesCollectionMakesTheWholeStoreReadOnly() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", QuestHandinLedgerStore.SCHEMA_VERSION);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not a row"));
        tag.put("Entries", strings);

        assertTrue(QuestHandinLedgerStore.load(tag, null).isReadOnlyFutureSchema());
    }

    private static QuestHandinLedgerEntry prepared(UUID handinUuid) {
        return QuestHandinLedgerEntry.prepared(handinUuid, PLAYER, "41", "9001", "hand_over",
                UUID.randomUUID(), 1L);
    }

    private static QuestHandinLedgerEntry removed(UUID handinUuid, List<QuestHandinRemoval> proof) {
        return prepared(handinUuid).withRemovalIntent(proof, 1L).withRemovedLocally(2L);
    }

    /** Records a transaction that really removed something and takes it to {@code state}. */
    private static void settle(QuestHandinLedgerStore store, QuestHandinLocalState state) {
        UUID handin = UUID.randomUUID();
        store.record(removed(handin, List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1))));
        store.update(handin, entry -> entry.withSettled(state, 2L));
    }

    private static String text(byte[] body) {
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
}
