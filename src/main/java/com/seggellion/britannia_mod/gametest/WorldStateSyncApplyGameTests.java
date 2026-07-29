package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.worldstate.ServiceNpcAssignmentsCandidateApply;
import com.seggellion.britannia_mod.worldstate.WorldStateChangeRecord;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 13 NeoForge Slice 2: proves {@link ServiceNpcAssignmentsCache#applyWorldStateChanges}
 * against a real live {@link ServerLevel} and a real filesystem -- not just the in-memory pure
 * transformation ({@code ServiceNpcAssignmentsCandidateApplyTest} already covers that with no
 * live server at all). {@code ServiceNpcAssignmentsCacheGameTests} (Milestone 6) already covers
 * this same cache's bootstrap-era save/load/discard-to-empty behavior; this class is additive,
 * not a replacement, and does not touch that file.
 *
 * "Simulating a crash" here follows exactly the technique {@code BankTransferReceiptGameTests}
 * already established for this program's identical durability mechanism: force the real write
 * through {@link net.minecraft.world.level.storage.DimensionDataStorage#save()}, then reconstruct
 * a brand-new {@link ServiceNpcAssignmentsCache} by reading the raw bytes directly off disk with
 * a fresh {@code NbtIo} read, deliberately bypassing the live, still-cached in-memory instance.
 * See that class's own docs for exactly what this does and does not prove.
 *
 * {@link ServiceNpcAssignmentsCache} is one single server-wide {@code SavedData}, not
 * one-per-structure -- exactly the same sharing hazard {@code WorldStateSyncGameTests} already
 * hit and fixed for the poller's own server-wide registry. Every assertion below therefore stays
 * in one continuous, ordered method against the one shared instance, so a sibling method can never
 * interleave a commit of its own into the middle of this method's own before/after comparisons.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class WorldStateSyncApplyGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID OWNING_SERVER = UUID.fromString("77777777-7777-4777-8777-777777777777");

    private WorldStateSyncApplyGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void applyRejectionLeavesDiskUntouchedAndIdempotentReapplicationMatchesOnDiskToo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServiceNpcAssignmentsCache cache = ServiceNpcAssignmentsCache.get(level);
        UUID spawnPointId = UUID.randomUUID();
        List<WorldStateChangeRecord> validBatch = List.of(spawnPointCreated(spawnPointId, 1));

        // Step 1: a valid batch commits snapshot and version together, durably -- proven by
        // reading fresh from disk, not from anything still resident in this process's memory.
        ServiceNpcAssignmentsCandidateApply.Result firstResult = cache.applyWorldStateChanges(validBatch, 10L);
        check(firstResult instanceof ServiceNpcAssignmentsCandidateApply.Applied, "expected the valid batch to apply cleanly");
        forceSynchronousFlush(level);
        ServiceNpcAssignmentsCache afterFirstCommit = readFreshFromDisk(level);
        check(afterFirstCommit.lastAppliedWorldStateVersion() == 10L, "version did not survive a fresh reload from disk");
        check(afterFirstCommit.snapshot().spawnPoints().containsKey(spawnPointId), "snapshot content did not survive a fresh reload from disk");
        int spawnPointCountAfterFirstCommit = afterFirstCommit.snapshot().spawnPoints().size();

        // Step 2: a batch this class's own candidate-apply rejects (an unrecognized
        // change_type -- case 2 from Slice 2's own three-policy distinction) must leave both the
        // live in-memory instance and the on-disk file exactly as they were. No flush is even
        // requested by production code for a Rejected result (see WorldStateSyncApply), but this
        // forces one anyway, proving nothing was left dirty from the rejected attempt either.
        WorldStateChangeRecord malformed = new WorldStateChangeRecord(
                2, "not_a_real_change_type", "service_npc_spawn_point", UUID.randomUUID().toString(),
                1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
        );
        ServiceNpcAssignmentsCandidateApply.Result rejectedResult =
                cache.applyWorldStateChanges(List.of(malformed), 999L);
        check(rejectedResult instanceof ServiceNpcAssignmentsCandidateApply.Rejected, "expected the malformed batch to be rejected");
        check(cache.lastAppliedWorldStateVersion() == 10L, "the live in-memory version changed despite rejection");
        forceSynchronousFlush(level);
        ServiceNpcAssignmentsCache afterRejection = readFreshFromDisk(level);
        check(afterRejection.lastAppliedWorldStateVersion() == 10L,
                "on-disk version changed after a rejected batch -- exactly the torn/inconsistent write this slice must prevent");
        check(afterRejection.snapshot().spawnPoints().size() == spawnPointCountAfterFirstCommit,
                "on-disk snapshot size changed after a rejected batch -- not byte-identical to the pre-rejection state");
        check(afterRejection.snapshot().spawnPoints().containsKey(spawnPointId),
                "on-disk snapshot lost real content after an unrelated rejected batch");

        // Step 3: re-applying the identical already-applied batch (a retried poll after an
        // ambiguous failure, Codex Prompt 13's own named idempotency requirement) through the
        // real cache must be a genuine no-op on disk too, not just in the pure function.
        ServiceNpcAssignmentsCandidateApply.Result secondResult = cache.applyWorldStateChanges(validBatch, 10L);
        check(secondResult instanceof ServiceNpcAssignmentsCandidateApply.Applied, "expected the idempotent replay to apply cleanly");
        forceSynchronousFlush(level);
        ServiceNpcAssignmentsCache afterReplay = readFreshFromDisk(level);
        check(afterReplay.lastAppliedWorldStateVersion() == 10L, "idempotent replay changed the on-disk version");
        check(afterReplay.snapshot().equals(afterFirstCommit.snapshot()),
                "idempotent replay produced on-disk snapshot content different from the original commit");

        // Step 4: version/content consistency, proven with a real state transition -- commit a
        // second, genuinely different batch at a higher version, and confirm the on-disk file
        // reflects the new content AND the new version together, never one without the other.
        UUID secondSpawnPointId = UUID.randomUUID();
        ServiceNpcAssignmentsCandidateApply.Result thirdResult =
                cache.applyWorldStateChanges(List.of(spawnPointCreated(secondSpawnPointId, 1)), 11L);
        check(thirdResult instanceof ServiceNpcAssignmentsCandidateApply.Applied, "expected the second real batch to apply cleanly");
        forceSynchronousFlush(level);
        ServiceNpcAssignmentsCache afterSecondCommit = readFreshFromDisk(level);
        check(afterSecondCommit.lastAppliedWorldStateVersion() == 11L,
                "on-disk version did not advance to match the newly committed content");
        check(afterSecondCommit.snapshot().spawnPoints().containsKey(secondSpawnPointId),
                "on-disk content did not advance to match the newly committed version");
        check(afterSecondCommit.snapshot().spawnPoints().containsKey(spawnPointId),
                "advancing to the new version lost previously committed content -- not a cumulative apply");

        helper.succeed();
    }

    private static WorldStateChangeRecord spawnPointCreated(UUID publicId, long revision) {
        JsonObject payload = new JsonObject();
        payload.addProperty("minecraft_server_public_id", OWNING_SERVER.toString());
        payload.addProperty("world_name", "Britain");
        payload.addProperty("dimension_key", "minecraft:overworld");
        payload.addProperty("x", 1);
        payload.addProperty("y", 64);
        payload.addProperty("z", 1);
        payload.addProperty("enabled", true);
        return new WorldStateChangeRecord(
                1, "created", "service_npc_spawn_point", publicId.toString(), revision, payload, "2026-07-16T12:00:00.000000Z"
        );
    }

    private static void forceSynchronousFlush(ServerLevel level) {
        level.getServer().overworld().getDataStorage().save();
    }

    /**
     * Deliberately does not go through {@link ServiceNpcAssignmentsCache#get}, which would just
     * hand back the same live, in-memory-cached instance this server already holds. Reads the
     * exact file {@link net.minecraft.world.level.storage.DimensionDataStorage} itself writes to
     * ({@code getWorldPath(LevelResource.ROOT)/data/<name>.dat}), mirroring {@code
     * BankTransferReceiptGameTests#readFreshFromDisk} exactly.
     */
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
