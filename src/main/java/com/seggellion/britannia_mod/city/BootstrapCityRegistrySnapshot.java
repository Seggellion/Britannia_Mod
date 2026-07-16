package com.seggellion.britannia_mod.city;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record BootstrapCityRegistrySnapshot(boolean available, Map<UUID, BootstrapCityDefinition> cities) {
    private static final BootstrapCityRegistrySnapshot UNAVAILABLE =
            new BootstrapCityRegistrySnapshot(false, Map.of());

    public BootstrapCityRegistrySnapshot {
        cities = Collections.unmodifiableMap(new LinkedHashMap<>(cities));
    }

    public static BootstrapCityRegistrySnapshot unavailable() {
        return UNAVAILABLE;
    }

    public static BootstrapCityRegistrySnapshot available(Collection<BootstrapCityDefinition> definitions) {
        LinkedHashMap<UUID, BootstrapCityDefinition> ordered = new LinkedHashMap<>();
        definitions.stream()
                .sorted(Comparator.comparing(BootstrapCityDefinition::displayName)
                        .thenComparing(definition -> definition.publicId().toString()))
                .forEach(definition -> {
                    if (ordered.putIfAbsent(definition.publicId(), definition) != null) {
                        throw new IllegalArgumentException("duplicate city public ID " + definition.publicId());
                    }
                });
        return new BootstrapCityRegistrySnapshot(true, ordered);
    }

    public BootstrapCityDefinition find(UUID publicId) {
        return cities.get(publicId);
    }
}
