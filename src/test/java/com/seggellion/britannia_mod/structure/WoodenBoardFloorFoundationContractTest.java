package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.seggellion.britannia_mod.structure.FoundationAssets.ASSETS;
import static com.seggellion.britannia_mod.structure.FoundationAssets.NORMAL_SIDE_TEXTURES;
import static com.seggellion.britannia_mod.structure.FoundationAssets.SIDE_FACES;
import static com.seggellion.britannia_mod.structure.FoundationAssets.asList;
import static com.seggellion.britannia_mod.structure.FoundationAssets.blockstate;
import static com.seggellion.britannia_mod.structure.FoundationAssets.readModel;
import static com.seggellion.britannia_mod.structure.FoundationAssets.resolveTextures;
import static com.seggellion.britannia_mod.structure.FoundationAssets.texturePath;
import static com.seggellion.britannia_mod.structure.FoundationAssets.variantModels;
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
 * <p>Because it is a family member, {@code FoundationFamilyContractTest} now holds everything about
 * it that is true of the family as a whole -- the masonry set, the shared cube geometry, that every
 * model and texture it names exists, and that it is wired up as a block. What is left here is what
 * only this block has: fourteen boards to keep straight, four facings to turn them by, and a plain
 * {@code wooden_board_floor} it must not be confused with in either direction.
 */
class WoodenBoardFloorFoundationContractTest {

    private static final String BLOCK = "wooden_board_floor_foundation";
    private static final String MODEL_DIR = "block/structure/" + BLOCK;
    private static final String BOARD_DIR = "block/structure/wooden_board_floor";

    private static final Map<String, Integer> FACINGS = Map.of(
            "north", 0, "east", 90, "south", 180, "west", 270);

    /**
     * The point of the block: the board a state selects is the board that state draws.
     *
     * <p>The family test proves the five other faces are masonry, because a board leaking onto a
     * side would put a fourth texture into a set it holds to exactly three. What it cannot see is
     * whether the tops got shuffled -- every variation would still reach the same masonry. So this
     * is the half that has to be checked variation by variation: a state that drew board seven
     * before the models changed still draws board seven.
     */
    @Test
    void everyVariantWearsTheBoardItsVariationSelects() throws IOException {
        for (Map.Entry<String, List<String>> variant : variantModels(BLOCK).entrySet()) {
            int variation = variationOf(variant.getKey());
            String expected = "britannia_mod:" + BOARD_DIR + "/wooden_board_floor_" + variation;

            for (String model : variant.getValue()) {
                assertEquals(expected, resolveTextures(model).get("up"),
                        model + " must show the board its variation selects");
                assertTrue(Files.exists(texturePath(expected)),
                        BLOCK + " variant " + variant.getKey() + " points at " + expected + ", which "
                                + "does not exist. A variant with no texture renders as the "
                                + "missing-texture checkerboard rather than failing, so nothing "
                                + "else would catch this.");
            }
        }
    }

    /**
     * The shipped defect, stated as its own shape.
     *
     * <p>The family test would fail on this too, but it would fail saying the block reaches a
     * texture set it should not, which is a long way from "there is a plank on the underside".
     * This block is the one that had the bug, so it gets to say so plainly.
     */
    @Test
    void noVariantIsBoardsOnAllSixFaces() throws IOException {
        for (List<String> models : variantModels(BLOCK).values()) {
            for (String model : models) {
                Map<String, String> textures = resolveTextures(model);
                for (String face : SIDE_FACES) {
                    assertNotNull(textures.get(face), model + " does not define the " + face + " face");
                    assertFalse(textures.get(face).startsWith("britannia_mod:" + BOARD_DIR + "/"),
                            model + " wears a board on its " + face + " face, which is exposed to "
                                    + "the basement or to the outside and has to be masonry");
                }
            }
        }
    }

    /**
     * Every variation gets the full choice of masonry, not just the block as a whole.
     *
     * <p>The family test asks what the block can reach across all forty-two models at once, which
     * three variations between them could satisfy while the other eleven each sat on one brick.
     * A perimeter of slabs drawing one brick texture would tile visibly, which is why
     * brick_foundation_spruce lists all three, so the choice has to survive per variation.
     */
    @Test
    void everyVariationCanShowAllThreeMasonryTextures() throws IOException {
        Map<Integer, Set<String>> reachedByVariation = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> variant : variantModels(BLOCK).entrySet()) {
            Set<String> reached = reachedByVariation
                    .computeIfAbsent(variationOf(variant.getKey()), key -> new LinkedHashSet<>());
            for (String model : variant.getValue()) {
                Map<String, String> textures = resolveTextures(model);
                for (String face : SIDE_FACES) {
                    reached.add(textures.get(face));
                }
            }
        }
        for (Map.Entry<Integer, Set<String>> entry : reachedByVariation.entrySet()) {
            assertEquals(NORMAL_SIDE_TEXTURES, entry.getValue(),
                    "variation " + entry.getKey() + " must be able to show exactly the three brick "
                            + "textures, the same set brick_foundation_spruce reaches");
        }
    }

    @Test
    void itsVariationsRunContiguouslyFromZeroAndDrawItsOwnModels() throws IOException {
        Map<String, List<String>> variants = variantModels(BLOCK);
        assertFalse(variants.isEmpty(), BLOCK + " declares no variants");

        Set<Integer> variations = new TreeSet<>();
        for (Map.Entry<String, List<String>> variant : variants.entrySet()) {
            variations.add(variationOf(variant.getKey()));
            for (String model : variant.getValue()) {
                assertTrue(model.startsWith("britannia_mod:" + MODEL_DIR + "/"),
                        BLOCK + " variant " + variant.getKey() + " draws " + model + ", which is not "
                                + "the foundation's own model set. Drawing the plain floor's models "
                                + "is the original defect.");
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
        JsonObject variants = blockstate(BLOCK).getAsJsonObject("variants");
        Set<String> seen = new HashSet<>(variants.keySet());

        int variations = (int) variants.keySet().stream()
                .map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();
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
        long floorVariations = countVariations("wooden_board_floor");
        long foundationVariations = countVariations(BLOCK);

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
        for (String variant : blockstate("wooden_board_floor").getAsJsonObject("variants").keySet()) {
            int variation = variationOf(variant);
            Path texture = ASSETS.resolve("textures/" + BOARD_DIR + "/wooden_board_floor_"
                    + variation + ".png");
            assertTrue(Files.exists(texture),
                    "wooden_board_floor variation " + variation + " has no texture: " + texture);
        }
    }

    /* ------------------------------------------------------------------ */

    private static long countVariations(String block) throws IOException {
        return variantModels(block).keySet().stream()
                .map(WoodenBoardFloorFoundationContractTest::variationOf).distinct().count();
    }

    private static int variationOf(String variantKey) {
        for (String part : variantKey.split(",")) {
            if (part.startsWith("variation=")) return Integer.parseInt(part.substring("variation=".length()));
        }
        throw new IllegalArgumentException("no variation in variant key: " + variantKey);
    }
}
