package com.seggellion.britannia_mod.service.spawn;

/** Converts durable local intent to the closed Slice 2 wire request. */
public final class ServiceNpcSpawnPendingRequestAdapter {
    private ServiceNpcSpawnPendingRequestAdapter() {}

    public static ServiceNpcSpawnOperationRequest adapt(ServiceNpcSpawnPendingRecord record) {
        if (record == null) throw new IllegalArgumentException("invalid_local_operation");
        ServiceNpcSpawnLocation location = record.location();
        boolean upsert = record.operation() == ServiceNpcSpawnPendingOperation.UPSERT;
        boolean hasRemoveSnapshot = record.cityPublicId() != null || record.serviceNpcTypeKey() != null;
        return new ServiceNpcSpawnOperationRequest(
            ServiceNpcSpawnOperationRequest.PROTOCOL_VERSION,
            record.operationId(),
            ServiceNpcSpawnOperation.valueOf(record.operation().name()),
            record.spawnPointId(),
            record.configurationRevision(),
            location.worldName(),
            location.dimension(),
            location.pos().getX(),
            location.pos().getY(),
            location.pos().getZ(),
            record.cityPublicId(),
            record.serviceNpcTypeKey(),
            upsert || hasRemoveSnapshot ? record.enabled() : null
        );
    }
}
