package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.skill.SkillManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Milestone 3 policy contracts for the Mining break gate, driven against the real shipped
 * catalogue: inclusive thresholds at every tier boundary (requirement −0.1 / exact / +0.1),
 * actor policy, admin bypass, and the skill-data-unavailable denial — all without Minecraft
 * bootstrap, mirroring {@code FarmingCultivationGateTest}.
 */
class MiningBreakGateTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static MineableCatalog catalog;

    @BeforeAll
    static void loadShippedCatalog() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(
                PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json")));
    }

    private static Optional<MineableDefinition> definition(String id) {
        return Optional.of(catalog.byId(id).orElseThrow(
                () -> new AssertionError("Missing definition " + id)));
    }

    @Test
    void everyActiveTierEnforcesTheInclusiveBoundaryExactly() {
        for (MineableDefinition definition : catalog.active()) {
            Optional<MineableDefinition> resolved = Optional.of(definition);
            float required = definition.requiredMining();

            if (required > 0.0f) {
                MiningBreakGate.Evaluation below = MiningBreakGate.evaluateResolved(
                        resolved, MiningBreakGate.Subject.loadedPlayer(required - 0.1f));
                assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL, below.type(),
                        definition.id() + " at requirement-0.1 must deny");
                assertFalse(below.permitsBreak(), definition.id());
                assertEquals(required, below.requiredMining(), 0.0f, definition.id());
            }

            MiningBreakGate.Evaluation exact = MiningBreakGate.evaluateResolved(
                    resolved, MiningBreakGate.Subject.loadedPlayer(required));
            assertEquals(MiningBreakGate.ResultType.ELIGIBLE, exact.type(),
                    definition.id() + " at exact requirement must allow (inclusive threshold)");
            assertTrue(exact.permitsBreak(), definition.id());

            MiningBreakGate.Evaluation above = MiningBreakGate.evaluateResolved(
                    resolved, MiningBreakGate.Subject.loadedPlayer(Math.min(100.0f, required + 0.1f)));
            assertTrue(above.permitsBreak(), definition.id() + " at requirement+0.1 must allow");
        }
    }

    @Test
    void metalLadderBoundariesMatchTheApprovedValues() {
        record Tier(String id, float required) {}
        for (Tier tier : List.of(new Tier("iron", 0.0f), new Tier("silver", 55.0f),
                new Tier("tin", 65.0f), new Tier("shadow_iron", 70.0f), new Tier("copper", 75.0f),
                new Tier("gold", 85.0f), new Tier("agapite", 90.0f), new Tier("verite", 95.0f),
                new Tier("valorite", 99.0f))) {
            MiningBreakGate.Evaluation evaluation = MiningBreakGate.evaluateResolved(
                    definition(tier.id()), MiningBreakGate.Subject.loadedPlayer(tier.required()));
            assertEquals(tier.required(), evaluation.requiredMining(), 0.0f, tier.id());
            assertEquals(MiningBreakGate.ResultType.ELIGIBLE, evaluation.type(), tier.id());
        }
    }

    @Test
    void unmanagedBlocksAreNotApplicable() {
        MiningBreakGate.Evaluation evaluation = MiningBreakGate.evaluateResolved(
                Optional.empty(), MiningBreakGate.Subject.loadedPlayer(0.0f));
        assertEquals(MiningBreakGate.ResultType.NOT_APPLICABLE, evaluation.type());
        assertTrue(evaluation.permitsBreak(), "the gate must never interfere with unmanaged blocks");
        assertEquals("", evaluation.feedbackTranslationKey());
    }

    @Test
    void deferredCatalogueEntriesNeverReachTheGate() {
        for (String blockId : List.of("minecraft:dripstone_block", "minecraft:obsidian")) {
            assertTrue(catalog.resolveBlock(blockId).isEmpty(),
                    blockId + " is DEFERRED and must resolve NOT_APPLICABLE for gameplay");
        }
    }

    @Test
    void automationIsDeniedEvenWithCreativeOrSkill() {
        MiningBreakGate.Subject automation = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.AUTOMATION, true, 2,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        MiningBreakGate.Evaluation evaluation =
                MiningBreakGate.evaluateResolved(definition("silver"), automation);
        assertEquals(MiningBreakGate.ResultType.NON_PLAYER_POLICY, evaluation.type());
        assertFalse(evaluation.permitsBreak());
        assertEquals("message.britannia_mod.mining.automation_blocked",
                evaluation.feedbackTranslationKey());
    }

    @Test
    void nonPlayerActorsAreDenied() {
        MiningBreakGate.Subject nonPlayer = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.NON_PLAYER, false, 0,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        assertEquals(MiningBreakGate.ResultType.NON_PLAYER_POLICY,
                MiningBreakGate.evaluateResolved(definition("stone"), nonPlayer).type());
    }

    @Test
    void creativeAndOperatorBypassTheThresholdOnly() {
        MiningBreakGate.Subject creative = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, true, 0,
                SkillManager.SkillDataState.AVAILABLE, 0.0f);
        MiningBreakGate.Evaluation viaCreative =
                MiningBreakGate.evaluateResolved(definition("valorite"), creative);
        assertEquals(MiningBreakGate.ResultType.APPROVED_BYPASS, viaCreative.type());
        assertTrue(viaCreative.permitsBreak());

        MiningBreakGate.Subject operator = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, false, 2,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        assertEquals(MiningBreakGate.ResultType.APPROVED_BYPASS,
                MiningBreakGate.evaluateResolved(definition("valorite"), operator).type(),
                "permission-level 2 bypasses even before skill data loads, matching Farming");
    }

    @Test
    void unavailableSkillDataDeniesNonAdminPlayers() {
        for (SkillManager.SkillDataState state : List.of(
                SkillManager.SkillDataState.NOT_LOADED,
                SkillManager.SkillDataState.LOADING,
                SkillManager.SkillDataState.UNAVAILABLE)) {
            MiningBreakGate.Subject subject = new MiningBreakGate.Subject(
                    MiningBreakGate.ActorType.PLAYER, false, 0, state, 100.0f);
            MiningBreakGate.Evaluation evaluation =
                    MiningBreakGate.evaluateResolved(definition("stone"), subject);
            assertEquals(MiningBreakGate.ResultType.SKILL_DATA_UNAVAILABLE, evaluation.type(), state.name());
            assertFalse(evaluation.permitsBreak(), state.name());
            assertEquals("message.britannia_mod.mining.skill_unavailable",
                    evaluation.feedbackTranslationKey());
        }
    }

    @Test
    void insufficientEvaluationCarriesTheFeedbackValues() {
        MiningBreakGate.Evaluation evaluation = MiningBreakGate.evaluateResolved(
                definition("copper"), MiningBreakGate.Subject.loadedPlayer(72.4f));
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL, evaluation.type());
        assertEquals(72.4f, evaluation.currentMining(), 0.0001f);
        assertEquals(75.0f, evaluation.requiredMining(), 0.0f);
        assertEquals("Copper", evaluation.definition().orElseThrow().displayName());
        assertEquals("message.britannia_mod.mining.insufficient", evaluation.feedbackTranslationKey());
        assertEquals("72.4", MiningBreakGate.formatSkill(72.4f));
        assertEquals("75", MiningBreakGate.formatSkill(75.0f));
        assertEquals("?", MiningBreakGate.formatSkill(Float.NaN));
    }
}
