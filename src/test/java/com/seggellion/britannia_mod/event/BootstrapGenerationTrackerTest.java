package com.seggellion.britannia_mod.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapGenerationTrackerTest {
    @Test
    void newerLoginInvalidatesAnOlderCompletion() {
        BootstrapGenerationTracker tracker = new BootstrapGenerationTracker();
        UUID player = UUID.randomUUID();
        long first = tracker.next(player);
        long second = tracker.next(player);
        assertFalse(tracker.isCurrent(player, first));
        assertTrue(tracker.isCurrent(player, second));
    }

    @Test
    void logoutAndServerStopInvalidateOutstandingCompletions() {
        BootstrapGenerationTracker tracker = new BootstrapGenerationTracker();
        UUID player = UUID.randomUUID();
        long login = tracker.next(player);
        tracker.invalidate(player);
        assertFalse(tracker.isCurrent(player, login));

        long reconnect = tracker.next(player);
        tracker.close();
        assertFalse(tracker.isCurrent(player, reconnect));
        assertEquals(-1L, tracker.next(player));
    }
}
