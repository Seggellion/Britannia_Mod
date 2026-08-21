package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.economy.crafting.GradedStoneIngredient;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.WeightedWoodType;
import com.seggellion.britannia_mod.mining.MineableCatalog;
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
     * The thirteen, and how a player gets each one.
     *
     * <p>Re-derived from the census by Rails' {@code StructureBillOfMaterials} on 2026-08-20 at
     * {@code 0206efd}: ten deeds, thirteen distinct commodities, unchanged from {@code b7bbf97}.
     * The count beside each is how many of the ten consume it.
     *
     * <p>The {@link Supply} beside each is the mechanism, not a second catalogue. MINED rows are
     * checked against the shipped Mining catalogue, ITEM rows against the shipped mapping table,
     * and WOOD rows against the wood-type registry. What is written here is only which of those a
     * commodity belongs to and what it is made from; every claim rests on the mod's own data.
     */
    private enum Supply {
        /** Felled with the two-handed axe; the species is a WeightedWoodType. */
        WOOD,
        /** Worked with the Britannia pickaxe; the row is in mineables.json. */
        MINED,
        /** A discrete item with a row in CommodityMappings, gathered rather than made. */
        ITEM,
        /** Made from something else by a shipped recipe. */
        PROCESSED,
        /** A discrete item classified by a branch of describeSaleItem rather than by the table. */
        ITEM_BY_BRANCH
    }

    /** Where a processing recipe's input comes from, in terms this file can actually check. */
    private enum Origin {
        /** An ACTIVE Mining catalogue row yields it, named by its economy commodity. */
        MINED,
        /** A managed deposit yields it, named by the deposit's registry id. */
        DEPOSIT
    }

    /**
     * A conversion: which shipped recipe makes this material, and what it is made from.
     *
     * <p>{@code feedstock} is checked two ways — that the recipe really names it, and that a
     * player can really get it. A recipe whose input nobody can obtain is not a supply path.
     */
    private record Processing(String recipeFile, Origin origin, String feedstock) {
    }

    private record Material(String commodity, int deeds, Supply supply, String source,
                            Processing processing) {
        Material(String commodity, int deeds, Supply supply, String source) {
            this(commodity, deeds, supply, source, null);
        }
    }

    private static final List<Material> DEED_MATERIALS = List.of(
            new Material("wood|logs|oak", 10, Supply.WOOD, "oak"),
            new Material("wood|logs|spruce", 6, Supply.WOOD, "spruce"),
            new Material("wood|logs|birch", 3, Supply.WOOD, "birch"),
            new Material("wood|logs|dark_oak", 1, Supply.WOOD, "dark_oak"),
            new Material("stone|rubble|cobblestone", 1, Supply.MINED, "cobblestone"),
            new Material("stone|igneous|diorite", 7, Supply.MINED, "diorite"),
            new Material("stone|sedimentary|sandstone", 1, Supply.MINED, "sandstone"),
            // Mining stone yields rubble, deliberately and unchanged. Common stone is what a
            // player makes of that rubble, the way vanilla has always let them.
            new Material("stone|common|stone", 4, Supply.PROCESSED, "graded:Stone",
                    new Processing("stone_from_cobblestone.json", Origin.MINED, "cobblestone")),
            new Material("stone|processed|plaster", 4, Supply.PROCESSED, "britannia_mod:plaster",
                    new Processing("plaster_from_limestone.json", Origin.MINED, "limestone")),
            new Material("glass|raw|raw_glass", 9, Supply.PROCESSED, "britannia_mod:raw_glass",
                    new Processing("raw_glass_from_silica_sand.json", Origin.DEPOSIT,
                            "britannia_mod:silica_sand_deposit")),
            new Material("textile|raw|thatch", 2, Supply.ITEM, "britannia_mod:straw"),
            new Material("clay|raw|clay", 2, Supply.ITEM, "minecraft:clay_ball"),
            // The ingot path lives in describeSaleItem rather than in the mapping table, and that
            // method cannot run without a loaded mod. HousingMaterialSupplyGameTests drives the
            // real serializer over an iron ingot; this row asserts the buyer half.
            new Material("metal|ingots|iron", 2, Supply.ITEM_BY_BRANCH, "minecraft:iron_ingot"));

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
     * The whole of it, in one place: every material a house is made of can reach a city.
     *
     * <p>Three things have to be true at once for a commodity to be supplied, and each of the
     * three has failed on its own during this programme. There has to be a mechanism a player can
     * work; the mod has to describe the resulting item with the exact identity Rails prices (clay
     * had none at all until the supply pass); and a trader who exists in the world has to accept
     * it (iron was correctly classified and unbuyable until Rails granted it to the Salvager at
     * {@code 0206efd}).
     *
     * <p>This is the regression guard for all three. A future house that introduces a fourteenth
     * commodity fails here naming the key it added, rather than being discovered when a player
     * cannot build it.
     */
    @Test
    void everyMaterialTheDeedsConsumeCanReachACity() throws IOException {
        Map<String, List<String>> buyers = buyersByCommodity(
                DEED_MATERIALS.stream().map(Material::commodity).toList());
        List<String> live = liveTraders();
        assertFalse(live.isEmpty(), "no trader in the registry has an entity at all");

        List<String> broken = new ArrayList<>();
        for (Material material : DEED_MATERIALS) {
            String why = supplyDefect(material);
            if (why != null) broken.add(material.commodity() + ": " + why);

            List<String> who = buyers.getOrDefault(material.commodity(), List.of());
            if (who.stream().noneMatch(live::contains)) {
                broken.add(material.commodity() + ": no trader a player can meet accepts it"
                        + (who.isEmpty() ? "" : " (dormant only: " + who + ")"));
            }
        }
        assertTrue(broken.isEmpty(),
                () -> "housing materials a player cannot supply:" + System.lineSeparator() + "  "
                        + String.join(System.lineSeparator() + "  ", broken));
    }

    /** The set is thirteen, and it is Rails' set rather than a convenient subset. */
    @Test
    void theBillOfMaterialsIsStillThirteenCommodities() {
        assertEquals(13, DEED_MATERIALS.size());
        assertEquals(13, DEED_MATERIALS.stream().map(Material::commodity).distinct().count());
        assertEquals(10, DEED_MATERIALS.stream().mapToInt(Material::deeds).max().orElseThrow(),
                "oak is in all ten deeds; a lower maximum means the census was re-counted");
    }

    /**
     * Why a material could not be supplied, or null when it can.
     *
     * <p>Every branch reads the mod's own shipped data. Nothing here restates what the mapping
     * table or the Mining catalogue says; it asks them.
     */
    private static String supplyDefect(Material material) {
        String[] parts = material.commodity().split("[|]", -1);
        return switch (material.supply()) {
            case WOOD -> WeightedWoodType.byId(material.source()).isPresent()
                    ? null : "no WeightedWoodType is registered for " + material.source();
            case MINED -> minedDefect(material.source(), parts[1]);
            case ITEM -> itemDefect(material.source(), material.commodity());
            case PROCESSED -> processedDefect(material);
            // The classifier branch is proven in-world; here only the identity shape is checked.
            case ITEM_BY_BRANCH -> parts[0].isBlank() ? "malformed commodity key" : null;
        };
    }

    /** An ACTIVE catalogue row must yield this commodity, and post it in the right family. */
    private static String minedDefect(String commodity, String subcategory) {
        boolean catalogued = catalog().active().stream()
                .anyMatch(definition -> definition.economyCommodity()
                        .map(commodity::equals).orElse(false));
        if (!catalogued) {
            return "no ACTIVE Mining definition yields " + commodity;
        }
        if (!Optional.of(subcategory).equals(CommodityMappings.stoneCommoditySubcategory(commodity))) {
            return commodity + " posts under "
                    + CommodityMappings.stoneCommoditySubcategory(commodity).orElse("nothing")
                    + " rather than " + subcategory;
        }
        return null;
    }

    /**
     * A made material needs a shipped recipe, a reachable input, and the right identity out.
     *
     * <p>All three are read out of the recipe JSON the game actually loads. The one thing not
     * proven here is the binding between a managed deposit and the item it yields — that needs a
     * world, and {@code HousingMaterialSupplyGameTests} does it.
     */
    private static String processedDefect(Material material) {
        Processing processing = material.processing();
        Path file = PROJECT.resolve("src/main/resources/data/britannia_mod/recipe")
                .resolve(processing.recipeFile());
        if (!Files.exists(file)) return "no shipped recipe at " + processing.recipeFile();

        JsonObject recipe;
        try {
            recipe = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException unreadable) {
            return processing.recipeFile() + " could not be read";
        }
        if (!text(recipe, "type").endsWith("smelting")) {
            return processing.recipeFile() + " is not a smelting recipe";
        }

        String inputDefect = recipeConsumes(recipe, processing.feedstock(), processing.origin());
        if (inputDefect != null) return inputDefect;

        String originDefect = originDefect(processing);
        if (originDefect != null) return originDefect;

        return producesIdentity(recipe, material);
    }

    /** The recipe's ingredient has to be the feedstock the row claims. */
    private static String recipeConsumes(JsonObject recipe, String feedstock, Origin origin) {
        JsonObject ingredient = recipe.getAsJsonObject("ingredient");
        String named = origin == Origin.MINED
                ? text(ingredient, "stone_type")
                : text(ingredient, "item");
        if (origin == Origin.MINED) {
            if (named == null) return "the recipe does not name a quarried stone as its input";
            if (!feedstock.equalsIgnoreCase(named)) {
                return "the recipe consumes " + named + " rather than " + feedstock;
            }
        } else if (named == null) {
            return "the recipe names no item input";
        }
        return null;
    }

    /** And that feedstock has to be something a player can get. */
    private static String originDefect(Processing processing) {
        return switch (processing.origin()) {
            case MINED -> catalog().active().stream()
                    .anyMatch(definition -> definition.economyCommodity()
                            .map(processing.feedstock()::equals).orElse(false))
                    ? null
                    : "nothing mineable yields " + processing.feedstock();
            case DEPOSIT -> ManagedDeposits.all().stream()
                    .anyMatch(deposit -> deposit.id().equals(processing.feedstock()))
                    ? null
                    : "no managed deposit " + processing.feedstock() + " is registered";
        };
    }

    /**
     * What comes out has to carry the commodity the deeds ask for.
     *
     * <p>Two shapes, because the economy has two: a discrete item resolves through the mapping
     * table, and a quarried stone resolves through its stone type. A graded row names the type the
     * recipe stamps, so this reads the recipe's own result rather than trusting the row.
     */
    private static String producesIdentity(JsonObject recipe, Material material) {
        JsonObject result = recipe.getAsJsonObject("result");
        String id = text(result, "id");
        if (id == null) return "the recipe declares no result";

        if (material.source().startsWith("graded:")) {
            String stoneType = result.has("components")
                    ? text(result.getAsJsonObject("components")
                            .getAsJsonObject("minecraft:custom_data"), "StoneType")
                    : null;
            if (stoneType == null) return "the recipe result carries no StoneType";
            String[] parts = material.commodity().split("[|]", -1);
            if (!Optional.of(parts[2]).equals(CommodityMappings.stoneCommodityKey(stoneType))) {
                return "the result stamps " + stoneType + ", which is not " + parts[2];
            }
            if (!Optional.of(parts[1])
                    .equals(CommodityMappings.stoneCommoditySubcategory(parts[2]))) {
                return parts[2] + " does not post under " + parts[1];
            }
            return null;
        }

        if (!material.source().equals(id)) {
            return "the recipe produces " + id + " rather than " + material.source();
        }
        return itemDefect(id, material.commodity());
    }

    /** The mapping table must give this item exactly the identity Rails prices. */
    private static String itemDefect(String itemId, String commodity) {
        Optional<CommodityMapping> mapping = CommodityMappings.forId(itemId);
        if (mapping.isEmpty()) return itemId + " carries no commodity mapping";
        if (!commodity.equals(mapping.get().normalizedKey())) {
            return itemId + " classifies as " + mapping.get().normalizedKey();
        }
        return null;
    }

    private static MineableCatalog catalog() {
        try {
            return MineableCatalog.parse(Files.newBufferedReader(PROJECT.resolve(
                    "src/main/resources/data/britannia_mod/mining/mineables.json")));
        } catch (IOException unreadable) {
            throw new AssertionError("the shipped Mining catalogue could not be read", unreadable);
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
