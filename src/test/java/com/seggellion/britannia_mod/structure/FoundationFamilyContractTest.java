package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /* -- the same masonry, cut as stairs --------------------------------------------------- */

    /**
     * The stairs are the family member that is not a cube.
     *
     * <p>Everything above measures a foundation by its five masonry faces, which a stair does not
     * have -- {@code minecraft:block/stairs} paints from a {@code bottom}/{@code top}/{@code side}
     * map instead. So the family rules that generalise are re-asserted here in the stair's own
     * terms rather than the block being bent to fit a cube-shaped test, and the two that do not --
     * the full-cube geometry and the pickaxe tier -- are replaced by the rules that do apply: the
     * vanilla stair parents, and stopping players by hardness rather than by a tool tier.
     */
    private static final String STAIRS = "brick_foundation_stairs";

    /** The faces a stair model paints. Everything visible on one is masonry. */
    private static final List<String> STAIR_FACES = List.of("bottom", "top", "side");

    private static final List<String> STAIR_SHAPES =
            List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");

    private static final Path STAIRS_SOURCE = ROOT.resolve(
            "src/main/java/com/seggellion/britannia_mod/block/BrickFoundationStairsBlock.java");

    @Test
    void theFoundationStairsShowTheNormalBrickMasonryAndNothingElse() throws IOException {
        Set<String> reached = new LinkedHashSet<>();
        for (String model : distinctModels(STAIRS)) {
            Map<String, String> textures = resolveTextures(model);
            for (String face : STAIR_FACES) {
                String texture = textures.get(face);
                assertNotNull(texture, model + " does not define the " + face + " face");
                assertTrue(NORMAL_SIDE_TEXTURES.contains(texture),
                        model + " paints " + texture + " on its " + face + " face, which is not "
                                + "part of the light foundation masonry set");
                reached.add(texture);
            }
        }
        assertEquals(NORMAL_SIDE_TEXTURES, reached,
                STAIRS + " must be able to show exactly the three normal brick textures, so a run "
                        + "of them varies the way the foundation course beside it does");
    }

    @Test
    void theFoundationStairsInheritVanillaStairGeometryRatherThanCarryingTheirOwn()
            throws IOException {
        Set<String> parents = new LinkedHashSet<>();
        for (String model : distinctModels(STAIRS)) {
            parents.add(parentOf(model));
            assertEquals(parentOf(model), rootParentOf(model),
                    model + " must reach vanilla in one hop; an intermediate model of our own "
                            + "would be a second copy of the stair geometry");
        }
        assertEquals(Set.of("block/stairs", "block/inner_stairs", "block/outer_stairs"), parents,
                STAIRS + " must be built from the three vanilla stair shapes");
    }

    @Test
    void theFoundationStairsCoverEveryFacingHalfAndShape() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (String shape : STAIR_SHAPES) {
            for (String half : List.of("bottom", "top")) {
                for (String facing : List.of("north", "south", "east", "west")) {
                    expected.add("facing=" + facing + ",half=" + half + ",shape=" + shape);
                }
            }
        }
        assertEquals(expected, new TreeSet<>(variantModels(STAIRS).keySet()),
                "a stair state with no entry renders as the missing-model cube, so every facing, "
                        + "half and shape must be listed");
    }

    @Test
    void everyFoundationStairsStateOffersAllThreeMasonryVariationsExactlyOnce() throws IOException {
        for (Map.Entry<String, List<String>> variant : variantModels(STAIRS).entrySet()) {
            List<String> models = variant.getValue();
            assertEquals(3, models.size(),
                    STAIRS + " offers " + models.size() + " models under " + variant.getKey()
                            + "; every state must offer all three masonry variations");
            assertEquals(models.size(), new LinkedHashSet<>(models).size(),
                    STAIRS + " lists a duplicate model under " + variant.getKey() + ", which would "
                            + "skew the random distribution");
        }
    }

    @Test
    void everyReferencedFoundationStairsModelAndTextureExists() throws IOException {
        for (String model : distinctModels(STAIRS)) {
            assertTrue(Files.exists(modelPath(model)),
                    STAIRS + " selects " + model + ", which does not exist");
            for (Map.Entry<String, String> texture : resolveTextures(model).entrySet()) {
                String reference = texture.getValue();
                assertFalse(reference.startsWith("#"),
                        model + " left texture alias " + texture.getKey() + " unresolved");
                if (!reference.startsWith("britannia_mod:")) {
                    continue;
                }
                assertTrue(Files.exists(texturePath(reference)),
                        "missing texture " + reference + " referenced by " + model);
            }
        }
    }

    @Test
    void theFoundationStairsAreFullyWiredUp() throws IOException {
        String blocks = Files.readString(ROOT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(ROOT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(ROOT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));

        assertTrue(blocks.contains("\"" + STAIRS + "\""), STAIRS + " is not registered");
        assertTrue(items.contains("\"" + STAIRS + "\""), STAIRS + " has no BlockItem");
        assertTrue(tabs.contains(STAIRS.toUpperCase() + "_ITEM"),
                STAIRS + " is missing from the creative tab");
        assertTrue(lang.has("block.britannia_mod." + STAIRS), STAIRS + " has no display name");
        assertTrue(Files.exists(itemModelPath(STAIRS)), STAIRS + " has no item model");

        // The one wiring rule that inverts: this block declares noLootTable() rather than shipping
        // a table, exactly as the moongate does. No player break exists to drop anything, and the
        // creative removal that does exist never drops.
        assertFalse(Files.exists(lootTablePath(STAIRS)),
                STAIRS + " ships a loot table, but the only removal it has is a creative one, which "
                        + "never drops. A table here is dead data that reads as though it does.");
        assertTrue(Files.readString(STAIRS_SOURCE).contains(".noLootTable()"),
                STAIRS + " must be explicitly drop-free");
    }

    @Test
    void theFoundationStairsItemModelPointsAtARealCanonicalVariant() throws IOException {
        String parent = read(itemModelPath(STAIRS)).get("parent").getAsString();
        assertTrue(allModels(STAIRS).contains(parent),
                STAIRS + " item model must render one of the block's own variants, so that the "
                        + "inventory icon and the placed block agree; it shows " + parent);
        assertTrue(Files.exists(modelPath(parent)), STAIRS + " item model parent does not exist");
    }

    /**
     * Permanent to a player, removable by an operator -- and the operator half is the fragile one.
     *
     * <p>Hardness is read in exactly one place, {@code getDestroyProgress}, which is why it stops a
     * survival or adventure player: they accumulate nothing and the break never completes. It is
     * also why it does not stop an operator, since
     * {@code ServerPlayerGameMode.destroyBlock} skips that gate for a creative player and goes
     * straight to {@code removeBlock}. Bedrock behaves the same way.
     *
     * <p>So the absence of an {@code onDestroyedByPlayer} override is a requirement here, not an
     * omission. That method is the one question asked in <em>every</em> game mode, so an override
     * refusing there would take the operator's removal away along with everybody else's -- which is
     * exactly the defect this guards against returning.
     *
     * <p>{@code FoundationStairsPermanenceGameTests} drives the live behaviour; this holds the
     * declarations that produce it, because a unit test that touches {@code BlockRegistry} without
     * bootstrapping Minecraft poisons the whole test JVM.
     */
    @Test
    void theFoundationStairsStopPlayersByHardnessAndLeaveTheOperatorAWayOut() throws IOException {
        String source = Files.readString(STAIRS_SOURCE);

        assertTrue(source.contains("extends StairBlock"),
                STAIRS + " must be an ordinary stair everywhere except in what may remove it");
        assertTrue(source.contains("strength(-1.0F, 3600000.0F)"),
                STAIRS + " must carry bedrock's hardness and blast resistance, which is what stops "
                        + "a survival or adventure break and every explosion");

        // Read the code, not the prose. Both method names are discussed in that class's own
        // javadoc, which is where the reasoning for their absence belongs.
        String code = withoutComments(source);
        assertFalse(code.contains("onDestroyedByPlayer"),
                STAIRS + " overrides onDestroyedByPlayer. That is the one removal question asked in "
                        + "every game mode, so refusing there locks the creative operator out along "
                        + "with everybody else and a misplaced stair becomes permanent for the "
                        + "person who placed it.");
        assertFalse(code.contains("getDestroyProgress"),
                STAIRS + " overrides getDestroyProgress, which is the hook the declared hardness "
                        + "already answers. A second opinion there can only disagree with it.");
    }

    /**
     * Java source with its block and line comments removed.
     *
     * <p>Every other source check here is for something that must be present, where a stray mention
     * in a comment is harmless. The two above are for something that must be <em>absent</em>, and
     * that inverts: the class documents at length why it does not override those two methods, so a
     * plain {@code contains} would read its own explanation as the violation.
     */
    private static String withoutComments(String source) {
        StringBuilder code = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                index = end < 0 ? source.length() : end + 2;
            } else if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                index = end < 0 ? source.length() : end;
            } else {
                code.append(source.charAt(index++));
            }
        }
        return code.toString();
    }

    /**
     * Permanence is declared on the block, and the housing rule is joined rather than duplicated.
     *
     * <p>The stairs sit in {@code house_foundation} beside the other perimeter courses, so a player
     * who reaches for one inside a house is told why by the rule that already exists. That rule
     * exempts creative, which is the same operator carve-out the hardness leaves open, arriving from
     * the other direction. A tool tier would be meaningless either way: no tool reaches this block.
     */
    @Test
    void theFoundationStairsJoinThePerimeterTagAndCarryNoToolTier() throws IOException {
        assertTrue(tagValues("britannia_mod/tags/block/house_foundation.json")
                        .contains("britannia_mod:" + STAIRS),
                STAIRS + " is a housing-boundary block and must be classified as perimeter, so the "
                        + "housing refusal explains itself instead of the block silently not moving");

        String registry = Files.readString(ROOT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        int from = registry.indexOf("\"" + STAIRS + "\"");
        assertTrue(from >= 0, STAIRS + " is not registered");
        String statement = registry.substring(from, registry.indexOf(");", from));
        assertFalse(statement.contains("requiresCorrectToolForDrops"),
                STAIRS + " cannot be mined by any player, so a tool tier promises drops no "
                        + "tool can ever produce");
        assertFalse(tagValues(PICKAXE_TAG).contains("britannia_mod:" + STAIRS),
                STAIRS + " cannot be mined at all, so it does not belong in mineable/pickaxe");
    }
}
