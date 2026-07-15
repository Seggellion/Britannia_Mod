package com.seggellion.britannia_mod.city;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class BootstrapCityRegistryCache {
    private static final AtomicReference<BootstrapCityRegistrySnapshot> CURRENT =
            new AtomicReference<>(BootstrapCityRegistrySnapshot.unavailable());

    private BootstrapCityRegistryCache() {
    }

    public static BootstrapCityRegistrySnapshot snapshot() {
        return CURRENT.get();
    }

    public static void replace(BootstrapCityRegistrySnapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static void clear() {
        CURRENT.set(BootstrapCityRegistrySnapshot.unavailable());
    }
}
