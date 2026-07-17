package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record ServiceNpcSpawnCanonicalLocation(
    UUID minecraftServerKey, String worldName, ResourceLocation dimension, int x, int y, int z
) {
    public ServiceNpcSpawnCanonicalLocation {
        Objects.requireNonNull(minecraftServerKey, "minecraftServerKey");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(dimension, "dimension");
        int bytes = worldName.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > ServiceNpcSpawnOperationRequest.MAX_WORLD_NAME_BYTES || worldName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid_canonical_world_name");
        }
        if (x < ServiceNpcSpawnOperationRequest.MIN_XZ || x > ServiceNpcSpawnOperationRequest.MAX_XZ
            || z < ServiceNpcSpawnOperationRequest.MIN_XZ || z > ServiceNpcSpawnOperationRequest.MAX_XZ
            || y < ServiceNpcSpawnOperationRequest.MIN_Y || y > ServiceNpcSpawnOperationRequest.MAX_Y) {
            throw new IllegalArgumentException("invalid_canonical_coordinates");
        }
    }
}
