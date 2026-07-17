package com.seggellion.britannia_mod.service.spawn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnPendingRequestAdapterTest {
    @Test
    void adaptsUpsertUsingStableIdentityAndOnlyProtocolFields() {
        ServiceNpcSpawnPendingRecord record = record(ServiceNpcSpawnPendingOperation.UPSERT, 7L);
        ServiceNpcSpawnOperationRequest request = ServiceNpcSpawnPendingRequestAdapter.adapt(record);

        assertEquals(record.operationId(), request.operationId());
        assertEquals(record.spawnPointId(), request.spawnUuid());
        assertEquals(ServiceNpcSpawnOperation.UPSERT, request.operation());
        assertEquals(record.configurationRevision(), request.sourceRevision());
        assertEquals(record.cityPublicId(), request.cityPublicId());
        assertEquals(record.serviceNpcTypeKey(), request.serviceNpcTypeKey());
        assertEquals(record.enabled(), request.enabled());

        JsonObject json = JsonParser.parseString(new String(
            ServiceNpcSpawnRequestSerializer.serialize(request), StandardCharsets.UTF_8
        )).getAsJsonObject();
        String encoded = json.toString().toLowerCase();
        assertFalse(encoded.contains("shard"));
        assertFalse(encoded.contains("attempt"));
        assertFalse(encoded.contains("retry"));
        assertFalse(encoded.contains("disposition"));
        assertFalse(encoded.contains("failure"));
        assertFalse(encoded.contains("collision"));
    }

    @Test
    void removeUsesOptionalSnapshotAndUnconfiguredRemoveOmitsIt() {
        ServiceNpcSpawnPendingRecord configured = record(ServiceNpcSpawnPendingOperation.REMOVE, 7L);
        ServiceNpcSpawnOperationRequest configuredRequest =
            ServiceNpcSpawnPendingRequestAdapter.adapt(configured);
        assertEquals(configured.cityPublicId(), configuredRequest.cityPublicId());
        assertEquals(configured.serviceNpcTypeKey(), configuredRequest.serviceNpcTypeKey());
        assertEquals(configured.enabled(), configuredRequest.enabled());

        ServiceNpcSpawnPendingRecord unconfigured = new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.REMOVE, UUID.randomUUID(), "Britannia",
            configured.location(), null, null, true, 0L, 10L
        );
        ServiceNpcSpawnOperationRequest unconfiguredRequest =
            ServiceNpcSpawnPendingRequestAdapter.adapt(unconfigured);
        assertNull(unconfiguredRequest.cityPublicId());
        assertNull(unconfiguredRequest.serviceNpcTypeKey());
        assertNull(unconfiguredRequest.enabled());
    }

    @Test
    void nullRecordFailsLocallyBeforeSubmission() {
        assertEquals("invalid_local_operation",
            assertThrows(IllegalArgumentException.class,
                () -> ServiceNpcSpawnPendingRequestAdapter.adapt(null)).getMessage());
    }

    private static ServiceNpcSpawnPendingRecord record(
            ServiceNpcSpawnPendingOperation operation, long revision
    ) {
        return new ServiceNpcSpawnPendingRecord(
            operation,
            UUID.randomUUID(),
            "Britannia",
            new ServiceNpcSpawnLocation(
                "Britannia",
                ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(4, 70, -9)
            ),
            UUID.randomUUID(),
            "bank_teller",
            false,
            revision,
            10L
        );
    }
}
