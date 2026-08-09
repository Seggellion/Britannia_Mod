package com.seggellion.britannia_mod.service.spawn;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ServiceNpcSpawnRetryPolicyTest {
    private static final UUID ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    void attemptsFollowExponentialBoundsWithDeterministicJitter() {
        long first = ServiceNpcSpawnRetryPolicy.backoffMillis(ID, 1);
        long second = ServiceNpcSpawnRetryPolicy.backoffMillis(ID, 2);
        long third = ServiceNpcSpawnRetryPolicy.backoffMillis(ID, 3);
        assertTrue(first >= 4_000 && first <= 6_000);
        assertTrue(second >= 8_000 && second <= 12_000);
        assertTrue(third >= 16_000 && third <= 24_000);
        assertEquals(first, ServiceNpcSpawnRetryPolicy.backoffMillis(ID, 1));
        assertNotEquals(first, second);
    }

    @Test
    void delayNeverExceedsFiveMinutesEvenAtOverflowScale() {
        for (int attempt : new int[] {0, 1, 10, 100, Integer.MAX_VALUE}) {
            long delay = ServiceNpcSpawnRetryPolicy.backoffMillis(ID, attempt);
            assertTrue(delay >= 0);
            assertTrue(delay <= 300_000);
        }
    }

    @Test
    void retryAfterUsesMaximumAndRetainsFinalCap() {
        long calculated = ServiceNpcSpawnRetryPolicy.backoffMillis(ID, 2);
        assertEquals(calculated, ServiceNpcSpawnRetryPolicy.effectiveDelayMillis(
            ID, 2, Duration.ofMillis(1)
        ));
        assertEquals(60_000, ServiceNpcSpawnRetryPolicy.effectiveDelayMillis(
            ID, 2, Duration.ofMinutes(1)
        ));
        assertEquals(300_000, ServiceNpcSpawnRetryPolicy.effectiveDelayMillis(
            ID, 2, Duration.ofHours(1)
        ));
        assertEquals(300_000, ServiceNpcSpawnRetryPolicy.slowRetryMillis());
    }

    @Test
    void clockAdditionSaturatesWithoutWrapping() {
        assertEquals(1_500, ServiceNpcSpawnRetryPolicy.saturatingAdd(1_000, 500));
        assertEquals(Long.MAX_VALUE, ServiceNpcSpawnRetryPolicy.saturatingAdd(Long.MAX_VALUE - 2, 5));
        assertThrows(IllegalArgumentException.class,
            () -> ServiceNpcSpawnRetryPolicy.saturatingAdd(-1, 5));
    }
}
