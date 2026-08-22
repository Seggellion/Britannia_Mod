package com.seggellion.britannia_mod.resource.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDepositPreviewProtocolTest {
    private static final UUID PREVIEW = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DEPOSIT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SHARD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SERVER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void parsesTheMirroredRailsV1RequestWithoutInventingAYRadiusOrPlannerModel() throws IOException {
        List<ResourceDepositPreviewProtocol.Request> requests =
                ResourceDepositPreviewProtocol.parsePending(contract("resource_deposit_preview_pending_v1.json"));

        assertEquals(1, requests.size());
        ResourceDepositPreviewProtocol.Request request = requests.getFirst();
        assertEquals(PREVIEW, request.previewUuid());
        assertEquals(DEPOSIT, request.resourceDepositUuid());
        assertEquals(7, request.resourceDepositRevision());
        assertEquals("iron", request.resourceDefinitionKey());
        assertEquals(SERVER, request.target().minecraftServerUuid());
        assertEquals("minecraft:overworld", request.target().dimensionKey());
        assertEquals(-12, request.x());
        assertEquals(34, request.z());
    }

    @Test
    void rejectsUnsupportedVersionsFractionalIntegersAndOversizedBatches() {
        assertThrows(ResourceDepositPreviewProtocol.MalformedProtocolException.class,
                () -> ResourceDepositPreviewProtocol.parsePending(
                        pendingJson("2").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ResourceDepositPreviewProtocol.MalformedProtocolException.class,
                () -> ResourceDepositPreviewProtocol.parsePending(
                        pendingJson("1").replace("\"x\":-12", "\"x\":1.5")
                                .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ResourceDepositPreviewProtocol.MalformedProtocolException.class,
                () -> ResourceDepositPreviewProtocol.parsePending(
                        pendingJson("1").replace("\"x\":-12", "\"x\":30000001")
                                .getBytes(StandardCharsets.UTF_8)));

        JsonObject root = JsonParser.parseString(pendingJson("1")).getAsJsonObject();
        for (int i = 1; i < 21; i++) {
            root.getAsJsonArray("preview_requests")
                    .add(root.getAsJsonArray("preview_requests").get(0).deepCopy());
        }
        assertThrows(ResourceDepositPreviewProtocol.MalformedProtocolException.class,
                () -> ResourceDepositPreviewProtocol.parsePending(
                        root.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void encodesACompletedResultInTheRailsContractVocabulary() {
        ResourceDepositPreviewProtocol.Request request = request();
        ResourceDepositPreviewProtocol.Evaluation result =
                new ResourceDepositPreviewProtocol.Evaluation(
                        40, 3, "vertical_layered", 2, ShapeRotation.ZW,
                        new ResourceDepositPreviewProtocol.Bounds(8, 12, 36, 44, 18, 22),
                        35, 30, 5, List.of("rejected_not_a_host:5"),
                        List.of("structure:55555555-5555-4555-8555-555555555555"), false);

        JsonObject json = JsonParser.parseString(new String(
                ResourceDepositPreviewProtocol.encodeResult(request, result), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(1, json.get("protocol_version").getAsInt());
        assertEquals(DEPOSIT.toString(), json.get("resource_deposit_uuid").getAsString());
        assertEquals(7, json.get("resource_deposit_revision").getAsLong());
        assertEquals("completed", json.get("status").getAsString());
        assertEquals("vertical_layered", json.getAsJsonObject("result").get("shape").getAsString());
        assertEquals("zw", json.getAsJsonObject("result").get("rotation").getAsString());
        assertFalse(json.getAsJsonObject("result").get("valid").getAsBoolean());
    }

    @Test
    void seedIsStableAcrossRepeatedPreviewRequestsForTheSameDepositRevision() {
        ResourceDepositPreviewProtocol.Request first = request();
        ResourceDepositPreviewProtocol.Request repeated = new ResourceDepositPreviewProtocol.Request(
                UUID.fromString("99999999-9999-4999-8999-999999999999"), DEPOSIT, 7, "iron",
                first.target(), first.x(), first.z());

        assertEquals(ResourceDepositPreviewEvaluator.previewSeed(first),
                ResourceDepositPreviewEvaluator.previewSeed(repeated));
    }

    @Test
    void encodesBoundedRetryableFailuresWithoutRawTransportData() {
        JsonObject json = JsonParser.parseString(new String(ResourceDepositPreviewProtocol.encodeResult(
                request(), new ResourceDepositPreviewProtocol.Failure(
                        "chunks_not_loaded", "preview footprint includes unloaded chunks", true)),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("failed", json.get("status").getAsString());
        assertEquals("chunks_not_loaded", json.get("error_code").getAsString());
        assertTrue(json.get("retryable").getAsBoolean());
    }

    private static byte[] contract(String name) throws IOException {
        try (InputStream input = ResourceDepositPreviewProtocolTest.class
                .getResourceAsStream("/contracts/" + name)) {
            if (input == null) throw new IOException("missing mirrored contract fixture: " + name);
            return input.readAllBytes();
        }
    }

    private static ResourceDepositPreviewProtocol.Request request() {
        return new ResourceDepositPreviewProtocol.Request(PREVIEW, DEPOSIT, 7, "iron",
                new ResourceDepositPreviewProtocol.Target(
                        SHARD, SERVER, "Britannia", "minecraft:overworld"), -12, 34);
    }

    private static String pendingJson(String version) {
        return """
                {"protocol_version":%s,"preview_requests":[{
                  "protocol_version":1,
                  "preview_uuid":"%s",
                  "resource_deposit_uuid":"%s",
                  "resource_deposit_revision":7,
                  "resource_definition_key":"iron",
                  "target":{"shard_uuid":"%s","minecraft_server_uuid":"%s",
                    "world_name":"Britannia","dimension_key":"minecraft:overworld"},
                  "coordinates":{"x":-12,"z":34},
                  "requested_at":"2026-08-22T00:00:00Z"
                }]}
                """.formatted(version, PREVIEW, DEPOSIT, SHARD, SERVER);
    }
}
