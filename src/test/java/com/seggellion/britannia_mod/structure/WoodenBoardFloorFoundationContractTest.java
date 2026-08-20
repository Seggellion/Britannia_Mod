package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Wooden Board Floor Foundation is the Wooden Board Floor, structurally.
 *
 * <p>It is the interior floor a house ships with -- the layer an owner cuts through to reach a
 * basement -- as opposed to the perimeter foundation around the outside, which stays put, and
 * as opposed to decorative flooring, which was never part of the structure. The distinction is
 * an identity, not a look: it has to be visually indistinguishable from the floor it mirrors or
 * every house that uses it changes appearance.
 *
 * <p>So these tests hold it to the floor's own assets rather than to a copy of them, and hold
 * both halves of the family to the same rule: every variant either has a texture or does not
 * exist. The family shipped one short -- fifteen models against fourteen boards -- and the
 * fifteenth drew the missing-texture checkerboard rather than failing, which is why nothing
 * noticed for the best part of a year.
 */
class WoodenBoardFloorFoundationContractTest {

    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = ROOT.resolve("src/main/resources/data/britannia_mod");

    private static final String BLOCK = "wooden_board_floor_foundation";
    private static final String MODEL_DIR = "block/structure/wooden_board_floor";

    @Test
    void everyDeclaredVariantHasAModelAndThatModelHasATexture() throws IOException {
        JsonObject variants = blockstate().getAsJsonObject("variants");
        assertFalse(variants.isEmpty(), BLOCK + " declares no variants");

        Set<Integer> variations = new TreeSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : variants.entrySet()) {
            variations.add(variationOf(entry.getKey()));

            String model = entry.getValue().getAsJsonObject().get("model").getAsString();
            assertTrue(model.startsWith("britannia_mod:" + MODEL_DIR + "/"),
                    BLOCK + " variant " + entry.getKey() + " draws " + model + ", which is not the "
                            + "Wooden Board Floor's own model set");

            Path modelPath = ASSETS.resolve("models").resolve(model.substring("britannia_mod:".length()) + ".json");
            assertTrue(Files.exists(modelPath), "missing model for " + entry.getKey() + ": " + modelPath);

            String texture = JsonParser.parseString(Files.readString(modelPath)).getAsJsonObject()
                    .getAsJsonObject("textures").get("all").getAsString();
            Path texturePath = ASSETS.resolve("textures")
                    .resolve(texture.substring("britannia_mod:".length()) + ".png");
            assertTrue(Files.exists(texturePath),
                    BLOCK + " variant " + entry.getKey() + " points at " + texture + ", which does "
                            + "not exist. A variant with no texture renders as the missing-texture "
                            + "checkerboard rather than failing, so nothing else would catch this.");
        }

        // Contiguous from zero: the block property is a range, so a gap would be a state that
        // exists and draws nothing.
        int expected = 0;
        for (int variation : variations) {
            assertEquals(expected++, variation, BLOCK + " skips a variation");
        }
    }

    @Test
    void allFourFacingsAreCoveredForEveryVariation() throws IOException {
        JsonObject variants = blockstate().getAsJsonObject("variants");
        Set<String> seen = new HashSet<>(variants.keySet());

        int variations = (int) variants.keySet().stream().map(WoodenBoardFloorFoundationContractTest::variationOf)
                .distinct().count();
        assertEquals(variations * 4, variants.size(),
                BLOCK + " does not cover four facings for each of its " + variations + " variations");

        for (int variation = 0; variation < variations; variation++) {
            for (String facing : new String[] { "north", "east", "south", "west" }) {
                assertTrue(seen.contains("facing=" + facing + ",variation=" + variation),
                        BLOCK + " has no facing=" + facing + " for variation " + variation);
            }
        }
    }

    @Test
    void theFloorAndItsFoundationOfferExactlyTheSameVariants() throws IOException {
        long floorVariations = read("blockstates/wooden_board_floor.json").getAsJsonObject("variants")
                .keySet().stream().map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();
        long foundationVariations = blockstate().getAsJsonObject("variants")
                .keySet().stream().map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();

        assertEquals(floorVariations, foundationVariations,
                "the structural floor and the flooring above it are the same boards; they cannot "
                        + "offer different sets of them");
    }

    /**
     * The floor itself, held to the same rule.
     *
     * <p>It used to declare a fifteenth variant whose texture has never existed -- commit
     * cb9483db added fifteen models and fourteen PNGs, and wooden_board_floor_14.png is not a
     * blob anywhere in this repository history. A variant with no texture renders as the
     * missing-texture checkerboard rather than failing, so nothing but this catches it.
     */
    @Test
    void everyVariantOfTheFloorItselfCanBeDrawn() throws IOException {
        for (String variant : read("blockstates/wooden_board_floor.json")
                .getAsJsonObject("variants").keySet()) {
            int variation = variationOf(variant);
            Path texture = ASSETS.resolve("textures/" + MODEL_DIR + "/wooden_board_floor_"
                    + variation + ".png");
            assertTrue(Files.exists(texture),
                    "wooden_board_floor variation " + variation + " has no texture: " + texture);
        }
    }

    @Test
    void itShipsTheRestOfWhatABlockNeeds() throws IOException {
        Path itemModel = ASSETS.resolve("models/item/" + BLOCK + ".json");
        assertTrue(Files.exists(itemModel), "no item model: " + itemModel);
        assertTrue(JsonParser.parseString(Files.readString(itemModel)).getAsJsonObject()
                        .get("parent").getAsString().startsWith("britannia_mod:" + MODEL_DIR + "/"),
                "the item should show the boards it places");

        Path loot = DATA.resolve("loot_table/blocks/" + BLOCK + ".json");
        assertTrue(Files.exists(loot), "no loot table: " + loot);
        assertTrue(Files.readString(loot).contains("britannia_mod:" + BLOCK),
                "the loot table should drop the block itself");

        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        assertTrue(lang.contains("\"block.britannia_mod." + BLOCK + "\""),
                "no en_us name, so it would show as block.britannia_mod." + BLOCK);
    }

    /* ------------------------------------------------------------------ */

    private static int variationOf(String variantKey) {
        for (String part : variantKey.split(",")) {
            if (part.startsWith("variation=")) return Integer.parseInt(part.substring("variation=".length()));
        }
        throw new IllegalArgumentException("no variation in variant key: " + variantKey);
    }

    private static JsonObject blockstate() throws IOException {
        return read("blockstates/" + BLOCK + ".json");
    }

    private static JsonObject read(String relative) throws IOException {
        Path path = ASSETS.resolve(relative);
        assertTrue(Files.exists(path), "missing asset: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
