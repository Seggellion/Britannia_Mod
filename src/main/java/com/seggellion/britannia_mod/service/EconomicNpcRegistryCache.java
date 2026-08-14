package com.seggellion.britannia_mod.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Mirrors {@link ServiceNpcRegistryCache} for the Economic specialization. */
public final class EconomicNpcRegistryCache {
    private static final AtomicReference<EconomicNpcRegistrySnapshot> CURRENT =
            new AtomicReference<>(EconomicNpcRegistrySnapshot.empty());

    private EconomicNpcRegistryCache() {
    }

    public static EconomicNpcRegistrySnapshot snapshot() {
        return CURRENT.get();
    }

    public static void replace(EconomicNpcRegistrySnapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static void clear() {
        CURRENT.set(EconomicNpcRegistrySnapshot.empty());
    }
}
