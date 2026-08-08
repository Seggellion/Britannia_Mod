package com.seggellion.britannia_mod.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ServiceNpcRegistryCache {
    private static final AtomicReference<ServiceNpcRegistrySnapshot> CURRENT =
            new AtomicReference<>(ServiceNpcRegistrySnapshot.empty());

    private ServiceNpcRegistryCache() {
    }

    public static ServiceNpcRegistrySnapshot snapshot() {
        return CURRENT.get();
    }

    public static void replace(ServiceNpcRegistrySnapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static void clear() {
        CURRENT.set(ServiceNpcRegistrySnapshot.empty());
    }
}
