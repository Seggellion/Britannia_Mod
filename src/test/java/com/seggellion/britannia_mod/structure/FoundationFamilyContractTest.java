package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.seggellion.britannia_mod.structure.FoundationAssets.ASSETS;
import static com.seggellion.britannia_mod.structure.FoundationAssets.DARK_SIDE_TEXTURE;
import static com.seggellion.britannia_mod.structure.FoundationAssets.NORMAL_SIDE_TEXTURES;
import static com.seggellion.britannia_mod.structure.FoundationAssets.ROOT;
import static com.seggellion.britannia_mod.structure.FoundationAssets.allModels;
import static com.seggellion.britannia_mod.structure.FoundationAssets.distinctModels;
import static com.seggellion.britannia_mod.structure.FoundationAssets.itemModelPath;
import static com.seggellion.britannia_mod.structure.FoundationAssets.lootTablePath;
import static com.seggellion.britannia_mod.structure.FoundationAssets.modelPath;
import static com.seggellion.britannia_mod.structure.FoundationAssets.parentOf;
import static com.seggellion.britannia_mod.structure.FoundationAssets.read;
import static com.seggellion.britannia_mod.structure.FoundationAssets.resolveTextures;
import static com.seggellion.britannia_mod.structure.FoundationAssets.rootParentOf;
import static com.seggellion.britannia_mod.structure.FoundationAssets.sideTexturesOf;
import static com.seggellion.britannia_mod.structure.FoundationAssets.tagValues;
import static com.seggellion.britannia_mod.structure.FoundationAssets.texturePath;
import static com.seggellion.britannia_mod.structure.FoundationAssets.variantModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the foundation material families apart.
 *
 * <p>A foundation block's material family is carried by its model {@code parent}: every normal
 * brick block parents a {@code foundation/brick_side_NN} model, every dark block parents
 * {@code foundation/dark_side}. These tests resolve each block's real texture set by walking that
 * parent chain, so a block cannot silently drift into the wrong family without failing here.
 *
 * <h2>What is a family member</h2>
 * Membership is about what a block paints on its sides, and nothing else. It is not about the
 * block's name, not about its top, and not about what it is made of underfoot -- which is why
 * {@code wooden_board_floor_foundation} belongs to the normal brick family despite being a wooden
 * floor by name, by top texture and by tool tier. It is the same masonry cube as
 * {@code brick_foundation_spruce} with different boards on top.
 *
 * <p>Two rules genuinely do not generalise across that membership, and both are scoped rather than
 * dropped: the pickaxe tier, which follows a block's tool requirement rather than its sides, and
 * the within-variant model distinctness, which is per blockstate variant and not per block.
 * Everything else is asserted once, over every foundation there is.
 */
class FoundationFamilyContractTest {

    /**
     * Blocks whose sides come from the three-variant normal brick set.
     *
     * <p>{@code wooden_board_floor_foundation} is the odd-looking member and the reason this list
     * is about sides rather than names: it is a ground-floor slab, so its underside and its four
     * edges are exposed to the basement and to the outside and are masonry, and only its top is
     * boards. {@code HouseFloorRoleTest} has the structural evidence for that role.
     */
    private static final List<String> NORMAL_FAMILY = List.of(
            "brick_foundation_oak", "brick_foundation_spruce", "brick_foundation_flagstone",
            "wooden_board_floor_foundation");

    /** Blocks whose sides come from the single dark texture. Must never see the normal set. */
    private static final List<String> DARK_FAMILY =
            List.of("brick_foundation_sandstone", "brick_foundation_dark_sandstone");

    /**
     * The foundations that require a correct tool to drop anything.
     *
     * <p>This tracks the block registration, not the family: a block that is masonry on five faces
     * can still be wood to break. {@code toolTiersFollowTheBlockRegistrationRatherThanTheFamily}
     * holds this list against {@code BlockRegistry} so it cannot rot into a stale duplicate.
     */
    private static final List<String> TOOL_GATED = List.of(
            "brick_foundation_oak", "brick_foundation_spruce", "brick_foundation_flagstone",
            "brick_foundation_sandstone", "brick_foundation_dark_sandstone");

    private static final String PICKAXE_TAG = "minecraft/tags/block/mineable/pickaxe.json";

    private static List<String> everyFoundation() {
        List<String> all = new ArrayList<>(NORMAL_FAMILY);
        all.addAll(DARK_FAMILY);
        return all;
    }

