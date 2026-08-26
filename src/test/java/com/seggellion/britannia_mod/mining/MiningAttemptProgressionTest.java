package com.seggellion.britannia_mod.mining;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RunUO progression window: ReqSkill as an absolute gate, MinSkill/MaxSkill as the difficulty
 * band a qualified attempt rolls against.
 *
 * <h2>What this replaced</h2>
 * An earlier revision of this class asserted that a resource could train a miner who was <em>below
 * its requirement</em>, tapered by a "reach factor" so the hardest block in the game did not become
 * the best training target. Both the taper and the behaviour it protected are gone by owner
 * decision, in favour of RunUO's own short-circuit: {@code FinishHarvesting} reads
 * {@code skillBase >= resource.ReqSkill && from.CheckSkill(...)}, so an unqualified miner never
 * reaches the check and the resource teaches them nothing. Training comes from resources you can
 * actually work — stone, iron, coal, and each tier as it unlocks.
 */
class MiningAttemptProgressionTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path CATALOG_JSON =
            PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json");

    private static MineableCatalog catalog;

    @BeforeAll
    static void loadShippedCatalog() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(CATALOG_JSON));
    }

    private static MineableDefinition definition(String id) {
        return catalog.byId(id).orElseThrow(() -> new AssertionError("missing mineable " + id));
    }

    /** The derived window reproduces RunUO's published ore table exactly. */
    @Test
    void theWindowReproducesRunUoForEveryMetal() {
        assertWindow("iron", 0.0f, 0.0f, 100.0f);
        assertWindow("tin", 65.0f, 25.0f, 105.0f);          // RunUO's Dull Copper slot
        assertWindow("shadow_iron", 70.0f, 30.0f, 110.0f);
        assertWindow("copper", 75.0f, 35.0f, 115.0f);
        assertWindow("gold", 85.0f, 45.0f, 125.0f);
        assertWindow("agapite", 90.0f, 50.0f, 130.0f);
        assertWindow("verite", 95.0f, 55.0f, 135.0f);
        assertWindow("valorite", 99.0f, 59.0f, 139.0f);
    }

    /** Silver is this project's own tier and has no RunUO counterpart; it follows the same rule. */
    @Test
    void projectSpecificTiersFollowTheSameDerivation() {
        assertWindow("silver", 55.0f, 15.0f, 95.0f);
        assertWindow("coal", 10.0f, 0.0f, 80.0f);
    }

    /**
     * Entry-tier resources inherit RunUO's Iron window. The window still governs their gain
     * difficulty even where it no longer governs extraction, which is why stone keeps one.
     */
    @Test
    void entryTierResourcesUseTheBeginnerWindow() {
        assertWindow("stone", 0.0f, 0.0f, 100.0f);
        assertWindow("cobblestone", 0.0f, 0.0f, 100.0f);
    }

    private static void assertWindow(String id, float req, float min, float max) {
        MineableDefinition definition = definition(id);
        assertEquals(req, MiningProgression.reqSkill(definition), 0.0f, id + " ReqSkill");
        assertEquals(min, MiningProgression.minSkill(definition), 0.0f, id + " MinSkill");
        assertEquals(max, MiningProgression.maxSkill(definition), 0.0f, id + " MaxSkill");
    }

    // ------------------------------------------------------------- the absolute eligibility gate

    /** Below the requirement there is no qualification, and therefore no check and no gain. */
    @Test
    void belowTheRequirementNothingQualifies() {
        MineableDefinition valorite = definition("valorite");
        assertFalse(MiningProgression.qualifies(0.0f, valorite));
        assertFalse(MiningProgression.qualifies(98.9f, valorite),
                "one tenth short is still short");
        assertTrue(MiningProgression.qualifies(99.0f, valorite), "the threshold is inclusive");
    }

    /**
     * And an unqualified miner has no success chance either, so no path can leak an extraction to
     * them even if the gate were somehow bypassed.
     */
    @Test
    void anUnqualifiedMinerHasNoSuccessChance() {
        MineableDefinition valorite = definition("valorite");
        assertEquals(0.0f, MiningProgression.successChance(0.0f, valorite), 0.0f);
        assertEquals(0.0f, MiningProgression.successChance(98.9f, valorite), 0.0f);
    }

    // ------------------------------------------------------------------ the success curve itself

    /**
     * RunUO's bounded chance: certain failure at the floor, certain success at the ceiling.
     *
     * <p>Measured on iron, not stone. Iron shares stone's 0/0/100 window but is an ore, so it still
     * rolls; the stone family extracts deterministically because it is the terrain the player digs
     * through. See {@code MiningExtractionModeTest}.
     */
    @Test
    void theSuccessCurveIsBoundedByTheWindow() {
        MineableDefinition iron = definition("iron");
        assertEquals(0.0f, MiningProgression.successChance(0.0f, iron), 0.0f,
                "a beginner on iron is qualified but cannot yet succeed");
        assertEquals(0.5f, MiningProgression.successChance(50.0f, iron), 1.0e-4f);
        assertEquals(1.0f, MiningProgression.successChance(100.0f, iron), 0.0f,
                "at MaxSkill the check is a certainty, with no RNG involved");
        assertEquals(1.0f, MiningProgression.successChance(150.0f, iron), 0.0f);
    }

    /**
     * Valorite at exactly its requirement is a coin toss — the number the design intends, and the
     * reason a grandmaster still fails half their swings at the top tier.
     */
    @Test
    void valoriteAtItsRequirementIsACoinToss() {
        assertEquals(0.5f, MiningProgression.successChance(99.0f, definition("valorite")), 1.0e-4f);
    }

    /**
     * A live balance consequence worth pinning: at the ordinary skill cap of 100 a miner still only
     * clears Valorite about half the time, because its window runs to 139. That is authentic RunUO
     * — the top tier is meant to resist — and it is recorded here so a future change to the cap or
     * the window is a deliberate decision rather than an accident.
     */
    @Test
    void aCappedMinerStillFindsValoriteHard() {
        float atCap = MiningProgression.successChance(100.0f, definition("valorite"));
        assertEquals(0.5125f, atCap, 1.0e-3f);
        assertTrue(atCap < 1.0f, "Valorite must not become a formality at the skill cap");
    }

    /** The curve rises monotonically across the band, so progress always feels like progress. */
    @Test
    void theCurveRisesMonotonically() {
        MineableDefinition valorite = definition("valorite");
        float previous = -1.0f;
        for (float skill = 99.0f; skill <= 145.0f; skill += 5.0f) {
            float chance = MiningProgression.successChance(skill, valorite);
            assertTrue(chance >= previous, "chance dipped at skill " + skill);
            previous = chance;
        }
        assertEquals(1.0f, previous, 0.0f);
    }

    /** Every active resource has a coherent window, so no tier can ship unreachable. */
    @Test
    void everyActiveResourceHasACoherentWindow() {
        for (MineableDefinition definition : catalog.all()) {
            if (definition.status() != MineableDefinition.Status.ACTIVE) {
                continue;
            }
            String id = definition.id();
            float min = MiningProgression.minSkill(definition);
            float max = MiningProgression.maxSkill(definition);
            assertTrue(max > min, id + " has an empty difficulty window");
            assertTrue(min >= 0.0f, id + " has a negative MinSkill");
            assertTrue(MiningProgression.reqSkill(definition) >= min,
                    id + " requires less skill than its own window floor");
            assertEquals(1.0f, MiningProgression.successChance(max, definition), 0.0f,
                    id + " must be a certainty at its MaxSkill");
        }
    }

    /** The beginner ramp exists and is where RunUO puts it. */
    @Test
    void theBeginnerRampMatchesRunUo() {
        assertEquals(10.0f, MiningSkill.BEGINNER_GAIN_FLOOR, 0.0f,
                "RunUO forces a gain below skill 10, which is what carries a new miner off the floor");
    }
}
