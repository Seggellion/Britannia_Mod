package com.seggellion.britannia_mod.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two extraction modes, and the reason the stone family is allowed to differ from RunUO.
 *
 * <h2>The live defect this locks out</h2>
 * A miner at Mining 0.4 swinging at ordinary stone saw the block break, reappear, and report "you
 * fail to extract anything usable". Stone is 0/0/100, so the RunUO success roll gave them
 * {@code (0.4 - 0) / 100} — refused better than 99 times in a hundred. For an ore that is correct
 * and interesting; for the terrain a player physically tunnels through it means they cannot dig at
 * all.
 *
 * <p>So the stone family extracts deterministically once the hard requirement is met, and ores keep
 * the RunUO roll. Both halves are asserted here, because the risk runs in both directions: making
 * stone deterministic must not quietly make ore deterministic too.
 */
class MiningExtractionModeTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path CATALOG_JSON =
            PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json");
    private static final Path RESOURCES_JSON =
            PROJECT.resolve("src/main/resources/data/britannia_mod/resources/resources.json");

    private static MineableCatalog catalog;

    /** Yield mode declared for each mineable id in the resource catalogue. */
    private static final Map<String, String> YIELD_MODE = new HashMap<>();

    /** Configured restoration interval, in hours, per mineable id. */
    private static final Map<String, Integer> REGEN_HOURS = new HashMap<>();

    @BeforeAll
    static void loadShippedCatalogues() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(CATALOG_JSON));
        try (BufferedReader reader = Files.newBufferedReader(RESOURCES_JSON)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.getAsJsonArray("resources").forEach(element -> {
                JsonObject resource = element.getAsJsonObject();
                if (!resource.has("mineable")) {
                    return;
                }
                String id = resource.get("mineable").getAsString();
                if (resource.has("yield") && resource.getAsJsonObject("yield").has("mode")) {
                    YIELD_MODE.put(id, resource.getAsJsonObject("yield").get("mode").getAsString());
                }
                if (resource.has("regeneration")
                        && resource.getAsJsonObject("regeneration").has("hours")) {
                    REGEN_HOURS.put(id,
                            resource.getAsJsonObject("regeneration").get("hours").getAsInt());
                }
            });
        }
    }

    private static boolean isActive(MineableDefinition definition) {
        return definition.status() == MineableDefinition.Status.ACTIVE;
    }

    // ------------------------------------------------------------------ the stone family, in full

    /**
     * Every stone resource extracts deterministically, at every skill from its requirement upward.
     * Parameterised over the shipped catalogue rather than spot-checked, so a new stone resource is
     * covered the day it is added.
     */
    @Test
    void everyStoneResourceExtractsDeterministicallyOnceQualified() {
        int checked = 0;
        for (MineableDefinition definition : catalog.all()) {
            if (!isActive(definition)
                    || definition.category() != MineableDefinition.Category.STONE) {
                continue;
            }
            checked++;
            String id = definition.id();
            assertEquals(MiningProgression.ExtractionMode.DETERMINISTIC_AFTER_REQUIREMENT,
                    MiningProgression.extractionMode(definition), id);

            float required = MiningProgression.reqSkill(definition);
            for (float over : new float[] {0.0f, 0.1f, 5.0f, 50.0f, 200.0f}) {
                assertEquals(1.0f, MiningProgression.successChance(required + over, definition), 0.0f,
                        id + " must always excavate at skill " + (required + over));
            }
        }
        assertEquals(17, checked, "the shipped catalogue has 17 active stone resources");
    }

    /** The reported case exactly: a beginner on ordinary stone digs, every time. */
    @Test
    void theReportedBeginnerCaseNowExcavates() {
        MineableDefinition stone = catalog.byId("stone").orElseThrow();
        assertEquals(1.0f, MiningProgression.successChance(0.4f, stone), 0.0f,
                "Mining 0.4 on ordinary stone was the live defect");
        assertEquals(1.0f, MiningProgression.successChance(0.0f, stone), 0.0f,
                "and Mining 0 must dig too, since stone requires 0");
    }

    /** Deterministic extraction does not mean ungated: the hard requirement still bites. */
    @Test
    void stoneHardRequirementsStillRefuseBelowTheirGate() {
        for (String id : new String[] {"calcite", "diorite", "granite", "deepslate", "volcanic_rock"}) {
            MineableDefinition definition = catalog.byId(id).orElseThrow();
            float required = MiningProgression.reqSkill(definition);
            assertTrue(required > 0.0f, id + " is expected to have a real requirement");
            assertEquals(0.0f, MiningProgression.successChance(required - 0.1f, definition), 0.0f,
                    id + " must refuse just below its requirement");
            assertEquals(1.0f, MiningProgression.successChance(required, definition), 0.0f,
                    id + " must excavate exactly at its requirement, with no roll");
        }
    }

    /**
     * Family and output agree, which is what makes family a legitimate discriminator rather than a
     * convenient one. If a future resource is filed as stone but pays something other than graded
     * stone — or pays graded stone from another family — this fails and the mode rule needs
     * revisiting rather than silently mis-classifying it.
     */
    @Test
    void theStoneFamilyAndTheGradedStoneYieldAreTheSameSet() {
        for (MineableDefinition definition : catalog.all()) {
            if (!isActive(definition)) {
                continue;
            }
            String id = definition.id();
            String yield = YIELD_MODE.get(id);
            if (yield == null) {
                continue;
            }
            boolean stoneFamily = definition.category() == MineableDefinition.Category.STONE;
            assertEquals(stoneFamily, "graded_stone".equals(yield),
                    id + ": stone family and graded_stone yield must coincide");
        }
    }

    // ------------------------------------------------------------------- ores must not regress

    /** Ores keep the RunUO roll; the stone correction must not leak into them. */
    @Test
    void oresRemainSkillChecked() {
        for (MineableDefinition definition : catalog.all()) {
            if (!isActive(definition)
                    || definition.category() != MineableDefinition.Category.ORE) {
                continue;
            }
            assertEquals(MiningProgression.ExtractionMode.SKILL_CHECKED,
                    MiningProgression.extractionMode(definition), definition.id());
        }
    }

    /** Valorite specifically, since it is the tier most likely to be "helpfully" smoothed. */
    @Test
    void valoriteStaysACoinTossAtItsRequirement() {
        MineableDefinition valorite = catalog.byId("valorite").orElseThrow();
        assertEquals(0.5f, MiningProgression.successChance(99.0f, valorite), 1.0e-4f);
        assertEquals(0.0f, MiningProgression.successChance(98.9f, valorite), 0.0f);
        assertEquals(1.0f, MiningProgression.successChance(139.0f, valorite), 0.0f);
    }

    /** Coal is a mineral, not stone, and deliberately keeps the roll pending an owner decision. */
    @Test
    void coalRemainsSkillCheckedAsAMineral() {
        MineableDefinition coal = catalog.byId("coal").orElseThrow();
        assertEquals(MiningProgression.ExtractionMode.SKILL_CHECKED,
                MiningProgression.extractionMode(coal));
        assertEquals(0.125f, MiningProgression.successChance(10.0f, coal), 1.0e-4f,
                "coal at its requirement is a 12.5% roll -- recorded so the number is a decision");
    }

    // ------------------------------------------------------------------------- restoration data

    /**
     * The renewable half of the bargain. Deterministic excavation is only safe because the terrain
     * comes back, so the configured intervals are pinned here rather than assumed.
     */
    @Test
    void everyStoneResourceRestoresAtSixHours() {
        for (MineableDefinition definition : catalog.all()) {
            if (!isActive(definition)
                    || definition.category() != MineableDefinition.Category.STONE) {
                continue;
            }
            assertEquals(6, REGEN_HOURS.get(definition.id()),
                    definition.id() + " must keep its six-hour restoration");
        }
        // The one deliberate outlier in the whole catalogue, so a sweep never flattens it.
        assertEquals(24, REGEN_HOURS.get("silica_sand"),
                "silica sand is deliberately 24h and must not be normalised to six");
    }
}
