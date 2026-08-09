package com.seggellion.britannia_mod.service.spawn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnProtocolClientTest {
    private static final UUID OPERATION_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID SPAWN_UUID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

    @Test
    void serializesExactUpsertShapeWithoutCredentialOrQueueState() {
        ServiceNpcSpawnOperationRequest request = upsert();
        byte[] bytes = ServiceNpcSpawnRequestSerializer.serialize(request);
        JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(1, json.get("protocol_version").getAsInt());
        assertEquals(OPERATION_ID.toString(), json.get("operation_id").getAsString());
        assertEquals("UPSERT", json.get("operation").getAsString());
        assertEquals(SPAWN_UUID.toString(), json.get("spawn_uuid").getAsString());
        assertEquals(3L, json.get("source_revision").getAsLong());
        assertEquals("Britannia 世界", json.getAsJsonObject("location").get("world_name").getAsString());
        assertEquals("minecraft:overworld", json.getAsJsonObject("location").get("dimension").getAsString());
        assertEquals(142, json.getAsJsonObject("location").get("x").getAsInt());
        assertEquals(UUID.fromString("12345678-1234-4234-8234-123456789abc").toString(),
            json.get("city_public_id").getAsString());
        assertEquals("bank_teller", json.get("service_npc_type_key").getAsString());
        assertFalse(json.entrySet().stream().anyMatch(entry ->
            entry.getKey().toLowerCase().contains("shard")
                || entry.getKey().toLowerCase().contains("secret")
                || entry.getKey().toLowerCase().contains("server_key")
                || entry.getKey().toLowerCase().contains("retry")
                || entry.getKey().toLowerCase().contains("disposition")));
        assertTrue(bytes.length <= ServiceNpcSpawnOperationRequest.MAX_REQUEST_BYTES);
    }

    @Test
    void removeOmitsAbsentConfigurationAndAllowsOptionalSnapshot() {
        JsonObject absent = JsonParser.parseString(new String(
            ServiceNpcSpawnRequestSerializer.serialize(remove(null, null, null)), StandardCharsets.UTF_8
        )).getAsJsonObject();
        assertFalse(absent.has("city_public_id"));
        assertFalse(absent.has("service_npc_type_key"));
        assertFalse(absent.has("enabled"));
        assertFalse(absent.toString().contains("null"));

        JsonObject snapshot = JsonParser.parseString(new String(
            ServiceNpcSpawnRequestSerializer.serialize(remove(UUID.randomUUID(), "bank_teller", false)),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        assertTrue(snapshot.has("city_public_id"));
        assertEquals(false, snapshot.get("enabled").getAsBoolean());
    }

    @Test
    void constructionRejectsUnsupportedOrIncompleteRequests() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnOperationRequest(
            2, OPERATION_ID, ServiceNpcSpawnOperation.UPSERT, SPAWN_UUID, 1, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 0, 64, 0, UUID.randomUUID(), "bank_teller", true
        ));
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.REMOVE, SPAWN_UUID, -1, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 0, 64, 0, null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.UPSERT, SPAWN_UUID, 1, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 0, 64, 0, null, "bank_teller", true
        ));
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.UPSERT, SPAWN_UUID, 1, "x".repeat(129),
            ResourceLocation.parse("minecraft:overworld"), 0, 64, 0, UUID.randomUUID(), "bank_teller", true
        ));
        assertThrows(IllegalArgumentException.class, () -> new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.UPSERT, SPAWN_UUID, 1, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 30_000_001, 64, 0,
            UUID.randomUUID(), "bank_teller", true
        ));
    }

    @Test
    void parsesAppliedUpsertAndIgnoresAdditiveFields() {
        ServiceNpcSpawnClientResult.Protocol result = protocol(200, """
            {"protocol_version":1,"success":true,"outcome":"APPLIED","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "acknowledged_revision":3,"registration_state":"LIVE",
             "acknowledged_at":"2026-07-16T12:00:00.123456Z","created":true,"future_field":"ignored"}
            """.formatted(OPERATION_ID, SPAWN_UUID), upsert());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.SUCCESS, result.disposition());
        assertEquals(ServiceNpcSpawnOutcome.APPLIED, result.response().outcome());
        assertEquals(3L, result.response().acknowledgedRevision());
        assertEquals(true, result.response().created());
    }

    @Test
    void parsesRemoveAndIdempotentSuccess() {
        for (String outcome : List.of("APPLIED", "ALREADY_APPLIED")) {
            ServiceNpcSpawnClientResult.Protocol result = protocol(200, """
                {"protocol_version":1,"success":true,"outcome":"%s","retryable":false,
                 "operation_id":"%s","spawn_uuid":"%s","submitted_revision":0,
                 "acknowledged_revision":4,"registration_state":"REMOVED",
                 "acknowledged_at":"2026-07-16T12:00:00Z","created":false}
                """.formatted(outcome, OPERATION_ID, SPAWN_UUID), remove(null, null, null));
            assertEquals(ServiceNpcSpawnClientResult.Disposition.SUCCESS, result.disposition());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-json",
        "{\"protocol_version\":1,\"success\":true,\"outcome\":\"APPLIED\",\"retryable\":false}",
        "{\"protocol_version\":2,\"success\":true,\"outcome\":\"APPLIED\",\"retryable\":false}",
        "{\"protocol_version\":1,\"success\":true,\"outcome\":\"FUTURE\",\"retryable\":false}"
    })
    void malformedHttp200NeverBecomesSuccess(String body) {
        ServiceNpcSpawnClientResult result = parse(200, body, Map.of(), upsert());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE, result.disposition());
    }

    @Test
    void rejectsMismatchedCorrelationAndRegistrationState() {
        String template = """
            {"protocol_version":1,"success":true,"outcome":"APPLIED","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "acknowledged_revision":3,"registration_state":"%s","acknowledged_at":"2026-07-16T12:00:00Z"}
            """;
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            parse(200, template.formatted(UUID.randomUUID(), SPAWN_UUID, "LIVE"), Map.of(), upsert()).disposition());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            parse(200, template.formatted(OPERATION_ID, SPAWN_UUID, "REMOVED"), Map.of(), upsert()).disposition());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STALE_REVISION", "REVISION_CONFLICT"})
    void parsesRevisionConflicts(String outcome) {
        String body = """
            {"protocol_version":1,"success":false,"outcome":"%s","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "current_revision":4,"registration_state":"LIVE"}
            """.formatted(outcome, OPERATION_ID, SPAWN_UUID);
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PERMANENT,
            parse(409, body, Map.of(), upsert()).disposition());
    }

    @Test
    void parsesLiveTombstonedAndRedactedUuidCollisions() {
        String base = """
            {"protocol_version":1,"success":false,"outcome":"UUID_COLLISION","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "collision_kind":"%s","replacement_uuid_required":true%s}
            """;
        String location = """
            ,"canonical_location":{"minecraft_server_key":"%s","world_name":"Britannia",
            "dimension":"minecraft:overworld","x":1,"y":64,"z":2}
            """.formatted(UUID.randomUUID()).strip();
        assertEquals(ServiceNpcSpawnClientResult.Disposition.UUID_COLLISION,
            parse(409, base.formatted(OPERATION_ID, SPAWN_UUID, "LIVE", location), Map.of(), upsert()).disposition());
        for (String kind : List.of("TOMBSTONED", "REDACTED")) {
            assertEquals(ServiceNpcSpawnClientResult.Disposition.UUID_COLLISION,
                parse(409, base.formatted(OPERATION_ID, SPAWN_UUID, kind, ""), Map.of(), upsert()).disposition());
        }
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            parse(409, base.formatted(OPERATION_ID, SPAWN_UUID, "LIVE",
                location.replace("minecraft:overworld", "INVALID")), Map.of(), upsert()).disposition());
    }

    @Test
    void parsesLocationOccupiedWithoutTreatingItAsUuidReplacement() {
        String body = """
            {"protocol_version":1,"success":false,"outcome":"LOCATION_OCCUPIED","retryable":false,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3,
             "occupying_spawn_uuid":"%s",
             "canonical_location":{"minecraft_server_key":"%s","world_name":"Britannia",
             "dimension":"minecraft:overworld","x":1,"y":64,"z":2}}
            """.formatted(OPERATION_ID, SPAWN_UUID, UUID.randomUUID(), UUID.randomUUID());
        ServiceNpcSpawnClientResult.Protocol result = protocol(409, body, upsert());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.LOCATION_OCCUPIED, result.disposition());
        assertNull(result.response().replacementUuidRequired());
    }

    @Test
    void authenticationAndDeploymentFailuresAreSafe() {
        assertEquals(ServiceNpcSpawnClientResult.Disposition.AUTHENTICATION_BLOCKED,
            parse(401, "html", Map.of(), upsert()).disposition());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.AUTHENTICATION_BLOCKED,
            parse(403, """
                {"protocol_version":1,"success":false,"outcome":"SERVER_NOT_AUTHORIZED","retryable":false}
                """, Map.of(), upsert()).disposition());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.RETRYABLE,
            parse(404, "html", Map.of(), upsert()).disposition());
    }

    @Test
    void retryableHttpClassificationAndRetryAfterAreBounded() {
        ServiceNpcSpawnClientResult.HttpFailure rate = (ServiceNpcSpawnClientResult.HttpFailure) parse(
            429, "", Map.of("retry-after", List.of("9999")), upsert()
        );
        assertEquals(Duration.ofMinutes(5), rate.retryAfter());
        String future = ZonedDateTime.now(java.time.ZoneOffset.UTC).plusHours(1)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        ServiceNpcSpawnClientResult.HttpFailure dated = (ServiceNpcSpawnClientResult.HttpFailure) parse(
            429, "", Map.of("Retry-After", List.of(future)), upsert()
        );
        assertEquals(Duration.ofMinutes(5), dated.retryAfter());
        for (int status : new int[] {500, 502, 504}) {
            assertEquals(ServiceNpcSpawnClientResult.Disposition.RETRYABLE,
                parse(status, "invalid", Map.of(), upsert()).disposition());
        }
        assertEquals(ServiceNpcSpawnClientResult.Disposition.RETRYABLE, parse(503, """
            {"protocol_version":1,"success":false,"outcome":"SERVICE_UNAVAILABLE","retryable":true,
             "operation_id":"%s","spawn_uuid":"%s","submitted_revision":3}
            """.formatted(OPERATION_ID, SPAWN_UUID), Map.of(), upsert()).disposition());
    }

    @Test
    void redirectsAndGenericConflictFailClosed() {
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            parse(302, "", Map.of(), upsert()).disposition());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            parse(409, "{}", Map.of(), upsert()).disposition());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PERMANENT,
            parse(418, "not-protocol", Map.of(), upsert()).disposition());
    }

    private static ServiceNpcSpawnClientResult.Protocol protocol(
        int status, String body, ServiceNpcSpawnOperationRequest request
    ) {
        return (ServiceNpcSpawnClientResult.Protocol) parse(status, body, Map.of(), request);
    }

    private static ServiceNpcSpawnClientResult parse(
        int status, String body, Map<String, List<String>> headers, ServiceNpcSpawnOperationRequest request
    ) {
        return ServiceNpcSpawnResponseParser.parse(
            status, body.getBytes(StandardCharsets.UTF_8), headers, request
        );
    }

    private static ServiceNpcSpawnOperationRequest upsert() {
        return new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.UPSERT, SPAWN_UUID, 3, "Britannia 世界",
            ResourceLocation.parse("minecraft:overworld"), 142, 68, -315,
            UUID.fromString("12345678-1234-4234-8234-123456789abc"), "bank_teller", true
        );
    }

    private static ServiceNpcSpawnOperationRequest remove(UUID city, String type, Boolean enabled) {
        return new ServiceNpcSpawnOperationRequest(
            1, OPERATION_ID, ServiceNpcSpawnOperation.REMOVE, SPAWN_UUID, 0, "Britannia",
            ResourceLocation.parse("minecraft:overworld"), 142, 68, -315, city, type, enabled
        );
    }
}
