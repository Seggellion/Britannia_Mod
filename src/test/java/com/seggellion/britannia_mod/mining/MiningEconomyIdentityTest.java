package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.economy.CommodityMappings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Milestone 8: every Mining output must resolve to a commodity identity Rails actually seeds.
 *
 * <p>Rails performs an <em>exact</em> lookup on category + subcategory + item_name whenever a sale
 * payload carries a category and a subcategory, with no legacy fallback — so a single wrong
 * subcategory silently makes an entire resource family unsellable. These contracts pin the payload
 * shape against the identities {@code CommoditySeeder} publishes.
 */
class MiningEconomyIdentityTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    /** category|subcategory|item_name exactly as Rails CommoditySeeder seeds them. */
    private static final Map<String, String> SEEDED_STONE = Map.ofEntries(
            Map.entry("cobblestone", "rubble"),
            Map.entry("stone", "common"),
            Map.entry("andesite", "igneous"),
            Map.entry("diorite", "igneous"),
            Map.entry("granite", "igneous"),
            Map.entry("tuff", "volcanic"),
            Map.entry("basalt", "volcanic"),
            Map.entry("blackstone", "volcanic"),
            Map.entry("limestone", "sedimentary"),
            Map.entry("quartz", "mineral"));

    private static final List<String> SEEDED_ORES = List.of(
            "tin", "copper", "iron", "silver", "gold", "shadow_iron", "agapite", "verite", "valorite");

    private static MineableCatalog catalog;

    @BeforeAll
    static void loadShippedCatalog() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(
                PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json")));
    }

    @Test
    void everyStoneCommodityIsPostedUnderItsSeededFamily() {
        SEEDED_STONE.forEach((commodity, family) ->
                assertEquals(Optional.of(family), CommodityMappings.stoneCommoditySubcategory(commodity),
                        commodity + " must be posted under the subcategory Rails seeds it in"));
    }

    /** The identity key Rails parses on "|" must name a seeded row, never the old "blocks". */
    @Test
    void stoneIdentityKeysMatchSeededRows() {
        SEEDED_STONE.forEach((commodity, family) ->
                assertEquals("stone|" + family + "|" + commodity,
                        CommodityMappings.stoneCommodityIdentityKey(commodity)));
        assertFalse(CommodityMappings.stoneCommodityIdentityKey("basalt").contains("blocks"),
                "no seeded commodity has ever used the subcategory 'blocks'");
    }

    /** Blackstone's managed drop is named "Blackrock"; it must still reach the blackstone row. */
    @Test
    void blackrockResolvesToTheSeededBlackstoneCommodity() {
        assertEquals(Optional.of("blackstone"), CommodityMappings.stoneCommodityKey("Blackrock"));
        assertEquals("stone|volcanic|blackstone",
                CommodityMappings.stoneCommodityIdentityKey("blackstone"));
    }

    /**
     * The catalogue's own declaration and the runtime mapping must agree for every resource that
     * claims an economy identity — the two could drift silently otherwise.
     */
    @Test
    void catalogueEconomyIdentitiesAgreeWithTheRuntimeMapping() {
        for (MineableDefinition definition : catalog.active()) {
            Optional<String> declared = definition.economyCommodity();
            if (declared.isEmpty()) {
                continue;
            }
            Optional<String> resolved = definition.category() == MineableDefinition.Category.ORE
                    ? CommodityMappings.oreCommodityKey(definition.dropName())
                    : CommodityMappings.stoneCommodityKey(definition.dropName());
            assertEquals(declared, resolved,
                    definition.id() + " drops \"" + definition.dropName()
                            + "\" which must resolve to its declared commodity");
        }
    }

    /** Every declared identity must be one Rails actually seeds — no invented commodities. */
    @Test
    void noMineableClaimsACommodityRailsDoesNotSeed() {
        for (MineableDefinition definition : catalog.active()) {
            String declared = definition.economyCommodity().orElse(null);
            if (declared == null) continue;
            boolean seeded = definition.category() == MineableDefinition.Category.ORE
                    ? SEEDED_ORES.contains(declared)
                    : SEEDED_STONE.containsKey(declared);
            assertTrue(seeded, definition.id() + " claims unseeded commodity '" + declared + "'");
        }
    }

    /** Resources with no commodity are a recorded gap, not an accident: they must be explicit. */
    @Test
    void resourcesWithoutACommodityAreDocumented() {
        List<String> unsellable = new ArrayList<>();
        for (MineableDefinition definition : catalog.active()) {
            if (definition.economyCommodity().isEmpty()) {
                unsellable.add(definition.id());
            }
            if (definition.category() == MineableDefinition.Category.STONE
                    && definition.economyCommodity().isEmpty()) {
                assertTrue(CommodityMappings.stoneCommodityKey(definition.dropName()).isEmpty(),
                        definition.id() + " resolves to a commodity but the catalogue says it has none");
            }
        }
        assertEquals(List.of("deepslate", "cobbled_deepslate", "igneous_rock", "metamorphic_rock",
                        "volcanic_rock", "glacial_rock", "dripstone"),
                unsellable.stream().sorted(
                        java.util.Comparator.comparingInt(List.of("deepslate", "cobbled_deepslate",
                                "igneous_rock", "metamorphic_rock", "volcanic_rock", "glacial_rock",
                                "dripstone")::indexOf)).toList(),
                "the unsellable set must change only by deliberate owner decision");
    }

    /** Ore payloads keep the seeded ore/raw shape established in milestone 5. */
    @Test
    void oreIdentitiesRemainSeededAndUnduplicated() {
        for (String ore : SEEDED_ORES) {
            assertEquals(Optional.of(ore), CommodityMappings.oreCommodityKey(ore));
        }
        assertEquals(Optional.of("silver"), CommodityMappings.oreCommodityKey("High-Purity Silver ore"),
                "the retired premium node must not gain a second silver identity");
        assertEquals(1L, catalog.active().stream()
                        .map(MineableDefinition::economyCommodity)
                        .filter(commodity -> commodity.filter("silver"::equals).isPresent())
                        .count(),
                "exactly one active resource may claim the silver commodity");
    }

    /** No two active resources may claim the same commodity by different names. */
    @Test
    void noTwoResourcesShareACommodityUnintentionally() {
        Map<String, String> owners = new java.util.HashMap<>();
        for (MineableDefinition definition : catalog.active()) {
            String commodity = definition.economyCommodity().orElse(null);
            if (commodity == null) continue;
            String previous = owners.put(commodity, definition.id());
            if (previous != null) {
                // Stone and Cobblestone deliberately share the cobblestone commodity: mining stone
                // yields rubble. Anything else sharing an identity is a mistake.
                assertTrue(commodity.equals("cobblestone"),
                        commodity + " claimed by both " + previous + " and " + definition.id());
            }
        }
    }
}
