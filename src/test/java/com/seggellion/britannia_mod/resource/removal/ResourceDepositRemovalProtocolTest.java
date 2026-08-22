package com.seggellion.britannia_mod.resource.removal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDepositRemovalProtocolTest {
    private static final UUID PREVIEW = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OPERATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void parsesBoundedPreviewAndRemovalRequestsWithTheExactApprovedSnapshot() {
        ResourceDepositRemovalProtocol.PreviewRequest preview =
                ResourceDepositRemovalProtocol.parsePendingPreviews(bytes(previewBatch())).getFirst();
        assertEquals(PREVIEW, preview.previewUuid());
        assertEquals("8f00aa", preview.depositInstanceId());
        assertEquals("minecraft:overworld", preview.target().dimensionKey());

        ResourceDepositRemovalProtocol.RemovalOperation removal =
                ResourceDepositRemovalProtocol.parsePendingRemovals(bytes(removalBatch())).getFirst();
        assertEquals(OPERATION, removal.operationUuid());
        assertEquals(PREVIEW, removal.removalPreviewUuid());
        assertEquals(30, removal.approvedPreview().affectedBlockCount());
        assertEquals(2, removal.approvedPreview().depletedCellCount());
        assertTrue(removal.approvedPreview().safeToRemove());
    }

    @Test
    void encodesCorrelatedNonMutatingInspectionAndIdempotentTombstoneReplay() {
        ResourceDepositRemovalProtocol.PreviewRequest preview =
                ResourceDepositRemovalProtocol.parsePendingPreviews(bytes(previewBatch())).getFirst();
        ResourceDepositRemovalProtocol.RemovalOperation removal =
                ResourceDepositRemovalProtocol.parsePendingRemovals(bytes(removalBatch())).getFirst();

        JsonObject previewResult = json(ResourceDepositRemovalProtocol.encodePreviewResult(
                preview, inspection()));
        assertEquals("completed", previewResult.get("status").getAsString());
        assertFalse(previewResult.has("removal_preview_uuid"),
                "the URL selects the preview; body correlation remains immutable deposit identity");
        assertEquals(30, previewResult.getAsJsonObject("result")
                .get("affected_block_count").getAsInt());

        JsonObject removalResult = json(ResourceDepositRemovalProtocol.encodeRemovalResult(
                removal, new ResourceDepositRemovalProtocol.Removed(35, 30, 2, 3, 0x8f00aaL, true)));
        assertEquals("removed", removalResult.get("status").getAsString());
        assertEquals("8f00aa", removalResult.getAsJsonObject("result")
                .get("tombstone_instance_id").getAsString());
        assertTrue(removalResult.getAsJsonObject("result").get("replayed").getAsBoolean());
    }

    @Test
    void rejectsUnsafeApprovalUnbalancedCountsOversizedBatchesAndDeferredCallbacks() {
        assertThrows(ResourceDepositRemovalProtocol.MalformedProtocolException.class,
                () -> ResourceDepositRemovalProtocol.parsePendingRemovals(bytes(
                        removalBatch().replace("\"safe_to_remove\":true", "\"safe_to_remove\":false"))));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDepositRemovalProtocol.Inspection(
                        new ResourceDepositPreviewProtocol.Bounds(0, 1, 0, 1, 0, 1),
                        10, 5, 2, 1, 1, 5, List.of(), List.of(), List.of(), true, "a".repeat(64)));
        String row = previewBatch().substring(previewBatch().indexOf("[{") + 1,
                previewBatch().lastIndexOf(']'));
        String oversized = "{\"protocol_version\":1,\"removal_preview_requests\":[" +
                String.join(",", java.util.Collections.nCopies(21, row)) + "]}";
        assertThrows(ResourceDepositRemovalProtocol.MalformedProtocolException.class,
                () -> ResourceDepositRemovalProtocol.parsePendingPreviews(bytes(oversized)));

        ResourceDepositRemovalProtocol.RemovalOperation removal =
                ResourceDepositRemovalProtocol.parsePendingRemovals(bytes(removalBatch())).getFirst();
        assertThrows(IllegalArgumentException.class, () ->
                ResourceDepositRemovalProtocol.encodeRemovalResult(
                        removal, ResourceDepositRemovalProtocol.Deferred.INSTANCE));
    }

    private static ResourceDepositRemovalProtocol.Inspection inspection() {
        return new ResourceDepositRemovalProtocol.Inspection(
                new ResourceDepositPreviewProtocol.Bounds(9, 11, 28, 36, 19, 21),
                35, 30, 2, 1, 2, 30,
                List.of("depleted_cells:2"), List.of(), List.of(), true, "a".repeat(64));
    }

    private static String previewBatch() {
        return "{\"protocol_version\":1,\"removal_preview_requests\":[{" +
                "\"protocol_version\":1," + correlation() +
                "\"removal_preview_uuid\":\"" + PREVIEW + "\"," + target() + "}]}";
    }

    private static String removalBatch() {
        return "{\"protocol_version\":1,\"removal_operations\":[{" +
                "\"protocol_version\":1,\"operation_uuid\":\"" + OPERATION + "\"," +
                correlation() + target() + ",\"approved_preview\":{" +
                "\"removal_preview_uuid\":\"" + PREVIEW + "\"," +
                "\"result_fingerprint\":\"" + "b".repeat(64) + "\",\"result\":" +
                inspectionJson() + "}}]}";
    }

    private static String correlation() {
        return "\"resource_deposit_uuid\":\"33333333-3333-4333-8333-333333333333\"," +
                "\"resource_deposit_revision\":7,\"actual_revision\":7," +
                "\"materialization_operation_uuid\":\"44444444-4444-4444-8444-444444444444\"," +
                "\"deposit_instance_id\":\"8f00aa\",\"resource_definition_key\":\"iron\",";
    }

    private static String target() {
        return "\"target\":{\"shard_uuid\":\"55555555-5555-4555-8555-555555555555\"," +
                "\"minecraft_server_uuid\":\"66666666-6666-4666-8666-666666666666\"," +
                "\"world_name\":\"Britannia\",\"dimension_key\":\"minecraft:overworld\"}";
    }

    private static String inspectionJson() {
        return "{\"bounds\":{\"min_x\":9,\"max_x\":11,\"min_y\":28,\"max_y\":36," +
                "\"min_z\":19,\"max_z\":21},\"owned_cell_count\":35," +
                "\"matching_cell_count\":30,\"depleted_cell_count\":2," +
                "\"player_modified_cell_count\":1,\"unexpected_cell_count\":2," +
                "\"affected_block_count\":30,\"warnings\":[\"depleted_cells:2\"]," +
                "\"protection_conflicts\":[],\"overlap_conflicts\":[]," +
                "\"safe_to_remove\":true,\"world_state_token\":\"" + "a".repeat(64) + "\"}";
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static JsonObject json(byte[] value) {
        return JsonParser.parseString(new String(value, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
