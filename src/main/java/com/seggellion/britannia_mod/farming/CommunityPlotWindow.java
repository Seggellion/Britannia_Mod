package com.seggellion.britannia_mod.farming;

import java.util.OptionalLong;

/**
 * The two public-plot preparation windows, in one place, in the same units Rails uses.
 *
 * <p>Rowan farming questline M9 items 1-3. A public plot expires twice on the way to a crop: a
 * hoed plot goes back to grass if nobody fertilizes it, and a fertilized plot goes back to grass
 * if nobody plants in it. Both windows existed before this milestone and both were enforced only
 * by the game; Rails independently enforces the same two deadlines on the stage-5 action steps
 * ({@code action_steps[].deadline {after, seconds}}, authored at 600 s and 300 s). The two were
 * different numbers, so a plot could survive in the world long after Rails had already reset the
 * player's progress, or -- far worse -- vanish while Rails still believed the step was open.
 *
 * <p>This class is the game side of that agreement. The numbers here are the numbers Rails
 * authors, expressed in seconds first and converted to ticks second, so the comparison is with
 * the same quantity Rails compares and a reader can check the two files against each other
 * without arithmetic.
 *
 * <p><b>Scope.</b> Both windows govern public (community) plots only. A private plot and a house
 * farm plot have no preparation window at all -- {@code FarmingBlockEntity.shouldReclaimCommunityPlot}
 * requires {@code communityPlot}, and {@code CommunityFarmBlockEntity} exists only under a public
 * plot -- so widening these windows loosens public farming and leaves private farming untouched.
 * There is no separate "questline window": a public plot is the same object whether or not the
 * player is on Rowan's questline, and giving the questline its own timer would mean a plot that
 * expired at two different moments depending on who was looking at it.
 */
public final class CommunityPlotWindow {

    /**
     * Hoed public plot -> grass. Mirrors {@code RowanFarmingQuestline::HOE_TO_FERTILIZE_SECONDS}.
     *
     * <p>Was 180 s (3600 ticks). The player has to find or mix fertilized dirt in this window, and
     * mixing it is an entire other quest stage; three minutes was not enough time to do the thing
     * the window is for.
     */
    public static final long HOE_TO_FERTILIZE_SECONDS = 600L;

    /**
     * Fertilized public plot with nothing growing in it -> grass. Mirrors
     * {@code RowanFarmingQuestline::FERTILIZE_TO_PLANT_SECONDS}.
     *
     * <p>Was 60 s (1200 ticks), and only ever evaluated on a random tick, so the number in the
     * source and the number the player experienced were not related to each other.
     */
    public static final long FERTILIZE_TO_PLANT_SECONDS = 300L;

    public static final long HOE_TO_FERTILIZE_TICKS = HOE_TO_FERTILIZE_SECONDS * 20L;
    public static final long FERTILIZE_TO_PLANT_TICKS = FERTILIZE_TO_PLANT_SECONDS * 20L;

    /**
     * Seconds-remaining marks at which the player is warned, descending.
     *
     * <p>A window that only speaks when it opens and when it closes is a trap: the plot the player
     * walked away from was fine when they left. These are the marks the project's planning inputs
     * name (60/30/10 s).
     */
    public static final long[] WARNING_SECONDS = { 60L, 30L, 10L };

    private CommunityPlotWindow() {
    }

    /** True once {@code now} has reached the deadline. A deadline of 0 means "no window open". */
    public static boolean expired(long now, long deadlineGameTime) {
        return deadlineGameTime > 0L && now >= deadlineGameTime;
    }

    /** Whole seconds left before the deadline, rounded up, or 0 once it has passed. */
    public static long secondsRemaining(long now, long deadlineGameTime) {
        if (deadlineGameTime <= 0L || now >= deadlineGameTime) {
            return 0L;
        }
        long ticks = deadlineGameTime - now;
        return (ticks + 19L) / 20L;
    }

    /**
     * The warning that belongs to this exact moment, if any.
     *
     * <p>A warning fires on the tick the window has exactly that many seconds left, which is the
     * tick {@link #nextEventTick} schedules. Being a pure function of the two times, it says the
     * same thing however it is reached -- a scheduled tick, a block-entity tick, or a test.
     */
    public static OptionalLong warningAt(long now, long deadlineGameTime) {
        if (deadlineGameTime <= 0L || now >= deadlineGameTime) {
            return OptionalLong.empty();
        }
        long ticksLeft = deadlineGameTime - now;
        for (long seconds : WARNING_SECONDS) {
            if (ticksLeft == seconds * 20L) {
                return OptionalLong.of(seconds);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * The next game time at which this window has something to do: the next warning mark it has
     * not yet passed, or the deadline itself. Empty once the deadline has been reached.
     *
     * <p>Callers schedule exactly one tick at a time from this, and re-schedule from inside that
     * tick. One pending tick per plot, no polling, and the chunk's own scheduled-tick storage
     * carries it across an unload -- which is the whole point of not using a random tick.
     */
    public static OptionalLong nextEventTick(long now, long deadlineGameTime) {
        if (deadlineGameTime <= 0L || now >= deadlineGameTime) {
            return OptionalLong.empty();
        }
        long next = deadlineGameTime;
        for (long seconds : WARNING_SECONDS) {
            long mark = deadlineGameTime - seconds * 20L;
            if (mark > now && mark < next) {
                next = mark;
            }
        }
        return OptionalLong.of(next);
    }

    /**
     * The delay to hand {@code Level#scheduleTick}, clamped into int range and to at least one
     * tick so a same-tick reschedule can never spin.
     */
    public static int scheduleDelay(long now, long eventTick) {
        long delay = eventTick - now;
        if (delay < 1L) {
            return 1;
        }
        return (int) Math.min(delay, Integer.MAX_VALUE);
    }
}
