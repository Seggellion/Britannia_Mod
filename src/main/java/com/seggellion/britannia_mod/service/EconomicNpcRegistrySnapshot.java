package com.seggellion.britannia_mod.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable cache value for the Economic NPC type registry bootstrap section.
 * Follows {@link ServiceNpcRegistrySnapshot}'s accept-or-empty-wholesale
 * pattern; {@code revision} is the Rails digest string, kept opaque.
 */
public record EconomicNpcRegistrySnapshot(
        int schemaVersion,
        String revision,
        Map<String, EconomicNpcTypeDefinition> economicNpcTypes
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final EconomicNpcRegistrySnapshot EMPTY =
            new EconomicNpcRegistrySnapshot(SUPPORTED_SCHEMA_VERSION, "", Map.of());

    public EconomicNpcRegistrySnapshot {
        Objects.requireNonNull(revision, "revision");
        economicNpcTypes = Collections.unmodifiableMap(new LinkedHashMap<>(economicNpcTypes));
    }

    public static EconomicNpcRegistrySnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return economicNpcTypes.isEmpty();
    }
}
