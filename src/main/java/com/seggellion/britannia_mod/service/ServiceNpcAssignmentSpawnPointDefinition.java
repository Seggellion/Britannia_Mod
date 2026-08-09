package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.UUID;

public record ServiceNpcAssignmentSpawnPointDefinition(
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
}
