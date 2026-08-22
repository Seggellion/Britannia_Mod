package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Wooden Board Floor Foundation is a ground-floor slab, not a floor.
 *
 * <p>It is the structural interior floor a house ships with -- the layer an owner cuts through to
 * reach a basement -- and it sits at y=0 with its underside and its four edges exposed to the
 * basement and to the outside. Every other member of that family is built accordingly:
 * {@code HouseFloorRoleTest} describes the ground slab as brick at the exposed edges and planks
 * underfoot, and {@code brick_foundation_spruce} is exactly that.
 *
 * <p>This block was not. It was generated against {@code wooden_board_floor}, so it wore boards on
 * all six faces and read as a floating plank cube with no masonry under it. The fix is the family's
 * own convention rather than a new one: a {@code foundation/brick_side_NN} parent paints the
 * masonry on the sides and the underside, and the child binds only {@code up} to its board.
 *
 * <p>So these tests hold both halves of the block at once -- the boards on top must stay the
 * floor's own boards, and the five other faces must stay masonry -- and they hold the plain
 * {@code wooden_board_floor} apart from it, because that block is still boards all over and is
 * not what this one copies any more.
 */
class WoodenBoardFloorFoundationContractTest {

    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = ROOT.resolve("src/main/resources/data/britannia_mod");

    private static final String BLOCK = "wooden_board_floor_foundation";
    private static final String MODEL_DIR = "block/structure/" + BLOCK;
    private static final String BOARD_DIR = "block/structure/wooden_board_floor";

    /** The faces a ground slab shows to the basement below and to the world outside. */
    private static final List<String> MASONRY_FACES = List.of("down", "north", "south", "west", "east");

    /** The shared light-brick set, the same three textures brick_foundation_spruce reaches. */
    private static final Set<String> MASONRY_TEXTURES = new LinkedHashSet<>(List.of(
            "britannia_mod:block/structure/brick_foundation_01",
            "britannia_mod:block/structure/brick_foundation_02",
            "britannia_mod:block/structure/brick_foundation_03"));

    private static final Map<String, Integer> FACINGS = Map.of(
            "north", 0, "east", 90, "south", 180, "west", 270);

    /**
     * The point of the block: boards you walk on, masonry you see from below and from outside.
     *
     * <p>The failure this exists to catch is boards on all six faces, which is what shipped. It
     * draws perfectly well and looks wrong, so nothing but an assertion about which face wears
     * which texture would ever notice.
     */
    @Test
    void everyVariantWearsItsOwnBoardOnTopAndMasonryEverywhereElse() throws IOException {
        for (Map.Entry<String, List<String>> variant : variantModels().entrySet()) {
            int variation = variationOf(variant.getKey());
            String expectedBoard = "britannia_mod:" + BOARD_DIR + "/wooden_board_floor_" + variation;

            for (String model : variant.getValue()) {
                Map<String, String> textures = resolveTextures(model);

                assertEquals(expectedBoard, textures.get("up"),
                        model + " must show the board its variation selects, so that a state which "
                                + "used to draw board " + variation + " still draws board " + variation);
                assertTrue(Files.exists(texturePath(expectedBoard)),
                        BLOCK + " variant " + variant.getKey() + " points at " + expectedBoard
                                + ", which does not exist. A variant with no texture renders as the "
                                + "missing-texture checkerboard rather than failing, so nothing else "
                                + "would catch this.");

                for (String face : MASONRY_FACES) {
                    String texture = textures.get(face);
                    assertNotNull(texture, model + " does not define the " + face + " face");
                    assertTrue(MASONRY_TEXTURES.contains(texture),
                            model + " paints " + texture + " on its " + face + " face. That face is "
                                    + "exposed to the basement or to the outside and has to be "
                                    + "masonry; boards there are the original defect.");
                    assertTrue(Files.exists(texturePath(texture)),
                            "missing texture " + texture + " referenced by " + model);
                }
            }
        }
    }

    /** Belt and braces on the same rule, stated as the shape of the bug rather than of the fix. */
    @Test
    void noVariantIsBoardsOnAllSixFaces() throws IOException {
        for (List<String> models : variantModels().values()) {
            for (String model : models) {
                Map<String, String> textures = resolveTextures(model);
                for (String face : MASONRY_FACES) {
                    assertFalse(textures.get(face).startsWith("britannia_mod:" + BOARD_DIR + "/"),
                            model + " wears a board on its " + face + " face");
                }
            }
        }
    }

