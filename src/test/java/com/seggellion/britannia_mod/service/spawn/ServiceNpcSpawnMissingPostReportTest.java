package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnSchemaTwoTest.location;

class ServiceNpcSpawnMissingPostReportTest {
    @Test
    void codecRoundTrips() {
        ServiceNpcSpawnMissingPostReport report = new ServiceNpcSpawnMissingPostReport(
            UUID.randomUUID(), "Britannia", location(), 3L, 500L
        );
        assertEquals(report, ServiceNpcSpawnMissingPostReport.fromNbt(report.toNbt()));
    }

    @Test
    void existingSchemaTwoRootWithoutReportsLoadsCleanlyAndFutureSavesIncludeTheField() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertFalse(loaded.isReadOnlyFutureSchema());
        assertTrue(loaded.snapshotMissingPostReports().isEmpty());

        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertTrue(saved.contains("MissingPostReports"));
        assertEquals(0, saved.getList("MissingPostReports", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void recordAndClearRoundTripThroughSaveAndLoad() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = new ServiceNpcSpawnPendingData();
        ServiceNpcSpawnMissingPostReport report = new ServiceNpcSpawnMissingPostReport(
            id, "Britannia", location(), 2L, 100L
        );
        assertTrue(data.recordMissingPostReport(report));
        assertEquals(report, data.findMissingPostReport(id));

        CompoundTag saved = data.save(new CompoundTag(), null);
        ServiceNpcSpawnPendingData reloaded = ServiceNpcSpawnPendingData.load(saved, null);
        assertEquals(report, reloaded.findMissingPostReport(id));

        assertTrue(reloaded.clearMissingPostReport(id));
        assertNull(reloaded.findMissingPostReport(id));
        assertFalse(reloaded.clearMissingPostReport(id));
    }

    @Test
    void malformedReportEntryIsQuarantinedWithoutAffectingValidEntries() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnMissingPostReport valid = new ServiceNpcSpawnMissingPostReport(
            id, "Britannia", location(), 1L, 100L
        );
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag reports = new ListTag();
        reports.add(valid.toNbt());
        reports.add(new CompoundTag());
        root.put("MissingPostReports", reports);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertFalse(loaded.isReadOnlyFutureSchema());
        assertEquals(1, loaded.snapshotMissingPostReports().size());
        assertEquals(valid, loaded.findMissingPostReport(id));

        CompoundTag saved = loaded.save(new CompoundTag(), null);
        assertEquals(2, saved.getList("MissingPostReports", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void duplicateSpawnPointIdIsQuarantined() {
        ServiceNpcSpawnMissingPostReport first = new ServiceNpcSpawnMissingPostReport(
            UUID.fromString("00000000-0000-4000-8000-000000000001"), "Britannia", location(), 1L, 100L
        );
        ServiceNpcSpawnMissingPostReport duplicate = new ServiceNpcSpawnMissingPostReport(
            first.spawnPointId(), "Britannia", location(), 2L, 200L
        );
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag reports = new ListTag();
        reports.add(first.toNbt());
        reports.add(duplicate.toNbt());
        root.put("MissingPostReports", reports);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertEquals(1, loaded.snapshotMissingPostReports().size());
        assertEquals(2, loaded.save(new CompoundTag(), null)
            .getList("MissingPostReports", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void structurallyBrokenReportCollectionFailsClosed() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        root.put("MissingPostReports", new CompoundTag());

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());
    }

    @Test
    void reportCollectionOverLimitFailsClosedWithoutPruning() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag reports = new ListTag();
        for (int index = 0; index <= ServiceNpcSpawnPendingData.MAX_COLLECTION_ENTRIES; index++) {
            reports.add(new CompoundTag());
        }
        root.put("MissingPostReports", reports);

        ServiceNpcSpawnPendingData loaded = ServiceNpcSpawnPendingData.load(root, null);
        assertTrue(loaded.isReadOnlyFutureSchema());
        assertEquals(reports.size(), loaded.save(new CompoundTag(), null)
            .getList("MissingPostReports", CompoundTag.TAG_COMPOUND).size());
    }
}
