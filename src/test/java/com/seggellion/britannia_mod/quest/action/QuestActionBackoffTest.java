package com.seggellion.britannia_mod.quest.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rowan farming questline M5: the retry schedule of protocol section 2.2, literally. */
class QuestActionBackoffTest {

    @Test
    void theScheduleIsImmediateThenTenThirtySixtyTwoMinutesFiveMinutes() {
        assertEquals(0L, QuestActionBackoff.delayMillis(0), "the first attempt is immediate");
        assertEquals(10_000L, QuestActionBackoff.delayMillis(1));
        assertEquals(30_000L, QuestActionBackoff.delayMillis(2));
        assertEquals(60_000L, QuestActionBackoff.delayMillis(3));
        assertEquals(120_000L, QuestActionBackoff.delayMillis(4));
        assertEquals(300_000L, QuestActionBackoff.delayMillis(5));
    }

    @Test
    void afterTheScheduleEndsItRetriesEveryFiveMinutesForever() {
        for (int attempts = 6; attempts < 200; attempts += 17) {
            assertEquals(QuestActionBackoff.MAX_DELAY_MILLIS, QuestActionBackoff.delayMillis(attempts),
                "attempt " + attempts + " must stay on the five-minute floor, never grow past it");
        }
    }

    @Test
    void aNegativeAttemptCountIsTreatedAsNoneRatherThanIndexingBackwards() {
        assertEquals(0L, QuestActionBackoff.delayMillis(-1));
        assertEquals(0L, QuestActionBackoff.delayMillis(Integer.MIN_VALUE));
    }
}
