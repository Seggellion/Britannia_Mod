package com.seggellion.britannia_mod.service;

import java.util.List;

public record ServiceNpcTypeDefinition(
        String key,
        String displayName,
        String professionKey,
        String minecraftEntityTypeKey,
        String defaultDialogueKey,
        List<String> allowedServiceKeys,
        boolean active,
        boolean spawnable,
        long definitionRevision
) {
    public ServiceNpcTypeDefinition {
        allowedServiceKeys = List.copyOf(allowedServiceKeys);
    }
}
