package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the foundation family's resources, so that the tests over them can be assertions only.
 *
 * <p>A foundation block is a blockstate that selects models, and a model that inherits most of
 * what it paints from a shared parent. Nothing about either is answerable by looking at one file:
 * the masonry a block shows lives two levels up its parent chain, and the models it can select
 * are spread across a variant map that is sometimes one key and sometimes fifty-six. Every test
 * that wants to say anything about a foundation therefore has to walk both structures first,
 * which is why this walking lives here rather than being copied into each of them.
 *
 * <p>These are readers, not rules. The only assertions here guard the read itself -- a file that
 * does not exist, a face a model never defines -- so that a test fails on the missing resource it
 * is actually about rather than on a NullPointerException three frames away.
 */
final class FoundationAssets {

    static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");
    static final Path DATA = ROOT.resolve("src/main/resources/data");

    /** The faces a ground slab shows to the basement below it and to the world outside. */
    static final List<String> SIDE_FACES = List.of("down", "north", "south", "west", "east");

    /** The three-variant light masonry set the normal brick family is built from. */
    static final Set<String> NORMAL_SIDE_TEXTURES = new LinkedHashSet<>(List.of(
            "britannia_mod:block/structure/brick_foundation_01",
            "britannia_mod:block/structure/brick_foundation_02",
            "britannia_mod:block/structure/brick_foundation_03"));

    /** The single texture the dark family is built from. */
    static final String DARK_SIDE_TEXTURE = "britannia_mod:block/structure/brick_dark_foundation";

    private FoundationAssets() {
    }

    /* -- blockstates ---------------------------------------------------------------------- */

    /**
     * Each variant key mapped to every model that key can select.
     *
     * <p>Flattens both blockstate forms. A block with one appearance writes a single object; a
     * block that picks at random writes an array, and Minecraft chooses between the entries of
     * one key by hashing the block position. Keeping the key means a test can still ask about
     * that choice -- which is per key, not per block.
     */
    static Map<String, List<String>> variantModels(String block) throws IOException {
        JsonObject variants = blockstate(block).getAsJsonObject("variants");
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

    /** Every model a block's blockstate can select, with the keys discarded. */
    static List<String> allModels(String block) throws IOException {
        List<String> models = new ArrayList<>();
        variantModels(block).values().forEach(models::addAll);
        return models;
    }

    /** Every distinct model a block can select, for tests that do not care how often. */
    static Set<String> distinctModels(String block) throws IOException {
        return new LinkedHashSet<>(allModels(block));
    }

    static List<JsonElement> asList(JsonElement value) {
        if (value == null) return List.of();
        if (!value.isJsonArray()) return List.of(value);
        List<JsonElement> entries = new ArrayList<>();
        value.getAsJsonArray().forEach(entries::add);
        return entries;
    }

    /* -- models --------------------------------------------------------------------------- */

    /**
     * Flattens a model's texture map down its parent chain, child entries winning.
     *
     * <p>This is what the block really paints. A foundation model names one texture of its own
     * and inherits five, so reading the file alone tells you almost nothing about it.
     */
    static Map<String, String> resolveTextures(String model) throws IOException {
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

    /** The distinct textures a model paints on its four sides and its underside. */
    static Set<String> sideTexturesOf(String model) throws IOException {
        Map<String, String> textures = resolveTextures(model);
        Set<String> sides = new LinkedHashSet<>();
        for (String face : SIDE_FACES) {
            String texture = textures.get(face);
            assertNotNull(texture, model + " does not define the " + face + " face");
            sides.add(texture);
        }
        return sides;
    }

    /** The vanilla model at the top of the parent chain, i.e. the shape the block really is. */
    static String rootParentOf(String model) throws IOException {
        String current = model;
        String parent = null;
        while (current != null && current.startsWith("britannia_mod:")) {
            JsonElement next = readModel(current).get("parent");
            parent = next == null ? null : next.getAsString();
            current = parent;
        }
        return parent;
    }

    static String parentOf(String model) throws IOException {
        return readModel(model).get("parent").getAsString();
    }

    /* -- paths ---------------------------------------------------------------------------- */

    static JsonObject blockstate(String block) throws IOException {
        return read(ASSETS.resolve("blockstates/" + block + ".json"));
    }

    static JsonObject readModel(String model) throws IOException {
        return read(modelPath(model));
    }

    static Path modelPath(String model) {
        return ASSETS.resolve("models/" + model.substring("britannia_mod:".length()) + ".json");
    }

    static Path texturePath(String texture) {
        return ASSETS.resolve("textures/" + texture.substring("britannia_mod:".length()) + ".png");
    }

    static Path itemModelPath(String block) {
        return ASSETS.resolve("models/item/" + block + ".json");
    }

    static Path lootTablePath(String block) {
        return DATA.resolve("britannia_mod/loot_table/blocks/" + block + ".json");
    }

    /** The block ids in one tag file, as written. */
    static Set<String> tagValues(String relative) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        read(DATA.resolve(relative)).getAsJsonArray("values")
                .forEach(entry -> values.add(entry.getAsString()));
        return values;
    }

    static JsonObject read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing file " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
