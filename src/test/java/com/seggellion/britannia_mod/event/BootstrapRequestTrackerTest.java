package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapRequestTrackerTest {
    @Test
    void newerLoginCancelsTheSupersededRequestWithoutRemovingTheNewRequest() {
        BootstrapRequestTracker tracker = new BootstrapRequestTracker();
        UUID player = UUID.randomUUID();
        WorldBootstrapAPI.RequestHandle first = tracker.register(player);
        WorldBootstrapAPI.RequestHandle second = tracker.register(player);

        assertTrue(first.isCancelled());
        assertFalse(second.isCancelled());

        tracker.complete(player, first);
        tracker.cancel(player);
        assertTrue(second.isCancelled());
    }

    @Test
    void logoutAndServerShutdownCancelOutstandingRequests() {
        BootstrapRequestTracker tracker = new BootstrapRequestTracker();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        WorldBootstrapAPI.RequestHandle logout = tracker.register(firstPlayer);
        WorldBootstrapAPI.RequestHandle shutdown = tracker.register(secondPlayer);

        tracker.cancel(firstPlayer);
        assertTrue(logout.isCancelled());
        assertFalse(shutdown.isCancelled());

        tracker.close();
        assertTrue(shutdown.isCancelled());
    }
}
