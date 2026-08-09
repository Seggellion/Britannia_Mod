package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

public record ServiceNpcSpawnCollisionEvidence(
    CollisionKind collisionKind,
    boolean replacementUuidRequired,
    ServiceNpcSpawnCanonicalLocation canonicalLocation,
    long receivedAtEpochMillis
) {
    public enum CollisionKind { LIVE, TOMBSTONED, REDACTED }

    public ServiceNpcSpawnCollisionEvidence {
        Objects.requireNonNull(collisionKind, "collisionKind");
        if (receivedAtEpochMillis < 0) throw new IllegalArgumentException("negative collision timestamp");
        if (collisionKind == CollisionKind.REDACTED && canonicalLocation != null) {
            throw new IllegalArgumentException("redacted collision cannot contain canonical location");
        }
        if (collisionKind != CollisionKind.LIVE && canonicalLocation != null) {
            throw new IllegalArgumentException("only live collision may contain canonical location");
        }
        if (canonicalLocation != null
            && (!ServiceNpcSpawnOperationRequest.isProtocolUuid(canonicalLocation.minecraftServerKey())
                || canonicalLocation.dimension().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII).length
                    > ServiceNpcSpawnOperationRequest.MAX_DIMENSION_BYTES)) {
            throw new IllegalArgumentException("invalid collision canonical location");
        }
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CollisionKind", collisionKind.name());
        tag.putBoolean("ReplacementUuidRequired", replacementUuidRequired);
        tag.putLong("ReceivedAtEpochMillis", receivedAtEpochMillis);
        if (canonicalLocation != null) {
            CompoundTag location = new CompoundTag();
            location.putUUID("MinecraftServerKey", canonicalLocation.minecraftServerKey());
            location.putString("WorldName", canonicalLocation.worldName());
            location.putString("Dimension", canonicalLocation.dimension().toString());
            location.putInt("X", canonicalLocation.x());
            location.putInt("Y", canonicalLocation.y());
            location.putInt("Z", canonicalLocation.z());
            tag.put("CanonicalLocation", location);
        }
        return tag;
    }

    static ServiceNpcSpawnCollisionEvidence fromNbt(CompoundTag tag) {
        CollisionKind kind = CollisionKind.valueOf(tag.getString("CollisionKind"));
        ServiceNpcSpawnCanonicalLocation canonical = null;
        if (tag.contains("CanonicalLocation", CompoundTag.TAG_COMPOUND)) {
            CompoundTag location = tag.getCompound("CanonicalLocation");
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(location.getString("Dimension"));
            if (dimension == null || !location.hasUUID("MinecraftServerKey")) {
                throw new IllegalArgumentException("invalid canonical location");
            }
            canonical = new ServiceNpcSpawnCanonicalLocation(
                location.getUUID("MinecraftServerKey"), location.getString("WorldName"), dimension,
                location.getInt("X"), location.getInt("Y"), location.getInt("Z")
            );
        }
        return new ServiceNpcSpawnCollisionEvidence(
            kind, tag.getBoolean("ReplacementUuidRequired"), canonical, tag.getLong("ReceivedAtEpochMillis")
        );
    }
}
