package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.UUID;

public record ServiceNpcAssignmentWorldNpcDefinition(
        UUID publicId,
        String name,
        String genderKey,
        String professionKey,
        @Nullable String serviceNpcTypeKey,
        long revision
) {
}
