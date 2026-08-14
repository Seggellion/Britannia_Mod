package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Milestone 11 guard for the complete new-assets registration and data surface. */
class NewAssetsCrossSystemAuditTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final Path DATA = RESOURCES.resolve("data/britannia_mod");

    private static final List<String> BLOCK_IDS = List.of(
            "globe", "fern", "hedge_bush", "pool_of_blood", "folded_cloth", "bolt_of_cloth",
            "pewter_mug", "kettle", "plates_and_silverware",
            "merchant_cart_red", "merchant_cart_purple", "merchant_cart_blue",
            "merchant_cart_green", "merchant_cart_yellow", "merchant_cart_white",
            "fountain", "scarecrow", "dress_form", "loom", "spinning_wheel",
            "display_case", "small_crate", "medium_crate", "large_crate",
            "water_well", "ladder", "training_dummy", "moongate_block",
            "custom_sandstone_brick", "ornate_sandstone_wall", "regular_sandstone_wall",
            "sandstone_block_wall", "ornate_sandstone_window", "sandstone_window",
            "sandstone_post", "ornate_sandstone_post", "sandstone_battlement",
            "sandstone_column");

    private static final Set<String> AXE_BLOCKS = Set.of(
            "globe", "merchant_cart_red", "merchant_cart_purple", "merchant_cart_blue",
            "merchant_cart_green", "merchant_cart_yellow", "merchant_cart_white",
            "scarecrow", "dress_form", "loom", "spinning_wheel", "display_case",
            "small_crate", "medium_crate", "large_crate", "ladder", "training_dummy");

    private static final Set<String> PICKAXE_BLOCKS = Set.of(
            "pewter_mug", "kettle", "plates_and_silverware", "fountain", "water_well",
            "custom_sandstone_brick", "ornate_sandstone_wall", "regular_sandstone_wall",
            "sandstone_block_wall", "ornate_sandstone_window", "sandstone_window",
            "sandstone_post", "ornate_sandstone_post", "sandstone_battlement",
            "sandstone_column");

    @Test
    void everyRequestedBlockHasOneRegistrationAndCompletePlayerFacingData() throws Exception {
        String blocks = javaSource("registry/BlockRegistry.java");
        String items = javaSource("registry/ItemRegistry.java");
        String creative = javaSource("registry/CreativeTabRegistry.java");
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));

        for (String id : BLOCK_IDS) {
            assertEquals(1, registrations(blocks, "BLOCKS", id), "block registration drift for " + id);
            assertEquals(1, registrations(items, "ITEMS", id), "block-item registration drift for " + id);
            assertEquals(1, occurrences(creative, "ItemRegistry." + id.toUpperCase() + "_ITEM.get()"),
                    "creative-tab entry drift for " + id);
            assertJson("assets/britannia_mod/blockstates/" + id + ".json");
            assertJson("assets/britannia_mod/models/item/" + id + ".json");
            assertTrue(language.has("block.britannia_mod." + id), "missing localization for " + id);
            if (!id.equals("moongate_block")) {
                assertJson("data/britannia_mod/loot_table/blocks/" + id + ".json");
            }
        }
        assertTrue(blocks.contains("MOONGATE_BLOCK = BLOCKS.register"));
        assertTrue(javaSource("block/MoongateBlock.java").contains(".noLootTable()"),
                "unbreakable moongate must remain explicitly drop-free");
    }

    @Test
    void itemEntityAndPlaceholderSurfacesAreComplete() throws Exception {
        String items = javaSource("registry/ItemRegistry.java");
        String entities = javaSource("registry/EntityRegistry.java");
        String creative = javaSource("registry/CreativeTabRegistry.java");
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));

        for (String id : List.of("ball_of_yarn", "spool_of_thread")) {
            assertEquals(1, registrations(items, "ITEMS", id));
            assertEquals(1, occurrences(creative, "ItemRegistry." + id.toUpperCase() + ".get()"));
            assertJson("assets/britannia_mod/models/item/" + id + ".json");
            assertTrue(language.has("item.britannia_mod." + id));
        }
        assertEquals(1, registrations(entities, "ENTITIES", "ibis"));
        assertEquals(1, registrations(items, "ITEMS", "ibis_spawn_egg"));
        assertEquals(1, occurrences(creative, "ItemRegistry.IBIS_SPAWN_EGG.get()"));
        assertJson("assets/britannia_mod/models/item/ibis_spawn_egg.json");
        assertJson("assets/britannia_mod/geo/ibis.geo.json");
        assertJson("assets/britannia_mod/animations/ibis.animation.json");
        assertJson("data/britannia_mod/loot_table/entities/ibis.json");
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/ibis_white.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/ibis_scarlet.png")));
        assertTrue(language.get("item.britannia_mod.ibis_spawn_egg").getAsString().contains("Temporary Art"));
        assertTrue(language.get("block.britannia_mod.moongate_block").getAsString().contains("Temporary Art"));
        assertTrue(language.get("item.britannia_mod.ball_of_yarn").getAsString().contains("Placeholder"));
        assertTrue(language.get("item.britannia_mod.spool_of_thread").getAsString().contains("Placeholder"));
    }

    @Test
    void toolsAndTransparentRenderLayersMatchMaterials() throws Exception {
        JsonObject axe = json(RESOURCES.resolve("data/minecraft/tags/block/mineable/axe.json"));
        JsonObject pickaxe = json(RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));
        Set<String> axeValues = values(axe);
        Set<String> pickaxeValues = values(pickaxe);
        for (String id : AXE_BLOCKS) assertTrue(axeValues.contains("britannia_mod:" + id), id + " lacks axe tag");
        for (String id : PICKAXE_BLOCKS) {
            assertTrue(pickaxeValues.contains("britannia_mod:" + id), id + " lacks pickaxe tag");
        }

        String client = javaSource("ClientModSetup.java");
        for (String holder : List.of(
                "GLOBE", "FERN", "HEDGE_BUSH", "POOL_OF_BLOOD", "FOLDED_CLOTH",
                "MERCHANT_CART_RED", "MERCHANT_CART_PURPLE", "MERCHANT_CART_BLUE",
                "MERCHANT_CART_GREEN", "MERCHANT_CART_YELLOW", "MERCHANT_CART_WHITE",
                "SCARECROW", "DRESS_FORM", "LOOM", "SMALL_CRATE", "MEDIUM_CRATE",
                "LARGE_CRATE", "WATER_WELL", "LADDER", "DISPLAY_CASE")) {
            assertTrue(client.contains("setRenderLayer(BlockRegistry." + holder + ".get(), RenderType.cutout())"),
                    holder + " lacks cutout rendering");
        }
        assertTrue(client.contains("setRenderLayer(BlockRegistry.FOUNTAIN.get(), RenderType.translucent())"));
        assertTrue(client.contains("setRenderLayer(BlockRegistry.MOONGATE_BLOCK.get(), RenderType.translucent())"));
    }

    @Test
    void functionalSystemsRemainIntegratedAndSilkIsExplicitlyDeferred() throws Exception {
        String ibisPopulation = javaSource("spawner/JhelomIbisPopulation.java");
        String citySpawner = javaSource("spawner/CitySpawner.java");
        assertTrue(ibisPopulation.contains("MAX_POPULATION = 15"));
        assertTrue(ibisPopulation.contains("CITY_NAME = \"Jhelom\""));
        assertTrue(citySpawner.contains("JhelomIbisPopulation.tick(level)"));

        assertTrue(javaSource("event/TrainingDummyEventHandler.java").contains("TrainingDummyService.attempt"));
        assertTrue(javaSource("training/TrainingDummyService.java").contains("WEAPON_SKILL_CAP = 25.0F"));
        assertTrue(javaSource("block/CrateBlock.java").contains("CrateBlockEntity"));
        String well = javaSource("util/WaterSourceInteraction.java");
        assertTrue(well.contains("Items.BUCKET"));
        assertTrue(well.contains("WateringCanItem"));
        assertTrue(well.contains("PitcherItem"));
        assertTrue(javaSource("item/AdventureLadderItem.java").contains("GameType.ADVENTURE"));
        String textiles = javaSource("textile/TextileProcessing.java");
        assertTrue(textiles.contains("ItemTags.WOOL"));
        assertTrue(textiles.contains("ItemRegistry.COTTON"));
        assertTrue(textiles.contains("ItemRegistry.FLAX"));
        assertTrue(textiles.contains("WEAVE_INPUT_COUNT = 5"));
        assertFalse(textiles.contains("SPIDERS_SILK"));
        assertTrue(javaSource("block/DisplayCaseBlock.java").contains("ConnectionForm.CORNER"));
        assertTrue(javaSource("block/MoongateBlock.java").contains("MoongateTeleportationHandler.teleportPlayer"));

        String manifest = Files.readString(PROJECT.resolve("docs/new-assets/ASSET_IMPORT_MANIFEST.md"));
        assertFalse(manifest.contains("Import status: `FOUND`"));
        assertFalse(manifest.contains("Import status: `PARTIAL`"));
        assertEquals(1, occurrences(manifest, "Import status: `MISSING`"));
        assertTrue(manifest.contains("Silk is a distinct future textile material"));
        assertTrue(manifest.contains("must not alias `britannia_mod:spiders_silk`"));
    }

    private static int registrations(String source, String registry, String id) {
        return (int) Pattern.compile(Pattern.quote(registry) + "\\.register\\(\\s*\"" + Pattern.quote(id) + "\"")
                .matcher(source).results().count();
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static String javaSource(String relative) throws Exception {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    private static void assertJson(String relative) throws Exception {
        json(RESOURCES.resolve(relative));
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Set<String> values(JsonObject tag) {
        return tag.getAsJsonArray("values").asList().stream()
                .map(value -> value.getAsString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
