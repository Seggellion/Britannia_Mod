package com.seggellion.britannia_mod.bannerdyeing.validation;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record ValidationSummary(
        int resourcesDiscovered,
        int resourcesDecoded,
        Map<RegistryDomain, Integer> activeEntries,
        Map<RegistryDomain, Integer> disabledEntries,
        int errors,
        int warnings,
        int information) {

    public ValidationSummary {
        activeEntries = immutableCounts(activeEntries);
        disabledEntries = immutableCounts(disabledEntries);
    }

    private static Map<RegistryDomain, Integer> immutableCounts(Map<RegistryDomain, Integer> source) {
        EnumMap<RegistryDomain, Integer> counts = new EnumMap<>(RegistryDomain.class);
        for (RegistryDomain domain : RegistryDomain.values()) {
            counts.put(domain, source.getOrDefault(domain, 0));
        }
        return Collections.unmodifiableMap(counts);
    }
}
