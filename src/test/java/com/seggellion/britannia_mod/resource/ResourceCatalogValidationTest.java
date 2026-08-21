package com.seggellion.britannia_mod.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 2: malformed resource data fails the load, loudly and specifically.
 *
 * <p>Shaped after {@code MineableCatalogValidationTest}. Every case drives the real parser against
 * a real (small) catalogue, so these are behavioural tests of the validation rules rather than
 * assertions about the source that implements them.
 *
 * <p>Two of them do double duty. {@link #aNewResourceNeedsNoJavaAtAll} is the milestone's central
 * claim — that a resource reusing an existing Mining requirement, extraction tag and shape is a
 * data change and nothing else — and it is demonstrable only because the parser accepts data the
 * shipped file does not contain.
 */
class ResourceCatalogValidationTest {

    /** A well-formed ore that reuses everything the shipped catalogue already has. */
    private static final String VALID_ORE = """
            {
              "id": "britannia_mod:silver",
              "display_name": "Silver ore",
              "family": "ore",
              "blocks": ["britannia_mod:silver_ore"],
              "mineable": "silver",
              "extraction_tool": "britannia_mod:mining_pickaxes",
              "yield": {"mode": "purity_ore"},
              "depleted": "fluid_aware_air",
              "regeneration": {"hours": 6},
              "generation": {"shape": "vertical_layered", "block": "britannia_mod:silver_ore", "min_radius": 1, "max_radius": 96, "host": "britannia_mod:ore_hosts"},
              "revision": 1
            }""";

    private static final String VALID_BED = """
            {
              "id": "britannia_mod:clay_deposit",
              "display_name": "Clay Bed",
              "family": "sediment",
              "blocks": ["britannia_mod:clay_deposit"],
              "extraction_tool": "britannia_mod:clay_shovels",
              "yield": {"mode": "item", "item": "minecraft:clay_ball", "count": 1},
              "depleted": "fluid_aware_air",
              "regeneration": {"hours": 6},
              "revision": 1
            }""";

    private static ResourceCatalog parse(String... definitions) {
        return ResourceCatalog.parse(new StringReader(
                "{\"schema\": 1, \"resources\": [" + String.join(",", definitions) + "]}"));
    }

    private static String failureOf(String... definitions) {
        return assertThrows(IllegalStateException.class, () -> parse(definitions)).getMessage();
    }

    /** A fixture that is not a broken catalogue: one resource, fully described, loads. */
    @Test
    void aWellFormedFixtureLoads() {
        ResourceCatalog catalog = parse(VALID_ORE, VALID_BED);
        assertEquals(2, catalog.all().size());
        assertTrue(catalog.byId("britannia_mod:silver").isPresent());
        assertTrue(catalog.byPath("clay_deposit").isPresent());
    }

    /* ------------------------------------------------------------------ */
    /*  Identity                                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void duplicateResourceIdsFail() {
        assertTrue(failureOf(VALID_ORE, VALID_ORE).contains("Duplicate resource definition id"));
    }

    @Test
    void twoResourcesClaimingTheSameBlockFail() {
        String other = VALID_ORE
                .replace("britannia_mod:silver\"", "britannia_mod:silver_two\"")
                .replace("\"mineable\": \"silver\"", "\"mineable\": \"tin\"");
        assertTrue(failureOf(VALID_ORE, other).contains("is claimed by both resource"));
    }

    /**
     * Two resources can never share one Mining requirement, and it turns out to be impossible to
     * even express the attempt.
     *
     * <p>Both spellings fail, by different guards, which is worth pinning because it shows the
     * rules overlap rather than leaving a seam. Claiming the same mineable <em>and</em> the same
     * block trips the duplicate-block check; claiming the same mineable with a different block
     * trips the stronger rule that a resource must cover exactly the blocks its mineable does.
     * There is no third arrangement.
     */
    @Test
    void twoResourcesCannotShareOneMiningRequirement() {
        String sameBlocks = VALID_ORE.replace("britannia_mod:silver\"", "britannia_mod:silver_two\"");
        assertTrue(failureOf(VALID_ORE, sameBlocks).contains("is claimed by both resource"),
                "the same block cannot belong to two resources");

        String differentBlocks = VALID_ORE
                .replace("britannia_mod:silver\"", "britannia_mod:silver_two\"")
                .replace("[\"britannia_mod:silver_ore\"]", "[\"britannia_mod:tin_ore\"]")
                .replace("\"block\": \"britannia_mod:silver_ore\"", "\"block\": \"britannia_mod:tin_ore\"");
        assertTrue(failureOf(VALID_ORE, differentBlocks).contains("they must match exactly"),
                "a resource must cover exactly the blocks its mineable claims");
    }

    @Test
    void anUnnamespacedIdFails() {
        assertTrue(failureOf(VALID_ORE.replace("britannia_mod:silver\"", "silver\""))
                .contains("namespaced lowercase id"));
    }

    /* ------------------------------------------------------------------ */
    /*  Mining reference                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    void anUnresolvedMineableReferenceFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"mineable\": \"silver\"", "\"mineable\": \"mithril\""))
                .contains("references unknown mineable 'mithril'"));
    }

    @Test
    void aMiningResourceWithNoMineableReferenceFails() {
        assertTrue(failureOf(VALID_ORE.replace("  \"mineable\": \"silver\",\n", ""))
                .contains("must reference a mineable"));
    }

    /** An ore may not quietly point at a stone's requirement, or vice versa. */
    @Test
    void aFamilyThatDisagreesWithItsMineableCategoryFails() {
        String mismatched = VALID_ORE
                .replace("\"family\": \"ore\"", "\"family\": \"stone\"")
                .replace("\"mode\": \"purity_ore\"", "\"mode\": \"graded_stone\"");
        assertTrue(failureOf(mismatched).contains("is category"));
    }

    /** The strongest anti-drift rule: neither catalogue may gain or lose a block alone. */
    @Test
    void blocksThatDisagreeWithTheMineableFail() {
        String extra = VALID_ORE.replace("[\"britannia_mod:silver_ore\"]",
                "[\"britannia_mod:silver_ore\", \"minecraft:iron_ore\"]");
        assertTrue(failureOf(extra).contains("they must match exactly"));
    }

    @Test
    void aSedimentBedThatClaimsAMiningRequirementFails() {
        String wrong = VALID_BED.replace("  \"extraction_tool\"",
                "  \"mineable\": \"silver\",\n  \"extraction_tool\"");
        assertTrue(failureOf(wrong).contains("must not reference a Mining requirement"));
    }

    /* ------------------------------------------------------------------ */
    /*  Yield                                                              */
    /* ------------------------------------------------------------------ */

    @Test
    void aYieldModeThatDoesNotMatchTheFamilyFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"mode\": \"purity_ore\"", "\"mode\": \"graded_stone\""))
                .contains("must use yield mode purity_ore"));
    }

    @Test
    void anItemYieldWithNoItemFails() {
        assertTrue(failureOf(VALID_BED.replace(", \"item\": \"minecraft:clay_ball\"", ""))
                .contains("must name it"));
    }

    @Test
    void aDerivedYieldThatAlsoNamesAnItemFails() {
        assertTrue(failureOf(VALID_ORE.replace("{\"mode\": \"purity_ore\"}",
                        "{\"mode\": \"purity_ore\", \"item\": \"minecraft:raw_iron\"}"))
                .contains("must not also name an item"));
    }

    @Test
    void aNonPositiveYieldCountFails() {
        assertTrue(failureOf(VALID_BED.replace("\"count\": 1", "\"count\": 0"))
                .contains("must be positive"));
    }

    /* ------------------------------------------------------------------ */
    /*  Regeneration                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    void anUnsafeRegenerationDurationFails() {
        for (String hours : java.util.List.of("0", "-6", "100000")) {
            assertTrue(failureOf(VALID_ORE.replace("\"hours\": 6", "\"hours\": " + hours))
                    .contains("Regeneration"), "hours " + hours + " must be refused");
        }
    }

    @Test
    void aValidTwentyFourHourDurationIsAccepted() {
        ResourceCatalog catalog = parse(VALID_BED.replace("\"hours\": 6", "\"hours\": 24"));
        assertEquals(24, catalog.byId("britannia_mod:clay_deposit").orElseThrow().regenerationHours());
    }

    /* ------------------------------------------------------------------ */
    /*  Depleted state and generation                                      */
    /* ------------------------------------------------------------------ */

    @Test
    void anUnknownDepletedStateFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"fluid_aware_air\"", "\"vanish\""))
                .contains("Unknown depleted state"));
    }

    @Test
    void anUnknownShapeFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"shape\": \"vertical_layered\"", "\"shape\": \"spiral\""))
                .contains("Unknown generation shape 'spiral'"));
    }

    /** Data may narrow a shape's safe range but never widen it back into the crashing one. */
    @Test
    void aMinimumRadiusBelowTheShapesOwnMinimumFails() {
        String snake = VALID_ORE
                .replace("\"shape\": \"vertical_layered\"", "\"shape\": \"snake\"")
                .replace("\"min_radius\": 1", "\"min_radius\": 3");
        assertTrue(failureOf(snake).contains("shape's own minimum"),
                "data may narrow a shape's range, never widen it below what the planner needs");
    }

    /**
     * A generation block without a host tag has no policy about what it may replace.
     *
     * <p>Which is the defect milestone 3 removed from the shapes: they each decided for themselves,
     * and disagreed. Leaving the field out must fail rather than default to something permissive.
     */
    @Test
    void generationWithNoHostTagFails() {
        String noHost = VALID_ORE.replace(", \"host\": \"britannia_mod:ore_hosts\"", "");
        assertTrue(failureOf(noHost).contains("missing 'host'"), noHost);
    }

    @Test
    void aMalformedHostTagFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"britannia_mod:ore_hosts\"", "\"not a tag\""))
                .contains("Malformed host tag"));
    }

    @Test
    void aMaximumRadiusBelowTheMinimumFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"max_radius\": 96", "\"max_radius\": 0"))
                .contains("below its min radius"));
    }

    @Test
    void generatingABlockTheResourceDoesNotGovernFails() {
        assertTrue(failureOf(VALID_ORE.replace("\"block\": \"britannia_mod:silver_ore\"",
                        "\"block\": \"minecraft:diamond_ore\""))
                .contains("which is not one of the blocks it governs"));
    }

    @Test
    void aSedimentBedThatConfiguresVeinGenerationFails() {
        String wrong = VALID_BED.replace("  \"revision\": 1",
                "  \"generation\": {\"shape\": \"layered\", \"block\": \"britannia_mod:clay_deposit\","
                        + " \"min_radius\": 1, \"max_radius\": 8,"
                        + " \"host\": \"britannia_mod:ore_hosts\"},\n  \"revision\": 1");
        assertTrue(failureOf(wrong).contains("must not configure vein generation"), wrong);
    }

    /* ------------------------------------------------------------------ */
    /*  Structural                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    void missingRequiredFieldsFailWithTheFieldNamed() {
        assertTrue(failureOf(VALID_ORE.replace("  \"extraction_tool\": \"britannia_mod:mining_pickaxes\",\n", ""))
                .contains("missing 'extraction_tool'"));
        assertTrue(failureOf(VALID_ORE.replace("  \"blocks\": [\"britannia_mod:silver_ore\"],\n", ""))
                .contains("missing array 'blocks'"));
        assertTrue(failureOf(VALID_ORE.replace("  \"regeneration\": {\"hours\": 6},\n", ""))
                .contains("missing object 'regeneration'"));
    }

    @Test
    void anUnsupportedSchemaFails() {
        assertTrue(assertThrows(IllegalStateException.class, () -> ResourceCatalog.parse(
                        new StringReader("{\"schema\": 99, \"resources\": []}")))
                .getMessage().contains("Unsupported resource catalogue schema"));
    }

    @Test
    void anEmptyCatalogueFails() {
        assertTrue(assertThrows(IllegalStateException.class, () -> ResourceCatalog.parse(
                        new StringReader("{\"schema\": 1, \"resources\": []}")))
                .getMessage().contains("Resource catalogue is empty"));
    }

    @Test
    void aCatalogueThatLeavesAnActiveMineableUnclaimedFailsTheCompletenessCheck() {
        ResourceCatalog partial = parse(VALID_ORE);
        assertTrue(assertThrows(IllegalStateException.class, partial::validateCoversEveryActiveMineable)
                .getMessage().contains("Every ACTIVE mineable needs a resource definition"));
    }

    /* ------------------------------------------------------------------ */
    /*  The milestone's central claim                                      */
    /* ------------------------------------------------------------------ */

    /**
     * A brand-new resource that reuses an existing Mining requirement, an existing extraction tag
     * and an existing shape is described entirely in data.
     *
     * <p>No {@code switch} on its name exists anywhere for it to be added to: the break gate reads
     * its tool from {@code extraction_tool}, the placement command dispatches on
     * {@link ResourceShape} rather than on the resource, and the restoration delay is read from
     * {@code regeneration}. This test would have been impossible before the milestone, because
     * every one of those was a Java branch keyed on the resource's name.
     */
    @Test
    void aNewResourceNeedsNoJavaAtAll() {
        String invented = """
                {
                  "id": "britannia_mod:tin",
                  "display_name": "Tin ore",
                  "family": "ore",
                  "blocks": ["britannia_mod:tin_ore"],
                  "mineable": "tin",
                  "extraction_tool": "britannia_mod:mining_pickaxes",
                  "yield": {"mode": "purity_ore"},
                  "depleted": "fluid_aware_air",
                  "regeneration": {"hours": 12},
                  "generation": {"shape": "geode", "block": "britannia_mod:tin_ore", "min_radius": 4, "max_radius": 16, "host": "britannia_mod:ore_hosts"},
                  "revision": 1
                }""";
        ResourceCatalog catalog = parse(invented);
        ResourceDefinition resource = catalog.byPath("tin").orElseThrow();

        assertEquals(ResourceShape.GEODE, resource.generation().orElseThrow().shape(),
                "it reuses an existing shape by naming it");
        assertEquals("britannia_mod:mining_pickaxes", resource.extractionToolTag(),
                "it reuses an existing extraction rule by naming its tag");
        assertEquals(12L * 60 * 60 * 1000, resource.regenerationMillis(),
                "it sets its own regeneration without touching the scheduler");
        assertEquals(65.0f,
                com.seggellion.britannia_mod.mining.MineableCatalog.instance()
                        .byId(resource.mineableId().orElseThrow()).orElseThrow().requiredMining(), 0.0f,
                "and it inherits its Mining requirement by reference, never by copy");
    }
}
