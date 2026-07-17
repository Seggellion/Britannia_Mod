package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnSchemaTwoTest.*;

class ServiceNpcSpawnOutboxMutationTest {
    @Test
    void exactTokenProtectsAttemptAndFailureMutations() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord first = upsert(id, 1, 10);
        data.put(first);
        ServiceNpcSpawnPendingOperationToken oldToken = first.token();
        assertTrue(data.markAttemptStarted(oldToken, 100, 200));
        ServiceNpcSpawnPendingRecord attempted = data.snapshot().get(id);
        assertEquals(1, attempted.attemptCount());
        assertEquals(100, attempted.lastAttemptAtEpochMillis());
        assertEquals(first.operationId(), attempted.operationId());
        assertEquals(ServiceNpcSpawnPendingDisposition.RETRY_WAIT, attempted.disposition());

        ServiceNpcSpawnPendingRecord newer = upsert(id, 2, 20);
        data.put(newer);
        newer = data.snapshot().get(id);
        assertFalse(data.markRetryWait(oldToken, "read_timeout", 300));
        assertFalse(data.markPermanentFailure(oldToken, "invalid_city"));
        assertEquals(ServiceNpcSpawnPendingDisposition.READY, data.snapshot().get(id).disposition());
        assertTrue(data.markRetryWait(newer.token(), "read_timeout", 300));
        assertEquals(ServiceNpcSpawnPendingDisposition.RETRY_WAIT, data.snapshot().get(id).disposition());
        assertEquals("read_timeout", data.snapshot().get(id).lastFailureCode());
        assertTrue(data.markPermanentFailure(newer.token(), "invalid_city"));
        assertEquals(ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE, data.snapshot().get(id).disposition());
        assertEquals(0, data.snapshot().get(id).nextAttemptAtEpochMillis());
    }

    @Test
    void invalidMutationMetadataAndAttemptOverflowFailSafely() {
        ServiceNpcSpawnPendingRecord record = upsert(UUID.randomUUID(), 1, 10);
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        data.put(record);
        assertThrows(IllegalArgumentException.class,
            () -> data.markRetryWait(record.token(), "unsafe message!", 20));
        assertThrows(IllegalArgumentException.class,
            () -> data.markAttemptStarted(record.token(), -1, 0));

        ServiceNpcSpawnPendingRecord maxed = new ServiceNpcSpawnPendingRecord(
            record.operation(), record.spawnPointId(), record.shardName(), record.location(),
            record.cityPublicId(), record.serviceNpcTypeKey(), record.enabled(),
            record.configurationRevision(), record.recordedAtEpochMillis(), UUID.randomUUID(),
            ServiceNpcSpawnPendingDisposition.READY, Integer.MAX_VALUE, null, 0, null, null, 0, null
        );
        ServiceNpcSpawnPendingData maxedData = new ServiceNpcSpawnPendingData();
        maxedData.put(maxed);
        assertFalse(maxedData.markAttemptStarted(maxed.token(), 10, 20));
    }

    @Test
    void collisionEvidenceIsExactTokenBoundAndNeverApplied() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord first = upsert(id, 1, 10);
        data.put(first);
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 100
        );
        ServiceNpcSpawnPendingRecord newer = upsert(id, 2, 20);
        data.put(newer);
        newer = data.snapshot().get(id);
        ServiceNpcSpawnPendingOperationToken newerToken = newer.token();
        assertFalse(data.markCollisionRepair(first.token(), evidence, "uuid_collision"));
        assertTrue(data.markCollisionRepair(newerToken, evidence, "uuid_collision"));
        ServiceNpcSpawnPendingRecord stored = data.snapshot().get(id);
        assertEquals(ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR, stored.disposition());
        assertEquals(evidence, stored.collisionEvidence());
        assertEquals(id, stored.spawnPointId());
        assertEquals(0, stored.collisionRepairCount());
        assertThrows(IllegalArgumentException.class, () -> data.markCollisionRepair(
            newerToken, new ServiceNpcSpawnCollisionEvidence(
                ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, false, null, 100
            ), "uuid_collision"
        ));
    }

    @Test
    void upsertSuccessAtomicallyCreatesReceiptAndRemoveSuccessDoesNot() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord upsert = upsert(UUID.randomUUID(), 3, 10);
        data.put(upsert);
        assertTrue(data.acknowledgeSuccess(upsert.token(), success(upsert, ServiceNpcSpawnOutcome.APPLIED)));
        assertFalse(data.snapshot().containsKey(upsert.spawnPointId()));
        ServiceNpcSpawnAcknowledgementReceipt receipt =
            data.findAcknowledgement(upsert.spawnPointId());
        assertNotNull(receipt);
        assertEquals(upsert.operationId(), receipt.operationId());
        assertEquals(upsert.location(), receipt.location());

        ServiceNpcSpawnPendingRecord remove = remove(UUID.randomUUID(), 0, 20);
        data.put(remove);
        assertTrue(data.acknowledgeSuccess(remove.token(), success(remove, ServiceNpcSpawnOutcome.ALREADY_APPLIED)));
        assertFalse(data.snapshot().containsKey(remove.spawnPointId()));
        assertNull(data.findAcknowledgement(remove.spawnPointId()));
    }

    @Test
    void obsoleteOrInconsistentSuccessCannotClearNewerWork() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord first = upsert(id, 1, 10);
        data.put(first);
        ServiceNpcSpawnPendingRecord newer = upsert(id, 2, 20);
        data.put(newer);
        newer = data.snapshot().get(id);
        assertFalse(data.acknowledgeSuccess(first.token(), success(first, ServiceNpcSpawnOutcome.APPLIED)));
        assertEquals(newer.operationId(), data.snapshot().get(id).operationId());
        assertNull(data.findAcknowledgement(id));

        ServiceNpcSpawnProtocolResponse wrongRevision = new ServiceNpcSpawnProtocolResponse(
            1, true, ServiceNpcSpawnOutcome.APPLIED, false, newer.operationId(), id,
            newer.configurationRevision(), newer.configurationRevision() + 1, null,
            ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE, Instant.ofEpochMilli(100),
            true, null, null, null, null, null
        );
        assertFalse(data.acknowledgeSuccess(newer.token(), wrongRevision));
        assertTrue(data.snapshot().containsKey(id));
    }

    @Test
    void receiptRoundTripConsumptionAndNewEditRulesAreExact() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord record = upsert(id, 1, 10);
        data.put(record);
        data.acknowledgeSuccess(record.token(), success(record, ServiceNpcSpawnOutcome.ALREADY_APPLIED));
        CompoundTag saved = data.save(new CompoundTag(), null);
        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(saved, null);
        assertEquals(1, loaded.snapshotAcknowledgements().size());
        assertFalse(loaded.consumeAcknowledgementIfMatches(
            id, new ServiceNpcSpawnLocation("other", location().dimension(), location().pos()),
            1, record.operationId()
        ));
        assertFalse(loaded.consumeAcknowledgementIfMatches(id, location(), 2, record.operationId()));
        assertFalse(loaded.consumeAcknowledgementIfMatches(id, location(), 1, UUID.randomUUID()));
        assertTrue(loaded.consumeAcknowledgementIfMatches(id, location(), 1, record.operationId()));
        assertTrue(loaded.snapshotAcknowledgements().isEmpty());

        ServiceNpcSpawnPendingData edit = ServiceNpcSpawnPendingData.load(saved, null);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, edit.put(upsert(id, 2, 20)));
        assertNull(edit.findAcknowledgement(id));
    }

    @Test
    void higherReceiptWinsAndLowerReceiptCannotReplaceItDuringLoad() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingRecord low = upsert(id, 1, 10);
        ServiceNpcSpawnPendingRecord high = upsert(id, 2, 20);
        ServiceNpcSpawnAcknowledgementReceipt lowReceipt = receipt(low, 100);
        ServiceNpcSpawnAcknowledgementReceipt highReceipt = receipt(high, 200);
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new net.minecraft.nbt.ListTag());
        net.minecraft.nbt.ListTag receipts = new net.minecraft.nbt.ListTag();
        receipts.add(highReceipt.toNbt());
        receipts.add(lowReceipt.toNbt());
        root.put("Acknowledgements", receipts);
        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertEquals(2, loaded.findAcknowledgement(id).acknowledgedRevision());
        assertEquals(2, loaded.save(new CompoundTag(), null)
            .getList("Acknowledgements", CompoundTag.TAG_COMPOUND).size());
    }

    private static ServiceNpcSpawnAcknowledgementReceipt receipt(
        ServiceNpcSpawnPendingRecord record, long acknowledgedAt
    ) {
        return new ServiceNpcSpawnAcknowledgementReceipt(
            record.operationId(), record.spawnPointId(), ServiceNpcSpawnPendingOperation.UPSERT,
            record.configurationRevision(), record.recordedAtEpochMillis(), record.location(),
            record.configurationRevision(), ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE,
            acknowledgedAt, ServiceNpcSpawnOutcome.APPLIED
        );
    }

    private static ServiceNpcSpawnProtocolResponse success(
        ServiceNpcSpawnPendingRecord record, ServiceNpcSpawnOutcome outcome
    ) {
        boolean upsert = record.operation() == ServiceNpcSpawnPendingOperation.UPSERT;
        return new ServiceNpcSpawnProtocolResponse(
            1, true, outcome, false, record.operationId(), record.spawnPointId(),
            record.configurationRevision(), record.configurationRevision(), null,
            upsert ? ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE
                : ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED,
            Instant.ofEpochMilli(100), true, null, null, null, null, null
        );
    }
}
