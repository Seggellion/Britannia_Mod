package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnSchemaTwoTest.location;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnSchemaTwoTest.remove;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnSchemaTwoTest.upsert;

class ServiceNpcSpawnAcknowledgedRegistrationTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void codecRoundTripsLiveAndRemovedStates() {
        ServiceNpcSpawnAcknowledgedRegistration live = new ServiceNpcSpawnAcknowledgedRegistration(
            UUID.randomUUID(), "Britannia", location(), 3L,
            ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        );
        assertEquals(live, ServiceNpcSpawnAcknowledgedRegistration.fromNbt(live.toNbt()));

        ServiceNpcSpawnAcknowledgedRegistration removed = new ServiceNpcSpawnAcknowledgedRegistration(
            UUID.randomUUID(), "Britannia", location(), 3L,
            ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED, 200L
        );
        assertEquals(removed, ServiceNpcSpawnAcknowledgedRegistration.fromNbt(removed.toNbt()));
    }

    @Test
    void existingSchemaTwoRootWithoutSnapshotsLoadsCleanlyAndFutureSavesIncludeTheField() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertFalse(loaded.isReadOnlyFutureSchema());
        assertTrue(loaded.snapshotAcknowledgedRegistrations().isEmpty());

        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertTrue(saved.contains("AcknowledgedRegistrations"));
        assertEquals(0, saved.getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void malformedSnapshotEntryIsQuarantinedWithoutAffectingValidEntries() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnAcknowledgedRegistration valid = new ServiceNpcSpawnAcknowledgedRegistration(
            id, "Britannia", location(), 1L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        );
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag snapshots = new ListTag();
        snapshots.add(valid.toNbt());
        snapshots.add(new CompoundTag());
        root.put("AcknowledgedRegistrations", snapshots);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertFalse(loaded.isReadOnlyFutureSchema());
        assertEquals(1, loaded.snapshotAcknowledgedRegistrations().size());
        assertEquals(valid, loaded.findAcknowledgedRegistration(id));

        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertEquals(2, saved.getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void structurallyBrokenSnapshotCollectionFailsClosed() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        root.put("AcknowledgedRegistrations", new CompoundTag());

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());
    }

    @Test
    void snapshotCollectionOverLimitFailsClosedWithoutPruning() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag snapshots = new ListTag();
        for (int index = 0; index <= ServiceNpcSpawnPendingData.MAX_COLLECTION_ENTRIES; index++) {
            snapshots.add(new CompoundTag());
        }
        root.put("AcknowledgedRegistrations", snapshots);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());
        assertEquals(snapshots.size(), loaded.save(new CompoundTag(), null)
            .getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void higherRevisionWinsOverLowerRevisionDuplicate() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnAcknowledgedRegistration high = new ServiceNpcSpawnAcknowledgedRegistration(
            id, "Britannia", location(), 2L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 200L
        );
        ServiceNpcSpawnAcknowledgedRegistration low = new ServiceNpcSpawnAcknowledgedRegistration(
            id, "Britannia", location(), 1L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        );
        ServiceNpcSpawnPendingData loaded = loadWithSnapshots(high, low);

        assertEquals(2L, loaded.findAcknowledgedRegistration(id).revision());
        assertEquals(2, loaded.save(new CompoundTag(), null)
            .getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void equalRevisionRemovedSupersedesLiveButNotViceVersa() {
        UUID removeWinsId = UUID.randomUUID();
        ServiceNpcSpawnAcknowledgedRegistration liveFirst = new ServiceNpcSpawnAcknowledgedRegistration(
            removeWinsId, "Britannia", location(), 3L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        );
        ServiceNpcSpawnAcknowledgedRegistration removedSecond = new ServiceNpcSpawnAcknowledgedRegistration(
            removeWinsId, "Britannia", location(), 3L, ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED, 150L
        );
        ServiceNpcSpawnPendingData removeWins = loadWithSnapshots(liveFirst, removedSecond);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED,
            removeWins.findAcknowledgedRegistration(removeWinsId).state());
        assertEquals(1, removeWins.save(new CompoundTag(), null)
            .getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());

        UUID staysRemovedId = UUID.randomUUID();
        ServiceNpcSpawnAcknowledgedRegistration removedFirst = new ServiceNpcSpawnAcknowledgedRegistration(
            staysRemovedId, "Britannia", location(), 3L, ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED, 100L
        );
        ServiceNpcSpawnAcknowledgedRegistration liveSecond = new ServiceNpcSpawnAcknowledgedRegistration(
            staysRemovedId, "Britannia", location(), 3L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 150L
        );
        ServiceNpcSpawnPendingData staysRemoved = loadWithSnapshots(removedFirst, liveSecond);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED,
            staysRemoved.findAcknowledgedRegistration(staysRemovedId).state());
        assertEquals(2, staysRemoved.save(new CompoundTag(), null)
            .getList("AcknowledgedRegistrations", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void upsertAcknowledgementWritesLiveSnapshotBeforeReceiptConsumption() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord record = upsert(id, 3, 10);
        data.put(record);
        assertTrue(data.acknowledgeSuccess(record.token(), success(record, ServiceNpcSpawnOutcome.APPLIED)));

        assertNotNull(data.findAcknowledgement(id));
        ServiceNpcSpawnAcknowledgedRegistration snapshot = data.findAcknowledgedRegistration(id);
        assertNotNull(snapshot);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, snapshot.state());
        assertEquals(3L, snapshot.revision());
        assertEquals(record.location(), snapshot.location());
        assertEquals(record.shardName(), snapshot.shardName());

        assertTrue(data.consumeAcknowledgementIfMatches(id, record.location(), 3L, record.operationId()));
        assertNull(data.findAcknowledgement(id));
        ServiceNpcSpawnAcknowledgedRegistration retained = data.findAcknowledgedRegistration(id);
        assertNotNull(retained);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, retained.state());
        assertEquals(3L, retained.revision());
    }

    @Test
    void removeAcknowledgementTransitionsSnapshotToRemoved() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord registration = upsert(id, 3, 10);
        data.put(registration);
        assertTrue(data.acknowledgeSuccess(registration.token(), success(registration, ServiceNpcSpawnOutcome.APPLIED)));
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.LIVE,
            data.findAcknowledgedRegistration(id).state());

        ServiceNpcSpawnPendingRecord removal = remove(id, 3, 20);
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, data.put(removal));
        ServiceNpcSpawnPendingRecord storedRemoval = data.snapshot().get(id);
        assertTrue(data.acknowledgeSuccess(storedRemoval.token(), success(storedRemoval, ServiceNpcSpawnOutcome.APPLIED)));

        ServiceNpcSpawnAcknowledgedRegistration snapshot = data.findAcknowledgedRegistration(id);
        assertNotNull(snapshot);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.REMOVED, snapshot.state());
        assertEquals(3L, snapshot.revision());
        assertNull(data.findAcknowledgement(id));
    }

    @Test
    void collisionReplacementLeavesOldSnapshotUntouchedAndReplacementGetsItsOwnSnapshot() {
        UUID original = UUID.randomUUID();
        UUID citySame = UUID.fromString("12345678-1234-4234-8234-123456789abc");
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnPendingRecord firstUpsert = upsert(original, 1, 10);
        data.put(firstUpsert);
        assertTrue(data.acknowledgeSuccess(firstUpsert.token(), success(firstUpsert, ServiceNpcSpawnOutcome.APPLIED)));
        ServiceNpcSpawnAcknowledgedRegistration originalSnapshot = data.findAcknowledgedRegistration(original);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, originalSnapshot.state());

        ServiceNpcSpawnPendingRecord secondUpsert = upsert(original, 2, 20);
        data.put(secondUpsert);
        ServiceNpcSpawnPendingRecord staged = data.snapshot().get(original);
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED, true, null, 30L
        );
        assertTrue(data.markCollisionRepair(staged.token(), evidence, "uuid_collision"));
        ServiceNpcSpawnPendingRecord collisionRecord = data.snapshot().get(original);
        UUID replacement = UUID.randomUUID();
        ServiceNpcSpawnPendingData.CollisionReplacementStage stage = data.stageCollisionReplacement(
            collisionRecord.token(), evidence, replacement, UUID.randomUUID(),
            location(), citySame, "bank_teller", true, 40L
        );
        assertEquals(ServiceNpcSpawnPendingData.MutationResult.ACCEPTED, stage.result());

        assertEquals(originalSnapshot, data.findAcknowledgedRegistration(original));
        assertNull(data.findAcknowledgedRegistration(replacement));

        ServiceNpcSpawnPendingRecord replacementRecord = data.snapshot().get(replacement);
        assertTrue(data.acknowledgeSuccess(
            replacementRecord.token(), success(replacementRecord, ServiceNpcSpawnOutcome.APPLIED)
        ));
        ServiceNpcSpawnAcknowledgedRegistration replacementSnapshot = data.findAcknowledgedRegistration(replacement);
        assertNotNull(replacementSnapshot);
        assertEquals(ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, replacementSnapshot.state());
        assertEquals(1L, replacementSnapshot.revision());
        assertEquals(originalSnapshot, data.findAcknowledgedRegistration(original));
    }

    private static ServiceNpcSpawnPendingData loadWithSnapshots(ServiceNpcSpawnAcknowledgedRegistration... snapshots) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag list = new ListTag();
        for (ServiceNpcSpawnAcknowledgedRegistration snapshot : snapshots) list.add(snapshot.toNbt());
        root.put("AcknowledgedRegistrations", list);
        return ServiceNpcSpawnPendingData.load(root, null);
    }

    private static ServiceNpcSpawnProtocolResponse success(
        ServiceNpcSpawnPendingRecord record, ServiceNpcSpawnOutcome outcome
    ) {
        boolean isUpsert = record.operation() == ServiceNpcSpawnPendingOperation.UPSERT;
        return new ServiceNpcSpawnProtocolResponse(
            1, true, outcome, false, record.operationId(), record.spawnPointId(),
            record.configurationRevision(), record.configurationRevision(), null,
            isUpsert ? ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE
                : ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED,
            Instant.ofEpochMilli(100), true, null, null, null, null, null
        );
    }
}
