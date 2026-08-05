package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import com.seggellion.britannia_mod.worldstate.ServiceNpcAssignmentsCandidateApply;
import com.seggellion.britannia_mod.worldstate.WorldStateChangeRecord;
import com.seggellion.britannia_mod.worldstate.WorldStateChangesClient;
import com.seggellion.britannia_mod.worldstate.WorldStateChangesResponse;
import com.seggellion.britannia_mod.worldstate.WorldStateSyncOutcome;
import com.seggellion.britannia_mod.worldstate.WorldStateSyncPoller;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone 13 NeoForge Slice 3, Steps 2 and 3, against a real live {@link ServerLevel}.
 *
 * STEP 2 (Codex Prompt 13's own named "Rails outage at startup" case): a durable cache already on
 * disk from a prior session must keep serving entity reconciliation correctly while several poll
 * attempts fail and retry on schedule, then recover cleanly once a normal delta arrives -- no
 * entity duplication or loss across the whole window.
 *
 * This test drives the poller through {@link WorldStateSyncPoller#newForTest}, not the real
 * server-registered singleton {@code WorldStateSyncPoller.start}/{@code activePollerForTest}
 * reaches: that singleton is shared for this entire GameTest session, and {@code
 * WorldStateSyncGameTests}' own test already stops it as part of proving Slice 1's lifecycle --
 * relying on it still being alive here would depend on undefined ordering between two unrelated
 * GameTest classes. Using {@code newForTest} with a fake client (deterministic failures, then a
 * real successful delta) wired to the real {@link ServiceNpcAssignmentsCache} proves the same
 * thing without that fragility, and without depending on any live Rails endpoint either.
 *
 * STEP 3's own investigation question -- does the existing indiscriminate reconciler already
 * handle delta-driven changes correctly, without needing new "only affected posts" targeting
 * logic -- is proven directly here too: the recovery delta below closes a live assignment through
 * {@link ServiceNpcAssignmentsCandidateApply}'s real apply path (Slice 2), and {@code
 * ServiceNpcSpawnBlockEntity#serverTick} (unmodified) picks it up and removes the stale entity on
 * the very next tick, exactly as it already does for a direct {@code replace()} in every existing
 * Milestone 6 {@code ServiceNpcAssignmentReconcilerGameTests} scenario.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class WorldStateSyncOutageAndReconciliationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String SHARD = "66666666-6666-4666-8666-666666666666";
    private static final UUID SERVER_ID = UUID.fromString("77777777-7777-4777-8777-777777778888");

    private WorldStateSyncOutageAndReconciliationGameTests() {
    }

    @GameTest(batch = "world_state_outage", template = TEMPLATE, timeoutTicks = 80)
    public static void railsOutageAtStartupServesExistingCacheThroughFailedRetriesThenRecoversViaADeltaWithNoDuplicationOrLoss(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // Step 1: a durable cache already on disk from a prior session -- real content, real
        // version, exactly as Slice 2's own commit mechanism would have left it.
        long priorVersion = 900_000_201L;
        ServiceNpcAssignmentsSnapshot priorSession = singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Outage Banker", "active", 1L, 1L
        );
        ServiceNpcAssignmentsCache.get(level).replaceFromFullBootstrapFallback(priorSession, priorVersion);
        level.getServer().overworld().getDataStorage().save();

        // Step 2: the existing cache serves entity reconciliation correctly before any poll ever
        // happens -- last-known-good, per Milestone 6/8's own established philosophy.
        post.serverTick();
        List<ServiceNpcEntity> initial = currentEntities(level, absolute, spawnPointId);
        check(initial.size() == 1, "existing on-disk cache did not serve reconciliation correctly at startup");
        UUID entityEngineId = initial.get(0).getUUID();

        // Step 3: several real poll attempts, each failing (a fake client standing in for "Rails
        // unreachable" -- this environment has no live Rails endpoint at all, matching every
        // other real-network GameTest in this program), driven through the poller's own real
        // onTick()/firePoll()/complete() logic via newForTest, synchronously (Runnable::run),
        // exactly like this class's own JUnit tests -- the off-tick-thread proof itself is
        // separately covered by WorldStateChangesClientTest and does not need re-proving here.
        AtomicInteger callCount = new AtomicInteger();
        int failuresBeforeRecovery = 3;
        WorldStateChangeRecord assignmentClosed = assignmentClosedChange(assignmentId, spawnPointId, worldNpcId, priorVersion + 1);
        WorldStateChangesResponse recoveryDelta = new WorldStateChangesResponse(
                1, SHARD, priorVersion, priorVersion + 1, priorVersion + 1, false, List.of(assignmentClosed)
        );
        WorldStateChangesClient client = new WorldStateChangesClient(
                // Optional.of(...), not Optional.empty() -- an empty CredentialsProvider makes
                // fetchChangesSince short-circuit with Failure("credentials_unavailable") before
                // ever reaching the TransportSubmitter below, which would make every one of this
                // test's polls fail identically regardless of callCount and never actually
                // exercise the failure-then-recovery sequence this test exists to prove.
                ignored -> Optional.of(com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), UUID.randomUUID()
                )),
                (ignored, task) -> {
                    int attempt = callCount.incrementAndGet();
                    WorldStateChangesClient.Result result = attempt <= failuresBeforeRecovery
                            ? new WorldStateChangesClient.Failure("transport_error")
                            : new WorldStateChangesClient.Success(recoveryDelta);
                    return CompletableFuture.completedFuture(result);
                },
                CancellableHttpRequest::new
        );
        WorldStateSyncPoller.ResponseApplier responseApplier = (server, response) ->
                ServiceNpcAssignmentsCache.get(level).applyWorldStateChanges(response.changes(), response.toVersion());
        WorldStateSyncPoller poller = WorldStateSyncPoller.newForTest(
                client, () -> 0, Runnable::run,
                () -> ServiceNpcAssignmentsCache.get(level).lastAppliedWorldStateVersion(),
                responseApplier,
                (server, targetVersion) -> {
                    throw new AssertionError("fullBootstrapApplier must not be invoked -- this scenario recovers via a delta");
                }
        );

        for (int cycle = 0; cycle < failuresBeforeRecovery; cycle++) {
            fireOnce(poller);
            check(poller.lastOutcomeForTest() instanceof WorldStateSyncOutcome.TransportFailure,
                    "expected a transport failure on retry " + cycle + ", got " + poller.lastOutcomeForTest());
            check(ServiceNpcAssignmentsCache.get(level).lastAppliedWorldStateVersion() == priorVersion,
                    "version advanced despite a failed poll on retry " + cycle);
            check(ServiceNpcAssignmentsCache.get(level).snapshot().equals(priorSession),
                    "cache content changed despite a failed poll on retry " + cycle);

            // Reconciliation must still be serving correctly throughout the whole outage window --
            // the same entity, never duplicated, never lost, on every tick a failed poll leaves
            // behind.
            post.serverTick();
            List<ServiceNpcEntity> stillThere = currentEntities(level, absolute, spawnPointId);
            check(stillThere.size() == 1, "entity duplicated or lost during a failed-poll retry cycle");
            check(stillThere.get(0).getUUID().equals(entityEngineId),
                    "reconciliation spawned a new entity instead of preserving the existing one during the outage");
        }

        // Step 4: recovery -- Rails becomes reachable and this poll returns a real delta (an
        // assignment closure) instead of failing.
        fireOnce(poller);
        check(poller.lastOutcomeForTest() instanceof WorldStateSyncOutcome.Accepted,
                "recovery poll was not accepted, got " + poller.lastOutcomeForTest());
        check(ServiceNpcAssignmentsCache.get(level).lastAppliedWorldStateVersion() == priorVersion + 1,
                "recovery did not durably advance the version to the delta's own to_version");

        // Step 3's own proof: the existing, unmodified reconciler picks up this delta-applied
        // change on the very next tick, exactly as it already does for a direct replace() --
        // no new "only affected posts" targeting logic was needed for this to work correctly.
        post.serverTick();
        check(currentEntities(level, absolute, spawnPointId).isEmpty(),
                "reconciliation did not pick up the delta-applied assignment closure on the next tick "
                        + "-- the existing indiscriminate approach would need scoping logic to fix this, and it does not");

        helper.succeed();
    }

    private static void fireOnce(WorldStateSyncPoller poller) {
        int total = WorldStateSyncPoller.BASE_CADENCE_TICKS;
        for (int tick = 0; tick < total; tick++) {
            poller.onTick();
        }
    }

    private static WorldStateChangeRecord assignmentClosedChange(
            UUID assignmentId, UUID spawnPointId, UUID worldNpcId, long version
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "closed");
        payload.addProperty("spawn_point_public_id", spawnPointId.toString());
        payload.addProperty("world_npc_public_id", worldNpcId.toString());
        return new WorldStateChangeRecord(
                version, "closed", "npc_spawn_assignment", assignmentId.toString(), version, payload, "2026-07-16T12:00:00.000000Z"
        );
    }

    private static ServiceNpcSpawnBlockEntity placePost(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().setPlacedBy(
                level, absolute, level.getBlockState(absolute), null, ItemStack.EMPTY
        );
        return post;
    }

    private static ServiceNpcSpawnBlockEntity requirePost(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post) return post;
        throw new IllegalStateException("missing Service NPC spawn block entity at " + pos);
    }

    private static UUID requireId(ServiceNpcSpawnBlockEntity post) {
        UUID id = post.getSpawnPointId();
        if (id != null) return id;
        throw new IllegalStateException("Service NPC spawn post has no UUID");
    }

    private static ServiceNpcAssignmentsSnapshot singleAssignmentSnapshot(
            UUID spawnPointId, BlockPos absolutePos, UUID assignmentId, UUID worldNpcId,
            String npcName, String status, long assignmentRevision, long worldNpcRevision
    ) {
        ServiceNpcAssignmentSpawnPointDefinition spawnPoint = new ServiceNpcAssignmentSpawnPointDefinition(
                spawnPointId, SERVER_ID, null, "bank_teller",
                "Test Level", "minecraft:overworld",
                absolutePos.getX(), absolutePos.getY(), absolutePos.getZ(), true, 1L
        );
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = new ServiceNpcAssignmentWorldNpcDefinition(
                worldNpcId, npcName, "female", "banker", "bank_teller", worldNpcRevision
        );
        ServiceNpcAssignmentDefinition assignment = new ServiceNpcAssignmentDefinition(
                assignmentId, spawnPointId, worldNpcId, status, assignmentRevision, "2026-07-18T04:38:00.058754Z"
        );
        return new ServiceNpcAssignmentsSnapshot(
                1, assignmentRevision,
                java.util.Map.of(spawnPointId, spawnPoint),
                java.util.Map.of(assignmentId, assignment),
                java.util.Map.of(worldNpcId, worldNpc)
        );
    }

    private static List<ServiceNpcEntity> currentEntities(ServerLevel level, BlockPos pos, UUID spawnPointId) {
        return level.getEntities(
                EntityRegistry.SERVICE_NPC.get(),
                new AABB(pos).inflate(16.0D),
                entity -> spawnPointId.equals(entity.getSpawnPointId()) && entity.isAlive()
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
