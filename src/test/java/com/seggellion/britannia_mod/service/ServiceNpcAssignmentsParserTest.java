package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNpcAssignmentsParserTest {
    private static final UUID SPAWN_POINT_ID = UUID.fromString("8bc97d05-0b4c-4f9c-82a4-82c38e0a0768");
    private static final UUID SERVER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CITY_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID WORLD_NPC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void missingSectionProducesAnEmptySnapshot() {
        JsonObject root = JsonParser.parseString("{\"fish\":[],\"cities\":[]}").getAsJsonObject();

        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.MISSING, result.status());
        assertTrue(result.snapshot().isEmpty());
        assertEquals(1, result.snapshot().schemaVersion());
    }

    @Test
    void parsesACompleteImmutableSnapshotAndIgnoresUnknownFields() {
        JsonObject root = validBootstrapRoot();

        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.ACCEPTED, result.status(), result.error());
        ServiceNpcAssignmentsSnapshot snapshot = result.snapshot();
        assertEquals(501424005180508762L, snapshot.revision());

        ServiceNpcAssignmentSpawnPointDefinition point = snapshot.spawnPoints().get(SPAWN_POINT_ID);
        assertEquals(SERVER_ID, point.minecraftServerPublicId());
        assertEquals(CITY_ID, point.cityPublicId());
        assertEquals("bank_teller", point.serviceNpcTypeKey());
        assertEquals("Britannia", point.worldName());
        assertEquals("minecraft:overworld", point.dimensionKey());
        assertEquals(142, point.x());
        assertEquals(68, point.y());
        assertEquals(-315, point.z());
        assertTrue(point.enabled());
        assertEquals(1L, point.revision());

        ServiceNpcAssignmentWorldNpcDefinition npc = snapshot.worldNpcs().get(WORLD_NPC_ID);
        assertEquals("Marian", npc.name());
        assertEquals("female", npc.genderKey());
        assertEquals("banker", npc.professionKey());
        assertEquals("bank_teller", npc.serviceNpcTypeKey());

        ServiceNpcAssignmentDefinition assignment = snapshot.assignments().get(ASSIGNMENT_ID);
        assertEquals(SPAWN_POINT_ID, assignment.spawnPointPublicId());
        assertEquals(WORLD_NPC_ID, assignment.worldNpcPublicId());
        assertEquals("active", assignment.status());

        assertTrue(assertThrowsUnsupported(() -> snapshot.spawnPoints().put(SPAWN_POINT_ID, point)));
        assertTrue(assertThrowsUnsupported(() -> snapshot.assignments().put(ASSIGNMENT_ID, assignment)));
        assertTrue(assertThrowsUnsupported(() -> snapshot.worldNpcs().put(WORLD_NPC_ID, npc)));
    }

    @Test
    void rejectsUnsupportedSchemasWithoutTouchingUnrelatedBootstrapMembers() {
        JsonObject root = validBootstrapRoot();
        root.getAsJsonObject(ServiceNpcAssignmentsParser.ROOT_KEY).addProperty("schema_version", 2);

        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.REJECTED, result.status());
        assertTrue(result.snapshot().isEmpty());
        assertTrue(result.error().contains("unsupported schema_version"));
        assertEquals("unrelated-data", root.get("unrelated").getAsString());
    }

    @Test
    void missingRequiredFieldDegradesWithoutThrowingPastItsOwnBoundary() {
        JsonObject root = validBootstrapRoot();
        root.getAsJsonObject(ServiceNpcAssignmentsParser.ROOT_KEY)
                .getAsJsonArray("spawn_points").get(0).getAsJsonObject()
                .remove("x");

        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.REJECTED, result.status());
        assertTrue(result.snapshot().isEmpty());
        assertEquals("unrelated-data", root.get("unrelated").getAsString());
    }

    @Test
    void assignmentReferencingAnUnknownSpawnPointIsRejected() {
        JsonObject root = validBootstrapRoot();
        root.getAsJsonObject(ServiceNpcAssignmentsParser.ROOT_KEY)
                .getAsJsonArray("assignments").get(0).getAsJsonObject()
                .addProperty("spawn_point_public_id", "55555555-5555-4555-8555-555555555555");

        ServiceNpcAssignmentsParser.ParseResult result = ServiceNpcAssignmentsParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcAssignmentsParser.ParseStatus.REJECTED, result.status());
        assertTrue(result.error().contains("unknown spawn point"));
    }

    private static boolean assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private static JsonObject validBootstrapRoot() {
        return JsonParser.parseString("""
                {
                  "unrelated": "unrelated-data",
                  "service_npc_assignments": {
                    "schema_version": 1,
                    "revision": 501424005180508762,
                    "future_section_field": {"ignored": true},
                    "spawn_points": [
                      {
                        "public_id": "8bc97d05-0b4c-4f9c-82a4-82c38e0a0768",
                        "minecraft_server_public_id": "11111111-1111-4111-8111-111111111111",
                        "city_public_id": "22222222-2222-4222-8222-222222222222",
                        "service_npc_type_key": "bank_teller",
                        "world_name": "Britannia",
                        "dimension_key": "minecraft:overworld",
                        "x": 142,
                        "y": 68,
                        "z": -315,
                        "enabled": true,
                        "revision": 1,
                        "future_spawn_point_field": "ignored"
                      }
                    ],
                    "world_npcs": [
                      {
                        "public_id": "33333333-3333-4333-8333-333333333333",
                        "name": "Marian",
                        "gender_key": "female",
                        "profession_key": "banker",
                        "service_npc_type_key": "bank_teller",
                        "revision": 1,
                        "future_npc_field": "ignored"
                      }
                    ],
                    "assignments": [
                      {
                        "public_id": "44444444-4444-4444-8444-444444444444",
                        "spawn_point_public_id": "8bc97d05-0b4c-4f9c-82a4-82c38e0a0768",
                        "world_npc_public_id": "33333333-3333-4333-8333-333333333333",
                        "status": "active",
                        "revision": 1,
                        "assigned_at": "2026-07-18T04:38:00.058754Z",
                        "future_assignment_field": "ignored"
                      }
                    ]
                  }
                }
                """).getAsJsonObject();
    }
}
