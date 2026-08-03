package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Server-thread-only, bounded scan over durable LIVE acknowledged registrations that reports
 * (but never removes) posts that have stayed absent across a suspicion window. This is
 * observation only: it never mutates {@link ServiceNpcSpawnAcknowledgedRegistration} state,
 * force-loads a chunk, or scans block entities globally — presence is checked at each
 * candidate's own recorded location, exactly like {@link ServiceNpcSpawnReceiptReconciler}
 * and {@link ServiceNpcSpawnCollisionRepairCoordinator} already do.
 *
 * <p>All work here is a fixed-size, in-memory lookup with no network or disk I/O, so bounding
 * the per-cycle batch ({@link #MAX_CANDIDATES_PER_CYCLE}) is what keeps this from ever being
 * able to hold up the tick thread — there is nothing here that blocks, so there is nothing to
 * move to another thread; the boundedness itself is the guarantee.
 */
public final class ServiceNpcSpawnMissingPostReconciler {
    public static final int MAX_CANDIDATES_PER_CYCLE = 16;
    public static final long STARTUP_GRACE_MILLIS = 120_000L;
    public static final long MIN_OBSERVATION_INTERVAL_MILLIS = 300_000L;

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Presence {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    @FunctionalInterface
    public interface PresenceProbe {
        Presence check(ServiceNpcSpawnAcknowledgedRegistration snapshot);
    }

    /**
     * Where a confirmed missing post is reported onward. Injected exactly like {@link
     * PresenceProbe} so this class keeps its "no network or disk I/O on the tick thread"
     * property: the production implementation hands the report to an off-thread executor and
     * returns immediately, and a test can supply a plain collector.
     */
    @FunctionalInterface
    public interface StalePostReporter {
        void report(ServiceNpcSpawnMissingPostReport report);
    }

    private static final StalePostReporter NO_REPORTING = report -> { };

    private final PresenceProbe probe;
    private final StalePostReporter reporter;
    private final LongSupplier clock;
    private final long startedAtEpochMillis;
    private final Map<UUID, Long> suspectedMissingSinceEpochMillis = new HashMap<>();
    private int cursorIndex;

    /** Detection only, with nothing reported onward -- the shape every existing caller used. */
    public ServiceNpcSpawnMissingPostReconciler(PresenceProbe probe, LongSupplier clock) {
        this(probe, NO_REPORTING, clock);
    }

    public ServiceNpcSpawnMissingPostReconciler(
            PresenceProbe probe, StalePostReporter reporter, LongSupplier clock
    ) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAtEpochMillis = Math.max(0L, clock.getAsLong());
    }

    public void processCycle(ServiceNpcSpawnPendingData data) {
        Objects.requireNonNull(data, "data");
        long now = Math.max(0L, clock.getAsLong());
        if (now - startedAtEpochMillis < STARTUP_GRACE_MILLIS) return;

        List<UUID> candidates = data.snapshotAcknowledgedRegistrations().values().stream()
            .filter(snapshot -> snapshot.state() == ServiceNpcSpawnAcknowledgedRegistration.State.LIVE)
            .map(ServiceNpcSpawnAcknowledgedRegistration::spawnPointId)
            .filter(id -> data.findPending(id) == null)
            .sorted()
            .toList();
        if (candidates.isEmpty()) {
            cursorIndex = 0;
            return;
        }

        int start = cursorIndex % candidates.size();
        int batch = Math.min(MAX_CANDIDATES_PER_CYCLE, candidates.size() - start);
        for (int offset = 0; offset < batch; offset++) {
            evaluate(data, candidates.get(start + offset), now);
        }
        cursorIndex = (start + batch) % candidates.size();
    }

    private void evaluate(ServiceNpcSpawnPendingData data, UUID spawnPointId, long now) {
        ServiceNpcSpawnAcknowledgedRegistration snapshot = data.findAcknowledgedRegistration(spawnPointId);
        if (snapshot == null || snapshot.state() != ServiceNpcSpawnAcknowledgedRegistration.State.LIVE
                || data.findPending(spawnPointId) != null) {
            suspectedMissingSinceEpochMillis.remove(spawnPointId);
            return;
        }

        Presence presence = probe.check(snapshot);
        if (presence == Presence.UNKNOWN) return;
        if (presence == Presence.PRESENT) {
            suspectedMissingSinceEpochMillis.remove(spawnPointId);
            data.clearMissingPostReport(spawnPointId);
            return;
        }

        Long firstMissingAt = suspectedMissingSinceEpochMillis.get(spawnPointId);
        if (firstMissingAt == null) {
            suspectedMissingSinceEpochMillis.put(spawnPointId, now);
            return;
        }
        if (now - firstMissingAt < MIN_OBSERVATION_INTERVAL_MILLIS) return;

        // Carry the FIRST confirmation's timestamp forward rather than stamping each
        // re-confirmation with the current time. Two reasons: "missing since" should answer when
        // this post was first confirmed gone, not when it was last re-checked; and the onward
        // report's identity is derived from that timestamp, so a moving value would make every
        // re-confirmation look like a brand-new observation instead of the same standing one.
        ServiceNpcSpawnMissingPostReport existing = data.findMissingPostReport(spawnPointId);
        long firstConfirmedAt = existing != null ? existing.detectedAtEpochMillis() : now;
        ServiceNpcSpawnMissingPostReport report = new ServiceNpcSpawnMissingPostReport(
                spawnPointId, snapshot.shardName(), snapshot.location(), snapshot.revision(), firstConfirmedAt
        );
        if (data.recordMissingPostReport(report) && existing == null) {
            LOGGER.warn(
                "Service NPC spawn post confirmed missing after sustained absence uuid={} location={} revision={}",
                spawnPointId, snapshot.location(), snapshot.revision()
            );
        }

        // Reported on every confirmation, not only the first. The reconciler re-enters its
        // suspicion window after each one, so a still-missing post naturally re-reports about
        // once per observation interval -- which is what makes a report that could not be
        // delivered (Rails down, executor saturated) simply arrive on a later cycle instead of
        // needing retry machinery here. The receiving side collapses repeats onto one record.
        reporter.report(report);
        suspectedMissingSinceEpochMillis.remove(spawnPointId);
    }

    public static PresenceProbe realProbe(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return snapshot -> {
            ServiceNpcSpawnLocation location = snapshot.location();
            if (!server.getWorldData().getLevelName().equals(location.worldName())) return Presence.UNKNOWN;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location.dimension());
            ServerLevel level = server.getLevel(dimension);
            if (level == null) return Presence.UNKNOWN;
            BlockPos pos = location.pos();
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !level.isPositionEntityTicking(pos)) {
                return Presence.UNKNOWN;
            }
            if (!level.getBlockState(pos).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) return Presence.ABSENT;
            if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)) return Presence.ABSENT;
            return snapshot.spawnPointId().equals(blockEntity.getSpawnPointId()) ? Presence.PRESENT : Presence.ABSENT;
        };
    }

    int cursorIndexForTest() {
        return cursorIndex;
    }

    boolean isSuspectedForTest(UUID spawnPointId) {
        return suspectedMissingSinceEpochMillis.containsKey(spawnPointId);
    }
}
