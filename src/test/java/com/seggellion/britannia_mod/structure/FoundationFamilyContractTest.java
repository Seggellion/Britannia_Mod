package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
class FoundationFamilyContractTest {

    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = ROOT.resolve("src/main/resources/data");

    /** Blocks whose sides come from the three-variant normal brick set. */
    private static final List<String> NORMAL_FAMILY =
            List.of("brick_foundation_oak", "brick_foundation_spruce", "brick_foundation_flagstone");

    /** Blocks whose sides come from the single dark texture. Must never see the normal set. */
    private static final List<String> DARK_FAMILY =
            List.of("brick_foundation_sandstone", "brick_foundation_dark_sandstone");

    private static final Set<String> NORMAL_SIDE_TEXTURES = new LinkedHashSet<>(List.of(
            "britannia_mod:block/structure/brick_foundation_01",
            "britannia_mod:block/structure/brick_foundation_02",
            "britannia_mod:block/structure/brick_foundation_03"));

    private static final String DARK_SIDE_TEXTURE = "britannia_mod:block/structure/brick_dark_foundation";

    private static final List<String> SIDE_FACES = List.of("down", "north", "south", "west", "east");

    @Test
    void everyNormalFoundationReachesAllThreeBrickTextures() throws IOException {
        for (String block : NORMAL_FAMILY) {
            Set<String> reached = new LinkedHashSet<>();
            for (String model : blockstateModels(block)) {
                reached.addAll(sideTexturesOf(model));
            }
            assertEquals(NORMAL_SIDE_TEXTURES, reached,
                    block + " must be able to show exactly the three normal brick textures");
        }
    }

