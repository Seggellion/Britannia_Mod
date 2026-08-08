package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStateChangesResponseParserTest {
    @Test
    void parsesAWellFormedResponseWithChangesIntact() {
        JsonObject root = json("""
                {"schema_version":1,"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":2,"current_version":2,"full_bootstrap_required":false,
                 "changes":[
                   {"version":1,"change_type":"created","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":1,
                    "payload":{"status":"active"},"created_at":"2026-07-16T12:00:00.000000Z"},
                   {"version":2,"change_type":"closed","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":2,
                    "payload":{"status":"closed"},"created_at":"2026-07-16T12:01:00.000000Z"}
                 ]}
                """);

        WorldStateChangesResponse response = WorldStateChangesResponseParser.parse(root);

        assertEquals(1, response.schemaVersion());
        assertEquals("11111111-1111-4111-8111-111111111111", response.shardPublicId());
        assertEquals(0, response.fromVersion());
        assertEquals(2, response.toVersion());
        assertEquals(2, response.currentVersion());
        assertFalse(response.fullBootstrapRequired());
        assertEquals(2, response.changes().size());
        assertEquals(1, response.changes().get(0).version());
        assertEquals("created", response.changes().get(0).changeType());
        assertEquals("active", response.changes().get(0).payload().get("status").getAsString());
        assertEquals(2, response.changes().get(1).version());
    }

    @Test
    void parsesAnEmptyChangesArrayCleanly() {
        JsonObject root = json("""
                {"schema_version":1,"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":0,"current_version":0,"full_bootstrap_required":false,
                 "changes":[]}
                """);

        WorldStateChangesResponse response = WorldStateChangesResponseParser.parse(root);

        assertTrue(response.changes().isEmpty());
    }

    @Test
    void rejectsAMissingTopLevelField() {
        JsonObject root = json("""
                {"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":0,"current_version":0,"full_bootstrap_required":false,
                 "changes":[]}
                """);

        WorldStateChangesResponseParser.MalformedResponseException error = assertThrows(
                WorldStateChangesResponseParser.MalformedResponseException.class,
                () -> WorldStateChangesResponseParser.parse(root)
        );
        assertEquals("schema_version", error.field());
    }

    @Test
    void rejectsAWrongTypedTopLevelField() {
        JsonObject root = json("""
                {"schema_version":"one","shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":0,"current_version":0,"full_bootstrap_required":false,
                 "changes":[]}
                """);

        assertThrows(WorldStateChangesResponseParser.MalformedResponseException.class,
                () -> WorldStateChangesResponseParser.parse(root));
    }

    @Test
    void rejectsAChangeEntryMissingARequiredField() {
        JsonObject root = json("""
                {"schema_version":1,"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":1,"current_version":1,"full_bootstrap_required":false,
                 "changes":[
                   {"change_type":"created","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":1,
                    "payload":{},"created_at":"2026-07-16T12:00:00.000000Z"}
                 ]}
                """);

        WorldStateChangesResponseParser.MalformedResponseException error = assertThrows(
                WorldStateChangesResponseParser.MalformedResponseException.class,
                () -> WorldStateChangesResponseParser.parse(root)
        );
        assertEquals("version", error.field());
    }

    @Test
    void rejectsAChangeEntryWithAMissingPayloadObject() {
        JsonObject root = json("""
                {"schema_version":1,"shard_public_id":"11111111-1111-4111-8111-111111111111",
                 "from_version":0,"to_version":1,"current_version":1,"full_bootstrap_required":false,
                 "changes":[
                   {"version":1,"change_type":"created","resource_type":"npc_spawn_assignment",
                    "resource_id":"aaaaaaaa-1111-4111-8111-111111111111","resource_revision":1,
                    "created_at":"2026-07-16T12:00:00.000000Z"}
                 ]}
                """);

        WorldStateChangesResponseParser.MalformedResponseException error = assertThrows(
                WorldStateChangesResponseParser.MalformedResponseException.class,
                () -> WorldStateChangesResponseParser.parse(root)
        );
        assertEquals("payload", error.field());
    }

    @Test
    void trulyTruncatedJsonFailsAtTheParserStageBeforeEverReachingFieldValidation() {
        // Mirrors exactly how WorldStateChangesClient encounters a truncated HTTP body: the raw
        // bytes never become a JsonObject at all, so this never even reaches
        // WorldStateChangesResponseParser -- confirming the client's JsonParseException catch
        // (mapped to safeCode "malformed_response") is the layer that actually handles this,
        // not this parser's own field-level checks.
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> JsonParser.parseString("{\"schema_version\":1,\"changes\":[{\"version\":1,")
                        .getAsJsonObject());
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
