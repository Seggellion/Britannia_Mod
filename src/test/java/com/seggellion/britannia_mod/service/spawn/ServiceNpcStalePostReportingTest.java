package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: the missing-post reconciler now reports a confirmed-missing post onward. These
 * tests pin the two properties that matter for the receiving side: nothing is reported until the
 * suspicion window has genuinely elapsed, and a post that stays missing keeps reporting the same
 * observation identity rather than looking like a brand-new one every cycle.
 */
class ServiceNpcStalePostReportingTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final long GRACE = ServiceNpcSpawnMissingPostReconciler.STARTUP_GRACE_MILLIS;
    private static final long WINDOW = ServiceNpcSpawnMissingPostReconciler.MIN_OBSERVATION_INTERVAL_MILLIS;

    @Test
    void nothingIsReportedBeforeTheSuspicionWindowElapses() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        List<ServiceNpcSpawnMissingPostReport> reported = new ArrayList<>();
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), reported::add, clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        assertTrue(reported.isEmpty(), "a single miss must never be reported");

        clock.set(GRACE + WINDOW - 1);
        reconciler.processCycle(data);
        assertTrue(reported.isEmpty(), "reported before the suspicion window had elapsed");
    }

    @Test
    void aGenuinelyMissingPostIsReportedOnceTheWindowElapses() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 7L);
        AtomicLong clock = new AtomicLong(0L);
        List<ServiceNpcSpawnMissingPostReport> reported = new ArrayList<>();
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), reported::add, clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        clock.set(GRACE + WINDOW);
        reconciler.processCycle(data);

        assertEquals(1, reported.size(), "a sustained absence was not reported");
        assertEquals(id, reported.get(0).spawnPointId());
        assertEquals(7L, reported.get(0).revisionAtDetection());
    }

    @Test
    void aStillMissingPostKeepsReportingTheSameObservationIdentity() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        List<ServiceNpcSpawnMissingPostReport> reported = new ArrayList<>();
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), reported::add, clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        clock.set(GRACE + WINDOW);
        reconciler.processCycle(data);
        long firstConfirmedAt = reported.get(0).detectedAtEpochMillis();

        // Two more full observation cycles while the post stays missing.
        for (int cycle = 1; cycle <= 2; cycle++) {
            clock.set(GRACE + WINDOW * (cycle * 2L));
            reconciler.processCycle(data);
            clock.set(GRACE + WINDOW * (cycle * 2L + 1));
            reconciler.processCycle(data);
        }

        assertEquals(3, reported.size(), "a still-missing post must keep reporting, so a lost report recovers");
        assertTrue(
            reported.stream().allMatch(report -> report.detectedAtEpochMillis() == firstConfirmedAt),
            "every repeat must carry the FIRST confirmation time, or the receiving side cannot "
                + "tell a standing observation from a brand-new one"
        );
        assertEquals(
            firstConfirmedAt, data.findMissingPostReport(id).detectedAtEpochMillis(),
            "the durable local record must also keep the first confirmation time"
        );
    }

    @Test
    void aPostThatReappearsStopsBeingReported() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 1L);
        AtomicLong clock = new AtomicLong(0L);
        List<ServiceNpcSpawnMissingPostReport> reported = new ArrayList<>();
        boolean[] present = { false };
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            snapshot -> present[0]
                ? ServiceNpcSpawnMissingPostReconciler.Presence.PRESENT
                : ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT,
            reported::add, clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        clock.set(GRACE + WINDOW);
        reconciler.processCycle(data);
        assertEquals(1, reported.size());

        present[0] = true;
        clock.set(GRACE + WINDOW * 4);
        reconciler.processCycle(data);
        clock.set(GRACE + WINDOW * 8);
        reconciler.processCycle(data);

        assertEquals(1, reported.size(), "a post that came back must stop being reported");
        assertNull(data.findMissingPostReport(id), "the durable local record must be cleared on reappearance");
    }

    @Test
    void reportingNeverRemovesOrMutatesTheRegistration() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnPendingData data = liveSnapshot(id, 3L);
        AtomicLong clock = new AtomicLong(0L);
        ServiceNpcSpawnMissingPostReconciler reconciler = new ServiceNpcSpawnMissingPostReconciler(
            constant(ServiceNpcSpawnMissingPostReconciler.Presence.ABSENT), report -> { }, clock::get);

        clock.set(GRACE);
        reconciler.processCycle(data);
        clock.set(GRACE + WINDOW);
        reconciler.processCycle(data);

        ServiceNpcSpawnAcknowledgedRegistration snapshot = data.findAcknowledgedRegistration(id);
        assertNotNull(snapshot, "confirming a post missing must never delete its registration");
        assertEquals(
            ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, snapshot.state(),
            "confirming a post missing must never change its registration state"
        );
    }

    @Test
    void theSerializedReportCarriesTheObservationRailsNeeds() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnMissingPostReport report = new ServiceNpcSpawnMissingPostReport(
            id, "Alpha",
            new ServiceNpcSpawnLocation("Britannia", OVERWORLD, new BlockPos(12, 68, -34)),
            5L, 1_700_000_000_000L
        );

        String body = new String(ServiceNpcStalePostReportClient.serialize(report), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(body.contains(id.toString()), "the report must identify the post");
        assertTrue(body.contains("\"world_name\":\"Britannia\""));
        assertTrue(body.contains("\"dimension\":\"minecraft:overworld\""));
        assertTrue(body.contains("\"x\":12"));
        assertTrue(body.contains("\"y\":68"));
        assertTrue(body.contains("\"z\":-34"));
        assertTrue(body.contains("observed_missing_since"), "the observation time is what makes repeats collapse");
        // A report is evidence, never an instruction: it must not carry an operation of any kind.
        assertFalse(body.contains("operation"), "a report must never look like an operation request");
        assertFalse(body.contains("REMOVE"), "a report must never ask for a removal");
    }

    private static ServiceNpcSpawnMissingPostReconciler.PresenceProbe constant(
        ServiceNpcSpawnMissingPostReconciler.Presence presence
    ) {
        return snapshot -> presence;
    }

    private static ServiceNpcSpawnPendingData liveSnapshot(UUID id, long revision) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        root.put("Records", new ListTag());
        root.put("Acknowledgements", new ListTag());
        ListTag registrations = new ListTag();
        registrations.add(new ServiceNpcSpawnAcknowledgedRegistration(
            id, "Britannia",
            new ServiceNpcSpawnLocation("Britannia", OVERWORLD, new BlockPos(12, 68, -34)),
            revision, ServiceNpcSpawnAcknowledgedRegistration.State.LIVE, 100L
        ).toNbt());
        root.put("AcknowledgedRegistrations", registrations);
        return ServiceNpcSpawnPendingData.load(root, null);
    }
}
