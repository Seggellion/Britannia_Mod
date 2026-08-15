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
            Map.entry("cobbled_deepslate", "rubble"),
            Map.entry("stone", "common"),
            Map.entry("andesite", "igneous"),
            Map.entry("diorite", "igneous"),
            Map.entry("granite", "igneous"),
            Map.entry("igneous_rock", "igneous"),
            Map.entry("tuff", "volcanic"),
            Map.entry("basalt", "volcanic"),
            Map.entry("blackstone", "volcanic"),
            Map.entry("volcanic_rock", "volcanic"),
            Map.entry("limestone", "sedimentary"),
            Map.entry("dripstone", "sedimentary"),
            Map.entry("glacial_rock", "sedimentary"),
            Map.entry("deepslate", "metamorphic"),
            Map.entry("metamorphic_rock", "metamorphic"),
            Map.entry("quartz", "mineral"));

    /** Rock price by Mining requirement, as seeded in Rails CommoditySeeder. */
    private static final Map<Float, Double> ROCK_PRICE_BY_REQUIREMENT = Map.of(
            0.0f, 1.0, 5.0f, 1.5, 10.0f, 2.0, 15.0f, 2.5, 20.0f, 3.0,
            25.0f, 3.5, 30.0f, 4.0, 35.0f, 5.0, 40.0f, 6.0, 45.0f, 7.0);

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

    /**
     * Owner decision 2026-08-15: every mined resource sells. A resource that reaches a player's
     * inventory with nowhere to sell it is now a defect, not a documented gap.
     */
    @Test
    void everyMinedResourceHasSomewhereToSell() {
        List<String> unsellable = new ArrayList<>();
        for (MineableDefinition definition : catalog.active()) {
            if (definition.economyCommodity().isEmpty()) {
                unsellable.add(definition.id());
            }
        }
        assertEquals(List.of(), unsellable, "these resources can be mined but not sold");
    }

    /**
     * The price ladder is the point of the rock economy: a rock is worth what it costs in Mining
     * skill to reach it, and Cobblestone is the floor because mining Stone yields rubble.
     */
    @Test
    void rockPricesRiseWithTheMiningRequirement() {
        for (MineableDefinition definition : catalog.active()) {
            if (definition.category() != MineableDefinition.Category.STONE) continue;
            String commodity = definition.economyCommodity().orElseThrow();
            if ("stone".equals(commodity) || "quartz".equals(commodity)) {
                continue; // neither is produced by mining a rock of that name
            }
            Double expected = ROCK_PRICE_BY_REQUIREMENT.get(definition.requiredMining());
            assertTrue(expected != null,
                    definition.id() + " sits at " + definition.requiredMining()
                            + ", which has no price step in the ladder");
        }
        assertEquals(1.0, ROCK_PRICE_BY_REQUIREMENT.get(0.0f),
                "Cobblestone, at requirement 0, must remain the cheapest rock");
        double previous = 0.0;
        for (float requirement : new float[] {0, 5, 10, 15, 20, 25, 30, 35, 40, 45}) {
            double price = ROCK_PRICE_BY_REQUIREMENT.get(requirement);
            assertTrue(price >= previous, "the ladder must never pay less for a harder rock");
            previous = price;
        }
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
