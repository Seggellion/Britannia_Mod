package com.seggellion.britannia_mod.service.spawn;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class ServiceNpcSpawnRetryPolicy {
    public static final long BASE_DELAY_MILLIS = 5_000L;
    public static final long MAX_DELAY_MILLIS = 300_000L;
    public static final long SLOW_RETRY_MILLIS = MAX_DELAY_MILLIS;
    private ServiceNpcSpawnRetryPolicy() {}

    public static long backoffMillis(UUID operationId, int attemptCount) {
        Objects.requireNonNull(operationId, "operationId");
        int attempt = Math.max(1, attemptCount);
        long unjittered = BASE_DELAY_MILLIS;
        for (int index = 1; index < attempt && unjittered < MAX_DELAY_MILLIS; index++) {
            unjittered = Math.min(MAX_DELAY_MILLIS, unjittered * 2L);
        }
        long mixed = mix(operationId.getMostSignificantBits()
            ^ Long.rotateLeft(operationId.getLeastSignificantBits(), 17)
            ^ (0x9E3779B97F4A7C15L * attempt));
        int basisPoints = 8_000 + (int) Long.remainderUnsigned(mixed, 4_001L);
        return Math.min(MAX_DELAY_MILLIS, unjittered * basisPoints / 10_000L);
    }

    public static long effectiveDelayMillis(UUID operationId, int attemptCount, Duration retryAfter) {
        long calculated = backoffMillis(operationId, attemptCount);
        if (retryAfter == null || retryAfter.isNegative()) return calculated;
        long hint;
        try {
            hint = retryAfter.toMillis();
        } catch (ArithmeticException overflow) {
            hint = MAX_DELAY_MILLIS;
        }
        return Math.min(MAX_DELAY_MILLIS, Math.max(calculated, hint));
    }

    public static long slowRetryMillis() {
        return SLOW_RETRY_MILLIS;
    }

    public static long saturatingAdd(long epochMillis, long delayMillis) {
        if (epochMillis < 0 || delayMillis < 0) throw new IllegalArgumentException("negative time");
        if (Long.MAX_VALUE - epochMillis < delayMillis) return Long.MAX_VALUE;
        return epochMillis + delayMillis;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
