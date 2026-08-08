package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnSchemaTwoTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void schemaOneUpsertAndRemoveMigrateDeterministicallyAndPreserveIntent() {
        CompoundTag upsert = legacy(upsert(UUID.randomUUID(), 3, 100));
        CompoundTag remove = legacy(remove(UUID.randomUUID(), 0, 200));
        CompoundTag corrupt = new CompoundTag();
        CompoundTag root = root(1, upsert, remove, corrupt);

        ServiceNpcSpawnPendingData first = ServiceNpcSpawnPendingData.load(root.copy(), null);
        ServiceNpcSpawnPendingData second = ServiceNpcSpawnPendingData.load(root.copy(), null);

        assertTrue(first.isDirty());
        assertEquals(2, first.snapshot().size());
        for (ServiceNpcSpawnPendingRecord record : first.snapshot().values()) {
            ServiceNpcSpawnPendingRecord repeated = second.snapshot().get(record.spawnPointId());
            assertEquals(record.operationId(), repeated.operationId());
            assertEquals(ServiceNpcSpawnPendingDisposition.READY, record.disposition());
            assertEquals(0, record.attemptCount());
            assertNull(record.lastAttemptAtEpochMillis());
            assertEquals(0, record.nextAttemptAtEpochMillis());
            assertNull(record.lastFailureCode());
            assertNull(record.supersedesSpawnPointId());
            assertEquals(0, record.collisionRepairCount());
            assertNull(record.collisionEvidence());
            assertTrue(ServiceNpcSpawnOperationRequest.isProtocolUuid(record.operationId()));
        }
        ServiceNpcSpawnPendingRecord migratedUpsert = first.snapshot().values().stream()
            .filter(record -> record.operation() == ServiceNpcSpawnPendingOperation.UPSERT).findFirst().orElseThrow();
        assertEquals("Britannia", migratedUpsert.shardName());
        assertEquals("world", migratedUpsert.location().worldName());
        assertEquals(OVERWORLD, migratedUpsert.location().dimension());
        assertEquals(new BlockPos(1, 64, 2), migratedUpsert.location().pos());
        assertEquals("bank_teller", migratedUpsert.serviceNpcTypeKey());
        assertTrue(migratedUpsert.enabled());
        assertEquals(3, migratedUpsert.configurationRevision());
        assertEquals(100, migratedUpsert.recordedAtEpochMillis());

        CompoundTag saved = first.save(new CompoundTag(), null);
        assertEquals(2, saved.getInt("SchemaVersion"));
        assertTrue(saved.contains("Acknowledgements"));
        assertEquals(3, saved.getList("Records", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void duplicateLegacyUuidAndMalformedRecordStayQuarantined() {
        ServiceNpcSpawnPendingRecord record = upsert(UUID.randomUUID(), 1, 10);
        CompoundTag first = legacy(record);
        CompoundTag duplicate = legacy(record);
        duplicate.putLong("RecordedAtEpochMillis", 11);
        CompoundTag root = root(1, first, duplicate, new CompoundTag());

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertEquals(1, loaded.snapshot().size());
        assertEquals(3, loaded.save(new CompoundTag(), null)
            .getList("Records", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void schemaTwoRoundTripsRetryPermanentCollisionAndOptionalFields() {
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord retry = upsert(UUID.randomUUID(), 1, 10);
        ServiceNpcSpawnPendingRecord permanent = upsert(UUID.randomUUID(), 2, 20);
        ServiceNpcSpawnPendingRecord collision = upsert(UUID.randomUUID(), 3, 30);
        data.put(retry);
        data.put(permanent);
        data.put(collision);
        assertTrue(data.markAttemptStarted(retry.token(), 100, 150));
        assertTrue(data.markRetryWait(retry.token(), "read_timeout", 200));
        assertTrue(data.markPermanentFailure(permanent.token(), "invalid_city"));
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.LIVE, true,
            new ServiceNpcSpawnCanonicalLocation(
                UUID.randomUUID(), "canonical", OVERWORLD, 5, 70, 6
            ), 300
        );
        assertTrue(data.markCollisionRepair(collision.token(), evidence, "uuid_collision"));

        CompoundTag saved = data.save(new CompoundTag(), null);
        ServiceNpcSpawnPendingData reloaded = ServiceNpcSpawnPendingData.load(saved, null);
        assertEquals(ServiceNpcSpawnPendingDisposition.RETRY_WAIT,
            reloaded.snapshot().get(retry.spawnPointId()).disposition());
        assertEquals(1, reloaded.snapshot().get(retry.spawnPointId()).attemptCount());
        assertEquals(100, reloaded.snapshot().get(retry.spawnPointId()).lastAttemptAtEpochMillis());
        assertEquals(200, reloaded.snapshot().get(retry.spawnPointId()).nextAttemptAtEpochMillis());
        assertEquals("read_timeout", reloaded.snapshot().get(retry.spawnPointId()).lastFailureCode());
        assertEquals(ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE,
            reloaded.snapshot().get(permanent.spawnPointId()).disposition());
        assertEquals(evidence, reloaded.snapshot().get(collision.spawnPointId()).collisionEvidence());

        CompoundTag removeTag = remove(UUID.randomUUID(), 0, 40).toNbt();
        assertFalse(removeTag.contains("CityPublicId"));
        assertFalse(removeTag.contains("ServiceNpcTypeKey"));
        assertFalse(removeTag.contains("LastAttemptAtEpochMillis"));
        assertFalse(removeTag.contains("LastFailureCode"));
        assertFalse(removeTag.contains("CollisionEvidence"));
        assertFalse(removeTag.toString().contains("ShardSecret"));
        assertFalse(removeTag.toString().contains("MinecraftServerKey"));
        assertFalse(removeTag.toString().contains("Authorization"));
    }

    @Test
    void malformedSchemaTwoEntriesAreQuarantinedAndFutureRootsRemainUntouched() {
        ServiceNpcSpawnPendingRecord valid = upsert(UUID.randomUUID(), 1, 10);
        CompoundTag unknownDisposition = valid.toNbt();
        unknownDisposition.putString("Disposition", "FUTURE");
        CompoundTag unsafeCode = valid.toNbt();
        unsafeCode.putUUID("SpawnPointId", UUID.randomUUID());
        unsafeCode.putString("LastFailureCode", "unsafe message!");
        CompoundTag malformedReceipt = new CompoundTag();
        CompoundTag root = root(2, valid.toNbt(), unknownDisposition, unsafeCode);
        ListTag acknowledgements = new ListTag();
        acknowledgements.add(malformedReceipt);
        root.put("Acknowledgements", acknowledgements);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertEquals(1, loaded.snapshot().size());
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertEquals(3, saved.getList("Records", CompoundTag.TAG_COMPOUND).size());
        assertEquals(1, saved.getList("Acknowledgements", CompoundTag.TAG_COMPOUND).size());

        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 99);
        future.putString("FutureField", "untouched");
        ServiceNpcSpawnPendingData readOnly = ServiceNpcSpawnPendingData.load(future, null);
        assertTrue(readOnly.isReadOnlyFutureSchema());
        assertEquals("untouched", readOnly.save(new CompoundTag(), null).getString("FutureField"));

        CompoundTag missing = new CompoundTag();
        missing.putString("Unknown", "preserve");
        ServiceNpcSpawnPendingData missingSchema = ServiceNpcSpawnPendingData.load(missing, null);
        assertTrue(missingSchema.isReadOnlyFutureSchema());
        assertEquals("preserve", missingSchema.save(new CompoundTag(), null).getString("Unknown"));
    }

    @Test
    void collectionLimitFailsClosedWithoutPruning() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        ListTag records = new ListTag();
        for (int index = 0; index <= ServiceNpcSpawnPendingData.MAX_COLLECTION_ENTRIES; index++) {
            records.add(new CompoundTag());
        }
        root.put("Records", records);
        root.put("Acknowledgements", new ListTag());
        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());
        assertEquals(records.size(), loaded.save(new CompoundTag(), null)
            .getList("Records", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void operationIdentityAndCompactionRulesAreStable() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord first = upsert(id, 1, 10);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(first));
        UUID firstOperation = data.snapshot().get(id).operationId();
        assertTrue(data.markPermanentFailure(first.token(), "invalid_city"));

        assertEquals(ServiceNpcSpawnPendingData.MutationResult.IDEMPOTENT,
            data.put(upsert(id, 1, 20)));
        assertEquals(firstOperation, data.snapshot().get(id).operationId());
        assertEquals(ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE, data.snapshot().get(id).disposition());
        assertEquals("invalid_city", data.snapshot().get(id).lastFailureCode());

        ServiceNpcSpawnPendingRecord higher = upsert(id, 2, 30);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(higher));
        assertNotEquals(firstOperation, data.snapshot().get(id).operationId());
        assertEquals(ServiceNpcSpawnPendingDisposition.READY, data.snapshot().get(id).disposition());

        ServiceNpcSpawnPendingRecord remove = remove(id, 2, 40);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(remove));
        UUID removeOperation = data.snapshot().get(id).operationId();
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.IDEMPOTENT,
            data.put(remove(id, 2, 40)));
        assertEquals(removeOperation, data.snapshot().get(id).operationId());
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            data.put(remove(id, 3, 50)));
        assertNotEquals(removeOperation, data.snapshot().get(id).operationId());
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.REJECTED_STALE,
            data.put(upsert(id, 4, 60)));
    }

    private static CompoundTag root(int schema, CompoundTag... records) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", schema);
        ListTag list = new ListTag();
        for (CompoundTag record : records) list.add(record);
        root.put("Records", list);
        if (schema == 2) root.put("Acknowledgements", new ListTag());
        return root;
    }

    private static CompoundTag legacy(ServiceNpcSpawnPendingRecord record) {
        CompoundTag tag = record.toNbt();
        tag.remove("OperationId");
        tag.remove("Disposition");
        tag.remove("AttemptCount");
        tag.remove("NextAttemptAtEpochMillis");
        tag.remove("CollisionRepairCount");
        return tag;
    }

    static ServiceNpcSpawnPendingRecord upsert(UUID id, long revision, long timestamp) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, id, "Britannia", location(),
            UUID.fromString("12345678-1234-4234-8234-123456789abc"), "bank_teller", true,
            revision, timestamp
        );
    }

    static ServiceNpcSpawnPendingRecord remove(UUID id, long revision, long timestamp) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.REMOVE, id, "Britannia", location(),
            null, null, true, revision, timestamp
        );
    }

    static ServiceNpcSpawnLocation location() {
        return new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(1, 64, 2));
    }
}
