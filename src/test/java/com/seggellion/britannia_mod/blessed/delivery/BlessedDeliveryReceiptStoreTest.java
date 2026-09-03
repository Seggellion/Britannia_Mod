package com.seggellion.britannia_mod.blessed.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors {@code BankTransferReceiptStoreTest}'s conventions: plain JUnit 5 against the store's
 * own {@code load}/{@code save} NBT seam, no live server. The forced-flush guarantee itself lives
 * in {@link BlessedDeliveryReceipts} and needs a real {@code ServerLevel}, so it is out of scope
 * here; what these tests prove is the data layer underneath it -- that a receipt written before a
 * crash comes back byte-identical afterwards, and that no corruption on the way back turns into
 * either a lost receipt or a wrongly-honoured one.
 */
class BlessedDeliveryReceiptStoreTest {

    private static final String MEDALLION = "britannia_mod:starfarers_medallion";

    // ---------- BlessedDeliveryReceipt validation ----------

    @Test
    void constructorRejectsAnyMissingIdentityField() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new BlessedDeliveryReceipt(
            null, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(NullPointerException.class, () -> new BlessedDeliveryReceipt(
            instance, null, MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(NullPointerException.class, () -> new BlessedDeliveryReceipt(
            instance, "deed-1", null, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(NullPointerException.class, () -> new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, null, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(NullPointerException.class, () -> new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, null, 1L));
    }

    @Test
    void constructorRejectsABlankDeedOrItemIdAndANegativeTimestamp() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BlessedDeliveryReceipt(
            instance, "  ", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(IllegalArgumentException.class, () -> new BlessedDeliveryReceipt(
            instance, "deed-1", "", owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L));
        assertThrows(IllegalArgumentException.class, () -> new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, -1L));
    }

    // A legacy Rails BlessedItem.uuid is not guaranteed uuid-shaped, which is exactly why deedId
    // is a String. If this store ever started parsing it, a real historical entitlement would be
    // rejected at the worst possible moment.
    @Test
    void aNonUuidShapedLegacyDeedIdIsAcceptedAndRoundTrips() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        BlessedDeliveryReceipt receipt = new BlessedDeliveryReceipt(
            instance, "legacy-deed-00017", MEDALLION, UUID.randomUUID(),
            BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 42L);
        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.CREATED, store.record(receipt));

        BlessedDeliveryReceiptStore reloaded =
            BlessedDeliveryReceiptStore.load(store.save(new CompoundTag(), null), null);
        assertEquals("legacy-deed-00017", reloaded.find(instance).orElseThrow().deedId());
    }

    @Test
    void matchesComparesTheFingerprintOnlyAndIgnoresStatusAndTimestamp() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        BlessedDeliveryReceipt pending = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1L);
        BlessedDeliveryReceipt deliveredLater = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.DELIVERED, 999L);

        assertTrue(pending.matches(deliveredLater), "status/timestamp must not be part of the fingerprint");
        assertTrue(pending.matches("deed-1", MEDALLION, owner));
        assertFalse(pending.matches("deed-2", MEDALLION, owner));
        assertFalse(pending.matches("deed-1", "britannia_mod:something_else", owner));
        assertFalse(pending.matches("deed-1", MEDALLION, UUID.randomUUID()));
    }

    // ---------- record / find ----------

    @Test
    void recordThenFindRoundTripPreservesEveryField() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        BlessedDeliveryReceipt receipt = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 1_700_000_000_000L);

        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.CREATED, store.record(receipt));

        BlessedDeliveryReceipt found = store.find(instance).orElseThrow();
        assertEquals(instance, found.instanceUuid());
        assertEquals("deed-1", found.deedId());
        assertEquals(MEDALLION, found.itemId());
        assertEquals(owner, found.ownerUuid());
        assertEquals(BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, found.status());
        assertEquals(1_700_000_000_000L, found.updatedEpochMillis());
        assertEquals(receipt, found);
    }

    @Test
    void findReturnsAnEmptyOptionalForAnUnknownInstance() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        assertTrue(store.find(UUID.randomUUID()).isEmpty());
    }

    // The crash-survival proof at the data layer: everything written before an unclean kill comes
    // back identical after the reload.
    @Test
    void saveAndLoadRoundTripThroughNbtPreservesEveryField() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID pendingInstance = UUID.randomUUID();
        UUID deliveredInstance = UUID.randomUUID();
        BlessedDeliveryReceipt pending = receipt(pendingInstance, "deed-1", MEDALLION, 111L);
        BlessedDeliveryReceipt delivered = new BlessedDeliveryReceipt(
            deliveredInstance, "deed-2", "britannia_mod:other_blessing", UUID.randomUUID(),
            BlessedDeliveryReceiptStatus.DELIVERED, 222L);
        store.record(pending);
        store.record(delivered);

        CompoundTag saved = store.save(new CompoundTag(), null);
        assertEquals(BlessedDeliveryReceiptStore.SCHEMA_VERSION, saved.getInt("SchemaVersion"));

        BlessedDeliveryReceiptStore reloaded = BlessedDeliveryReceiptStore.load(saved, null);
        assertFalse(reloaded.isReadOnlyFutureSchema());
        assertEquals(pending, reloaded.find(pendingInstance).orElseThrow());
        assertEquals(delivered, reloaded.find(deliveredInstance).orElseThrow());

        BlessedDeliveryReceipt reloadedDelivered = reloaded.find(deliveredInstance).orElseThrow();
        assertEquals("deed-2", reloadedDelivered.deedId());
        assertEquals("britannia_mod:other_blessing", reloadedDelivered.itemId());
        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, reloadedDelivered.status());
        assertEquals(222L, reloadedDelivered.updatedEpochMillis());
    }

    /**
     * Pins the exact on-disk shape. This is not a restatement of the round-trip test above: that
     * one proves the store can read back what it wrote, which would stay true even if the tag
     * names or types silently changed together. This one proves the shape itself has not moved --
     * any change here is a real, on-disk-visible schema change and must bump {@code
     * SCHEMA_VERSION} rather than being made quietly.
     */
    @Test
    void thePersistedNbtShapeIsTheDocumentedSetOfTagNamesAndTypes() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.DELIVERED, 1_700_000_000_000L).toNbt();

        assertEquals(6, tag.size(), "an unexpected extra or missing tag means the persisted shape moved");
        // UUIDs persist as a 4-int IntArrayTag -- vanilla CompoundTag#putUUID's own encoding.
        assertTrue(tag.contains("InstanceUuid", Tag.TAG_INT_ARRAY));
        assertTrue(tag.contains("OwnerUuid", Tag.TAG_INT_ARRAY));
        assertEquals(4, tag.getIntArray("InstanceUuid").length);
        assertEquals(instance, tag.getUUID("InstanceUuid"));
        assertEquals(owner, tag.getUUID("OwnerUuid"));

        assertTrue(tag.contains("DeedId", Tag.TAG_STRING));
        assertTrue(tag.contains("ItemId", Tag.TAG_STRING));
        assertTrue(tag.contains("Status", Tag.TAG_STRING));
        assertTrue(tag.contains("UpdatedEpochMillis", Tag.TAG_LONG));
        assertEquals("deed-1", tag.getString("DeedId"));
        assertEquals(MEDALLION, tag.getString("ItemId"));
        assertEquals("DELIVERED", tag.getString("Status"), "Status persists as the enum name, never its ordinal");
        assertEquals(1_700_000_000_000L, tag.getLong("UpdatedEpochMillis"));

        // And the root: an int SchemaVersion plus a compound-element list named Receipts.
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        store.record(receipt(instance, "deed-1", MEDALLION, 1L));
        CompoundTag root = store.save(new CompoundTag(), null);
        assertEquals(2, root.size());
        assertTrue(root.contains("SchemaVersion", Tag.TAG_INT));
        assertEquals(1, root.getInt("SchemaVersion"));
        assertTrue(root.contains("Receipts", Tag.TAG_LIST));
        assertEquals(1, root.getList("Receipts", CompoundTag.TAG_COMPOUND).size());
    }

    // ---------- idempotency and the fingerprint refusal ----------

    @Test
    void recordingTheSameInstanceTwiceWithTheSameFingerprintIsAnIdempotentReplayAndDoesNotDuplicate() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        BlessedDeliveryReceipt first = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 100L);

        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.CREATED, store.record(first));
        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.IDEMPOTENT_REPLAY, store.record(first));

        BlessedDeliveryReceipt sameFingerprintDifferentTimestamp = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 500L);
        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.IDEMPOTENT_REPLAY,
            store.record(sameFingerprintDifferentTimestamp));

        assertEquals(1, store.scan().pendingDelivery().size(), "a replay must never create a second receipt");
        assertEquals(100L, store.find(instance).orElseThrow().updatedEpochMillis(),
            "a replay must leave the stored receipt completely untouched, timestamp included");
    }

    // The duplicate-item hole this store exists to close: a replay arriving after the item was
    // already delivered must not regress the stored status back to pending.
    @Test
    void aReplayAfterDeliveryDoesNotRegressTheStoredStatusBackToPending() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        store.record(new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 100L));
        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.MARKED, store.markDelivered(instance, 200L));

        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.IDEMPOTENT_REPLAY, store.record(new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 300L)));

        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, store.find(instance).orElseThrow().status());
        assertEquals(200L, store.find(instance).orElseThrow().updatedEpochMillis());
        assertTrue(store.scan().pendingDelivery().isEmpty());
    }

    @Test
    void recordingTheSameInstanceWithADifferentDeedIdIsAFingerprintMismatchAndMutatesNothing() {
        assertFingerprintMismatchLeavesTheStoredReceiptIntact("deed-DIFFERENT", MEDALLION, null);
    }

    @Test
    void recordingTheSameInstanceWithADifferentItemIdIsAFingerprintMismatchAndMutatesNothing() {
        assertFingerprintMismatchLeavesTheStoredReceiptIntact("deed-1", "britannia_mod:wrong_item", null);
    }

    @Test
    void recordingTheSameInstanceWithADifferentOwnerIsAFingerprintMismatchAndMutatesNothing() {
        assertFingerprintMismatchLeavesTheStoredReceiptIntact("deed-1", MEDALLION, UUID.randomUUID());
    }

    // ---------- markDelivered ----------

    @Test
    void markDeliveredTransitionsTheStatusAndRestampsTheTimestamp() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        BlessedDeliveryReceipt original = receipt(instance, "deed-1", MEDALLION, 100L);
        store.record(original);

        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.MARKED, store.markDelivered(instance, 250L));

        BlessedDeliveryReceipt marked = store.find(instance).orElseThrow();
        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, marked.status());
        assertEquals(250L, marked.updatedEpochMillis());
        // Every other field survives the transition untouched.
        assertEquals(original.instanceUuid(), marked.instanceUuid());
        assertEquals(original.deedId(), marked.deedId());
        assertEquals(original.itemId(), marked.itemId());
        assertEquals(original.ownerUuid(), marked.ownerUuid());
    }

    @Test
    void markDeliveredIsIdempotentAndDoesNotRestampAnAlreadyDeliveredReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        store.markDelivered(instance, 250L);

        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.ALREADY_DELIVERED,
            store.markDelivered(instance, 900L));
        assertEquals(250L, store.find(instance).orElseThrow().updatedEpochMillis(),
            "an already-delivered replay must preserve the original delivery time");
        assertEquals(1, store.scan().delivered().size());
    }

    @Test
    void markDeliveredOnAnUnknownInstanceIsNotFoundAndNeverFabricatesAReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID unknown = UUID.randomUUID();

        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.NOT_FOUND, store.markDelivered(unknown, 1L));
        assertTrue(store.find(unknown).isEmpty(), "marking a missing instance must never create a receipt");
        assertTrue(store.scan().isEmpty());
    }

    @Test
    void markDeliveredSurvivesTheNbtRoundTrip() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        store.markDelivered(instance, 250L);

        BlessedDeliveryReceiptStore reloaded =
            BlessedDeliveryReceiptStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, reloaded.find(instance).orElseThrow().status());
        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.ALREADY_DELIVERED,
            reloaded.markDelivered(instance, 900L));
    }

    // ---------- corruption quarantine ----------

    @Test
    void aCorruptRecordIsQuarantinedAndTheGoodRecordsAroundItStillLoad() {
        BlessedDeliveryReceipt before = receipt(UUID.randomUUID(), "deed-before", MEDALLION, 10L);
        BlessedDeliveryReceipt after = receipt(UUID.randomUUID(), "deed-after", MEDALLION, 30L);

        CompoundTag corrupt = new CompoundTag();
        corrupt.putUUID("InstanceUuid", UUID.randomUUID());
        corrupt.putString("SomethingElse", "keep-me-verbatim");
        // Deliberately missing DeedId/ItemId/OwnerUuid/Status/UpdatedEpochMillis.

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BlessedDeliveryReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(before.toNbt());
        list.add(corrupt);
        list.add(after.toNbt());
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);
        assertFalse(loaded.isReadOnlyFutureSchema(), "one corrupt record must not take the whole store read-only");

        BlessedDeliveryReceiptStore.ScanResult scan = loaded.scan();
        assertEquals(2, scan.pendingDelivery().size(), "the good records on both sides of the corrupt one must survive");
        assertEquals(before, loaded.find(before.instanceUuid()).orElseThrow());
        assertEquals(after, loaded.find(after.instanceUuid()).orElseThrow());

        assertEquals(1, scan.unreadable().size(), "the corrupt record was not surfaced as unreadable at all");
        assertTrue(scan.unreadable().get(0).reason().contains("corrupt"));
        assertEquals("keep-me-verbatim", scan.unreadable().get(0).rawTag().getString("SomethingElse"),
            "the raw tag must be preserved verbatim");
        assertFalse(scan.isEmpty());

        CompoundTag reSaved = loaded.save(new CompoundTag(), null);
        assertEquals(3, reSaved.getList("Receipts", CompoundTag.TAG_COMPOUND).size(),
            "the quarantined tag must round-trip through save() too, not be silently dropped");
    }

    @Test
    void aDuplicateInstanceUuidOnDiskQuarantinesTheSecondCopyRatherThanSilentlyOverwritingTheFirst() {
        UUID instance = UUID.randomUUID();
        BlessedDeliveryReceipt first = receipt(instance, "deed-1", MEDALLION, 10L);
        BlessedDeliveryReceipt impostor = receipt(instance, "deed-2", "britannia_mod:wrong_item", 20L);

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BlessedDeliveryReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(first.toNbt());
        list.add(impostor.toNbt());
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);
        assertEquals(first, loaded.find(instance).orElseThrow(), "the first copy wins; the second must not overwrite it");
        assertEquals(1, loaded.scan().unreadable().size());
    }

    // ---------- future schema ----------

    @Test
    void aFutureSchemaVersionLoadsReadOnlyAndRefusesEveryMutation() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        root.putString("FutureField", "retain-me");

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);

        assertTrue(loaded.isReadOnlyFutureSchema());
        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.READ_ONLY_SCHEMA,
            loaded.record(receipt(UUID.randomUUID(), "deed-1", MEDALLION, 1L)));
        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.READ_ONLY_SCHEMA,
            loaded.markDelivered(UUID.randomUUID(), 1L));
        assertTrue(loaded.scan().pendingDelivery().isEmpty());
        assertTrue(loaded.scan().delivered().isEmpty());
        assertEquals("retain-me", loaded.save(new CompoundTag(), null).getString("FutureField"),
            "a future-schema store must be re-saved verbatim, never rewritten in today's shape");
    }

    // The severe version of the same gap: a receipt that would parse perfectly under today's
    // schema, but is stamped with tomorrow's version, must still be surfaced as needing attention
    // rather than vanishing -- it might be a physically-delivered medallion.
    @Test
    void aFutureSchemaStoreStillSurfacesItsEntriesAsUnreadableRatherThanSilentlyEmpty() {
        BlessedDeliveryReceipt wouldBeValidToday = receipt(UUID.randomUUID(), "deed-1", MEDALLION, 42L);

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        ListTag list = new ListTag();
        list.add(wouldBeValidToday.toNbt());
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore.ScanResult scan = BlessedDeliveryReceiptStore.load(root, null).scan();

        assertTrue(scan.pendingDelivery().isEmpty(), "a future-schema entry must never be treated as a normal receipt");
        assertEquals(1, scan.unreadable().size());
        assertTrue(scan.unreadable().get(0).reason().contains("99"));
        assertFalse(scan.isEmpty());
    }

    @Test
    void aMissingSchemaVersionIsAlsoTreatedAsReadOnlyRatherThanCrashing() {
        CompoundTag root = new CompoundTag();
        root.putString("SomeOtherField", "x");

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);

        assertTrue(loaded.isReadOnlyFutureSchema());
        assertTrue(loaded.scan().pendingDelivery().isEmpty());
        assertEquals(1, loaded.scan().unreadable().size());
    }

    // ---------- scan ----------

    @Test
    void scanSplitsPendingFromDeliveredAndKeepsUnreadableSeparate() {
        UUID pendingInstance = UUID.randomUUID();
        UUID deliveredInstance = UUID.randomUUID();

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BlessedDeliveryReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(receipt(pendingInstance, "deed-pending", MEDALLION, 10L).toNbt());
        list.add(new BlessedDeliveryReceipt(
            deliveredInstance, "deed-delivered", MEDALLION, UUID.randomUUID(),
            BlessedDeliveryReceiptStatus.DELIVERED, 20L).toNbt());
        list.add(new CompoundTag());
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore.ScanResult scan = BlessedDeliveryReceiptStore.load(root, null).scan();

        assertEquals(1, scan.pendingDelivery().size());
        assertEquals(pendingInstance, scan.pendingDelivery().get(0).instanceUuid());
        assertEquals(BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, scan.pendingDelivery().get(0).status());

        assertEquals(1, scan.delivered().size());
        assertEquals(deliveredInstance, scan.delivered().get(0).instanceUuid());
        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, scan.delivered().get(0).status());

        assertEquals(1, scan.unreadable().size());
        assertFalse(scan.isEmpty());

        // Cross-checks: neither real receipt leaked into the other's bucket.
        assertTrue(scan.pendingDelivery().stream().noneMatch(r -> r.instanceUuid().equals(deliveredInstance)));
        assertTrue(scan.delivered().stream().noneMatch(r -> r.instanceUuid().equals(pendingInstance)));
    }

    @Test
    void scanOfAnEmptyStoreIsEmpty() {
        assertTrue(new BlessedDeliveryReceiptStore().scan().isEmpty());
    }

    // ---------- markDestroyed (M7) ----------

    @Test
    void markDestroyedTransitionsADeliveredReceiptAndRestampsTheTimestamp() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        BlessedDeliveryReceipt original = receipt(instance, "deed-1", MEDALLION, 100L);
        store.record(original);
        store.markDelivered(instance, 200L);

        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.MARKED, store.markDestroyed(instance, 300L));

        BlessedDeliveryReceipt destroyed = store.find(instance).orElseThrow();
        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, destroyed.status());
        assertEquals(300L, destroyed.updatedEpochMillis());
        // Identity survives the terminal transition untouched.
        assertEquals(original.instanceUuid(), destroyed.instanceUuid());
        assertEquals(original.deedId(), destroyed.deedId());
        assertEquals(original.itemId(), destroyed.itemId());
        assertEquals(original.ownerUuid(), destroyed.ownerUuid());
    }

    /**
     * The deliberate decision, pinned as a test rather than left to a javadoc nobody re-reads: a
     * still-pending receipt CAN go straight to destroyed. Refusing would strand it in the
     * pendingDelivery bucket -- the delivery-candidate list -- where a reconciliation pass would
     * eventually "finish" a delivery for an item that is already gone, manufacturing a second
     * physical medallion out of a bookkeeping refusal.
     */
    @Test
    void markDestroyedIsAlsoAllowedStraightFromPendingAndEmptiesTheDeliveryCandidateBucket() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        assertEquals(1, store.scan().pendingDelivery().size());

        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.MARKED, store.markDestroyed(instance, 250L));

        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, store.find(instance).orElseThrow().status());
        assertTrue(store.scan().pendingDelivery().isEmpty(),
            "a destroyed receipt must never remain a delivery candidate");
        assertEquals(1, store.scan().destroyed().size());
    }

    @Test
    void markDestroyedIsIdempotentAndDoesNotRestampAnAlreadyDestroyedReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        store.markDelivered(instance, 200L);
        store.markDestroyed(instance, 300L);

        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.ALREADY_DESTROYED,
            store.markDestroyed(instance, 900L));
        assertEquals(300L, store.find(instance).orElseThrow().updatedEpochMillis(),
            "a retried destruction report must preserve the first, true observation time");
        assertEquals(1, store.scan().destroyed().size());
    }

    @Test
    void markDestroyedOnAnUnknownInstanceIsNotFoundAndNeverFabricatesAReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID unknown = UUID.randomUUID();

        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.NOT_FOUND, store.markDestroyed(unknown, 1L));
        assertTrue(store.find(unknown).isEmpty(), "destroying a missing instance must never create a receipt");
        assertTrue(store.scan().isEmpty());
    }

    @Test
    void markDestroyedOnAReadOnlyFutureSchemaStoreWritesNothing() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);

        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.READ_ONLY_SCHEMA,
            loaded.markDestroyed(UUID.randomUUID(), 1L));
        assertTrue(loaded.scan().destroyed().isEmpty());
    }

    // ---------- destruction is terminal ----------

    /**
     * The whole point of the status: a positively-destroyed materialization can never be
     * physically re-delivered. Before DESTROYED existed, markDelivered's "is it already
     * DELIVERED?" test would have quietly walked a destroyed receipt back to DELIVERED.
     */
    @Test
    void markDeliveredRefusesToResurrectADestroyedReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        store.markDelivered(instance, 200L);
        store.markDestroyed(instance, 300L);

        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.ALREADY_DESTROYED,
            store.markDelivered(instance, 900L));
        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, store.find(instance).orElseThrow().status(),
            "destruction is terminal; nothing may transition out of it");
        assertEquals(300L, store.find(instance).orElseThrow().updatedEpochMillis());
    }

    // A Rails replay of the original materialization must not resurrect it either. record()
    // answers IDEMPOTENT_REPLAY (the fingerprint still matches -- status is not part of it), and
    // the caller's own "deliver only if the stored status is still pending" rule is what refuses;
    // this pins that the stored status it will read is still DESTROYED.
    @Test
    void aReplayOfTheOriginalMaterializationDoesNotRegressADestroyedReceipt() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        store.record(new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 100L));
        store.markDelivered(instance, 200L);
        store.markDestroyed(instance, 300L);

        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.IDEMPOTENT_REPLAY, store.record(new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 400L)));

        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, store.find(instance).orElseThrow().status());
        assertEquals(300L, store.find(instance).orElseThrow().updatedEpochMillis());
        assertTrue(store.scan().pendingDelivery().isEmpty(),
            "a replay must never return a destroyed materialization to the delivery-candidate bucket");
    }

    // ---------- DESTROYED persistence and M6 backward compatibility ----------

    @Test
    void aDestroyedReceiptSurvivesTheNbtRoundTripAndStaysTerminal() {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        store.record(receipt(instance, "deed-1", MEDALLION, 100L));
        store.markDelivered(instance, 200L);
        store.markDestroyed(instance, 300L);

        CompoundTag saved = store.save(new CompoundTag(), null);
        assertEquals("DESTROYED",
            saved.getList("Receipts", CompoundTag.TAG_COMPOUND).getCompound(0).getString("Status"),
            "the new status persists as its NAME, exactly like the two before it");
        assertEquals(1, saved.getInt("SchemaVersion"),
            "adding an enum constant is not an on-disk schema change and must not bump the version");

        BlessedDeliveryReceiptStore reloaded = BlessedDeliveryReceiptStore.load(saved, null);
        assertFalse(reloaded.isReadOnlyFutureSchema());
        BlessedDeliveryReceipt destroyed = reloaded.find(instance).orElseThrow();
        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, destroyed.status());
        assertEquals(300L, destroyed.updatedEpochMillis());
        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.ALREADY_DESTROYED,
            reloaded.markDestroyed(instance, 900L));
        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.ALREADY_DESTROYED,
            reloaded.markDelivered(instance, 900L));
    }

    /**
     * The mandatory backward-compatibility proof: a file written by an M6 build -- SchemaVersion
     * 1, and rows whose Status spells only the two names M6 knew -- must load cleanly on this
     * build, with no quarantine, no read-only fallback and no shifted meanings. The tag shape is
     * built here by hand rather than by calling today's toNbt, so the test still fails if a future
     * change to the writer silently redefines the format both sides of the comparison.
     */
    @Test
    void anM6ShapedFileWithNoDestroyedRowsStillLoadsCleanly() {
        UUID pendingInstance = UUID.randomUUID();
        UUID deliveredInstance = UUID.randomUUID();
        UUID pendingOwner = UUID.randomUUID();
        UUID deliveredOwner = UUID.randomUUID();

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 1);
        ListTag list = new ListTag();
        list.add(m6ReceiptTag(pendingInstance, "deed-1", MEDALLION, pendingOwner,
            "PENDING_PHYSICAL_DELIVERY", 111L));
        list.add(m6ReceiptTag(deliveredInstance, "deed-2", MEDALLION, deliveredOwner, "DELIVERED", 222L));
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);

        assertFalse(loaded.isReadOnlyFutureSchema(), "an M6 file must not be treated as an unsupported schema");
        BlessedDeliveryReceiptStore.ScanResult scan = loaded.scan();
        assertTrue(scan.unreadable().isEmpty(), "no M6 row may be quarantined by this build");
        assertTrue(scan.destroyed().isEmpty());

        BlessedDeliveryReceipt pending = loaded.find(pendingInstance).orElseThrow();
        assertEquals(BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, pending.status());
        assertEquals("deed-1", pending.deedId());
        assertEquals(pendingOwner, pending.ownerUuid());
        assertEquals(111L, pending.updatedEpochMillis());

        BlessedDeliveryReceipt delivered = loaded.find(deliveredInstance).orElseThrow();
        assertEquals(BlessedDeliveryReceiptStatus.DELIVERED, delivered.status());
        assertEquals(222L, delivered.updatedEpochMillis());

        assertEquals(1, scan.pendingDelivery().size());
        assertEquals(1, scan.delivered().size());

        // And the M6 file remains fully mutable on this build -- the point of not bumping the
        // schema version is that an upgraded world keeps its crash protection.
        assertEquals(BlessedDeliveryReceiptStore.MarkDeliveredOutcome.MARKED,
            loaded.markDelivered(pendingInstance, 333L));
        assertEquals(BlessedDeliveryReceiptStore.MarkDestroyedOutcome.MARKED,
            loaded.markDestroyed(deliveredInstance, 444L));
    }

    /**
     * The other direction, which is why SCHEMA_VERSION deliberately did NOT move: an M6 jar
     * reading an M7 file hits a Status name it cannot resolve. This build's per-record quarantine
     * is the same code that would run there, so the behaviour is pinned here -- the unknown row is
     * isolated, preserved verbatim and re-saved, and every row beside it still loads. A schema
     * bump would instead have taken that whole store read-only, refusing every future delivery.
     */
    @Test
    void anUnrecognisedStatusNameIsQuarantinedRatherThanCrashingOrTakingTheStoreReadOnly() {
        UUID goodInstance = UUID.randomUUID();
        UUID futureInstance = UUID.randomUUID();

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BlessedDeliveryReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(m6ReceiptTag(goodInstance, "deed-1", MEDALLION, UUID.randomUUID(), "DELIVERED", 10L));
        list.add(m6ReceiptTag(futureInstance, "deed-2", MEDALLION, UUID.randomUUID(), "SOME_FUTURE_STATUS", 20L));
        root.put("Receipts", list);

        BlessedDeliveryReceiptStore loaded = BlessedDeliveryReceiptStore.load(root, null);

        assertFalse(loaded.isReadOnlyFutureSchema());
        assertEquals(1, loaded.scan().delivered().size(), "the readable row beside it must still load");
        assertTrue(loaded.find(futureInstance).isEmpty());
        assertEquals(1, loaded.scan().unreadable().size());
        assertEquals("deed-2", loaded.scan().unreadable().get(0).rawTag().getString("DeedId"),
            "the unreadable row must be preserved verbatim, never dropped");
        assertEquals(2, loaded.save(new CompoundTag(), null)
            .getList("Receipts", CompoundTag.TAG_COMPOUND).size());
    }

    // ---------- scan with three real buckets ----------

    @Test
    void scanBucketsDestroyedSeparatelyFromPendingAndDelivered() {
        UUID pendingInstance = UUID.randomUUID();
        UUID deliveredInstance = UUID.randomUUID();
        UUID destroyedInstance = UUID.randomUUID();

        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        store.record(receipt(pendingInstance, "deed-pending", MEDALLION, 10L));
        store.record(receipt(deliveredInstance, "deed-delivered", MEDALLION, 20L));
        store.record(receipt(destroyedInstance, "deed-destroyed", MEDALLION, 30L));
        store.markDelivered(deliveredInstance, 40L);
        store.markDelivered(destroyedInstance, 50L);
        store.markDestroyed(destroyedInstance, 60L);
        store.addUnreadableEntryForTesting(
            new BlessedDeliveryReceiptStore.UnreadableEntry("corrupt: synthetic", new CompoundTag()));

        BlessedDeliveryReceiptStore.ScanResult scan = store.scan();

        assertEquals(1, scan.pendingDelivery().size());
        assertEquals(pendingInstance, scan.pendingDelivery().get(0).instanceUuid());
        assertEquals(1, scan.delivered().size());
        assertEquals(deliveredInstance, scan.delivered().get(0).instanceUuid());
        assertEquals(1, scan.destroyed().size());
        assertEquals(destroyedInstance, scan.destroyed().get(0).instanceUuid());
        assertEquals(BlessedDeliveryReceiptStatus.DESTROYED, scan.destroyed().get(0).status());
        assertEquals(1, scan.unreadable().size());
        assertFalse(scan.isEmpty());

        // Cross-checks: the destroyed receipt leaked into neither live bucket.
        assertTrue(scan.pendingDelivery().stream().noneMatch(r -> r.instanceUuid().equals(destroyedInstance)));
        assertTrue(scan.delivered().stream().noneMatch(r -> r.instanceUuid().equals(destroyedInstance)));
    }

    // ---------- helpers ----------

    private static void assertFingerprintMismatchLeavesTheStoredReceiptIntact(
            String replayDeedId, String replayItemId, UUID replayOwnerOrNull
    ) {
        BlessedDeliveryReceiptStore store = new BlessedDeliveryReceiptStore();
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        BlessedDeliveryReceipt stored = new BlessedDeliveryReceipt(
            instance, "deed-1", MEDALLION, owner, BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 100L);
        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.CREATED, store.record(stored));

        BlessedDeliveryReceipt conflicting = new BlessedDeliveryReceipt(
            instance, replayDeedId, replayItemId,
            replayOwnerOrNull == null ? owner : replayOwnerOrNull,
            BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, 200L);

        assertEquals(BlessedDeliveryReceiptStore.RecordOutcome.FINGERPRINT_MISMATCH, store.record(conflicting),
            "a replay disagreeing on deedId/itemId/ownerUuid must be refused, not accepted");
        assertEquals(stored, store.find(instance).orElseThrow(),
            "a fingerprint mismatch must never overwrite the stored receipt");
        assertEquals(1, store.scan().pendingDelivery().size(), "a fingerprint mismatch must never add a receipt");
    }

    private static BlessedDeliveryReceipt receipt(UUID instanceUuid, String deedId, String itemId, long updatedAt) {
        return new BlessedDeliveryReceipt(
            instanceUuid, deedId, itemId, UUID.randomUUID(),
            BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, updatedAt);
    }

    /**
     * An M6-era receipt tag built by hand, with the Status written as a raw name string. Used to
     * synthesise a pre-M7 file without routing through today's writer.
     */
    private static CompoundTag m6ReceiptTag(UUID instanceUuid, String deedId, String itemId,
                                            UUID ownerUuid, String statusName, long updatedAt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("InstanceUuid", instanceUuid);
        tag.putString("DeedId", deedId);
        tag.putString("ItemId", itemId);
        tag.putUUID("OwnerUuid", ownerUuid);
        tag.putString("Status", statusName);
        tag.putLong("UpdatedEpochMillis", updatedAt);
        return tag;
    }
}