    /**
     * The masonry has to vary the way the rest of the family's does.
     *
     * <p>A perimeter of slabs all drawing brick_foundation_01 would tile visibly, which is why
     * brick_foundation_spruce lists all three. Every board variation gets the same choice.
     */
    @Test
    void everyVariationCanShowAllThreeMasonryTextures() throws IOException {
        Map<Integer, Set<String>> reachedByVariation = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> variant : variantModels().entrySet()) {
            Set<String> reached = reachedByVariation
                    .computeIfAbsent(variationOf(variant.getKey()), key -> new LinkedHashSet<>());
            for (String model : variant.getValue()) {
                Map<String, String> textures = resolveTextures(model);
                for (String face : MASONRY_FACES) {
                    reached.add(textures.get(face));
                }
            }
        }
        for (Map.Entry<Integer, Set<String>> entry : reachedByVariation.entrySet()) {
            assertEquals(MASONRY_TEXTURES, entry.getValue(),
                    "variation " + entry.getKey() + " must be able to show exactly the three brick "
                            + "textures, the same set brick_foundation_spruce reaches");
        }
    }

    /** It is a full cube like the rest of the family, not a slab and not a shape of its own. */
    @Test
    void itKeepsTheSharedFoundationGeometry() throws IOException {
        for (List<String> models : variantModels().values()) {
            for (String model : models) {
                assertTrue(readModel(model).get("parent").getAsString()
                                .startsWith("britannia_mod:block/structure/foundation/brick_side_"),
                        model + " must parent the shared brick foundation model rather than carry "
                                + "its own copy of the masonry");
                assertEquals("minecraft:block/cube", rootParentOf(model),
                        model + " must keep the shared full-cube foundation geometry");
            }
        }
    }

    @Test
    void everyDeclaredVariantHasAModelThatExists() throws IOException {
        Map<String, List<String>> variants = variantModels();
        assertFalse(variants.isEmpty(), BLOCK + " declares no variants");

        Set<Integer> variations = new TreeSet<>();
        for (Map.Entry<String, List<String>> variant : variants.entrySet()) {
            variations.add(variationOf(variant.getKey()));
            for (String model : variant.getValue()) {
                assertTrue(model.startsWith("britannia_mod:" + MODEL_DIR + "/"),
                        BLOCK + " variant " + variant.getKey() + " draws " + model + ", which is not "
                                + "the foundation's own model set");
                assertTrue(Files.exists(modelPath(model)),
                        "missing model for " + variant.getKey() + ": " + modelPath(model));
            }
        }

        // Contiguous from zero: the block property is a range, so a gap would be a state that
        // exists and draws nothing.
        int expected = 0;
        for (int variation : variations) {
            assertEquals(expected++, variation, BLOCK + " skips a variation");
        }
    }

    @Test
    void allFourFacingsAreCoveredForEveryVariationAndTurnTheBoards() throws IOException {
        JsonObject variants = blockstate().getAsJsonObject("variants");
        Set<String> seen = new HashSet<>(variants.keySet());

        int variations = (int) variants.keySet().stream().map(WoodenBoardFloorFoundationContractTest::variationOf)
                .distinct().count();
        assertEquals(variations * 4, variants.size(),
                BLOCK + " does not cover four facings for each of its " + variations + " variations");

        for (int variation = 0; variation < variations; variation++) {
            for (Map.Entry<String, Integer> facing : FACINGS.entrySet()) {
                String key = "facing=" + facing.getKey() + ",variation=" + variation;
                assertTrue(seen.contains(key), BLOCK + " has no " + key);

                for (JsonElement entry : asList(variants.get(key))) {
                    JsonElement rotation = entry.getAsJsonObject().get("y");
                    assertEquals(facing.getValue(), rotation == null ? 0 : rotation.getAsInt(),
                            key + " must turn the boards to face " + facing.getKey());
                }
            }
        }
    }

    @Test
    void theFloorAndItsFoundationOfferExactlyTheSameVariants() throws IOException {
        long floorVariations = read("blockstates/wooden_board_floor.json").getAsJsonObject("variants")
                .keySet().stream().map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();
        long foundationVariations = variantModels().keySet().stream()
                .map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();

        assertEquals(floorVariations, foundationVariations,
                "the structural floor and the flooring above it walk on the same boards; they cannot "
                        + "offer different sets of them");
    }

    /**
     * The plain floor is not a foundation and must not follow this block's fix.
     *
     * <p>It is decorative flooring laid on top of something else, so boards on every face are
     * correct for it. Pinned because the two are generated from the same board set and the easy
     * mistake in either direction is to treat them as one block.
     */
    @Test
    void theRegularFloorStaysBoardsOnEveryFace() throws IOException {
        for (List<String> models : variantModels("wooden_board_floor").values()) {
            for (String model : models) {
                JsonObject json = readModel(model);
                assertEquals("minecraft:block/cube_all", json.get("parent").getAsString(),
                        model + " is decorative flooring and should stay a plain all-faces cube");
                assertTrue(json.getAsJsonObject("textures").get("all").getAsString()
                                .startsWith("britannia_mod:" + BOARD_DIR + "/"),
                        model + " should stay on the board textures");
            }
        }
    }

    /**
     * The floor itself, held to the rule that every variant it offers can be drawn.
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
            Path texture = ASSETS.resolve("textures/" + BOARD_DIR + "/wooden_board_floor_"
                    + variation + ".png");
            assertTrue(Files.exists(texture),
                    "wooden_board_floor variation " + variation + " has no texture: " + texture);
        }
    }

    @Test
    void itShipsTheRestOfWhatABlockNeeds() throws IOException {
        Path itemModel = ASSETS.resolve("models/item/" + BLOCK + ".json");
        assertTrue(Files.exists(itemModel), "no item model: " + itemModel);

        String parent = JsonParser.parseString(Files.readString(itemModel)).getAsJsonObject()
                .get("parent").getAsString();
        List<String> placeable = new ArrayList<>();
        variantModels().values().forEach(placeable::addAll);
        assertTrue(placeable.contains(parent),
                "the item should render one of the block's own variants, so that the inventory icon "
                        + "and the placed block agree; it shows " + parent);
        assertTrue(Files.exists(modelPath(parent)), "the item model parent does not exist: " + parent);

        Path loot = DATA.resolve("loot_table/blocks/" + BLOCK + ".json");
        assertTrue(Files.exists(loot), "no loot table: " + loot);
        assertTrue(Files.readString(loot).contains("britannia_mod:" + BLOCK),
                "the loot table should drop the block itself");

        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        assertTrue(lang.contains("\"block.britannia_mod." + BLOCK + "\""),
                "no en_us name, so it would show as block.britannia_mod." + BLOCK);
    }

    /* ------------------------------------------------------------------ */

    private static Map<String, List<String>> variantModels() throws IOException {
        return variantModels(BLOCK);
    }

    /** Each variant key mapped to every model it can select, across plain and random-array forms. */
    private static Map<String, List<String>> variantModels(String block) throws IOException {
        JsonObject variants = read("blockstates/" + block + ".json").getAsJsonObject("variants");
        assertNotNull(variants, block + " has no variants block");
        Map<String, List<String>> models = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            List<String> selectable = new ArrayList<>();
            for (JsonElement entry : asList(variant.getValue())) {
                selectable.add(entry.getAsJsonObject().get("model").getAsString());
            }
            models.put(variant.getKey(), selectable);
        }
        return models;
    }

    private static List<JsonElement> asList(JsonElement value) {
        if (value == null) return List.of();
        if (!value.isJsonArray()) return List.of(value);
        List<JsonElement> entries = new ArrayList<>();
        value.getAsJsonArray().forEach(entries::add);
        return entries;
    }

    /** Flattens a model's texture map down its parent chain, child entries winning. */
    private static Map<String, String> resolveTextures(String model) throws IOException {
        Map<String, String> resolved = new LinkedHashMap<>();
        String current = model;
        while (current != null && current.startsWith("britannia_mod:")) {
            JsonObject json = readModel(current);
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures != null) {
                for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                    resolved.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            }
            JsonElement parent = json.get("parent");
            current = parent == null ? null : parent.getAsString();
        }
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            assertFalse(entry.getValue().startsWith("#"),
                    model + " left texture alias " + entry.getKey() + " unresolved");
        }
        return resolved;
    }

    /** The vanilla model at the top of the parent chain, i.e. the shape the block really is. */
    private static String rootParentOf(String model) throws IOException {
        String current = model;
        String parent = null;
        while (current != null && current.startsWith("britannia_mod:")) {
            JsonElement next = readModel(current).get("parent");
            parent = next == null ? null : next.getAsString();
            current = parent;
        }
        return parent;
    }

    private static int variationOf(String variantKey) {
        for (String part : variantKey.split(",")) {
            if (part.startsWith("variation=")) return Integer.parseInt(part.substring("variation=".length()));
        }
        throw new IllegalArgumentException("no variation in variant key: " + variantKey);
    }

    private static JsonObject readModel(String model) throws IOException {
        return read("models/" + model.substring("britannia_mod:".length()) + ".json");
    }

    private static Path modelPath(String model) {
        return ASSETS.resolve("models/" + model.substring("britannia_mod:".length()) + ".json");
    }

    private static Path texturePath(String texture) {
        return ASSETS.resolve("textures/" + texture.substring("britannia_mod:".length()) + ".png");
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
