package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ServiceNpcAssignmentsCacheGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private static final UUID SPAWN_POINT_ID = UUID.fromString("8bc97d05-0b4c-4f9c-82a4-82c38e0a0768");
    private static final UUID SERVER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID WORLD_NPC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private ServiceNpcAssignmentsCacheGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void cacheSurvivesASimulatedServerRestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServiceNpcAssignmentsSnapshot snapshot = sampleSnapshot();
        ServiceNpcAssignmentsCache.get(level).replace(snapshot);

        CompoundTag saved = ServiceNpcAssignmentsCache.get(level).save(new CompoundTag(), level.registryAccess());
        ServiceNpcAssignmentsCache reloaded = ServiceNpcAssignmentsCache.load(saved, level.registryAccess());

        check(reloaded.snapshot().equals(snapshot), "cache did not survive a simulated save/reload boundary");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void offlineStartupRecoversTheLastKnownGoodSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServiceNpcAssignmentsSnapshot snapshot = sampleSnapshot();
        ServiceNpcAssignmentsCache.get(level).replace(snapshot);
        CompoundTag saved = ServiceNpcAssignmentsCache.get(level).save(new CompoundTag(), level.registryAccess());

        // Simulate a fresh server process where Rails is unreachable: nothing has
        // fetched yet, only the on-disk tag exists, exactly like NeoForge's lazy
        // SavedData load path running before any successful WorldBootstrapAPI.fetch.
        ServiceNpcAssignmentsCache freshBoot = ServiceNpcAssignmentsCache.load(saved, level.registryAccess());

        check(!freshBoot.snapshot().isEmpty(), "offline startup did not recover a last-known-good snapshot");
        check(freshBoot.snapshot().equals(snapshot), "offline startup snapshot did not match the last successful fetch");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void onDiskSchemaMismatchDiscardsAndStartsEmptyRatherThanThrowing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CompoundTag futureVersionTag = new CompoundTag();
        futureVersionTag.putInt("SchemaVersion", ServiceNpcAssignmentsCache.SCHEMA_VERSION + 1);

        ServiceNpcAssignmentsCache loaded = ServiceNpcAssignmentsCache.load(futureVersionTag, level.registryAccess());

        check(loaded.snapshot().isEmpty(), "on-disk schema mismatch did not degrade to an empty cache");

        // A discarded cache must behave exactly like a fresh one: normal writes proceed
        // and overwrite the incompatible file on next save, matching ServiceNpcRegistryCache's
        // own discard-to-empty policy for its analogous (wire-level) unsupported-schema case.
        ServiceNpcAssignmentsSnapshot snapshot = sampleSnapshot();
        loaded.replace(snapshot);
        check(loaded.snapshot().equals(snapshot), "cache did not accept writes after discarding a mismatched schema");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void corruptOnDiskContentIsQuarantinedWithoutThrowing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putInt("SchemaVersion", ServiceNpcAssignmentsCache.SCHEMA_VERSION);
        corruptTag.putLong("Revision", 1L);
        // SpawnPoints/Assignments/WorldNpcs lists are intentionally absent/malformed.

        ServiceNpcAssignmentsCache loaded = ServiceNpcAssignmentsCache.load(corruptTag, level.registryAccess());

        check(loaded.snapshot().isEmpty(), "corrupt on-disk content was not quarantined to an empty cache");
        helper.succeed();
    }

    private static ServiceNpcAssignmentsSnapshot sampleSnapshot() {
        ServiceNpcAssignmentSpawnPointDefinition point = new ServiceNpcAssignmentSpawnPointDefinition(
                SPAWN_POINT_ID, SERVER_ID, null, "bank_teller",
                "Britannia", "minecraft:overworld", 142, 68, -315, true, 1L
        );
        ServiceNpcAssignmentWorldNpcDefinition npc = new ServiceNpcAssignmentWorldNpcDefinition(
                WORLD_NPC_ID, "Marian", "female", "banker", "bank_teller", 1L
        );
        ServiceNpcAssignmentDefinition assignment = new ServiceNpcAssignmentDefinition(
                ASSIGNMENT_ID, SPAWN_POINT_ID, WORLD_NPC_ID, "active", 1L, "2026-07-18T04:38:00.058754Z"
        );

        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints = new LinkedHashMap<>();
        spawnPoints.put(point.publicId(), point);
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs = new LinkedHashMap<>();
        worldNpcs.put(npc.publicId(), npc);
        Map<UUID, ServiceNpcAssignmentDefinition> assignments = new LinkedHashMap<>();
        assignments.put(assignment.publicId(), assignment);

        return new ServiceNpcAssignmentsSnapshot(1, 999L, spawnPoints, assignments, worldNpcs);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
