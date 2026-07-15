package com.seggellion.britannia_mod.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ServiceNpcRegistrySnapshot(
        int schemaVersion,
        long revision,
        Map<String, ServiceActionDefinition> serviceActions,
        Map<String, ServiceNpcTypeDefinition> serviceNpcTypes,
        Map<String, ServiceDialogueSetDefinition> dialogueSets
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final ServiceNpcRegistrySnapshot EMPTY = new ServiceNpcRegistrySnapshot(
            SUPPORTED_SCHEMA_VERSION,
            0L,
            Map.of(),
            Map.of(),
            Map.of()
    );

    public ServiceNpcRegistrySnapshot {
        serviceActions = immutableOrderedMap(serviceActions);
        serviceNpcTypes = immutableOrderedMap(serviceNpcTypes);
        dialogueSets = immutableOrderedMap(dialogueSets);
    }

    public static ServiceNpcRegistrySnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return serviceNpcTypes.isEmpty() && dialogueSets.isEmpty();
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
