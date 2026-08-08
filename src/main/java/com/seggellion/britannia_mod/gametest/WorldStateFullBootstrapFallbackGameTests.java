package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import com.seggellion.britannia_mod.worldstate.WorldStateFullBootstrapFallback;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Milestone 13 NeoForge Slice 3: proves {@link WorldStateFullBootstrapFallback} against a real
 * live {@link ServerLevel} -- specifically that a failed fetch (no live Rails endpoint exists in
 * this GameTest environment, exactly like every other real-network call this program's own
 * GameTests have already established) never corrupts the existing durable cache, and that a
 * successful fallback's commit mechanism genuinely writes snapshot and version together.
 *
 * The {@code NoPlayerOnline} branch is deliberately not exercised here: this GameTest server
 * keeps at least one player connected for its entire batched session (confirmed directly -- an
 * earlier version of this file asserted zero players online and failed for exactly that reason),
 * and disconnecting it to force that branch would disrupt every other concurrently-running
 * GameTest, not just this file's own. That branch is proven correct by direct inspection instead:
 * {@link WorldStateFullBootstrapFallback#triggerAndApply}'s early-return for an empty player list
 * returns before referencing {@link ServiceNpcAssignmentsCache} or scheduling any {@code
 * server.execute(...)} work at all, so there is no code path through which it could touch
 * anything -- not a claim resting on a test, a structural guarantee visible directly in the
 * source.
 *
 * {@link ServiceNpcAssignmentsCache} is one single server-wide {@code SavedData} holding one
 * whole-replace snapshot and one version number, not a per-record store like {@code
 * BankTransferReceiptStore} -- there is no way to scope an assertion here to "just this test's own
 * data" the way that store's own docs describe. Every assertion below therefore stays in one
 * continuous, ordered method (this file's own earlier version split them across three methods and
 * genuinely collided under real concurrent GameTest batching, not hypothetically -- exactly the
 * same class of hazard {@code WorldStateSyncGameTests} and {@code WorldStateSyncApplyGameTests}
 * already hit and fixed the same way for their own shared singletons).
 *
 * <h2>Why this file names its own {@code batch}</h2>
 * Keeping the assertions in one method fixed collisions <em>within</em> this file, but not the
 * other half of the same hazard: <b>five other GameTest classes write that same server-wide
 * cache</b>, and tests inside one batch run concurrently. This test writes a baseline, waits
 * forty ticks, and asserts nothing changed -- a window a neighbouring class's write lands in.
 * That is what made it fail; adding banking tests never touched this subsystem, it only changed
 * batch composition until the collision became deterministic rather than occasional.
 *
 * <p>{@code GameTestRunner.runBatch} starts batch {@code n+1} only once batch {@code n} has
 * finished, so a distinct batch name is a genuine mutual-exclusion primitive. Every class that
 * writes this cache now names its own, which serialises them against each other and against the
 * default batch. The three single-test classes among them are thereby fully isolated; the
 * multi-test ones keep the internal arrangement they already pass under.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class WorldStateFullBootstrapFallbackGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID SERVER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private WorldStateFullBootstrapFallbackGameTests() {
    }

    @GameTest(batch = "world_state_fallback", template = TEMPLATE, timeoutTicks = 200)
    public static void aFailedFetchLeavesTheCacheUntouchedAndASuccessfulCommitWritesSnapshotAndVersionTogether(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        // Step 1: a real, non-empty baseline -- version chosen to be implausibly distinctive so
        // this test's own before/after comparisons are meaningful even under this shared cache's
        // real concurrent-GameTest exposure (see class doc).
        UUID spawnPointId = UUID.randomUUID();
        ServiceNpcAssignmentsSnapshot baseline = singleSpawnPointSnapshot(spawnPointId);
        long baselineVersion = 900_000_001L;
        ServiceNpcAssignmentsCache.get(level).replaceFromFullBootstrapFallback(baseline, baselineVersion);
        forceSynchronousFlush(level);

        // Step 2: a failed fetch (no live Rails endpoint is reachable in this GameTest
        // environment) must leave both the live instance and the on-disk file exactly as they
        // were. Observed via runAfterDelay, never a blocking future.get() -- triggerAndApply
        // completes its returned future from inside server.execute(...), and this GameTest method
        // itself already runs on that same tick thread, so blocking synchronously here would
        // deadlock against the very thread that has to run to complete it.
        AtomicReference<WorldStateFullBootstrapFallback.Result> failedResultRef = new AtomicReference<>();
        WorldStateFullBootstrapFallback.triggerAndApply(server, 900_000_099L)
                .whenComplete((result, failure) -> failedResultRef.set(result));

        helper.runAfterDelay(40, () -> {
            check(failedResultRef.get() instanceof WorldStateFullBootstrapFallback.Failed,
                    "expected a real Failed outcome (no live Rails endpoint exists here), got " + failedResultRef.get());
            check(ServiceNpcAssignmentsCache.get(level).lastAppliedWorldStateVersion() == baselineVersion,
                    "the live in-memory version changed after a failed fallback attempt");
            check(ServiceNpcAssignmentsCache.get(level).snapshot().equals(baseline),
                    "the live in-memory snapshot changed after a failed fallback attempt");

            forceSynchronousFlush(level);
            ServiceNpcAssignmentsCache afterFailure = readFreshFromDisk(level);
            check(afterFailure.lastAppliedWorldStateVersion() == baselineVersion,
                    "on-disk version changed after a failed fallback attempt -- exactly the corruption this slice must prevent");
            check(afterFailure.snapshot().equals(baseline), "on-disk snapshot changed after a failed fallback attempt");

            // Step 3: the successful-commit mechanism itself, proven directly -- stands in for
            // "what triggerAndApply does once WorldBootstrapAPI.fetch succeeds" by calling the
            // exact same durable-commit method that success path calls, mirroring
            // WorldStateSyncApplyGameTests' identical split for the ordinary delta-apply path
            // (a genuine HTTP 200 end-to-end cannot be proven here for the same reason it never
            // has been anywhere in this program's GameTests: no live Rails endpoint exists).
            UUID secondSpawnPointId = UUID.randomUUID();
            ServiceNpcAssignmentsSnapshot successSnapshot = singleSpawnPointSnapshot(secondSpawnPointId);
            long successVersion = 900_000_002L;
            ServiceNpcAssignmentsCache.get(level).replaceFromFullBootstrapFallback(successSnapshot, successVersion);
            forceSynchronousFlush(level);

            ServiceNpcAssignmentsCache afterSuccess = readFreshFromDisk(level);
            check(afterSuccess.lastAppliedWorldStateVersion() == successVersion, "version did not survive a fresh reload from disk");
            check(afterSuccess.snapshot().equals(successSnapshot), "snapshot did not survive a fresh reload from disk");

            helper.succeed();
        });
    }

    private static ServiceNpcAssignmentsSnapshot singleSpawnPointSnapshot(UUID spawnPointId) {
        ServiceNpcAssignmentSpawnPointDefinition spawnPoint = new ServiceNpcAssignmentSpawnPointDefinition(
                spawnPointId, SERVER_ID, null, "bank_teller", "Britain", "minecraft:overworld", 1, 1, 1, true, 1L
        );
        return new ServiceNpcAssignmentsSnapshot(
                1, 1L, Map.of(spawnPointId, spawnPoint), Map.<UUID, ServiceNpcAssignmentDefinition>of(),
                Map.<UUID, ServiceNpcAssignmentWorldNpcDefinition>of()
        );
    }

    private static void forceSynchronousFlush(ServerLevel level) {
        level.getServer().overworld().getDataStorage().save();
    }

    private static ServiceNpcAssignmentsCache readFreshFromDisk(ServerLevel level) {
        File dataFile = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(ServiceNpcAssignmentsCache.DATA_NAME + ".dat")
                .toFile();
        check(dataFile.isFile(), "expected a Service NPC assignments cache file on disk at " + dataFile);

        CompoundTag outer;
        try (InputStream input = new FileInputStream(dataFile)) {
            outer = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new IllegalStateException("failed reading Service NPC assignments cache file directly", exception);
        }
        return ServiceNpcAssignmentsCache.load(outer.getCompound("data"), level.registryAccess());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
