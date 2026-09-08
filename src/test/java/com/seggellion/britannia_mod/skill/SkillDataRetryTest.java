package com.seggellion.britannia_mod.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M9 item 4, and discovery defect D11: skill data that failed to load is
 * retried on a bounded schedule, and a Farming value of zero is never confused with no value.
 *
 * <p>The schedule itself is arithmetic and is tested here. That a recovered fetch lets the player
 * plant without relogging needs a server and a world, and is proved in
 * {@code SkillDataRecoveryGameTests}.
 */
class SkillDataRetryTest {

    // ------------------------------------------------------------------ the bounded schedule

    @Test
    void theFirstAttemptIsImmediateAndEachFailureBacksOffFurther() {
        assertEquals(0L, SkillDataBackoff.delayMillis(0));
        assertEquals(10_000L, SkillDataBackoff.delayMillis(1));
        assertEquals(30_000L, SkillDataBackoff.delayMillis(2));
        assertEquals(60_000L, SkillDataBackoff.delayMillis(3));
        assertEquals(120_000L, SkillDataBackoff.delayMillis(4));
        assertEquals(300_000L, SkillDataBackoff.delayMillis(5));
    }

    @Test
    void theBackoffStopsGrowingRatherThanRunningAway() {
        assertEquals(SkillDataBackoff.MAX_DELAY_MILLIS, SkillDataBackoff.delayMillis(6));
        assertEquals(SkillDataBackoff.MAX_DELAY_MILLIS, SkillDataBackoff.delayMillis(50));
        assertEquals(SkillDataBackoff.MAX_DELAY_MILLIS, SkillDataBackoff.delayMillis(Integer.MAX_VALUE));
    }

    @Test
    void theNumberOfAutomaticAttemptsIsBoundedToo() {
        for (int attempts = 0; attempts < SkillDataBackoff.MAX_ATTEMPTS; attempts++) {
            assertFalse(SkillDataBackoff.exhausted(attempts),
                    "attempt " + attempts + " should still be allowed");
        }
        assertTrue(SkillDataBackoff.exhausted(SkillDataBackoff.MAX_ATTEMPTS));
        assertTrue(SkillDataBackoff.exhausted(SkillDataBackoff.MAX_ATTEMPTS + 1));
    }

    @Test
    void aNegativeAttemptCountCannotProduceANegativeDelay() {
        assertEquals(0L, SkillDataBackoff.delayMillis(-1));
    }

    // ------------------------------------------- a real zero versus no value at all (item 4)

    @Test
    void anAuthoritativeZeroIsAValueAndAnUnavailableZeroIsNot() {
        SkillManager.SkillSnapshot realZero =
                new SkillManager.SkillSnapshot(SkillManager.SkillDataState.AVAILABLE, 0.0f);
        SkillManager.SkillSnapshot unknown =
                new SkillManager.SkillSnapshot(SkillManager.SkillDataState.UNAVAILABLE, 0.0f);

        // The float is identical in both. Only the state tells them apart, which is the whole
        // point of the accessor.
        assertEquals(realZero.value(), unknown.value());

        assertTrue(realZero.valueKnown());
        assertTrue(realZero.knownValue().isPresent());
        assertEquals(0.0, realZero.knownValue().getAsDouble());

        assertFalse(unknown.valueKnown());
        assertTrue(unknown.knownValue().isEmpty());
    }

    @Test
    void neitherOfTheTwoStatesBeforeAvailableCountsAsAValue() {
        assertFalse(new SkillManager.SkillSnapshot(SkillManager.SkillDataState.NOT_LOADED, 0.0f)
                .valueKnown());
        assertFalse(new SkillManager.SkillSnapshot(SkillManager.SkillDataState.LOADING, 0.0f)
                .valueKnown());
    }

    @Test
    void aRecoveredValueOfZeroIsStillAValue() {
        // Carrot requires Farming 0, so a player whose real Farming is 0 must be able to plant it.
        // The gate compares numbers only once the state is AVAILABLE; this pins the half of that
        // contract that lives on the snapshot.
        SkillManager.SkillSnapshot recovered =
                new SkillManager.SkillSnapshot(SkillManager.SkillDataState.AVAILABLE, 0.0f);
        assertTrue(recovered.valueKnown());
        assertTrue(recovered.knownValue().orElse(Double.NaN) >= 0.0d);
    }
}