    /* -- the two families ----------------------------------------------------------------- */

    @Test
    void everyNormalFoundationReachesAllThreeBrickTextures() throws IOException {
        for (String block : NORMAL_FAMILY) {
            Set<String> reached = new LinkedHashSet<>();
            for (String model : distinctModels(block)) {
                reached.addAll(sideTexturesOf(model));
            }
            assertEquals(NORMAL_SIDE_TEXTURES, reached,
                    block + " must be able to show exactly the three normal brick textures");
        }
    }

    @Test
    void darkFoundationsNeverReferenceTheNormalBrickTextureSet() throws IOException {
        for (String block : DARK_FAMILY) {
            for (String model : distinctModels(block)) {
                Set<String> sides = sideTexturesOf(model);
                assertEquals(Set.of(DARK_SIDE_TEXTURE), sides,
                        block + " must stay on the dark texture");
                for (String normal : NORMAL_SIDE_TEXTURES) {
                    assertFalse(sides.contains(normal),
                            block + " leaked the normal brick texture " + normal);
                }
            }
        }
    }

    @Test
    void familySeparationIsCarriedByModelParentsNotNaming() throws IOException {
        // brick_foundation_sandstone is named like the normal family but is a dark block, and
        // wooden_board_floor_foundation is named like neither and is a normal one. These are the
        // exact traps a name-prefix rule would fall into, so pin both explicitly.
        for (String model : distinctModels("brick_foundation_sandstone")) {
            assertEquals("britannia_mod:block/structure/foundation/dark_side", parentOf(model),
                    "brick_foundation_sandstone must parent the dark family model");
        }
        for (String block : List.of("brick_foundation_oak", "wooden_board_floor_foundation")) {
            for (String model : distinctModels(block)) {
                assertTrue(parentOf(model)
                                .startsWith("britannia_mod:block/structure/foundation/brick_side_"),
                        block + " must parent a normal brick family model rather than carry its own "
                                + "copy of the masonry");
            }
        }
    }

    @Test
    void flagstoneUsesFlagstoneTopsAndNoOtherFamilysTop() throws IOException {
        Set<String> tops = new LinkedHashSet<>();
        for (String model : distinctModels("brick_foundation_flagstone")) {
            tops.add(resolveTextures(model).get("up"));
        }
        assertEquals(Set.of(
                        "britannia_mod:block/structure/flagstone_01",
                        "britannia_mod:block/structure/flagstone_02"),
                tops, "flagstone foundation must reach both flagstone tops");
    }

    /* -- rules that hold for every foundation --------------------------------------------- */

    @Test
    void everyFoundationKeepsTheSharedFullCubeGeometry() throws IOException {
        for (String block : everyFoundation()) {
            for (String model : distinctModels(block)) {
                assertEquals("minecraft:block/cube", rootParentOf(model),
                        model + " must keep the shared full-cube foundation geometry");
            }
        }
    }

    /**
     * Distinctness is per blockstate variant, because the random choice is.
     *
     * <p>Minecraft picks between the entries of one variant key by hashing the block position, so
     * a model listed twice under the same key is what skews that distribution. The same model
     * appearing under several keys is not a duplicate at all -- it is one appearance offered in
     * more than one state, which is exactly how wooden_board_floor_foundation's forty-two models
     * cover fifty-six facing-and-variation states.
     */
    @Test
    void noBlockstateVariantListsTheSameModelTwice() throws IOException {
        for (String block : everyFoundation()) {
            for (Map.Entry<String, List<String>> variant : variantModels(block).entrySet()) {
                List<String> models = variant.getValue();
                String where = variant.getKey().isEmpty()
                        ? "its only variant" : "variant " + variant.getKey();
                assertEquals(models.size(), new LinkedHashSet<>(models).size(),
                        block + " lists a duplicate model under " + where + ", which would skew the "
                                + "random distribution");
            }
        }
    }

    @Test
    void everyReferencedFoundationModelAndTextureExists() throws IOException {
        for (String block : everyFoundation()) {
            for (String model : distinctModels(block)) {
                assertTrue(Files.exists(modelPath(model)),
                        block + " selects " + model + ", which does not exist");
                for (Map.Entry<String, String> texture : resolveTextures(model).entrySet()) {
                    String reference = texture.getValue();
                    assertFalse(reference.startsWith("#"),
                            model + " left texture alias " + texture.getKey() + " unresolved");
                    if (!reference.startsWith("britannia_mod:")) {
                        continue; // vanilla textures ship in the Minecraft jar, not this repo
                    }
                    assertTrue(Files.exists(texturePath(reference)),
                            "missing texture " + reference + " referenced by " + model
                                    + ". A model with no texture renders as the missing-texture "
                                    + "checkerboard rather than failing, so nothing else would "
                                    + "catch this.");
                }
            }
        }
    }

