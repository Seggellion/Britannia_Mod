package com.seggellion.britannia_mod.dirtgathering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtGatheringCooldownTest {
    @Test
    void shippedCooldownIsExactlySixtySeconds() {
        assertEquals(1_200, DirtGatheringCooldown.COOLDOWN_TICKS);
        assertEquals(2_200L, DirtGatheringCooldown.nextAllowedTick(1_000L));
        assertEquals("britannia_mod:dirt_gather_next_tick",
                DirtGatheringCooldown.NEXT_ALLOWED_TICK_TAG);
    }

    @Test
    void activeCooldownReportsCeilingSecondsAndExpiresAtTheBoundary() {
        DirtGatheringCooldown.Status full = DirtGatheringCooldown.inspect(1_000L, 2_200L);
        assertFalse(full.ready());
        assertEquals(1_200L, full.remainingTicks());
        assertEquals(60L, full.remainingSeconds());

        DirtGatheringCooldown.Status partial = DirtGatheringCooldown.inspect(1_000L, 1_021L);
        assertEquals(2L, partial.remainingSeconds());
        assertTrue(DirtGatheringCooldown.inspect(1_000L, 1_000L).ready());
    }

    @Test
    void impossibleFutureValueFailsOpenAfterAClockRewind() {
        DirtGatheringCooldown.Status maximumValid = DirtGatheringCooldown.inspect(100L, 1_300L);
        assertFalse(maximumValid.ready());
        assertFalse(maximumValid.rewound());

        DirtGatheringCooldown.Status rewound = DirtGatheringCooldown.inspect(100L, 1_301L);
        assertTrue(rewound.ready());
        assertTrue(rewound.rewound());
        assertEquals(0L, rewound.remainingTicks());
    }

    @Test
    void repeatedCooldownFeedbackIsLimitedToOnceEveryTwoSeconds() {
        assertFalse(DirtGatheringCooldown.shouldSendFeedback(1_039L, 1_000L));
        assertTrue(DirtGatheringCooldown.shouldSendFeedback(1_040L, 1_000L));
        assertTrue(DirtGatheringCooldown.shouldSendFeedback(10L, 1_000L),
                "a rewound clock must not mute feedback indefinitely");
    }
}
