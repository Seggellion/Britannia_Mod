package com.seggellion.britannia_mod.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Routine skill gains stay authoritative, and speak at most once per 0.2.
 *
 * <p>Both gain paths used to chat the player on every increment. Since the engine's gain unit is
 * 0.1, that was a message - two, counting the blank spacer line - on every successful roll. The
 * notification is now throttled rather than removed: gain accrues against the value at the last
 * announcement and only speaks once the unreported total reaches 0.2, reporting everything accrued
 * since rather than just the final step.
 *
 * <p>The threshold rule is exercised directly through {@link SkillManager#shouldAnnounceRoutineGain};
 * the surrounding bookkeeping is asserted against the source because it needs a live
 * {@code ServerPlayer} and a network channel that no unit harness here can stand up.
 */
class RoutineSkillGainPresentationTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    private static String skillManagerSource() throws Exception {
        // The working tree's line endings are a checkout artifact (core.autocrlf), not part of
        // the shape this test pins. Without normalization, methodBody()'s "\n}\n" terminator
        // never matches a CRLF-materialized file, so every "body" silently extends to
        // end-of-file and absence assertions trip on code from unrelated methods.
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/skill/SkillManager.java"))
                .replace("\r\n", "\n");
    }

    @Test
    void aSingleGainStepStaysQuiet() {
        // One 0.1 step must not speak; that was the spam.
        assertFalse(SkillManager.shouldAnnounceRoutineGain(10.0f, 10.1f));
        assertFalse(SkillManager.shouldAnnounceRoutineGain(0.0f, 0.1f));
        assertFalse(SkillManager.shouldAnnounceRoutineGain(99.8f, 99.9f));
    }

    @Test
    void twoGainStepsSpeak() {
        assertTrue(SkillManager.shouldAnnounceRoutineGain(10.0f, 10.2f));
        assertTrue(SkillManager.shouldAnnounceRoutineGain(0.0f, 0.2f));
    }

    @Test
    void repeatedFloatAdditionStillClearsTheThreshold() {
        // The value arrives as current + 0.1f applied repeatedly, so it drifts off exact decimals.
        // Without the epsilon the second step can land a hair under 0.2 and stay silent forever.
        float value = 0.0f;
        for (int step = 0; step < 200; step++) {
            float before = value;
            value += 0.1f;
            float next = value + 0.1f;
            assertFalse(SkillManager.shouldAnnounceRoutineGain(before, value),
                    "one step spoke at " + before);
            assertTrue(SkillManager.shouldAnnounceRoutineGain(before, next),
                    "two steps stayed silent at " + before);
            value = next;
        }
    }

    @Test
    void aLargeSingleAwardSpeaksImmediately() {
        // awardSkillGain takes arbitrary amounts; a 1.0 award should not wait for a second one.
        assertTrue(SkillManager.shouldAnnounceRoutineGain(10.0f, 11.0f));
        assertTrue(SkillManager.shouldAnnounceRoutineGain(0.0f, 40.0f));
    }

    @Test
    void everyOtherStepSpeaksOverALongGrind() {
        // Ten 0.1 steps against a carried baseline should yield five messages, not ten and not one.
        float value = 0.0f;
        float baseline = 0.0f;
        int announcements = 0;
        for (int step = 0; step < 10; step++) {
            value += 0.1f;
            if (SkillManager.shouldAnnounceRoutineGain(baseline, value)) {
                announcements++;
                baseline = value;
            }
        }
        assertEquals(5, announcements);
        // The remainder is carried, not dropped: the last announcement caught the full 1.0.
        assertEquals(value, baseline, 1.0e-4f);
    }

    @Test
    void theThresholdIsTheAgreedOne() throws Exception {
        assertEquals(0.2f, SkillManager.ANNOUNCE_THRESHOLD);
    }

    @Test
    void gainsStillMutatePersistAndSync() throws Exception {
        String source = skillManagerSource();

        for (String method : new String[] {"trySkillGainCapped", "awardSkillGain"}) {
            String body = methodBody(source, method);
            assertTrue(body.contains("p.set(key, newValue)"),
                    method + " must still write the authoritative value");
            assertTrue(body.contains("sendSkillSync(player)"),
                    method + " must still sync the client so the Skills GUI stays accurate");
            assertTrue(body.contains("postGain(player, key, newValue)"),
                    method + " must still persist the gain to Rails");
            assertTrue(body.contains("announceRoutineGain(player, key, current, newValue)"),
                    method + " must report through the throttle");
            assertFalse(body.contains("sendSystemMessage"),
                    method + " must not chat the player directly, bypassing the throttle");
        }
    }

    @Test
    void onlyTheThrottleTalksAboutRoutineGains() throws Exception {
        String source = skillManagerSource();
        assertEquals(1, countOccurrences(source, "has increased by"),
                "the gain message must exist in exactly one place, inside the throttle");
    }

    @Test
    void nonRoutineWritesRebaseTheThrottle() throws Exception {
        // An admin set or a confirmed training purchase jumps the value. Without rebasing, the
        // next ordinary 0.1 step would report the whole jump as though the player had earned it.
        String source = skillManagerSource();
        for (String method : new String[] {"setSkillAdmin", "applyConfirmedValue"}) {
            assertTrue(methodBody(source, method).contains("resetAnnouncedValue(player, key, value)"),
                    method + " must rebase the announcement baseline");
        }
    }

    @Test
    void theAccumulatorIsClearedWithTheRestOfThePlayerState() throws Exception {
        String source = skillManagerSource();
        assertTrue(methodBody(source, "onPlayerLogOut").contains("ANNOUNCED_SKILL_VALUES.remove"),
                "the accumulator would leak per disconnected player");
        assertTrue(methodBody(source, "onPlayerLogin").contains("ANNOUNCED_SKILL_VALUES.remove"),
                "a reconnect must not inherit the previous session's pending fraction");
    }

    @Test
    void capAndProgressionRulesAreUnchanged() {
        assertEquals(80.0F, SkillManager.nextCappedGainValue(79.95F, 80.0F, 100.0F));
        assertEquals(25.0F, SkillManager.nextCappedGainValue(24.95F, 100.0F, 25.0F));
        assertEquals(10.1F, SkillManager.nextCappedGainValue(10.0F, 100.0F, 25.0F));
    }

    @Test
    void semanticallyDifferentFeedbackIsNotCollateralDamage() throws Exception {
        String source = skillManagerSource();
        assertTrue(source.contains("public static void setSkillAdmin("), "admin set path removed");
        assertTrue(source.contains("public static void applyConfirmedValue("),
                "confirmed-value path removed");

        String gate = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FarmingCultivationGate.java"));
        assertTrue(gate.contains("sendDenialFeedback"), "planting denial feedback removed");
        assertTrue(gate.contains("message.britannia_mod.farming.cultivation.skill_unavailable"),
                "skill-data-unavailable feedback removed");
    }

    /**
     * Source of one method, from its declaration to the start of the next top-level member. Crude,
     * but enough to keep these assertions scoped to the path under test.
     */
    private static String methodBody(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertTrue(start >= 0, "method not found: " + methodName);
        int end = source.indexOf("\n}\n", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
