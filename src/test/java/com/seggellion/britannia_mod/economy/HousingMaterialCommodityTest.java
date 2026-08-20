package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.economy.crafting.GradedStoneIngredient;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryParser;
import com.seggellion.britannia_mod.service.EconomicNpcRegistrySnapshot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every material an Architect house is built from, and whether a player could ever supply it.
 *
 * <h2>What this is for</h2>
 * Rails counts a deed's cost from the shipped structure export, so the bill of materials is not a
 * design opinion — it is arithmetic over the blocks in the building. Thirteen commodities come out
 * of it. A commodity is only really supplied when three separate things are true at once: a player
 * can obtain an item, the mod can tell the economy what that item is, and a trader who actually
 * exists in the world accepts the identity. Two out of three is a house nobody can build.
 *
 * <p>The classifier half is checked here, against the real serializer's mapping table, and the
 * trader half against Rails' own policy fixture rather than against a belief about it. The obtain
 * half is world behaviour and lives in the deposit GameTests.
 */
class HousingMaterialCommodityTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final Path FIXTURE =
            Path.of("src/test/resources/wire_contract/trader_policy_parity.json");

    /**
     * The thirteen, counted from the census by Rails' {@code StructureBillOfMaterials} on
     * 2026-08-20 at {@code b7bbf97}, with how many of the ten deeds consume each.
     */
    private static final Map<String, Integer> DEED_COMMODITIES = new LinkedHashMap<>();


    static {
        DEED_COMMODITIES.put("wood|logs|oak", 10);
        DEED_COMMODITIES.put("wood|logs|spruce", 6);
        DEED_COMMODITIES.put("wood|logs|birch", 3);
        DEED_COMMODITIES.put("wood|logs|dark_oak", 1);
        DEED_COMMODITIES.put("stone|common|stone", 4);
        DEED_COMMODITIES.put("stone|rubble|cobblestone", 1);
        DEED_COMMODITIES.put("stone|igneous|diorite", 7);
        DEED_COMMODITIES.put("stone|sedimentary|sandstone", 1);
        DEED_COMMODITIES.put("stone|processed|plaster", 4);
        DEED_COMMODITIES.put("glass|raw|raw_glass", 9);
        DEED_COMMODITIES.put("textile|raw|thatch", 2);
        DEED_COMMODITIES.put("metal|ingots|iron", 2);
        DEED_COMMODITIES.put("clay|raw|clay", 2);
    }

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Constructing an Item after bootstrap touches a frozen registry; the repo's own
        // WallJunctionAlignmentTest unfreezes for the same reason.
        net.neoforged.neoforge.registries.GameData.unfreezeData();
    }

    /* ------------------------------------------------------------------ */
    /*  The four materials this pass added                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void sandstoneClassifiesAsSedimentaryStone() {
        assertEquals(Optional.of("sandstone"), CommodityMappings.stoneCommodityKey("Sandstone"),
                "the mined drop name must resolve to the seeded commodity");
        assertEquals(Optional.of("sedimentary"), CommodityMappings.stoneCommoditySubcategory("sandstone"));
        assertEquals("stone|sedimentary|sandstone",
                CommodityMappings.stoneCommodityIdentityKey("sandstone"));
    }

    @Test
    void plasterClassifiesAsProcessedStone() {
        assertEquals("stone|processed|plaster", normalizedKeyOf("britannia_mod:plaster"));
    }

    @Test
    void rawGlassClassifiesAsRawGlass() {
        assertEquals("glass|raw|raw_glass", normalizedKeyOf("britannia_mod:raw_glass"));
    }

    @Test
    void thatchClassifiesAsRawTextileAndComesFromStraw() {
        assertEquals("textile|raw|thatch", normalizedKeyOf("britannia_mod:straw"));
    }

    /**
     * Silica is the feedstock, and says so.
     *
     * <p>It carries an honest identity — Rails does hold a {@code glass|raw|sand} row — but that
     * row is accepted only by the Glassblower, who has no entity and cannot be met. That is the
     * processing step, expressed as market policy rather than as a rule in the mod: a player who
     * wants coin fires the sand first.
     */
    @Test
    void silicaSandIsSandAndNotRawGlass() {
        assertEquals("glass|raw|sand", normalizedKeyOf("britannia_mod:silica_sand"));
        assertFalse("raw_glass".equals(mappingOf("britannia_mod:silica_sand").itemName()),
                "silica sand would be sellable as finished glass stock, deleting the kiln");
    }

    /* ------------------------------------------------------------------ */
    /*  Negatives                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    void wheatIsGrainAndNotThatch() {
        CommodityMapping wheat = CommodityMappings.forStack(new ItemStack(Items.WHEAT)).orElseThrow();
        assertEquals("grain", wheat.category(),
                "roofing straw and bread wheat must stay different commodities, or a city would "
                        + "feed its population on thatch");
        assertEquals("grain|whole|wheat", wheat.normalizedKey());
    }

    @Test
    void ordinarySandAndGlassBlocksAreNotCommodities() {
        for (Item vanilla : List.of(Items.SAND, Items.GLASS, Items.GLASS_PANE, Items.SANDSTONE)) {
            assertTrue(CommodityMappings.forStack(new ItemStack(vanilla)).isEmpty(),
                    vanilla + " is sellable, so world generation is economic supply");
        }
    }

    /**
     * Finished construction blocks are not the materials they are made of.
     *
     * <p>Every plaster wall, window and thatch roof in the registry, checked as a set rather than
     * by naming a handful — a new one added later cannot quietly become a sale item.
     */
    @Test
    void finishedConstructionBlocksAreNotBuildingMaterials() {
        List<String> leaks = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!"britannia_mod".equals(id.getNamespace())) continue;
            String path = id.getPath();
            boolean finished = path.contains("wall") || path.contains("window")
                    || path.contains("roof") || path.contains("foundation")
                    || path.contains("archway") || path.contains("brick");
            if (!finished) continue;
            CommodityMappings.forStack(new ItemStack(BuiltInRegistries.ITEM.get(id)))
                    .ifPresent(mapping -> leaks.add(id + " -> " + mapping.normalizedKey()));
        }
        assertTrue(leaks.isEmpty(),
                "finished construction blocks sell as raw material, so a house is a quarry: " + leaks);
    }

    /* ------------------------------------------------------------------ */
    /*  Plaster's feedstock                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Only limestone becomes plaster.
     *
     * <p>Every rock a player mines is the same item with a different stone type on it, so a recipe
     * written against the item alone would turn any cobblestone into plaster. The ingredient reads
     * the type, which is what makes "specific mineral in, building plaster out" true rather than
     * merely intended.
     */
    @Test
    void onlyLimestoneSatisfiesThePlasterIngredient() {
        GradedStoneIngredient limestoneOnly = new GradedStoneIngredient("limestone");
        assertTrue(limestoneOnly.test(gradedStone("Limestone")),
                "calcite's own drop name must satisfy the recipe it was quarried for");
        assertTrue(limestoneOnly.test(gradedStone("limestone")),
                "the normalised spelling must match too");

        for (String other : List.of("Stone", "Cobblestone", "Granite", "Sandstone", "Diorite")) {
            assertFalse(limestoneOnly.test(gradedStone(other)),
                    other + " can be smelted into plaster, so generic stone substitutes for it");
        }
        assertFalse(limestoneOnly.test(new ItemStack(Items.STONE)),
                "a plain stone block satisfies the plaster recipe");
        assertFalse(limestoneOnly.test(ItemStack.EMPTY));
    }

    @Test
    void thePlasterRecipeIsShippedAndNamesTheLimestoneIngredient() throws IOException {
        JsonObject recipe = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/recipe/plaster_from_limestone.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:smelting", recipe.get("type").getAsString(),
                "the ordinary furnace is the processing step; no new workstation was invented");
        JsonObject ingredient = recipe.getAsJsonObject("ingredient");
        assertEquals("britannia_mod:graded_stone", ingredient.get("type").getAsString());
        assertEquals("limestone", ingredient.get("stone_type").getAsString());
        assertEquals("britannia_mod:plaster", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt(),
                "one limestone, one plaster: the simplest explainable relation");
    }

    @Test
    void theGlassRecipeIsShippedAndConsumesSilicaNotSand() throws IOException {
        JsonObject recipe = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/recipe/raw_glass_from_silica_sand.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:smelting", recipe.get("type").getAsString());
        assertEquals("britannia_mod:silica_sand",
                recipe.getAsJsonObject("ingredient").get("item").getAsString(),
                "vanilla sand must not be a glassmaking feedstock");
        assertEquals("britannia_mod:raw_glass",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    /* ------------------------------------------------------------------ */
    /*  Reachability                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Twelve of the thirteen deed commodities have a buyer a player can actually walk up to.
     *
     * <p>The thirteenth is {@code metal|ingots|iron}, and it is a Rails policy gap rather than a
     * mod one: the mod mines iron, forges it, and describes the ingot as {@code metal|ingots|iron}
     * correctly, but the only trader accepting {@code metal|ingots|*} is the Salvager, whose key
     * list is copper, silver and gold. Nobody buys iron. The Castle wants sixteen and the Stone
     * Keep four.
     *
     * <p>Pinned as an exact set rather than tolerated, so this fails two ways that both matter:
     * if a NEW commodity loses its buyer, and if Rails grants iron a buyer and this note goes
     * stale. Rails is the policy authority and was not edited to make this pass.
     */
    @Test
    void everyDeedCommodityHasALiveBuyerExceptTheOneRailsHasNotGranted() throws IOException {
        Map<String, List<String>> buyers = buyersByCommodity(DEED_COMMODITIES.keySet());
        List<String> live = liveTraders();
        assertFalse(live.isEmpty(), "no trader in the registry has an entity at all");

        List<String> unbuyable = new ArrayList<>();
        for (String commodity : DEED_COMMODITIES.keySet()) {
            List<String> who = buyers.getOrDefault(commodity, List.of());
            if (who.stream().noneMatch(live::contains)) unbuyable.add(commodity);
        }
        assertEquals(List.of("metal|ingots|iron"), unbuyable,
                "the set of housing commodities with no reachable buyer changed; buyers were "
                        + buyers);
    }

    /** The four this pass added are all among the reachable ones. */
    @Test
    void theFourNewMaterialsAllHaveALiveBuyer() throws IOException {
        List<String> added = List.of("stone|sedimentary|sandstone", "stone|processed|plaster",
                "glass|raw|raw_glass", "textile|raw|thatch");
        Map<String, List<String>> buyers = buyersByCommodity(added);
        List<String> live = liveTraders();
        for (String commodity : added) {
            assertTrue(buyers.get(commodity).stream().anyMatch(live::contains),
                    commodity + " has no trader a player can meet: " + buyers.get(commodity));
        }
    }

    /** And the mason's raw-glass grant stays narrow: he buys the stock, never the sand. */
    @Test
    void theMasonBuysRawGlassButNotSilicaSand() throws IOException {
        Map<String, List<String>> buyers = buyersByCommodity(
                List.of("glass|raw|raw_glass", "glass|raw|sand"));
        assertTrue(buyers.get("glass|raw|raw_glass").contains("stone_trader"));
        assertFalse(buyers.getOrDefault("glass|raw|sand", List.of()).contains("stone_trader"),
                "the mason now buys unfired silica, which deletes the processing step");
        assertEquals(List.of("glass_trader"), buyers.getOrDefault("glass|raw|sand", List.of()),
                "silica's only buyer must remain the dormant Glassblower");
    }

    /** Thatch goes to the lumberjack, who exists; the tailor who also accepts it does not. */
    @Test
    void thatchHasTheWoodTraderAsItsLiveBuyer() throws IOException {
        List<String> buyers = buyersByCommodity(List.of("textile|raw|thatch"))
                .get("textile|raw|thatch");
        assertTrue(buyers.contains("wood_trader"), buyers.toString());
    }

    /* ------------------------------------------------------------------ */

    /**
     * Traders a player can actually meet, read from Rails' own registry rather than remembered.
     *
     * <p>A type with no {@code minecraft_entity_type_key} is catalogued and prices things, but no
     * entity of it is ever registered or spawned, so a player cannot walk up to one. That is the
     * whole difference between a commodity being priced and a commodity being sellable.
     */
    private static List<String> liveTraders() throws IOException {
        List<String> live = new ArrayList<>();
        snapshot().economicNpcTypes().forEach((key, definition) -> {
            if (definition.minecraftEntityTypeKey() != null) live.add(key);
        });
        return live;
    }

    /**
     * Which traders accept a commodity, answered by the mod's own policy over Rails' own registry.
     *
     * <p>Not read off the verdict rows: those cover the 28 probes Rails chose, and the deeds
     * consume thirteen commodities of which only some are probed. The registry section IS the
     * policy, though, and {@code TraderPolicyWireParityTest} proves this parser and this filter
     * return Rails' answer in all 28 probed cells — so running the remaining commodities through
     * the same pair is the honest extension rather than a second opinion.
     */
    private static Map<String, List<String>> buyersByCommodity(Iterable<String> commodities)
            throws IOException {
        EconomicNpcRegistrySnapshot snapshot = snapshot();
        Map<String, List<String>> buyers = new LinkedHashMap<>();
        for (String commodity : commodities) {
            String[] parts = commodity.split("[|]", -1);
            JsonObject described = new JsonObject();
            described.addProperty("category", parts[0]);
            described.addProperty("subcategory", parts[1]);
            described.addProperty("item_name", parts[2]);

            List<String> who = new ArrayList<>();
            snapshot.economicNpcTypes().forEach((key, definition) -> {
                if (TraderCommodityFilter.resolve(definition, key).accepts(described)) who.add(key);
            });
            buyers.put(commodity, who);
        }
        return buyers;
    }

    private static EconomicNpcRegistrySnapshot snapshot() throws IOException {
        JsonObject fixture = JsonParser.parseString(
                Files.readString(PROJECT.resolve(FIXTURE), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject root = new JsonObject();
        root.add(EconomicNpcRegistryParser.ROOT_KEY, fixture.getAsJsonObject("registry"));
        return EconomicNpcRegistryParser.parseBootstrapRoot(root);
    }

    private static String text(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static ItemStack gradedStone(String stoneType) {
        GradeStoneItem item = new GradeStoneItem(new Item.Properties());
        ItemStack stack = new ItemStack(item);
        item.setStoneType(stack, stoneType);
        return stack;
    }

    private static CommodityMapping mappingOf(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR) {
            // The item is registered by the mod, so a bare unit test sees only the mapping table.
            return CommodityMappings.forId(itemId).orElseThrow(
                    () -> new AssertionError(itemId + " carries no commodity mapping"));
        }
        return CommodityMappings.forStack(new ItemStack(item)).orElseThrow(
                () -> new AssertionError(itemId + " carries no commodity mapping"));
    }

    private static String normalizedKeyOf(String itemId) {
        return mappingOf(itemId).normalizedKey();
    }
}
