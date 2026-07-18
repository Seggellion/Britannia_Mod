package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Slice 2's own "tags spawn points by their owning Minecraft server so a
 * client can filter locally" Rails test: two Minecraft servers under one shard,
 * each with its own spawn point/assignment/World NPC, and proves filtering isolates
 * only the entries owned by the requesting server's own public id.
 */
class ServiceNpcAssignmentsSnapshotFilterTest {
    private static final UUID FIRST_SERVER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_SERVER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final UUID FIRST_SPAWN_POINT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SECOND_SPAWN_POINT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID FIRST_WORLD_NPC_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID SECOND_WORLD_NPC_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID FIRST_ASSIGNMENT_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final UUID SECOND_ASSIGNMENT_ID = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

    @Test
    void filteringIsolatesOnlyTheRequestingServersEntries() {
        JsonObject root = twoServerBootstrapRoot();
        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);
        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.ACCEPTED, result.status(), result.error());
        ServiceNpcAssignmentsSnapshot full = result.snapshot();
        assertEquals(2, full.spawnPoints().size());
        assertEquals(2, full.assignments().size());
        assertEquals(2, full.worldNpcs().size());

        ServiceNpcAssignmentsSnapshot filteredToFirst = full.filteredForServer(FIRST_SERVER_ID);
        assertEquals(1, filteredToFirst.spawnPoints().size());
        assertTrue(filteredToFirst.spawnPoints().containsKey(FIRST_SPAWN_POINT_ID));
        assertEquals(FIRST_SERVER_ID, filteredToFirst.spawnPoints().get(FIRST_SPAWN_POINT_ID).minecraftServerPublicId());
        assertEquals(1, filteredToFirst.assignments().size());
        assertTrue(filteredToFirst.assignments().containsKey(FIRST_ASSIGNMENT_ID));
        assertEquals(1, filteredToFirst.worldNpcs().size());
        assertTrue(filteredToFirst.worldNpcs().containsKey(FIRST_WORLD_NPC_ID));

        ServiceNpcAssignmentsSnapshot filteredToSecond = full.filteredForServer(SECOND_SERVER_ID);
        assertEquals(1, filteredToSecond.spawnPoints().size());
        assertTrue(filteredToSecond.spawnPoints().containsKey(SECOND_SPAWN_POINT_ID));
        assertEquals(1, filteredToSecond.assignments().size());
        assertTrue(filteredToSecond.assignments().containsKey(SECOND_ASSIGNMENT_ID));
        assertEquals(1, filteredToSecond.worldNpcs().size());
        assertTrue(filteredToSecond.worldNpcs().containsKey(SECOND_WORLD_NPC_ID));
    }

    @Test
    void unknownServerIdFiltersToEmpty() {
        JsonObject root = twoServerBootstrapRoot();
        ServiceNpcAssignmentsSnapshot full = ServiceNpcAssignmentsParser.parseBootstrapRoot(root).snapshot();

        ServiceNpcAssignmentsSnapshot filtered = full.filteredForServer(UUID.fromString("99999999-9999-4999-8999-999999999999"));

        assertTrue(filtered.isEmpty());
    }

    @Test
    void nullServerIdFiltersToEmpty() {
        JsonObject root = twoServerBootstrapRoot();
        ServiceNpcAssignmentsSnapshot full = ServiceNpcAssignmentsParser.parseBootstrapRoot(root).snapshot();

        ServiceNpcAssignmentsSnapshot filtered = full.filteredForServer(null);

        assertTrue(filtered.isEmpty());
    }

    /**
     * Reproduces WorldBootstrapAPI.fetch()'s exact expression
     * ({@code credentials.minecraftServerKey().orElse(null)}) rather than a bare
     * {@code null} literal, so this proves the specific case where
     * ServerCredentials resolves (bootstrap proceeds past its own credentials
     * guard) but its own minecraftServerKey is independently MISSING or INVALID
     * (an unconfigured/malformed Minecraft-Server-Key) — the same real, already
     * anticipated state ServiceNpcSpawnRegistrationClient guards against for M5
     * spawn registration. Confirms filtering fails closed to empty rather than
     * ever returning the full, unfiltered, all-servers snapshot.
     */
    @Test
    void emptyOptionalServerKeyFromCredentialsFiltersToEmptyNotTheFullUnfilteredSet() {
        JsonObject root = twoServerBootstrapRoot();
        ServiceNpcAssignmentsSnapshot full = ServiceNpcAssignmentsParser.parseBootstrapRoot(root).snapshot();
        Optional<UUID> unresolvedServerKey = Optional.empty();

        ServiceNpcAssignmentsSnapshot filtered = full.filteredForServer(unresolvedServerKey.orElse(null));

        assertTrue(filtered.isEmpty());
        assertTrue(filtered.spawnPoints().isEmpty());
        assertTrue(filtered.assignments().isEmpty());
        assertTrue(filtered.worldNpcs().isEmpty());
    }

    private static JsonObject twoServerBootstrapRoot() {
        return JsonParser.parseString("""
                {
                  "service_npc_assignments": {
                    "schema_version": 1,
                    "revision": 123456789,
                    "spawn_points": [
                      {
                        "public_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "minecraft_server_public_id": "11111111-1111-4111-8111-111111111111",
                        "city_public_id": "22222222-2222-4222-8222-222222222222",
                        "service_npc_type_key": "bank_teller",
                        "world_name": "Britannia",
                        "dimension_key": "minecraft:overworld",
                        "x": 100, "y": 64, "z": 100,
                        "enabled": true,
                        "revision": 1
                      },
                      {
                        "public_id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "minecraft_server_public_id": "22222222-2222-4222-8222-222222222222",
                        "city_public_id": "22222222-2222-4222-8222-222222222222",
                        "service_npc_type_key": "bank_teller",
                        "world_name": "Britannia",
                        "dimension_key": "minecraft:overworld",
                        "x": 200, "y": 64, "z": 200,
                        "enabled": true,
                        "revision": 1
                      }
                    ],
                    "world_npcs": [
                      {
                        "public_id": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                        "name": "First Banker",
                        "gender_key": "female",
                        "profession_key": "banker",
                        "service_npc_type_key": "bank_teller",
                        "revision": 1
                      },
                      {
                        "public_id": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                        "name": "Second Banker",
                        "gender_key": "male",
                        "profession_key": "banker",
                        "service_npc_type_key": "bank_teller",
                        "revision": 1
                      }
                    ],
                    "assignments": [
                      {
                        "public_id": "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
                        "spawn_point_public_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "world_npc_public_id": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                        "status": "active",
                        "revision": 1,
                        "assigned_at": "2026-07-18T04:38:00.058754Z"
                      },
                      {
                        "public_id": "ffffffff-ffff-4fff-8fff-ffffffffffff",
                        "spawn_point_public_id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "world_npc_public_id": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                        "status": "active",
                        "revision": 1,
                        "assigned_at": "2026-07-18T04:39:00.058754Z"
                      }
                    ]
                  }
                }
                """).getAsJsonObject();
    }
}
