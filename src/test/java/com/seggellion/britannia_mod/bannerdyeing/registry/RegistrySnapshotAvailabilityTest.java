package com.seggellion.britannia_mod.bannerdyeing.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegistrySnapshotAvailabilityTest {
    @Test
    void defaultPublisherIsUnavailableUntilFirstPublication() {
        RegistrySnapshotPublisher publisher = new RegistrySnapshotPublisher();
        assertFalse(publisher.hasPublishedSnapshot());
        publisher.publish(RegistrySnapshot.empty());
        assertTrue(publisher.hasPublishedSnapshot());
    }

    @Test
    void explicitlySeededPublisherIsAvailable() {
        assertTrue(new RegistrySnapshotPublisher(RegistrySnapshot.empty()).hasPublishedSnapshot());
    }
}
