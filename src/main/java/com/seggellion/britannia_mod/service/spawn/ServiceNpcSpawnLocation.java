package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ServiceNpcSpawnLocation(String worldName, ResourceLocation dimension, BlockPos pos) {
    public static final int MAX_WORLD_NAME_BYTES = 128;

    public ServiceNpcSpawnLocation {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(pos, "pos");
        if (worldName.isBlank()) throw new IllegalArgumentException("worldName must not be blank");
        if (worldName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_WORLD_NAME_BYTES) {
            throw new IllegalArgumentException("worldName is too long");
        }
        pos = pos.immutable();
    }

    // worldName is diagnostic only (Rails active-coordinate identity is server + dimension + x/y/z);
    // physical identity must not change on a world-name-only rename.
    @Override
    public boolean equals(Object other) {
        return other instanceof ServiceNpcSpawnLocation that
            && dimension.equals(that.dimension)
            && pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, pos);
    }
}
