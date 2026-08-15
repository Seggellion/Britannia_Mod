package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.economy.CommodityMappings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Milestone 5: Silver is a complete resource — one economic identity, a resolvable refining chain,
 * the approved 55.0 tier, and the assets and localization a real item needs.
 */
class SilverVerticalSliceTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    @Test
    void minedSilverResolvesToTheSeededSilverCommodity() {
        assertEquals(Optional.of("silver"), CommodityMappings.oreCommodityKey("Silver ore"));
        assertEquals(Optional.of("silver"), CommodityMappings.oreCommodityKey("silver"));
        assertEquals(Optional.of("silver"), CommodityMappings.oreCommodityKey("silver_ore"));
    }

    /**
     * High-Purity Silver was retired by owner decision (there is exactly one Silver metal/ore), but
     * players may still hold mined stacks from before. They keep selling as ordinary Silver rather
     * than becoming unsellable, which is also what "one Silver identity" means economically.
     */
    @Test
    void legacyHighPuritySilverStacksStillSellAsOrdinarySilver() {
        assertEquals(Optional.of("silver"),
                CommodityMappings.oreCommodityKey("High-Purity Silver ore"));
    }

    @Test
    void everyApprovedMetalResolvesToItsSeededOreRawIdentity() {
        record Ore(String dropName, String commodity) {}
        for (Ore ore : List.of(
                new Ore("Iron ore", "iron"),
                new Ore("Silver ore", "silver"),
                new Ore("Tin ore", "tin"),
                new Ore("Shadow Iron ore", "shadow_iron"),
                new Ore("Copper ore", "copper"),
                new Ore("Gold ore", "gold"),
                new Ore("Agapite ore", "agapite"),
                new Ore("Verite ore", "verite"),
                new Ore("Valorite ore", "valorite"))) {
            assertEquals(Optional.of(ore.commodity()), CommodityMappings.oreCommodityKey(ore.dropName()),
                    ore.dropName() + " must resolve to the seeded ore/raw identity");
        }
    }

    @Test
    void unknownOreNamesDoNotInventCommodities() {
        assertTrue(CommodityMappings.oreCommodityKey("Mithril ore").isEmpty());
        assertTrue(CommodityMappings.oreCommodityKey("").isEmpty());
        assertTrue(CommodityMappings.oreCommodityKey("Cobblestone").isEmpty());
    }

    /**
     * The name the refining chain keys on. {@code SmallForgeBlock} strips " ore" and asks
     * {@code UOMetalToolMaterial} for the metal; that lookup itself needs live registries, so the
     * resolution is asserted in {@code SilverMiningGameTests.silverRefinesIntoTheExistingIngot}.
     */
    @Test
    void minedSilverStripsToTheMetalNameTheForgeLooksUp() {
        assertEquals("silver", "Silver ore".toLowerCase(Locale.ROOT).replace(" ore", ""));
    }

    @Test
    void silverIsCataloguedAtTheApprovedTierWithItsEconomyIdentity() throws Exception {
        MineableCatalog catalog = MineableCatalog.parse(Files.newBufferedReader(
                PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json")));
        MineableDefinition silver = catalog.byId("silver").orElseThrow();

        assertEquals(55.0f, silver.requiredMining(), 0.0f);
        assertEquals(MineableDefinition.Category.ORE, silver.category());
        assertEquals(MineableDefinition.Status.ACTIVE, silver.status());
        assertEquals(List.of("britannia_mod:silver_ore"), silver.blockIds());
        assertEquals("Silver ore", silver.dropName(), "the drop name the refining chain keys on");
        assertEquals(Optional.of("silver"), silver.economyCommodity());
        assertTrue(silver.restorable());
        assertEquals(Optional.of("silver"), CommodityMappings.oreCommodityKey(silver.dropName()),
                "the catalogue drop name and the economy mapping must agree");
    }

    /** Milestone 6: every registered ore/rock block must render — no missing-model blocks. */
    @Test
    void everyCatalogueBlockOwnedByThisModShipsItsAssets() throws Exception {
        MineableCatalog catalog = MineableCatalog.parse(Files.newBufferedReader(
                PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json")));
        for (MineableDefinition definition : catalog.all()) {
            for (String blockId : definition.blockIds()) {
                if (!blockId.startsWith("britannia_mod:")) continue;
                String name = blockId.substring("britannia_mod:".length());
                for (String asset : List.of(
                        "blockstates/" + name + ".json",
                        "models/block/" + name + ".json",
                        "models/item/" + name + ".json")) {
                    assertTrue(Files.exists(ASSETS.resolve(asset)),
                            definition.id() + " is registered but missing " + asset);
                }
                // Milestone 10: a model that names a texture nobody shipped renders as the
                // missing-texture checkerboard, which no asset-existence check alone would catch.
                String model = Files.readString(ASSETS.resolve("models/block/" + name + ".json"));
                java.util.regex.Matcher textures = java.util.regex.Pattern
                        .compile("\"(?:all|texture|side|end|up|down|north|south|east|west)\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(model);
                while (textures.find()) {
                    String reference = textures.group(1);
                    if (!reference.startsWith("britannia_mod:")) {
                        continue; // vanilla textures ship with the game
                    }
                    String texture = reference.substring("britannia_mod:".length());
                    assertTrue(Files.exists(ASSETS.resolve("textures/" + texture + ".png")),
                            definition.id() + "'s model references missing texture " + reference);
                }
            }
        }
    }

    /** Milestone 6: the refined metals a miner produces all need display names. */
    @Test
    void everyCustomIngotIsLocalized() throws Exception {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        for (String metal : List.of("silver", "tin", "copper", "shadow_iron",
                "agapite", "verite", "valorite")) {
            assertTrue(lang.contains("\"item.britannia_mod." + metal + "_ingot\""),
                    "missing lang for " + metal + " ingot");
        }
        assertTrue(lang.contains("\"item.britannia_mod.grade_stone_item\""),
                "the mined stone item needs a display name too");
    }

    @Test
    void silverShipsItsBlockAssetsAndLocalization() throws Exception {
        for (String asset : List.of(
                "blockstates/silver_ore.json",
                "models/block/silver_ore.json",
                "models/item/silver_ore.json",
                "textures/block/silver_ore.png",
                "models/item/silver_ingot.json")) {
            assertTrue(Files.exists(ASSETS.resolve(asset)), "missing Silver asset " + asset);
        }

        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        assertTrue(lang.contains("\"block.britannia_mod.silver_ore\""), "silver ore block name");
        assertTrue(lang.contains("\"item.britannia_mod.silver_ingot\""), "silver ingot name");
        assertTrue(lang.contains("\"item.britannia_mod.purity_ore_item\""),
                "the mined-ore item Silver drops needs a display name");
    }

    /** Every custom metal now has its own art rather than borrowing Mojang's iron ingot. */
    @Test
    void everyCustomIngotHasItsOwnTexture() throws Exception {
        for (String metal : List.of("silver", "tin", "copper", "bronze", "shadow_iron",
                "agapite", "verite", "valorite")) {
            String model = Files.readString(ASSETS.resolve("models/item/" + metal + "_ingot.json"));
            assertTrue(model.contains("britannia_mod:item/" + metal + "_ingot"),
                    metal + " ingot must use its own texture");
            assertFalse(model.contains("minecraft:item/iron_ingot"),
                    metal + " ingot must no longer borrow the vanilla iron texture");
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + metal + "_ingot.png")),
                    metal + " ingot texture is missing");
        }
    }
}
