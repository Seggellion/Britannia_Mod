package com.seggellion.britannia_mod.bannerdyeing.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import org.junit.jupiter.api.Test;

class BannerReloadCycleTest {
    @Test
    void repeatedPublicationNeverExposesAPartialOrMixedSnapshot() throws Exception {
        RegistrySnapshot first = DyeResolverFixtures.productionSnapshot();
        RegistrySnapshotPublisher publisher = new RegistrySnapshotPublisher();
        for (int cycle = 0; cycle < 25; cycle++) {
            publisher.publish(first);
            RegistrySnapshot observed = publisher.current();
            assertSame(first, observed);
            assertEquals(35, observed.banners().activeCount());
            assertEquals(4, observed.fabricMaterials().activeCount());
            assertEquals(4, observed.materialPalettes().activeCount());
            assertEquals(7, observed.pigments().activeCount());
            assertEquals(2, observed.mounts().activeCount());
            assertTrue(publisher.hasPublishedSnapshot());
        }
    }
}
