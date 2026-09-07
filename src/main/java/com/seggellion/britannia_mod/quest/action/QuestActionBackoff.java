package com.seggellion.britannia_mod.quest.action;

/**
 * The outbox retry schedule of protocol section 2.2, verbatim: attempt immediately, then 10 s,
 * 30 s, 60 s, 2 min, 5 min, then every 5 min. Login flushes a player's entries and server start
 * schedules the whole outbox regardless of where an entry sits on this schedule.
 */
public final class QuestActionBackoff {
    /** Delay before attempt N+1, indexed by the number of attempts that have already failed. */
    private static final long[] SCHEDULE = {
        10_000L,   // after 1 failure
        30_000L,   // after 2
        60_000L,   // after 3
        120_000L,  // after 4
        300_000L   // after 5
    };

    public static final long MAX_DELAY_MILLIS = 300_000L;

    private QuestActionBackoff() {}

    /** The delay before the next attempt after {@code failedAttempts} failures. */
    public static long delayMillis(int failedAttempts) {
        if (failedAttempts <= 0) return 0L;
        int index = Math.min(failedAttempts, SCHEDULE.length) - 1;
        return SCHEDULE[index];
    }
}
