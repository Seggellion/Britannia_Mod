package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.diagnostics.BoundedDiagnosticTracker;
import com.seggellion.britannia_mod.client.banner.BannerMissingLogTracker;
import com.seggellion.britannia_mod.client.banner.BannerRenderFailure;
import org.junit.jupiter.api.Test;

class BannerDiagnosticBoundsTest {
    @Test
    void genericDiagnosticsDeduplicateAndEvictAtTheirConfiguredCeiling() {
        BoundedDiagnosticTracker<String> tracker = new BoundedDiagnosticTracker<>(8);
        assertTrue(tracker.first("same"));
        assertFalse(tracker.first("same"));
        for (int index = 0; index < 100; index++) {
            tracker.first("entry-" + index);
        }
        assertEquals(8, tracker.size());
        assertEquals(8, tracker.maximumSize());
    }

    @Test
    void clientMissingAssetDiagnosticsRemainBoundedUnderHostileUniqueKeys() {
        BannerMissingLogTracker tracker = new BannerMissingLogTracker();
        for (int index = 0; index < 1_000; index++) {
            tracker.first(BannerRenderFailure.MISSING_BASE_TEXTURE, "missing-" + index, 1, 1);
        }
        assertEquals(BannerMissingLogTracker.MAX_DIAGNOSTICS, tracker.size());
    }
}
