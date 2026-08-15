package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Milestone 9: feedback is legible and throttled, and authority stays on the server.
 */
class MiningFeedbackPolicyTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    private static String code(String relative) throws Exception {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative))
                .replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static final String KEY = "INSUFFICIENT_SKILL|silver|20.0|55.0";
    private static final String OTHER = "INSUFFICIENT_SKILL|valorite|20.0|99.0";

    @Test
    void aRepeatedDenialIsQuietUntilTheCooldownElapses() {
        assertTrue(MiningBreakGate.shouldSendDenial("", 0L, KEY, 1_000L),
                "the first denial must always be shown");
        assertFalse(MiningBreakGate.shouldSendDenial(KEY, 1_000L, KEY, 1_001L),
                "a held click must not rewrite the same line every dig");
        assertFalse(MiningBreakGate.shouldSendDenial(KEY, 1_000L, KEY,
                        1_000L + MiningBreakGate.DENIAL_FEEDBACK_COOLDOWN_TICKS - 1),
                "still inside the cooldown");
        assertTrue(MiningBreakGate.shouldSendDenial(KEY, 1_000L, KEY,
                        1_000L + MiningBreakGate.DENIAL_FEEDBACK_COOLDOWN_TICKS),
                "the reminder returns once the cooldown elapses");
    }

    /** Looking at a different resource must answer immediately, not after the cooldown. */
    @Test
    void aDifferentDenialIsAlwaysShownImmediately() {
        assertTrue(MiningBreakGate.shouldSendDenial(KEY, 1_000L, OTHER, 1_001L),
                "a different requirement is new information and must not be swallowed");
    }

    /** A rewound clock must not mute feedback forever. */
    @Test
    void aRewoundClockDoesNotSilenceFeedback() {
        assertTrue(MiningBreakGate.shouldSendDenial(KEY, 9_000L, KEY, 5L));
    }

    /** The message must carry both numbers and the material, per design §2.4. */
    @Test
    void theInsufficientMessageNamesTheMaterialAndBothValues() throws Exception {
        String lang = Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"));
        String line = lang.lines()
                .filter(text -> text.contains("message.britannia_mod.mining.insufficient"))
                .findFirst().orElseThrow();
        for (String placeholder : List.of("%1$s", "%2$s", "%3$s")) {
            assertTrue(line.contains(placeholder),
                    "the denial must state current, required and material: missing " + placeholder);
        }
    }

    /** Feedback must stay on the action bar rather than becoming chat spam. */
    @Test
    void denialFeedbackUsesTheActionBar() throws Exception {
        String gate = code("mining/MiningBreakGate.java");
        assertTrue(gate.contains("displayClientMessage") && gate.contains(", true)"),
                "denials belong on the action bar, as Farming's gate does");
        assertFalse(gate.contains("sendSystemMessage"),
                "a denial must never be written to chat");
    }

    /**
     * Eligibility is recomputed from authoritative server state on every attempt. If it were ever
     * cached onto an ItemStack, a client-visible component would become a skill oracle and a
     * threshold crossing would not take effect until the stack changed.
     */
    @Test
    void eligibilityIsNeverStoredOnAnItemStack() throws Exception {
        for (String source : List.of("mining/MiningBreakGate.java", "mining/Mineables.java",
                "mining/MiningSkill.java", "mining/MiningProvenance.java")) {
            String body = code(source);
            for (String forbidden : List.of("DataComponents", "CustomData", "setTag", "getOrCreateTag")) {
                assertFalse(body.contains(forbidden),
                        source + " must not persist Mining eligibility on items (found " + forbidden + ")");
            }
        }
    }

    /** The gate reads the live skill each time, so a gain takes effect on the very next attempt. */
    @Test
    void theGateReadsLiveSkillStateOnEveryEvaluation() throws Exception {
        String gate = code("mining/MiningBreakGate.java");
        assertTrue(gate.contains("SkillManager.getSkillSnapshot"),
                "the gate must read the authoritative snapshot, not a cached value");
        assertFalse(gate.contains("static float") && gate.contains("cachedSkill"),
                "no cached skill value may exist");
    }

    /** Operator diagnostics must observe without changing anything. */
    @Test
    void theAdminCommandIsReadOnlyAndOperatorGated() throws Exception {
        String command = code("commands/MiningDebugCommand.java");
        assertTrue(command.contains("hasPermission(2)"), "diagnostics are operator-only");
        for (String mutator : List.of("setSkillAdmin", "awardSkillGain", "markPlayerPlaced",
                "forget(", "setBlock", "recordBrokenBlock", "removeBlock")) {
            assertFalse(command.contains(mutator),
                    "the diagnostic command must not mutate state (found " + mutator + ")");
        }
        for (String question : List.of("Mineables.resolve", "MiningBreakGate.evaluate",
                "getSkillSnapshot", "getBrokenBlocks", "isPlayerPlaced")) {
            assertTrue(command.contains(question),
                    "diagnostics must be able to answer: " + question);
        }
    }
}