    @Test
    void darkFoundationsNeverReferenceTheNormalBrickTextureSet() throws IOException {
        for (String block : DARK_FAMILY) {
            for (String model : blockstateModels(block)) {
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
        // brick_foundation_sandstone is named like the normal family but is a dark block.
        // This is the exact trap a name-prefix rule would fall into, so pin it explicitly.
        for (String model : blockstateModels("brick_foundation_sandstone")) {
            assertEquals("britannia_mod:block/structure/foundation/dark_side",
                    readModel(model).get("parent").getAsString(),
                    "brick_foundation_sandstone must parent the dark family model");
        }
        for (String model : blockstateModels("brick_foundation_oak")) {
            assertTrue(readModel(model).get("parent").getAsString()
                            .startsWith("britannia_mod:block/structure/foundation/brick_side_"),
                    "brick_foundation_oak must parent a normal brick family model");
        }
    }

    @Test
    void flagstoneUsesFlagstoneTopsAndNoOtherFamilysTop() throws IOException {
        Set<String> tops = new LinkedHashSet<>();
        for (String model : blockstateModels("brick_foundation_flagstone")) {
            tops.add(resolveTextures(model).get("up"));
        }
        assertEquals(Set.of(
                        "britannia_mod:block/structure/flagstone_01",
                        "britannia_mod:block/structure/flagstone_02"),
                tops, "flagstone foundation must reach both flagstone tops");
    }

    @Test
    void normalFoundationsVaryOnlyByTextureAndKeepOneGeometry() throws IOException {
        for (String block : NORMAL_FAMILY) {
            for (String model : blockstateModels(block)) {
                assertEquals("minecraft:block/cube", rootParentOf(model),
                        model + " must keep the shared full-cube foundation geometry");
            }
        }
    }

    @Test
    void everyVariantInAFoundationBlockstateIsDistinct() throws IOException {
        List<String> all = new ArrayList<>(NORMAL_FAMILY);
        all.addAll(DARK_FAMILY);
        for (String block : all) {
            List<String> models = blockstateModels(block);
            assertEquals(models.size(), new LinkedHashSet<>(models).size(),
                    block + " lists a duplicate model, which would skew the random distribution");
        }
    }

    @Test
    void everyReferencedFoundationModelAndTextureExists() throws IOException {
        List<String> all = new ArrayList<>(NORMAL_FAMILY);
        all.addAll(DARK_FAMILY);
        for (String block : all) {
            for (String model : blockstateModels(block)) {
                for (Map.Entry<String, String> texture : resolveTextures(model).entrySet()) {
                    String reference = texture.getValue();
                    assertFalse(reference.startsWith("#"),
                            model + " left texture alias " + texture.getKey() + " unresolved");
                    if (!reference.startsWith("britannia_mod:")) {
                        continue; // vanilla textures ship in the Minecraft jar, not this repo
                    }
                    assertTrue(Files.exists(texturePath(reference)),
                            "missing texture " + reference + " referenced by " + model);
                }
            }
        }
    }

    @Test
    void everyFoundationBlockIsFullyWiredUp() throws IOException {
        List<String> all = new ArrayList<>(NORMAL_FAMILY);
        all.addAll(DARK_FAMILY);

        String blocks = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(
                ROOT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        JsonArray pickaxe = read(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"))
                .getAsJsonArray("values");

        Set<String> mineable = new LinkedHashSet<>();
        pickaxe.forEach(entry -> mineable.add(entry.getAsString()));

        for (String block : all) {
            String constant = block.toUpperCase();
            assertTrue(blocks.contains("\"" + block + "\""), block + " is not registered");
            assertTrue(items.contains("\"" + block + "\""), block + " has no BlockItem");
            assertTrue(tabs.contains(constant + "_ITEM"), block + " is missing from the creative tab");
            assertTrue(lang.has("block.britannia_mod." + block), block + " has no display name");
            assertTrue(Files.exists(itemModelPath(block)), block + " has no item model");
            assertTrue(Files.exists(DATA.resolve("britannia_mod/loot_table/blocks/" + block + ".json")),
                    block + " has no loot table, so it would drop nothing");
            assertTrue(mineable.contains("britannia_mod:" + block),
                    block + " uses requiresCorrectToolForDrops but is not pickaxe-mineable");
        }
    }

    @Test
    void foundationItemModelsPointAtARealCanonicalVariant() throws IOException {
        List<String> all = new ArrayList<>(NORMAL_FAMILY);
        all.addAll(DARK_FAMILY);
        for (String block : all) {
            String parent = read(itemModelPath(block)).get("parent").getAsString();
            assertTrue(blockstateModels(block).contains(parent),
                    block + " item model must render one of the block's own variants");
            assertTrue(Files.exists(modelPath(parent)), block + " item model parent does not exist");
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Every model a block's blockstate can select, across plain and random-array variants. */
    private static List<String> blockstateModels(String block) throws IOException {
        JsonObject variants = read(ASSETS.resolve("blockstates/" + block + ".json"))
                .getAsJsonObject("variants");
        assertNotNull(variants, block + " has no variants block");
        List<String> models = new ArrayList<>();
        for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            JsonElement value = variant.getValue();
            if (value.isJsonArray()) {
                for (JsonElement entry : value.getAsJsonArray()) {
                    models.add(entry.getAsJsonObject().get("model").getAsString());
                }
            } else {
                models.add(value.getAsJsonObject().get("model").getAsString());
            }
        }
        return models;
    }

    /** The distinct textures a model paints on its four sides and underside. */
    private static Set<String> sideTexturesOf(String model) throws IOException {
        Map<String, String> textures = resolveTextures(model);
        Set<String> sides = new LinkedHashSet<>();
        for (String face : SIDE_FACES) {
            String texture = textures.get(face);
            assertNotNull(texture, model + " does not define the " + face + " face");
            sides.add(texture);
        }
        return sides;
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

    private static JsonObject readModel(String model) throws IOException {
        return read(modelPath(model));
    }

    private static Path modelPath(String model) {
        return ASSETS.resolve("models/" + model.substring("britannia_mod:".length()) + ".json");
    }

    private static Path itemModelPath(String block) {
        return ASSETS.resolve("models/item/" + block + ".json");
    }

    private static Path texturePath(String texture) {
        return ASSETS.resolve("textures/" + texture.substring("britannia_mod:".length()) + ".png");
    }

    private static JsonObject read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing file " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
