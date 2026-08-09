package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Publishes an entire immutable snapshot with one volatile write. */
public final class RegistrySnapshotPublisher {
    private final AtomicReference<Publication> current;

    public RegistrySnapshotPublisher() {
        current = new AtomicReference<>(new Publication(RegistrySnapshot.empty(), false));
    }

    public RegistrySnapshotPublisher(RegistrySnapshot initialSnapshot) {
        current = new AtomicReference<>(new Publication(
                Objects.requireNonNull(initialSnapshot, "initialSnapshot"), true));
    }

    public RegistrySnapshot current() {
        return current.get().snapshot();
    }

    public boolean hasPublishedSnapshot() {
        return current.get().published();
    }

    void publish(RegistrySnapshot snapshot) {
        current.set(new Publication(Objects.requireNonNull(snapshot, "snapshot"), true));
    }

    private record Publication(RegistrySnapshot snapshot, boolean published) {
    }
}
