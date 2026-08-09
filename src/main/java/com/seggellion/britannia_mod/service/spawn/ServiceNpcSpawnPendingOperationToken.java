package com.seggellion.britannia_mod.service.spawn;

import java.util.Objects;
import java.util.UUID;

public record ServiceNpcSpawnPendingOperationToken(
    UUID operationId,
    UUID spawnPointId,
    ServiceNpcSpawnPendingOperation operation,
    long configurationRevision,
    long recordedAtEpochMillis
) {
    public ServiceNpcSpawnPendingOperationToken {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(operation, "operation");
        if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(operationId)
            || !ServiceNpcSpawnOperationRequest.isProtocolUuid(spawnPointId)) {
            throw new IllegalArgumentException("token UUID is not protocol-valid");
        }
        if (configurationRevision < 0 || recordedAtEpochMillis < 0) {
            throw new IllegalArgumentException("token values must be non-negative");
        }
    }
}
