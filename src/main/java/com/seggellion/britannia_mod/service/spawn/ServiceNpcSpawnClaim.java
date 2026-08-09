package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record ServiceNpcSpawnClaim(UUID spawnPointId, ServiceNpcSpawnLocation location) {
    public ServiceNpcSpawnClaim {
        Objects.requireNonNull(spawnPointId, "spawnPointId");
        Objects.requireNonNull(location, "location");
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SpawnPointId", spawnPointId);
        tag.putString("WorldName", location.worldName());
        tag.putString("Dimension", location.dimension().toString());
        tag.putInt("X", location.pos().getX());
        tag.putInt("Y", location.pos().getY());
        tag.putInt("Z", location.pos().getZ());
        return tag;
    }

    public static ServiceNpcSpawnClaim fromNbt(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("invalid dimension");
        return new ServiceNpcSpawnClaim(
                tag.getUUID("SpawnPointId"),
                new ServiceNpcSpawnLocation(
                        tag.getString("WorldName"),
                        dimension,
                        new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
                )
        );
    }
}
