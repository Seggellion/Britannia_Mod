package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the standalone flagstone building block to the flagstone texture set.
 *
 * <p>Its variation count, its blockstate and its models all have to agree: the Java property
 * range decides how far the decorator tool cycles, so a texture added in one place and missed
 * in another would leave a state with no model. These tests read both sides and compare them.
 */
class FlagstoneBlockContractTest {

    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = ROOT.resolve("src/main/resources/data");

    private static final Set<String> FLAGSTONE_TEXTURES = new LinkedHashSet<>(List.of(
            "britannia_mod:block/structure/flagstone_01",
            "britannia_mod:block/structure/flagstone_02"));

    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    @Test
    void blockstateCoversEveryFacingAndVariationCombination() throws IOException {
        JsonObject variants = blockstate();
        Set<String> expected = new LinkedHashSet<>();
        for (int variation = 0; variation < FLAGSTONE_TEXTURES.size(); variation++) {
            for (String facing : FACINGS) {
                expected.add("facing=" + facing + ",variation=" + variation);
            }
        }
        assertEquals(expected, variants.keySet(),
                "every state the block can hold needs exactly one model");
    }

    @Test
    void variationRangeInJavaMatchesTheModelsOnDisk() throws IOException {
        String source = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/block/FlagstoneBlock.java"));
        int highest = FLAGSTONE_TEXTURES.size() - 1;
        assertTrue(source.contains("IntegerProperty.create(\"variation\", 0, " + highest + ")"),
                "FlagstoneBlock must declare one variation value per flagstone texture");
    }

    @Test
    void everyVariantPaintsAFlagstoneTextureAndNothingElse() throws IOException {
        Set<String> used = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> variant : blockstate().entrySet()) {
            String model = variant.getValue().getAsJsonObject().get("model").getAsString();
            JsonObject json = readModel(model);
            assertEquals("minecraft:block/cube_all", json.get("parent").getAsString(),
                    model + " must stay a plain full cube");
            String texture = json.getAsJsonObject("textures").get("all").getAsString();
            assertTrue(FLAGSTONE_TEXTURES.contains(texture),
                    model + " paints " + texture + ", which is not flagstone art");
            assertTrue(Files.exists(texturePath(texture)), "missing texture " + texture);
            used.add(texture);
        }
        assertEquals(FLAGSTONE_TEXTURES, used, "both flagstone textures must be reachable");
    }

    @Test
    void flagstoneNeverBorrowsTheFoundationBrickOrDarkTextures() throws IOException {
        Set<String> foreign = Set.of(
                "britannia_mod:block/structure/brick_foundation_01",
                "britannia_mod:block/structure/brick_foundation_02",
                "britannia_mod:block/structure/brick_foundation_03",
                "britannia_mod:block/structure/brick_dark_foundation",
                "britannia_mod:block/structure/brick_foundation");
        for (Map.Entry<String, JsonElement> variant : blockstate().entrySet()) {
            String model = variant.getValue().getAsJsonObject().get("model").getAsString();
            String texture = readModel(model).getAsJsonObject("textures").get("all").getAsString();
            assertFalse(foreign.contains(texture),
                    model + " leaked the foundation texture " + texture);
        }
    }

    @Test
    void eachVariationIsReachableByRotationOfExactlyOneModel() throws IOException {
        JsonObject variants = blockstate();
        for (int variation = 0; variation < FLAGSTONE_TEXTURES.size(); variation++) {
            Set<String> models = new LinkedHashSet<>();
            Set<Integer> rotations = new LinkedHashSet<>();
            for (String facing : FACINGS) {
                JsonObject entry = variants.getAsJsonObject("facing=" + facing + ",variation=" + variation);
                models.add(entry.get("model").getAsString());
                rotations.add(entry.has("y") ? entry.get("y").getAsInt() : 0);
            }
            assertEquals(1, models.size(), "variation " + variation + " must use one model");
            assertEquals(Set.of(0, 90, 180, 270), rotations,
                    "variation " + variation + " must rotate through all four facings");
        }
    }

    @Test
    void flagstoneIsFullyWiredUp() throws IOException {
        String blocks = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));

        assertTrue(blocks.contains("\"flagstone\""), "flagstone is not registered");
        assertTrue(items.contains("\"flagstone\""), "flagstone has no BlockItem");
        assertTrue(tabs.contains("FLAGSTONE_ITEM"), "flagstone is missing from the creative tab");
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).has("block.britannia_mod.flagstone"),
                "flagstone has no display name");
        assertTrue(Files.exists(DATA.resolve("britannia_mod/loot_table/blocks/flagstone.json")),
                "flagstone has no loot table, so it would drop nothing");

        JsonArray pickaxe = read(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"))
                .getAsJsonArray("values");
        Set<String> mineable = new LinkedHashSet<>();
        pickaxe.forEach(entry -> mineable.add(entry.getAsString()));
        assertTrue(mineable.contains("britannia_mod:flagstone"),
                "flagstone uses requiresCorrectToolForDrops but is not pickaxe-mineable");
    }

    @Test
    void itemModelRendersOneOfTheBlocksOwnVariants() throws IOException {
        String parent = read(ASSETS.resolve("models/item/flagstone.json")).get("parent").getAsString();
        Set<String> blockModels = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> variant : blockstate().entrySet()) {
            blockModels.add(variant.getValue().getAsJsonObject().get("model").getAsString());
        }
        assertTrue(blockModels.contains(parent), "flagstone item model must render a real variant");
    }

    // ---- helpers -------------------------------------------------------------------------

    private static JsonObject blockstate() throws IOException {
        return read(ASSETS.resolve("blockstates/flagstone.json")).getAsJsonObject("variants");
    }

    private static JsonObject readModel(String model) throws IOException {
        return read(ASSETS.resolve("models/" + model.substring("britannia_mod:".length()) + ".json"));
    }

    private static Path texturePath(String texture) {
        return ASSETS.resolve("textures/" + texture.substring("britannia_mod:".length()) + ".png");
    }

    private static JsonObject read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing file " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
