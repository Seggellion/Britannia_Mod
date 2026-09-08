package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M9 items 1-3: the two public-plot windows agree with Rails, expire on a
 * clock rather than on a dice roll, and count down out loud on the way.
 *
 * <p>The determinism this file proves is the arithmetic half: {@link CommunityPlotWindow} is a pure
 * function of two game times and says nothing about how often the world happens to tick. The other
 * half -- that the reclaim really runs at {@code randomTickSpeed} 0 -- is proved in
 * {@code CommunityPlotWindowGameTests}, which needs a world.
 */
class CommunityPlotWindowTest {

    // ------------------------------------------------------------------ item 2: the numbers

    @Test
    void bothWindowsCarryTheSecondsRailsEnforcesOnTheSameTwoSteps() {
        // QuestContent::RowanFarmingQuestline::HOE_TO_FERTILIZE_SECONDS / FERTILIZE_TO_PLANT_SECONDS.
        assertEquals(600L, CommunityPlotWindow.HOE_TO_FERTILIZE_SECONDS);
        assertEquals(300L, CommunityPlotWindow.FERTILIZE_TO_PLANT_SECONDS);
    }

    @Test
    void theTickConstantsAreTheSecondsAndNothingElse() {
        assertEquals(CommunityPlotWindow.HOE_TO_FERTILIZE_SECONDS * 20L,
                CommunityPlotWindow.HOE_TO_FERTILIZE_TICKS);
        assertEquals(CommunityPlotWindow.FERTILIZE_TO_PLANT_SECONDS * 20L,
                CommunityPlotWindow.FERTILIZE_TO_PLANT_TICKS);
    }

    @Test
    void theBlockEntitiesUseTheSharedWindowRatherThanNumbersOfTheirOwn() {
        assertEquals(CommunityPlotWindow.HOE_TO_FERTILIZE_TICKS,
                CommunityFarmBlockEntity.PREPARED_EXPIRY_TICKS);
        assertEquals(CommunityPlotWindow.FERTILIZE_TO_PLANT_TICKS,
                FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS);
    }

    // ------------------------------------------------------------------ item 1: expiry

    @Test
    void aWindowExpiresExactlyOnItsDeadlineAndNotBefore() {
        long deadline = 12_000L;
        assertFalse(CommunityPlotWindow.expired(deadline - 1L, deadline));
        assertTrue(CommunityPlotWindow.expired(deadline, deadline));
        assertTrue(CommunityPlotWindow.expired(deadline + 5_000L, deadline));
    }

    @Test
    void aDeadlineOfZeroIsNoWindowAtAllRatherThanAWindowInThePast() {
        // Every plot with no window open stores 0, including every private plot, so this is the
        // case that decides whether ordinary farming is touched by any of this. It is not.
        assertFalse(CommunityPlotWindow.expired(0L, 0L));
        assertFalse(CommunityPlotWindow.expired(9_999_999L, 0L));
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.nextEventTick(50L, 0L));
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.warningAt(50L, 0L));
        assertEquals(0L, CommunityPlotWindow.secondsRemaining(50L, 0L));
    }

    @Test
    void secondsRemainingRoundsUpSoAPlayerIsNeverToldZeroWhileTheWindowIsOpen() {
        long deadline = 1_000L;
        assertEquals(50L, CommunityPlotWindow.secondsRemaining(0L, deadline));
        assertEquals(1L, CommunityPlotWindow.secondsRemaining(deadline - 20L, deadline));
        assertEquals(1L, CommunityPlotWindow.secondsRemaining(deadline - 1L, deadline));
        assertEquals(0L, CommunityPlotWindow.secondsRemaining(deadline, deadline));
    }

    // ------------------------------------------------------------------ item 3: the countdown

    @Test
    void theCountdownMarksAreSixtyThirtyAndTenSeconds() {
        assertEquals(3, CommunityPlotWindow.WARNING_SECONDS.length);
        assertEquals(60L, CommunityPlotWindow.WARNING_SECONDS[0]);
        assertEquals(30L, CommunityPlotWindow.WARNING_SECONDS[1]);
        assertEquals(10L, CommunityPlotWindow.WARNING_SECONDS[2]);
    }

    @Test
    void aWarningBelongsOnlyToTheTickThatIsExactlyThatManySecondsOut() {
        long deadline = 12_000L;
        assertEquals(OptionalLong.of(60L), CommunityPlotWindow.warningAt(deadline - 1_200L, deadline));
        assertEquals(OptionalLong.of(30L), CommunityPlotWindow.warningAt(deadline - 600L, deadline));
        assertEquals(OptionalLong.of(10L), CommunityPlotWindow.warningAt(deadline - 200L, deadline));
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.warningAt(deadline - 201L, deadline));
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.warningAt(deadline - 199L, deadline));
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.warningAt(deadline, deadline));
    }

    @Test
    void theScheduleWalksTheMarksInOrderAndFinishesOnTheDeadline() {
        long deadline = CommunityPlotWindow.HOE_TO_FERTILIZE_TICKS;
        long now = 0L;
        long[] expected = { deadline - 1_200L, deadline - 600L, deadline - 200L, deadline };
        for (long mark : expected) {
            OptionalLong next = CommunityPlotWindow.nextEventTick(now, deadline);
            assertTrue(next.isPresent(), "the window stopped scheduling before its deadline");
            assertEquals(mark, next.getAsLong());
            now = next.getAsLong();
        }
        assertEquals(OptionalLong.empty(), CommunityPlotWindow.nextEventTick(now, deadline),
                "the deadline itself must schedule nothing further");
    }

    @Test
    void aWindowShorterThanItsMarksStillSchedulesTheMarksItCanReach() {
        // Nothing opens a window this short today, but a plot loaded from a save written before
        // M9 carries an old, shorter deadline, and it must still count down rather than skip
        // straight to being reclaimed.
        long deadline = 500L; // 25 s: the 60 s and 30 s marks are already in the past
        assertEquals(OptionalLong.of(300L), CommunityPlotWindow.nextEventTick(0L, deadline));
        assertEquals(OptionalLong.of(10L), CommunityPlotWindow.warningAt(300L, deadline));
        assertEquals(OptionalLong.of(deadline), CommunityPlotWindow.nextEventTick(300L, deadline));
    }

    @Test
    void theScheduleDelayIsNeverZeroSoARescheduleCannotSpin() {
        assertEquals(1, CommunityPlotWindow.scheduleDelay(100L, 100L));
        assertEquals(1, CommunityPlotWindow.scheduleDelay(100L, 50L));
        assertEquals(40, CommunityPlotWindow.scheduleDelay(100L, 140L));
    }
}
