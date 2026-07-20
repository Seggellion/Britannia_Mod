package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Publishes an entire immutable snapshot with one volatile write. */
public final class RegistrySnapshotPublisher {
    private final AtomicReference<RegistrySnapshot> current;

    public RegistrySnapshotPublisher() {
        this(RegistrySnapshot.empty());
    }

    public RegistrySnapshotPublisher(RegistrySnapshot initialSnapshot) {
        current = new AtomicReference<>(Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
    }

    public RegistrySnapshot current() {
        return current.get();
    }

    void publish(RegistrySnapshot snapshot) {
        current.set(Objects.requireNonNull(snapshot, "snapshot"));
    }
}
