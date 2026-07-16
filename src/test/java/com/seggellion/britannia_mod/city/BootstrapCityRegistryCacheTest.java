package com.seggellion.britannia_mod.city;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapCityRegistryCacheTest {
    @AfterEach
    void clearCache() {
        BootstrapCityRegistryCache.clear();
    }

    @Test
    void unavailableAndImmutableSnapshotsAreDistinct() {
        assertFalse(BootstrapCityRegistryCache.snapshot().available());

        UUID britainId = UUID.randomUUID();
        BootstrapCityRegistrySnapshot snapshot = BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(britainId, "Britain")
        ));
        BootstrapCityRegistryCache.replace(snapshot);

        assertTrue(BootstrapCityRegistryCache.snapshot().available());
        assertEquals("Britain", BootstrapCityRegistryCache.snapshot().find(britainId).displayName());
        assertThrows(UnsupportedOperationException.class,
                () -> BootstrapCityRegistryCache.snapshot().cities().clear());
    }
}
