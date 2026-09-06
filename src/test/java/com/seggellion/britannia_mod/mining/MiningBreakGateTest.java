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
    void retiredResourcesNeverReachTheGate() {
        // Owner decisions 2026-08-14: one Silver metal/ore, and Obsidian is not a UO material.
        for (String blockId : List.of("minecraft:obsidian", "britannia_mod:high_purity_silver_ore")) {
            assertTrue(catalog.resolveBlock(blockId).isEmpty(),
                    blockId + " is retired and must resolve NOT_APPLICABLE");
        }
        assertTrue(catalog.resolveBlock("minecraft:dripstone_block").isPresent(),
                "Dripstone is active and must be gated at its approved tier");
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL,
                MiningBreakGate.evaluateResolved(definition("dripstone"),
                        MiningBreakGate.Subject.loadedPlayer(34.9f)).type());
        assertEquals(MiningBreakGate.ResultType.ELIGIBLE,
                MiningBreakGate.evaluateResolved(definition("dripstone"),
                        MiningBreakGate.Subject.loadedPlayer(35.0f)).type());
    }

    @Test
    void automationIsDeniedEvenWithCreativeOrSkill() {
        MiningBreakGate.Subject automation = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.AUTOMATION, true, 2,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN, true);
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
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN, true);
        assertEquals(MiningBreakGate.ResultType.NON_PLAYER_POLICY,
                MiningBreakGate.evaluateResolved(definition("stone"), nonPlayer).type());
    }

    /**
     * The owner-approved absolute invariant: below the hard requirement, nothing breaks. Not in
     * survival, not in adventure, not in creative, not at op level 2 or 4, not for the owner.
     *
     * <p>Both bypasses this replaces were real defects. Operator permission was invisible to the
     * whole GameTest suite because every fixture builds a permission-0 player, and it reproduced
     * instantly on a live client. Creative was the subtler one: creative siting and creative
     * harvesting are different authorities, and conflating them meant an administrator placing a
     * resource node could also empty it at zero skill.
     */
    @Test
    void noGameModeOrPermissionLevelBypassesTheHardRequirement() {
        MiningBreakGate.Subject creative = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, true, 0,
                SkillManager.SkillDataState.AVAILABLE, 0.0f, true);
        MiningBreakGate.Evaluation viaCreative =
                MiningBreakGate.evaluateResolved(definition("valorite"), creative);
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL, viaCreative.type(),
                "creative must not bypass the Mining ladder");
        assertFalse(viaCreative.permitsBreak());

        MiningBreakGate.Subject survivalOperator = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, false, 2,
                SkillManager.SkillDataState.AVAILABLE, 0.0f, true);
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL,
                MiningBreakGate.evaluateResolved(definition("valorite"), survivalOperator).type(),
                "op must not bypass the Mining ladder while in survival");

        MiningBreakGate.Subject creativeOperator = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, true, 4,
                SkillManager.SkillDataState.AVAILABLE, 0.0f, true);
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL,
                MiningBreakGate.evaluateResolved(definition("valorite"), creativeOperator).type(),
                "creative + op level 4 is still not a licence to mine Valorite at zero skill");

        // And the gate stays permissive where it should: the same actors at the requirement pass.
        MiningBreakGate.Subject qualifiedCreative = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, true, 4,
                SkillManager.SkillDataState.AVAILABLE, 99.0f, true);
        assertEquals(MiningBreakGate.ResultType.ELIGIBLE,
                MiningBreakGate.evaluateResolved(definition("valorite"), qualifiedCreative).type());
    }

    @Test
    void unavailableSkillDataDeniesNonAdminPlayers() {
        for (SkillManager.SkillDataState state : List.of(
                SkillManager.SkillDataState.NOT_LOADED,
                SkillManager.SkillDataState.LOADING,
                SkillManager.SkillDataState.UNAVAILABLE)) {
            MiningBreakGate.Subject subject = new MiningBreakGate.Subject(
                    MiningBreakGate.ActorType.PLAYER, false, 0, state, 100.0f, true);
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

    // ---------------------------------------------------------------------------------------
    // OreVein milestone 1: the tool is now part of the decision, not an afterthought left to
    // whichever handler happened to run next.
    // ---------------------------------------------------------------------------------------

    /**
     * The defect this milestone exists to close. Skill alone used to be a full answer, so a
     * maxed-out miner holding anything at all was told ELIGIBLE and the block fell through to
     * vanilla breaking.
     */
    @Test
    void skillNeverSubstitutesForTheTool() {
        for (String id : List.of("stone", "iron", "silver", "copper", "verite", "valorite")) {
            MiningBreakGate.Evaluation evaluation = MiningBreakGate.evaluateResolved(
                    definition(id), MiningBreakGate.Subject.loadedPlayerWithWrongTool(100.0f));
            assertEquals(MiningBreakGate.ResultType.WRONG_TOOL, evaluation.type(),
                    id + " must refuse a wrong tool at any skill");
            assertFalse(evaluation.permitsBreak(), id + " must not reach vanilla breaking");
            assertEquals("message.britannia_mod.mining.wrong_tool",
                    evaluation.feedbackTranslationKey());
        }
    }

    /** The requirement is still enforced for someone holding the right thing. */
    @Test
    void theRightToolStillHasToClearTheRequirement() {
        assertEquals(MiningBreakGate.ResultType.INSUFFICIENT_SKILL,
                MiningBreakGate.evaluateResolved(
                        definition("valorite"), MiningBreakGate.Subject.loadedPlayer(50.0f)).type());
        assertEquals(MiningBreakGate.ResultType.ELIGIBLE,
                MiningBreakGate.evaluateResolved(
                        definition("valorite"), MiningBreakGate.Subject.loadedPlayer(99.0f)).type());
    }

    /** An unmanaged block is still nobody's business, whatever is in hand. */
    @Test
    void theToolCheckOnlyAppliesToManagedResources() {
        assertEquals(MiningBreakGate.ResultType.NOT_APPLICABLE,
                MiningBreakGate.evaluateResolved(
                        Optional.empty(),
                        MiningBreakGate.Subject.loadedPlayerWithWrongTool(0.0f)).type(),
                "a wrong tool on ordinary world must not become a Mining denial");
    }

    /** The tool question is still asked of everyone, in every game mode. */
    @Test
    void everyActorStillNeedsTheRightToolWhateverTheirGameMode() {
        MiningBreakGate.Subject creativeWrongTool = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, true, 4,
                SkillManager.SkillDataState.AVAILABLE, 100.0f, false);
        assertEquals(MiningBreakGate.ResultType.WRONG_TOOL,
                MiningBreakGate.evaluateResolved(definition("valorite"), creativeWrongTool).type());

        MiningBreakGate.Subject operatorWrongTool = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, false, 2,
                SkillManager.SkillDataState.AVAILABLE, 100.0f, false);
        assertEquals(MiningBreakGate.ResultType.WRONG_TOOL,
                MiningBreakGate.evaluateResolved(definition("valorite"), operatorWrongTool).type());
    }

    /** Automation is refused on actor policy first, so its tool never becomes the reason. */
    @Test
    void actorPolicyIsDecidedBeforeTheTool() {
        MiningBreakGate.Subject automationWrongTool = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.AUTOMATION, false, 0,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN, false);
        assertEquals(MiningBreakGate.ResultType.NON_PLAYER_POLICY,
                MiningBreakGate.evaluateResolved(definition("stone"), automationWrongTool).type());
    }

    /** A wrong tool is a knowable, fixable fact, so it is reported ahead of transient skill state. */
    @Test
    void theToolIsReportedBeforeUnavailableSkillData() {
        MiningBreakGate.Subject unloadedWrongTool = new MiningBreakGate.Subject(
                MiningBreakGate.ActorType.PLAYER, false, 0,
                SkillManager.SkillDataState.NOT_LOADED, Float.NaN, false);
        assertEquals(MiningBreakGate.ResultType.WRONG_TOOL,
                MiningBreakGate.evaluateResolved(definition("stone"), unloadedWrongTool).type());
    }
}
