package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.UUID;

public record ServiceNpcAssignmentSpawnPointDefinition(
        UUID publicId,
        UUID minecraftServerPublicId,
        @Nullable UUID cityPublicId,
        @Nullable String serviceNpcTypeKey,
        @Nullable String economicNpcTypeKey,
        String worldName,
        String dimensionKey,
        int x,
        int y,
        int z,
        boolean enabled,
        long revision
) {
    /** Compatibility constructor for pre-Milestone-5 (service-only) callers. */
    public ServiceNpcAssignmentSpawnPointDefinition(
            UUID publicId,
            UUID minecraftServerPublicId,
            @Nullable UUID cityPublicId,
            @Nullable String serviceNpcTypeKey,
            String worldName,
            String dimensionKey,
            int x,
            int y,
            int z,
            boolean enabled,
            long revision
    ) {
        this(publicId, minecraftServerPublicId, cityPublicId, serviceNpcTypeKey, null,
                worldName, dimensionKey, x, y, z, enabled, revision);
    }
}