    @Test
    void everyFoundationBlockIsFullyWiredUp() throws IOException {
        String blocks = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));

        for (String block : everyFoundation()) {
            assertTrue(blocks.contains("\"" + block + "\""), block + " is not registered");
            assertTrue(items.contains("\"" + block + "\""), block + " has no BlockItem");
            assertTrue(tabs.contains(block.toUpperCase() + "_ITEM"),
                    block + " is missing from the creative tab");
            assertTrue(lang.has("block.britannia_mod." + block), block + " has no display name");
            assertTrue(Files.exists(itemModelPath(block)), block + " has no item model");
            assertTrue(Files.exists(lootTablePath(block)),
                    block + " has no loot table, so it would drop nothing");
            assertTrue(Files.readString(lootTablePath(block)).contains("britannia_mod:" + block),
                    block + " has a loot table that does not drop the block itself");
        }
    }

    @Test
    void foundationItemModelsPointAtARealCanonicalVariant() throws IOException {
        for (String block : everyFoundation()) {
            String parent = read(itemModelPath(block)).get("parent").getAsString();
            assertTrue(allModels(block).contains(parent),
                    block + " item model must render one of the block's own variants, so that the "
                            + "inventory icon and the placed block agree; it shows " + parent);
            assertTrue(Files.exists(modelPath(parent)), block + " item model parent does not exist");
        }
    }

    /* -- the one rule that follows the registration, not the family ----------------------- */

    /**
     * A tool-gated foundation must be pickaxe-mineable; one that is not must not be.
     *
     * <p>{@code requiresCorrectToolForDrops()} without a matching tool tag is the expensive half of
     * this: the block becomes unbreakable-for-drops and silently yields nothing. The other half is
     * the copy-paste that this refactor made possible -- adding a wood-tier block to the brick
     * family and carrying the family's pickaxe tag entry across with it.
     */
    @Test
    void toolGatedFoundationsArePickaxeMineableAndTheRestAreNot() throws IOException {
        Set<String> pickaxe = tagValues(PICKAXE_TAG);
        for (String block : everyFoundation()) {
            String id = "britannia_mod:" + block;
            if (TOOL_GATED.contains(block)) {
                assertTrue(pickaxe.contains(id),
                        block + " uses requiresCorrectToolForDrops but is not pickaxe-mineable, so "
                                + "it would drop nothing however it is broken");
            } else {
                assertFalse(pickaxe.contains(id),
                        block + " requires no tool and breaks by hand. It is in the brick family by "
                                + "its sides alone, and carrying the family's pickaxe tag entry "
                                + "across to it is the mistake this catches.");
            }
        }
    }

    /**
     * Holds {@link #TOOL_GATED} against the registrations it claims to describe.
     *
     * <p>Reads the source rather than the block, because a unit test that touches
     * {@code BlockRegistry} without bootstrapping Minecraft poisons the whole test JVM. The slice
     * runs from the registry name to the end of that registration statement, which is where a
     * block's properties are; if someone reformats those statements this fails loudly rather than
     * quietly stopping to check anything.
     */
    @Test
    void toolTiersFollowTheBlockRegistrationRatherThanTheFamily() throws IOException {
        String source = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));

        for (String block : everyFoundation()) {
            int from = source.indexOf("\"" + block + "\"");
            assertTrue(from >= 0, block + " is not registered");
            int to = source.indexOf(");", from);
            assertTrue(to > from,
                    "could not find the end of " + block + "'s registration statement; the registry "
                            + "has been reformatted and this test needs rewriting rather than "
                            + "silently passing");

            boolean gated = source.substring(from, to).contains("requiresCorrectToolForDrops");
            assertEquals(TOOL_GATED.contains(block), gated,
                    block + " is registered " + (gated ? "with" : "without")
                            + " requiresCorrectToolForDrops, which TOOL_GATED disagrees with. The "
                            + "list and the pickaxe tag both need to follow the registration.");
        }
    }
}
