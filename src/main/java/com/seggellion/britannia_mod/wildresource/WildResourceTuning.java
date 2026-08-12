package com.seggellion.britannia_mod.wildresource;

import net.minecraft.util.RandomSource;

/** Centralized cadence, retry, probe, and spacing policy for one wild resource entry. */
public record WildResourceTuning(
        int attemptIntervalMinTicks,
        int attemptIntervalMaxTicks,
        int respawnCooldownMinTicks,
        int respawnCooldownMaxTicks,
        int maxRandomProbes,
        int minimumSameTypeSpacing
) {
    public WildResourceTuning {
        requireRange("attempt interval", attemptIntervalMinTicks, attemptIntervalMaxTicks);
        requireRange("respawn cooldown", respawnCooldownMinTicks, respawnCooldownMaxTicks);
        if (maxRandomProbes <= 0) {
            throw new IllegalArgumentException("maxRandomProbes must be positive");
        }
        if (minimumSameTypeSpacing < 0) {
            throw new IllegalArgumentException("minimumSameTypeSpacing cannot be negative");
        }
    }

    public int nextAttemptDelay(RandomSource random) {
        return between(random, attemptIntervalMinTicks, attemptIntervalMaxTicks);
    }

    public int nextRespawnDelay(RandomSource random) {
        return between(random, respawnCooldownMinTicks, respawnCooldownMaxTicks);
    }

    private static int between(RandomSource random, int minimum, int maximum) {
        return minimum == maximum ? minimum : random.nextIntBetweenInclusive(minimum, maximum);
    }

    private static void requireRange(String name, int minimum, int maximum) {
        if (minimum <= 0 || maximum < minimum) {
            throw new IllegalArgumentException(name + " must be a positive ordered range");
        }
    }
}
