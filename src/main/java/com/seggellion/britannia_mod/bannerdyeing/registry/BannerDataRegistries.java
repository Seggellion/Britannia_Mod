package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Common-side lookup entry point used by reload integration and later gameplay milestones. */
public final class BannerDataRegistries {
    private static final RegistrySnapshotPublisher PUBLISHER = new RegistrySnapshotPublisher();
    private static final AtomicReference<Diagnostics> DIAGNOSTICS =
            new AtomicReference<>(new Diagnostics(RegistrySnapshot.empty(), List.of()));

    private BannerDataRegistries() {
    }

    public static RegistrySnapshot current() {
        return PUBLISHER.current();
    }

    public static boolean isAvailable() {
        return PUBLISHER.hasPublishedSnapshot();
    }

    /** Returns diagnostics only when they belong to the exact immutable publication supplied by the caller. */
    public static List<ValidationIssue> validationIssues(RegistrySnapshot snapshot) {
        Diagnostics diagnostics = DIAGNOSTICS.get();
        return diagnostics.snapshot() == snapshot ? diagnostics.issues() : List.of();
    }

    static RegistrySnapshotPublisher publisher() {
        return PUBLISHER;
    }

    static void publish(RegistrySnapshot snapshot, List<ValidationIssue> issues) {
        PUBLISHER.publish(snapshot);
        DIAGNOSTICS.set(new Diagnostics(snapshot, List.copyOf(issues)));
    }

    private record Diagnostics(RegistrySnapshot snapshot, List<ValidationIssue> issues) {
    }
}
