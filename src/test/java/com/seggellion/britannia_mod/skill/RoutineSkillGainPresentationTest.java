package com.seggellion.britannia_mod.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Routine skill gains must stay authoritative but silent.
 *
 * <p>Both gain paths used to mutate the value and then immediately chat the player a
 * "Your skill in X has increased by 0.1%" line, once per increment. Progression now belongs to the
 * Skills GUI, so the notification is gone while the mutation, the cap, the client sync and the
 * Rails persistence are untouched.
 *
 * <p>The mutation itself is exercised through {@link SkillManager#nextCappedGainValue}; the
 * presentation is asserted against the source because the surrounding methods need a live
 * {@code ServerPlayer} and an HTTP executor that no unit harness here can stand up.
 */
class RoutineSkillGainPresentationTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    private static String skillManagerSource() throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/skill/SkillManager.java"));
    }

    @Test
    void noGainPathChatsThePlayerAboutARoutineIncrement() throws Exception {
        String source = skillManagerSource();
        assertFalse(source.contains("has increased by"),
                "routine per-gain notification text is back in SkillManager");
        assertFalse(source.contains("sendSystemMessage"),
                "SkillManager must not push per-gain chat at the player");
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
            assertFalse(body.contains("sendSystemMessage"),
                    method + " must not chat the player about a routine increment");
        }
    }

    /**
     * Source of one method, from its declaration to the start of the next top-level member. Crude,
     * but enough to keep these assertions scoped to the gain paths rather than to unrelated
     * methods that legitimately sync the client.
     */
    private static String methodBody(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertTrue(start >= 0, "method not found: " + methodName);
        int end = source.indexOf("\n}\n", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    @Test
    void capAndProgressionRulesAreUnchanged() {
        // Definition cap wins.
        assertEquals(80.0F, SkillManager.nextCappedGainValue(79.95F, 80.0F, 100.0F));
        // Activity cap wins.
        assertEquals(25.0F, SkillManager.nextCappedGainValue(24.95F, 100.0F, 25.0F));
        // Ordinary step is still the 0.1 gain unit.
        assertEquals(10.1F, SkillManager.nextCappedGainValue(10.0F, 100.0F, 25.0F));
    }

    @Test
    void semanticallyDifferentFeedbackIsNotCollateralDamage() throws Exception {
        // Guildmaster training, admin set and confirmed-value paths must still exist and must
        // still sync; suppressing routine gain spam must not have removed them.
        String source = skillManagerSource();
        assertTrue(source.contains("public static void setSkillAdmin("), "admin set path removed");
        assertTrue(source.contains("public static void applyConfirmedValue("),
                "confirmed-value path removed");

        // The cultivation gate owns insufficient-skill and unavailable-data feedback; it must not
        // have been silenced along with the routine gain notification.
        String gate = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FarmingCultivationGate.java"));
        assertTrue(gate.contains("sendDenialFeedback"), "planting denial feedback removed");
        assertTrue(gate.contains("message.britannia_mod.farming.cultivation.skill_unavailable"),
                "skill-data-unavailable feedback removed");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
