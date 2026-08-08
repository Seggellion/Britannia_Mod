package com.seggellion.britannia_mod.service;

import java.util.UUID;

public record ServiceNpcAssignmentDefinition(
        UUID publicId,
        UUID spawnPointPublicId,
        UUID worldNpcPublicId,
        String status,
        long revision,
        String assignedAt
) {
}
