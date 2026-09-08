package com.seggellion.britannia_mod.skill;

/**
 * The bounded retry schedule for a player's skill data (Rowan farming questline M9 item 4, and
 * discovery defect D11).
 *
 * <p>Skill values are fetched once, at login. If that fetch failed the player's state was left
 * {@code UNAVAILABLE} for the whole session and nothing ever asked again, so a player who happened
 * to log in during a Rails hiccup could not plant anything until they relogged -- a failure the
 * player had no way to diagnose and no way to clear from inside the game.
 *
 * <p>The schedule is the outbox's, deliberately: 10 s, 30 s, 60 s, 2 min, 5 min. It is
 * <em>bounded</em> in both senses the milestone asks for -- the gap between attempts stops growing
 * at 5 minutes, and the number of attempts stops at {@link #MAX_ATTEMPTS}, after which the player
 * is left with a state that says "unavailable" rather than a background loop hammering an endpoint
 * that is evidently not coming back. A player action can still ask for one more attempt
 * ({@code SkillManager#requestSkillDataRetry}), which is what makes the retry visible: trying to
 * plant is exactly the moment the data is wanted.
 */
public final class SkillDataBackoff {

    /** Delay before attempt N+1, indexed by the number of attempts that have already failed. */
    private static final long[] SCHEDULE = {
        10_000L,   // after 1 failure
        30_000L,   // after 2
        60_000L,   // after 3
        120_000L,  // after 4
        300_000L   // after 5
    };

    public static final long MAX_DELAY_MILLIS = 300_000L;

    /**
     * How many automatic attempts a session makes after the login fetch failed. Six covers roughly
     * the first ten minutes; past that the outage is not a hiccup and a background retry is not the
     * right answer.
     */
    public static final int MAX_ATTEMPTS = 6;

    /**
     * The shortest gap between two player-triggered attempts, so a player leaning on the plant
     * button cannot turn a bounded retry into a flood.
     */
    public static final long MANUAL_RETRY_COOLDOWN_MILLIS = 5_000L;

    private SkillDataBackoff() {
    }

    /** The delay before the next attempt after {@code failedAttempts} failures. */
    public static long delayMillis(int failedAttempts) {
        if (failedAttempts <= 0) {
            return 0L;
        }
        int index = Math.min(failedAttempts, SCHEDULE.length) - 1;
        return SCHEDULE[index];
    }

    /** Whether another automatic attempt is still allowed after this many failures. */
    public static boolean exhausted(int failedAttempts) {
        return failedAttempts >= MAX_ATTEMPTS;
    }
}
