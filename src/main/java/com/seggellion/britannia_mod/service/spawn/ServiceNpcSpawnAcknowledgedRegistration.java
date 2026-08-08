package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable, server-wide record of the last Rails-acknowledged revision for a spawn point,
 * kept independent of the transient {@link ServiceNpcSpawnAcknowledgementReceipt} and of
 * block-entity NBT so a REMOVE can carry an exact revision even after both are lost.
 */
public record ServiceNpcSpawnAcknowledgedRegistration(
    UUID spawnPointId,
    String shardName,
    ServiceNpcSpawnLocation location,
    long revision,
    State state,
    long recordedAtEpochMillis
) {
    public enum State {
        LIVE,
        REMOVED
    }

    public ServiceNpcSpawnAcknowledgedRegistration {
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(shardName, "shardName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(state, "state");
        if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(spawnPointId)) {
            throw new IllegalArgumentException("spawnPointId is not a protocol UUID");
        }
        if (shardName.isBlank()) throw new IllegalArgumentException("shardName must not be blank");
        if (revision < 0L || recordedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("acknowledged registration metadata must be non-negative");
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
        tag.putLong("Revision", revision);
        tag.putString("State", state.name());
        tag.putLong("RecordedAtEpochMillis", recordedAtEpochMillis);
        return tag;
    }

    static ServiceNpcSpawnAcknowledgedRegistration fromNbt(CompoundTag tag) {
        if (!tag.hasUUID("SpawnPointId") || !tag.contains("State", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing acknowledged registration identity");
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid acknowledged registration dimension");
        return new ServiceNpcSpawnAcknowledgedRegistration(
            tag.getUUID("SpawnPointId"),
            tag.getString("ShardName"),
            new ServiceNpcSpawnLocation(
                tag.getString("WorldName"), dimension,
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
            ),
            tag.getLong("Revision"),
            State.valueOf(tag.getString("State")),
            tag.getLong("RecordedAtEpochMillis")
        );
    }
}
