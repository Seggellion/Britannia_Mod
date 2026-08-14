package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Immutable, queue-independent version 1 operation supplied explicitly by a caller. */
public record ServiceNpcSpawnOperationRequest(
    int protocolVersion,
    UUID operationId,
    ServiceNpcSpawnOperation operation,
    UUID spawnUuid,
    long sourceRevision,
    String worldName,
    ResourceLocation dimension,
    int x,
    int y,
    int z,
    UUID cityPublicId,
    String serviceNpcTypeKey,
    Boolean enabled
) {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_REQUEST_BYTES = 16 * 1024;
    public static final int MAX_WORLD_NAME_BYTES = 128;
    public static final int MAX_DIMENSION_BYTES = 255;
    public static final int MIN_XZ = -30_000_000;
    public static final int MAX_XZ = 30_000_000;
    public static final int MIN_Y = -4_096;
    public static final int MAX_Y = 4_096;

    public ServiceNpcSpawnOperationRequest {
        if (protocolVersion != PROTOCOL_VERSION) throw new IllegalArgumentException("unsupported_protocol");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(spawnUuid, "spawnUuid");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(dimension, "dimension");
        if (!isProtocolUuid(operationId) || !isProtocolUuid(spawnUuid)
            || (cityPublicId != null && !isProtocolUuid(cityPublicId))) {
            throw new IllegalArgumentException("invalid_uuid");
        }
        int worldBytes = worldName.getBytes(StandardCharsets.UTF_8).length;
        if (worldBytes < 1 || worldBytes > MAX_WORLD_NAME_BYTES || worldName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid_world_name");
        }
        String dimensionValue = dimension.toString();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(dimensionValue)
            || dimensionValue.getBytes(StandardCharsets.US_ASCII).length > MAX_DIMENSION_BYTES) {
            throw new IllegalArgumentException("invalid_dimension");
        }
        if (sourceRevision < 0 || (operation == ServiceNpcSpawnOperation.UPSERT && sourceRevision < 1)) {
            throw new IllegalArgumentException("invalid_revision");
        }
        if (x < MIN_XZ || x > MAX_XZ || z < MIN_XZ || z > MAX_XZ || y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("invalid_coordinates");
        }
        if (operation == ServiceNpcSpawnOperation.UPSERT
            && (cityPublicId == null || serviceNpcTypeKey == null || enabled == null)) {
            throw new IllegalArgumentException("incomplete_upsert");
        }
        if (serviceNpcTypeKey != null
            && (serviceNpcTypeKey.isEmpty() || serviceNpcTypeKey.getBytes(StandardCharsets.UTF_8).length > 255
                || !serviceNpcTypeKey.matches("(?:economic:)?[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))) {
            throw new IllegalArgumentException("invalid_service_npc_type");
        }
    }

    static boolean isProtocolUuid(UUID value) {
        return value != null && value.version() >= 1 && value.version() <= 5 && value.variant() == 2;
    }
}
