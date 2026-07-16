package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record ServiceNpcSpawnPendingRecord(
        ServiceNpcSpawnPendingOperation operation,
        UUID spawnPointId,
        String shardName,
        ServiceNpcSpawnLocation location,
        @Nullable UUID cityPublicId,
        @Nullable String serviceNpcTypeKey,
        boolean enabled,
        long configurationRevision,
        long recordedAtEpochMillis
) {
    public static final int MAX_SHARD_NAME_BYTES = 128;
    public static final int MAX_TYPE_KEY_BYTES = 64;

    public ServiceNpcSpawnPendingRecord {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(shardName, "shardName");
        Objects.requireNonNull(location, "location");
        requireBounded(shardName, MAX_SHARD_NAME_BYTES, "shardName");
        if (shardName.isBlank()) throw new IllegalArgumentException("shardName must not be blank");
        if (serviceNpcTypeKey != null) {
            requireBounded(serviceNpcTypeKey, MAX_TYPE_KEY_BYTES, "serviceNpcTypeKey");
        }
        if (configurationRevision < 0L) {
            throw new IllegalArgumentException("configurationRevision must be non-negative");
        }
        if (recordedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("recordedAtEpochMillis must be non-negative");
        }
        if (operation == ServiceNpcSpawnPendingOperation.UPSERT
                && (cityPublicId == null || serviceNpcTypeKey == null || serviceNpcTypeKey.isBlank())) {
            throw new IllegalArgumentException("UPSERT requires city and service type");
        }
    }

    public boolean sameSnapshot(ServiceNpcSpawnPendingRecord other) {
        return other != null
                && operation == other.operation
                && spawnPointId.equals(other.spawnPointId)
                && shardName.equals(other.shardName)
                && location.equals(other.location)
                && Objects.equals(cityPublicId, other.cityPublicId)
                && Objects.equals(serviceNpcTypeKey, other.serviceNpcTypeKey)
                && enabled == other.enabled
                && configurationRevision == other.configurationRevision;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Operation", operation.name());
        tag.putUUID("SpawnPointId", spawnPointId);
        tag.putString("ShardName", shardName);
        tag.putString("WorldName", location.worldName());
        tag.putString("Dimension", location.dimension().toString());
        tag.putInt("X", location.pos().getX());
        tag.putInt("Y", location.pos().getY());
        tag.putInt("Z", location.pos().getZ());
        if (cityPublicId != null) tag.putUUID("CityPublicId", cityPublicId);
        if (serviceNpcTypeKey != null) tag.putString("ServiceNpcTypeKey", serviceNpcTypeKey);
        tag.putBoolean("Enabled", enabled);
        tag.putLong("ConfigurationRevision", configurationRevision);
        tag.putLong("RecordedAtEpochMillis", recordedAtEpochMillis);
        return tag;
    }

    public static ServiceNpcSpawnPendingRecord fromNbt(CompoundTag tag) {
        ServiceNpcSpawnPendingOperation operation = ServiceNpcSpawnPendingOperation.valueOf(tag.getString("Operation"));
        UUID spawnPointId = tag.getUUID("SpawnPointId");
        String shardName = tag.getString("ShardName");
        String worldName = tag.getString("WorldName");
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid dimension");
        ServiceNpcSpawnLocation location = new ServiceNpcSpawnLocation(
                worldName,
                dimension,
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
        );
        UUID cityPublicId = tag.hasUUID("CityPublicId") ? tag.getUUID("CityPublicId") : null;
        String serviceType = tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null;
        return new ServiceNpcSpawnPendingRecord(
                operation,
                spawnPointId,
                shardName,
                location,
                cityPublicId,
                serviceType,
                tag.getBoolean("Enabled"),
                tag.getLong("ConfigurationRevision"),
                tag.getLong("RecordedAtEpochMillis")
        );
    }

    private static void requireBounded(String value, int maxBytes, String field) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }
}
