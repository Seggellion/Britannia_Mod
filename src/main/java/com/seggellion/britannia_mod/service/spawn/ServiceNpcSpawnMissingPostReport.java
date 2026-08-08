package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable, inspectable record that the missing-post reconciler confirmed a live-registered
 * spawn point is gone. This is observational only: it never tombstones or deletes the
 * Rails-mirrored {@link ServiceNpcSpawnAcknowledgedRegistration}; only the existing
 * UPSERT/REMOVE acknowledgement path may change that snapshot's state.
 */
public record ServiceNpcSpawnMissingPostReport(
    UUID spawnPointId,
    String shardName,
    ServiceNpcSpawnLocation location,
    long revisionAtDetection,
    long detectedAtEpochMillis
) {
    public ServiceNpcSpawnMissingPostReport {
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(shardName, "shardName");
        Objects.requireNonNull(location, "location");
        if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(spawnPointId)) {
            throw new IllegalArgumentException("spawnPointId is not a protocol UUID");
        }
        if (shardName.isBlank()) throw new IllegalArgumentException("shardName must not be blank");
        if (revisionAtDetection < 0L || detectedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("missing-post report metadata must be non-negative");
        }
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SpawnPointId", spawnPointId);
        tag.putString("ShardName", shardName);
        tag.putString("WorldName", location.worldName());
        tag.putString("Dimension", location.dimension().toString());
        tag.putInt("X", location.pos().getX());
        tag.putInt("Y", location.pos().getY());
        tag.putInt("Z", location.pos().getZ());
        tag.putLong("RevisionAtDetection", revisionAtDetection);
        tag.putLong("DetectedAtEpochMillis", detectedAtEpochMillis);
        return tag;
    }

    static ServiceNpcSpawnMissingPostReport fromNbt(CompoundTag tag) {
        if (!tag.hasUUID("SpawnPointId")) {
            throw new IllegalArgumentException("missing missing-post report identity");
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid missing-post report dimension");
        return new ServiceNpcSpawnMissingPostReport(
            tag.getUUID("SpawnPointId"),
            tag.getString("ShardName"),
            new ServiceNpcSpawnLocation(
                tag.getString("WorldName"), dimension,
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
            ),
            tag.getLong("RevisionAtDetection"),
            tag.getLong("DetectedAtEpochMillis")
        );
    }
}
