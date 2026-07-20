package com.seggellion.britannia_mod.bannerdyeing.registry;

/** Common-side lookup entry point used by reload integration and later gameplay milestones. */
public final class BannerDataRegistries {
    private static final RegistrySnapshotPublisher PUBLISHER = new RegistrySnapshotPublisher();

    private BannerDataRegistries() {
    }

    public static RegistrySnapshot current() {
        return PUBLISHER.current();
    }

    static RegistrySnapshotPublisher publisher() {
        return PUBLISHER;
    }
}
