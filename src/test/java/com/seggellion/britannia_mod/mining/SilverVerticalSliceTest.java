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

    /** The design forbids a second Silver identity; purity carries the premium instead. */
    @Test
    void highPuritySilverReusesTheSilverIdentityRatherThanDuplicatingIt() {
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
        assertEquals("high-purity silver",
                "High-Purity Silver ore".toLowerCase(Locale.ROOT).replace(" ore", ""),
                "the premium node strips to a name no metal is registered under -- known gap");
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

    /** Documented placeholder: the ingot art is still the vanilla iron texture (M6 asset work). */
    @Test
    void silverIngotStillUsesThePlaceholderTextureSharedByEveryCustomIngot() throws Exception {
        String model = Files.readString(ASSETS.resolve("models/item/silver_ingot.json"));
        assertTrue(model.contains("minecraft:item/iron_ingot"),
                "if this fails, dedicated Silver ingot art landed and the gap analysis must be updated");
        assertFalse(Files.exists(ASSETS.resolve("textures/item/silver_ingot.png")),
                "no dedicated Silver ingot texture is expected yet");
    }
}
