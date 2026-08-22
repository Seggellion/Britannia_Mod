package com.seggellion.britannia_mod.resource.materialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceDepositMaterializationProtocolTest {
    private static final UUID OPERATION =
            UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID DEPOSIT =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void parsesTheMirroredRailsOperationWithItsExactApprovedM4Snapshot() throws IOException {
        List<ResourceDepositMaterializationProtocol.Operation> operations =
                ResourceDepositMaterializationProtocol.parsePending(
                        contract("resource_deposit_materialization_pending_v1.json"));

        assertEquals(1, operations.size());
        ResourceDepositMaterializationProtocol.Operation operation = operations.getFirst();
        assertEquals(OPERATION, operation.operationUuid());
        assertEquals(DEPOSIT, operation.resourceDepositUuid());
        assertEquals(7, operation.resourceDepositRevision());
        assertEquals("iron", operation.resourceDefinitionKey());
        assertEquals(10, operation.x());
        assertEquals(20, operation.z());
        assertEquals(35, operation.approvedPreview().plannedBlockCount());
        assertEquals(35, operation.approvedPreview().validHostCount());
    }

    @Test
    void encodesSuccessReplayAndFailureExactlyLikeTheMirroredRailsFixtures() throws IOException {
        ResourceDepositMaterializationProtocol.Operation operation = operation();
        ResourceDepositPreviewProtocol.Bounds bounds =
                new ResourceDepositPreviewProtocol.Bounds(8, 12, 36, 44, 18, 22);
        ResourceDepositMaterializationProtocol.Generated generated =
                new ResourceDepositMaterializationProtocol.Generated(
                        0x8f00aaL, 3, 40, bounds, 35, 35, 0, 1, false);
        ResourceDepositMaterializationProtocol.Generated replayed =
                new ResourceDepositMaterializationProtocol.Generated(
                        0x8f00aaL, 3, 40, bounds, 35, 35, 0, 1, true);
        ResourceDepositMaterializationProtocol.Failure failed =
                new ResourceDepositMaterializationProtocol.Failure(
                        "preview_world_changed",
                        "current host state differs from approved preview", false);

        assertEquals(json(contract("resource_deposit_materialization_generated_v1.json")),
                json(ResourceDepositMaterializationProtocol.encodeResult(operation, generated)));
        assertEquals(json(contract("resource_deposit_materialization_replayed_v1.json")),
                json(ResourceDepositMaterializationProtocol.encodeResult(operation, replayed)));
        assertEquals(json(contract("resource_deposit_materialization_failed_v1.json")),
                json(ResourceDepositMaterializationProtocol.encodeResult(operation, failed)));
    }

    @Test
    void rejectsInvalidApprovalAndNeverSerializesInternalDeferredProgress() throws IOException {
        String pending = new String(contract("resource_deposit_materialization_pending_v1.json"),
                StandardCharsets.UTF_8);
        assertThrows(ResourceDepositMaterializationProtocol.MalformedProtocolException.class,
                () -> ResourceDepositMaterializationProtocol.parsePending(
                        pending.replace("\"valid\": true", "\"valid\": false")
                                .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceDepositMaterializationProtocol.encodeResult(
                        operation(), ResourceDepositMaterializationProtocol.Deferred.INSTANCE));
    }

    @Test
    void worldAdminIdentityIsStablePerOperationAndExplainsItsRailsRevision() {
        String encoded = DepositIdentity.worldAdminEncoding(
                OPERATION, DEPOSIT, 7, "minecraft:overworld", "iron");
        assertEquals("v1|admin|world_admin_map|55555555-5555-4555-8555-555555555555|" +
                "22222222-2222-4222-8222-222222222222|7|minecraft:overworld|iron", encoded);
        assertEquals(DepositIdentity.worldAdmin(
                        OPERATION, DEPOSIT, 7, "minecraft:overworld", "iron"),
                DepositIdentity.worldAdmin(
                        OPERATION, DEPOSIT, 7, "minecraft:overworld", "iron"));
        assertNotEquals(DepositIdentity.worldAdmin(
                        OPERATION, DEPOSIT, 7, "minecraft:overworld", "iron"),
                DepositIdentity.worldAdmin(
                        UUID.randomUUID(), DEPOSIT, 7, "minecraft:overworld", "iron"));
    }

    private static ResourceDepositMaterializationProtocol.Operation operation() throws IOException {
        return ResourceDepositMaterializationProtocol.parsePending(
                contract("resource_deposit_materialization_pending_v1.json")).getFirst();
    }

    private static JsonElement json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
    }

    private static byte[] contract(String name) throws IOException {
        try (InputStream input = ResourceDepositMaterializationProtocolTest.class
                .getResourceAsStream("/contracts/" + name)) {
            if (input == null) throw new IOException("missing mirrored contract fixture: " + name);
            return input.readAllBytes();
        }
    }
}
