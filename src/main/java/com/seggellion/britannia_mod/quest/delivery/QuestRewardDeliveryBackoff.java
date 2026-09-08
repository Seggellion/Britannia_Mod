package com.seggellion.britannia_mod.quest.delivery;

/**
 * The reconciliation schedule of protocol section 1.8, step 6: a failing acknowledgement is
 * retried every 60 seconds while pending, doubling per failed attempt and capped at 10 minutes
 * between attempts. Login and a journal refresh attempt immediately regardless of the schedule.
 * A queued delivery's insertion is retried on inventory change and every 30 seconds; it never
 * calls Rails, so it needs no backoff.
 */
public final class QuestRewardDeliveryBackoff {
    public static final long BASE_DELAY_MILLIS = 60_000L;
    public static final long MAX_DELAY_MILLIS = 600_000L;
    public static final long QUEUED_RETRY_MILLIS = 30_000L;

    private QuestRewardDeliveryBackoff() {}

    /** The delay before the next acknowledgement attempt after {@code failedAttempts} failures. */
    public static long delayMillis(int failedAttempts) {
        int attempts = Math.max(1, failedAttempts);
        long delay = BASE_DELAY_MILLIS;
        for (int i = 1; i < attempts && delay < MAX_DELAY_MILLIS; i++) delay *= 2L;
        return Math.min(delay, MAX_DELAY_MILLIS);
    }
}
