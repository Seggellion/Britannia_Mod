package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnMissingPostReconcilerTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final long GRACE = ServiceNpcSpawnMissingPostReconciler.STARTUP_GRACE_MILLIS;
    private static final long WINDOW = ServiceNpcSpawnMissingPostReconciler.MIN_OBSERVATION_INTERVAL_MILLIS;

    @Test
    void singleMissNeverFlags() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler =
            new ServiceNpcSpawnMissingPostReconciler(constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);

        assertNull(data.findMissingPostReport(id));
        assertTrue(reconciler.isSuspectedForTest(id));
    }

    @Test
    void sustainedMissesAcrossSuspicionWindowFlag() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 4L);
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler =
            new ServiceNpcSpawnMissingPostReconciler(constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        assertNull(data.findMissingPostReport(id));

        clock.set(GRACE + WINDOW - 1);
        reconciler.processCycle(data);
        assertNull(data.findMissingPostReport(id), "flagged before the suspicion window elapsed");

        clock.set(GRACE + WINDOW);
        reconciler.processCycle(data);
        ServiceNpcSpawnMissingPostReport report = data.findMissingPostReport(id);
        assertNotNull(report, "sustained absence across the suspicion window did not flag");
        assertEquals(4L, report.revisionAtDetection());
        assertFalse(reconciler.isSuspectedForTest(id));
    }

    @Test
    void startupGraceSuppressesFlagsImmediatelyAfterBoot() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler =
            new ServiceNpcSpawnMissingPostReconciler(constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), clock::get);

        clock.set(GRACE - 1);
        reconciler.processCycle(data);

        assertNull(data.findMissingPostReport(id));
        assertFalse(reconciler.isSuspectedForTest(id), "grace period still recorded suspicion");
    }

    @Test
    void unknownPresenceNeitherConfirmsNorClearsSuspicion() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        Map<UUID, ServiceNpcSpawnMissingPostReconciler.Presence> scripted = new HashMap<>();
        ServiceNpcSpawnMissingPostReconciler reconciler =
            new ServiceNpcSpawnMissingPostReconciler(snapshot -> scripted.get(snapshot.spawnPointId()), clock::get);

        clock.set(GRACE);
        scripted.put(id, ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT);
        reconciler.processCycle(data);
        assertTrue(reconciler.isSuspectedForTest(id));

        clock.set(GRACE + WINDOW + 1_000L);
        scripted.put(id, ServiceNpcSpawnMissingPostReconciler.Presence.UNKNOWN);
        reconciler.processCycle(data);
        assertNull(data.findMissingPostReport(id), "an unloaded/non-ticking observation was treated as confirmation");
        assertTrue(reconciler.isSuspectedForTest(id), "an unknown observation erased prior suspicion");

        scripted.put(id, ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT);
        reconciler.processCycle(data);
        assertNotNull(data.findMissingPostReport(id), "a real absence after unknown gaps did not eventually flag");
    }

    @Test
    void reappearanceClearsSuspicionAndAnyExistingReport() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        assertTrue(data.recordMissingPostReport(new ServiceNpcSpawnMissingPostReport(
            id, "Britannia", location(), 1L, 10L
        )));
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler =
            new ServiceNpcSpawnMissingPostReconciler(constant(ServiceNpcSpawnMissingPostReconciler.Presence.PRESENT), clock::get);
        clock.set(GRACE);

        reconciler.processCycle(data);

        assertFalse(reconciler.isSuspectedForTest(id));
        assertNull(data.findMissingPostReport(id), "reappearance did not clear the existing missing-post report");
    }

    @Test
    void pendingRecordExcludesCandidateFromReconciliation() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        data.put(new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, id, "Britannia", location(),
            UUID.fromString("12345678-1234-4234-8234-123456789abc"), "bank_teller", true, 2L, 20L
        ));
        List<UUID> probed = new ArrayList<>();
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            snapshot -> {
                probed.add(snapshot.spawnPointId());
                return ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT;
            },
            clock::get
        );

        reconciler.processCycle(data);

        assertTrue(probed.isEmpty(), "reconciler probed a spawn point with an in-flight pending record");
    }

    @Test
    void cursorResumesAcrossMultipleBoundedBatches() {
        int total = ServiceNpcSpawnMissingPostReconciler.MAX_CANDIDATES_PER_CYCLE * 2 + 3;
        List<UUID> ids = new ArrayList<>();
        List<ServiceNpcSpawnAcknowledgedRegistration> snapshots = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            snapshots.add(new ServiceNpcSpawnAcknowledgedRegistration(
                id, "Britannia", new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(index, 64, 0)),
                1L, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
            ));
        }
        ServiceNpcSpawnPendingData data = loadWithSnapshots(snapshots);
        List<UUID> probedOrder = new ArrayList<>();
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            snapshot -> {
                probedOrder.add(snapshot.spawnPointId());
                return ServiceNpcSpawnMissingPostReconciler.Presence.PRESENT;
            },
            clock::get
        );
        clock.set(GRACE);

        reconciler.processCycle(data);
        assertEquals(ServiceNpcSpawnMissingPostReconciler.MAX_CANDIDATES_PER_CYCLE, probedOrder.size(),
            "first cycle did not bound its work to MAX_CANDIDATES_PER_CYCLE");

        reconciler.processCycle(data);
        assertEquals(2 * ServiceNpcSpawnMissingPostReconciler.MAX_CANDIDATES_PER_CYCLE, probedOrder.size());

        reconciler.processCycle(data);
        assertEquals(total, probedOrder.size(),
            "cursor did not resume correctly across batches to eventually cover every candidate");

        List<UUID> sortedIds = new ArrayList<>(ids);
        sortedIds.sort(null);
        assertEquals(sortedIds, probedOrder, "candidate scan order was not deterministic across batches");
    }

    private static ServiceNpcSpawnMissingPostReconciler.PresenceProbe constant(
        ServiceNpcSpawnMissingPostReconciler.Presence presence
    ) {
        return snapshot -> presence;
    }

    private static ServiceNpcSpawnPendingData liveSnapshot(UUID id, long revision) {
        return loadWithSnapshots(List.of(new ServiceNpcSpawnAcknowledgedRegistration(
            id, "Britannia", location(), revision, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        )));
    }

    private static ServiceNpcSpawnPendingData loadWithSnapshots(List<ServiceNpcSpawnAcknowledgedRegistration> snapshots) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag list = new ListTag();
        for (ServiceNpcSpawnAcknowledgedRegistration snapshot : snapshots) list.add(snapshot.toNbt());
        root.put("AcknowledgedRegistrations", list);
        return ServiceNpcSpawnPendingData.load(root, null);
    }

    private static ServiceNpcSpawnLocation location() {
        return new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(1, 64, 2));
    }
}
