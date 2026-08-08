package com.seggellion.britannia_mod.bank.transfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankTransferReceiptStoreTest {

    @Test
    void constructorRejectsNeitherOrBothOfItemPayloadAndCurrencyAmount() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, null, null, null, null,
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, new byte[]{1, 2, 3}, 5L, UUID.randomUUID(), null,
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L));
    }

    @Test
    void constructorRejectsANegativeCurrencyAmountOrTimestamp() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.WITHDRAWAL, null, -1L, null, null,
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.WITHDRAWAL, null, 5L, null, null,
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, -1L));
    }

    @Test
    void constructorRejectsWorldNpcPublicIdPresenceThatDoesNotMatchOperationType() {
        UUID id = UUID.randomUUID();
        // CHEQUE_REDEMPTION without worldNpcPublicId.
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.CHEQUE_REDEMPTION, null, null, null, null,
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L));
        // A non-redemption type carrying worldNpcPublicId.
        assertThrows(IllegalArgumentException.class, () ->
            new BankTransferReceipt(id, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, null, null, null, UUID.randomUUID(),
                BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L));
    }

    @Test
    void aValidRedemptionShapedReceiptSavesAndRoundTripsThroughNbt() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID chequePublicId = UUID.randomUUID();
        BankTransferReceipt receipt = new BankTransferReceipt(
            chequePublicId, UUID.randomUUID(), BankTransferOperationType.CHEQUE_REDEMPTION, null, null, null, UUID.randomUUID(),
            BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 1L
        );

        assertEquals(BankTransferReceiptStore.RecordOutcome.CREATED, store.record(receipt));

        CompoundTag saved = store.save(new CompoundTag(), null);
        BankTransferReceiptStore reloaded = BankTransferReceiptStore.load(saved, null);
        assertEquals(receipt, reloaded.find(chequePublicId));
        assertFalse(reloaded.isReadOnlyFutureSchema());
    }

    @Test
    void itemPayloadIsDefensivelyCopiedBothWaysAndNotSharedWithTheCaller() {
        byte[] source = {1, 2, 3};
        BankTransferReceipt receipt = itemReceipt(UUID.randomUUID(), source, 100L);
        source[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, receipt.itemPayload(), "mutating the caller's array must not affect the stored receipt");

        byte[] first = receipt.itemPayload();
        first[0] = 55;
        assertArrayEquals(new byte[]{1, 2, 3}, receipt.itemPayload(), "mutating a returned copy must not affect the stored receipt");
    }

    @Test
    void recordIsIdempotentForAnIdenticalRetryButRejectsAConflictingRetry() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID id = UUID.randomUUID();
        BankTransferReceipt receipt = itemReceipt(id, new byte[]{1, 2, 3}, 100L);

        assertEquals(BankTransferReceiptStore.RecordOutcome.CREATED, store.record(receipt));
        assertEquals(BankTransferReceiptStore.RecordOutcome.IDEMPOTENT_REPLAY, store.record(receipt));
        assertEquals(1, store.scanUnresolved().pending().size());

        BankTransferReceipt conflicting = itemReceipt(id, new byte[]{9, 9, 9}, 100L);
        assertThrows(IllegalStateException.class, () -> store.record(conflicting));
    }

    @Test
    void resolveRemovesTheReceiptAndIsIdempotentForAMissingOne() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID id = UUID.randomUUID();
        store.record(itemReceipt(id, new byte[]{1, 2, 3}, 100L));

        assertTrue(store.resolve(id));
        assertNull(store.find(id));
        assertTrue(store.scanUnresolved().isEmpty());
        assertTrue(store.scanUnresolved().pending().isEmpty());
        assertFalse(store.resolve(id), "resolving an already-resolved receipt must be a no-op, not an error");
        assertFalse(store.resolve(UUID.randomUUID()), "resolving a receipt that never existed must be a no-op");
    }

    @Test
    void aDepositAndAWithdrawalBothInFlightAreTrackedAndResolvedIndependently() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID depositId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        store.record(itemReceipt(BankTransferOperationType.DEPOSIT, depositId, new byte[]{1}, 10L));
        store.record(itemReceipt(BankTransferOperationType.WITHDRAWAL, withdrawalId, new byte[]{2}, 20L));

        assertEquals(2, store.scanUnresolved().pending().size());

        assertTrue(store.resolve(depositId));

        List<BankTransferReceipt> remaining = store.scanUnresolved().pending();
        assertEquals(1, remaining.size());
        assertEquals(withdrawalId, remaining.get(0).operationId());
        assertEquals(BankTransferOperationType.WITHDRAWAL, remaining.get(0).operationType());
    }

    @Test
    void saveAndLoadRoundTripPreservesEveryField() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID itemOpId = UUID.randomUUID();
        UUID currencyOpId = UUID.randomUUID();
        BankTransferReceipt itemReceipt = itemReceipt(itemOpId, new byte[]{5, 6, 7, 8}, 123L);
        BankTransferReceipt currencyReceipt = new BankTransferReceipt(
            currencyOpId, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, null, 500L, null, null,
            BankTransferReceiptStatus.PENDING_LOCAL_ACTION, 456L
        );
        store.record(itemReceipt);
        store.record(currencyReceipt);

        CompoundTag saved = store.save(new CompoundTag(), null);
        BankTransferReceiptStore reloaded = BankTransferReceiptStore.load(saved, null);

        assertEquals(itemReceipt, reloaded.find(itemOpId));
        assertEquals(currencyReceipt, reloaded.find(currencyOpId));
        assertFalse(reloaded.isReadOnlyFutureSchema());
    }

    @Test
    void corruptReceiptIsQuarantinedNotCrashedAndTheValidOneSurvives() {
        BankTransferReceipt valid = itemReceipt(UUID.randomUUID(), new byte[]{1, 2}, 10L);

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BankTransferReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(valid.toNbt());
        list.add(new CompoundTag());
        root.put("Receipts", list);

        BankTransferReceiptStore loaded = BankTransferReceiptStore.load(root, null);

        assertEquals(1, loaded.scanUnresolved().pending().size());
        assertEquals(valid, loaded.find(valid.operationId()));

        CompoundTag reSaved = loaded.save(new CompoundTag(), null);
        assertEquals(2, reSaved.getList("Receipts", CompoundTag.TAG_COMPOUND).size(),
            "the quarantined tag must round-trip through save() too, not be silently dropped");
    }

    // Part A: a corrupt entry that scanUnresolved() silently excluded would be a real gap --
    // it might represent a real in-flight operation, and the whole point of this store is to
    // surface exactly that. This is a distinct, separately-checked assertion from the test
    // above: not just "the valid receipt survives", but "the corrupt one is not just gone".
    @Test
    void scanUnresolvedSurfacesACorruptEntryAsUnreadableRatherThanSilentlyDroppingIt() {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putUUID("OperationId", UUID.randomUUID());
        // Deliberately missing OperationType/Status/CreatedAtEpochMillis/payload-or-currency.

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BankTransferReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(corrupt);
        root.put("Receipts", list);

        BankTransferReceiptStore.ScanResult scan = BankTransferReceiptStore.load(root, null).scanUnresolved();

        assertTrue(scan.pending().isEmpty(), "a corrupt entry must never be silently treated as a normal pending receipt");
        assertEquals(1, scan.unreadable().size(), "the corrupt receipt was not surfaced as unreadable at all");
        assertFalse(scan.isEmpty(), "a scan with an unreadable entry must not report itself as empty");
        assertTrue(scan.unreadable().get(0).reason().contains("corrupt"));
    }

    @Test
    void unsupportedFutureSchemaVersionIsReadOnlyAndPreservedVerbatim() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        root.putString("FutureField", "retain-me");

        BankTransferReceiptStore loaded = BankTransferReceiptStore.load(root, null);

        assertTrue(loaded.isReadOnlyFutureSchema());
        assertTrue(loaded.scanUnresolved().pending().isEmpty());
        assertEquals(BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA,
            loaded.record(itemReceipt(UUID.randomUUID(), new byte[]{1}, 1L)));
        assertFalse(loaded.resolve(UUID.randomUUID()));
        assertEquals("retain-me", loaded.save(new CompoundTag(), null).getString("FutureField"));
    }

    // Part A, the more severe version of the same gap: an unsupported schema version makes the
    // *whole store* go read-only, and the first draft's readOnly() cleared its records with no
    // trace at all -- meaning every receipt in a future-schema file, including a real in-flight
    // one, would be completely invisible to scanUnresolved(). This proves the fix: a receipt
    // that would parse perfectly cleanly under today's schema, but is stamped with tomorrow's
    // schema version, is still surfaced as needing attention.
    @Test
    void scanUnresolvedSurfacesAnUnsupportedFutureSchemaVersionEntryRatherThanSilentlyDroppingIt() {
        BankTransferReceipt wouldBeValidUnderCurrentSchema = itemReceipt(UUID.randomUUID(), new byte[]{4, 5, 6}, 999L);

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        ListTag list = new ListTag();
        list.add(wouldBeValidUnderCurrentSchema.toNbt());
        root.put("Receipts", list);

        BankTransferReceiptStore loaded = BankTransferReceiptStore.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());

        BankTransferReceiptStore.ScanResult scan = loaded.scanUnresolved();
        assertTrue(scan.pending().isEmpty(), "a future-schema entry must never be silently treated as a normal pending receipt");
        assertEquals(1, scan.unreadable().size(), "the future-schema entry was not surfaced as unreadable at all");
        assertTrue(scan.unreadable().get(0).reason().contains("99"));
    }

    @Test
    void missingSchemaVersionIsAlsoTreatedAsReadOnlyRatherThanCrashing() {
        CompoundTag root = new CompoundTag();
        root.putString("SomeOtherField", "x");

        BankTransferReceiptStore loaded = BankTransferReceiptStore.load(root, null);

        assertTrue(loaded.isReadOnlyFutureSchema());
        assertTrue(loaded.scanUnresolved().pending().isEmpty());
    }

    // ---------- Part A: RECONCILIATION_REQUIRED escalation ----------

    @Test
    void escalateToReconciliationRequiredTransitionsAnExistingReceiptAndIsIdempotent() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID id = UUID.randomUUID();
        store.record(itemReceipt(id, new byte[]{1, 2, 3}, 100L));

        assertEquals(BankTransferReceiptStore.EscalateOutcome.ESCALATED, store.escalateToReconciliationRequired(id));
        assertEquals(BankTransferReceiptStatus.RECONCILIATION_REQUIRED, store.find(id).status(),
            "escalation must actually change the stored receipt's status");

        assertEquals(BankTransferReceiptStore.EscalateOutcome.ALREADY_ESCALATED, store.escalateToReconciliationRequired(id),
            "escalating an already-escalated receipt must be a no-op, not an error");
        assertEquals(BankTransferReceiptStatus.RECONCILIATION_REQUIRED, store.find(id).status());
    }

    @Test
    void escalateToReconciliationRequiredReportsNotFoundForAMissingOperationRatherThanFabricatingAReceipt() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID id = UUID.randomUUID();

        assertEquals(BankTransferReceiptStore.EscalateOutcome.NOT_FOUND, store.escalateToReconciliationRequired(id));
        assertNull(store.find(id), "escalating a missing operation must never create a receipt");
    }

    @Test
    void escalateToReconciliationRequiredPreservesEveryOtherFieldOfTheReceipt() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID id = UUID.randomUUID();
        BankTransferReceipt original = itemReceipt(BankTransferOperationType.WITHDRAWAL, id, new byte[]{9, 8, 7}, 555L);
        store.record(original);

        store.escalateToReconciliationRequired(id);
        BankTransferReceipt escalated = store.find(id);

        assertEquals(original.operationId(), escalated.operationId());
        assertEquals(original.operationType(), escalated.operationType());
        assertArrayEquals(original.itemPayload(), escalated.itemPayload());
        assertEquals(original.createdAtEpochMillis(), escalated.createdAtEpochMillis());
        assertEquals(BankTransferReceiptStatus.RECONCILIATION_REQUIRED, escalated.status());
    }

    @Test
    void escalateToReconciliationRequiredIsANoOpOnAReadOnlyFutureSchemaStore() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 99);
        BankTransferReceiptStore loaded = BankTransferReceiptStore.load(root, null);

        assertEquals(BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA,
            loaded.escalateToReconciliationRequired(UUID.randomUUID()));
    }

    // Part A: proves scanUnresolved() actually separates all three categories when a store
    // contains one of each simultaneously, not just that each category works in isolation
    // (already covered by the tests above and the pre-existing corrupt/future-schema tests).
    @Test
    void scanUnresolvedSeparatesPendingReconciliationRequiredAndUnreadableIntoDistinctBuckets() {
        BankTransferReceiptStore store = new BankTransferReceiptStore();
        UUID pendingId = UUID.randomUUID();
        UUID reconciliationRequiredId = UUID.randomUUID();

        store.record(itemReceipt(pendingId, new byte[]{1}, 10L));
        store.record(itemReceipt(reconciliationRequiredId, new byte[]{2}, 20L));
        assertEquals(BankTransferReceiptStore.EscalateOutcome.ESCALATED,
            store.escalateToReconciliationRequired(reconciliationRequiredId));

        CompoundTag corrupt = new CompoundTag();
        corrupt.putUUID("OperationId", UUID.randomUUID());
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", BankTransferReceiptStore.SCHEMA_VERSION);
        ListTag list = new ListTag();
        list.add(itemReceipt(pendingId, new byte[]{1}, 10L).toNbt());
        BankTransferReceipt reconciliationRequiredReceipt = new BankTransferReceipt(
            reconciliationRequiredId, UUID.randomUUID(), BankTransferOperationType.DEPOSIT, new byte[]{2}, null, UUID.randomUUID(), null,
            BankTransferReceiptStatus.RECONCILIATION_REQUIRED, 20L
        );
        list.add(reconciliationRequiredReceipt.toNbt());
        list.add(corrupt);
        root.put("Receipts", list);

        BankTransferReceiptStore.ScanResult scan = BankTransferReceiptStore.load(root, null).scanUnresolved();

        assertEquals(1, scan.pending().size(), "expected exactly one ordinary pending receipt");
        assertEquals(pendingId, scan.pending().get(0).operationId());

        assertEquals(1, scan.reconciliationRequired().size(), "expected exactly one reconciliation_required receipt");
        assertEquals(reconciliationRequiredId, scan.reconciliationRequired().get(0).operationId());
        assertEquals(BankTransferReceiptStatus.RECONCILIATION_REQUIRED, scan.reconciliationRequired().get(0).status());

        assertEquals(1, scan.unreadable().size(), "expected exactly one unreadable entry");
        assertFalse(scan.isEmpty());

        // Cross-checks: neither real receipt leaked into the other's bucket.
        assertTrue(scan.pending().stream().noneMatch(r -> r.operationId().equals(reconciliationRequiredId)));
        assertTrue(scan.reconciliationRequired().stream().noneMatch(r -> r.operationId().equals(pendingId)));
    }

    private static BankTransferReceipt itemReceipt(UUID operationId, byte[] payload, long createdAt) {
        return itemReceipt(BankTransferOperationType.DEPOSIT, operationId, payload, createdAt);
    }

    private static BankTransferReceipt itemReceipt(
            BankTransferOperationType type, UUID operationId, byte[] payload, long createdAt
    ) {
        return new BankTransferReceipt(
            operationId, UUID.randomUUID(), type, payload, null, UUID.randomUUID(), null,
            BankTransferReceiptStatus.PENDING_LOCAL_ACTION, createdAt
        );
    }
}
