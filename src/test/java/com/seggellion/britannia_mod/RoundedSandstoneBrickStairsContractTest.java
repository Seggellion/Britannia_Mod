package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Resource and registration contracts for the rounded sandstone-brick stair family. */
class RoundedSandstoneBrickStairsContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final Path MODELS = ASSETS.resolve("models/block/structure/sandstone");
    private static final Path TEXTURES = ASSETS.resolve("textures/block/structure/sandstone");
    private static final List<String> FACINGS = List.of("east", "north", "south", "west");
    private static final List<String> HALVES = List.of("bottom", "top");
    private static final List<String> SHAPES = List.of(
            "inner_left", "inner_right", "outer_left", "outer_right", "straight");
    private static final Map<String, Integer> BASE_YAW = Map.of(
            "east", 0, "north", 270, "south", 90, "west", 180);

    @Test
    void everySuppliedTopTextureHasOneRegisteredStairBlockAndItem() throws Exception {
        Pattern name = Pattern.compile("custom_sandstone_brick_top_(\\d+)\\.png");
        Set<Integer> textureVariants = new HashSet<>();
        try (Stream<Path> paths = Files.list(TEXTURES)) {
            paths.map(path -> path.getFileName().toString()).forEach(fileName -> {
                Matcher matcher = name.matcher(fileName);
                if (matcher.matches()) textureVariants.add(Integer.parseInt(matcher.group(1)));
            });
        }
        assertEquals(Set.of(0, 1, 2, 3), textureVariants);

        String blocks = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));

        assertTrue(blocks.contains("new StairBlock("));
        assertTrue(blocks.contains("BlockBehaviour.Properties.ofFullCopy(Blocks.SANDSTONE_STAIRS)"));
        assertTrue(blocks.contains("CUSTOM_SANDSTONE_BRICK.value().defaultBlockState()"));
        for (int variant : textureVariants) {
            String id = id(variant);
            String constant = id.toUpperCase();
            assertTrue(blocks.contains("DeferredHolder<Block, StairBlock> " + constant));
            assertTrue(blocks.contains("registerSandstoneBrickStairs(\"" + id + "\")"));
            assertTrue(items.contains("\"" + id + "\""));
            assertTrue(items.contains("BlockRegistry." + constant + ".get()"));
            assertTrue(tabs.contains("ItemRegistry." + constant + "_ITEM.get()"));
            assertTrue(language.has("block.britannia_mod." + id));
        }
    }

    @Test
    void everyVariantCoversTheExactFortyVanillaStairStates() throws Exception {
        Set<String> expectedStates = new HashSet<>();
        for (String facing : FACINGS) {
            for (String half : HALVES) {
                for (String shape : SHAPES) {
                    expectedStates.add("facing=" + facing + ",half=" + half + ",shape=" + shape);
                }
            }
        }

        for (int variant = 0; variant < 4; variant++) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + id(variant) + ".json"))
                    .getAsJsonObject("variants");
            assertEquals(expectedStates, variants.keySet());

            for (String state : expectedStates) {
                JsonObject value = variants.getAsJsonObject(state);
                assertTrue(value.get("uvlock").getAsBoolean(), state);
                assertEquals(expectedModel(variant, state), value.get("model").getAsString(), state);
                boolean top = state.contains("half=top");
                assertEquals(top ? 180 : 0, value.has("x") ? value.get("x").getAsInt() : 0, state);
                assertEquals(expectedYaw(state), value.has("y") ? value.get("y").getAsInt() : 0,
                        state);
            }
        }
    }

    @Test
    void straightInnerAndOuterModelsReuseTheRightTopTextureWithoutDuplication() throws Exception {
        Map<String, String> shapes = Map.of(
                "", "rounded_sandstone_brick_stairs",
                "_inner", "rounded_sandstone_brick_stairs_inner",
                "_outer", "rounded_sandstone_brick_stairs_outer");

        for (int variant = 0; variant < 4; variant++) {
            String texture = "britannia_mod:block/structure/sandstone/custom_sandstone_brick_top_"
                    + variant;
            for (Map.Entry<String, String> shape : shapes.entrySet()) {
                JsonObject model = json(MODELS.resolve(
                        "custom_sandstone_brick_stairs" + shape.getKey() + "_" + variant + ".json"));
                assertEquals("britannia_mod:block/structure/sandstone/" + shape.getValue(),
                        model.get("parent").getAsString());
                assertEquals(texture, model.getAsJsonObject("textures").get("all").getAsString());
            }

            JsonObject item = json(ASSETS.resolve("models/item/" + id(variant) + ".json"));
            assertEquals("britannia_mod:block/structure/sandstone/" + id(variant),
                    item.get("parent").getAsString());
        }
    }

    @Test
    void geometryUsesACompactDeliberateBevelAndStaysInsideOneBlock() throws Exception {
        Map<String, Integer> expectedElementCounts = Map.of(
                "rounded_sandstone_brick_stairs", 7,
                "rounded_sandstone_brick_stairs_inner", 11,
                "rounded_sandstone_brick_stairs_outer", 15);

        for (Map.Entry<String, Integer> expected : expectedElementCounts.entrySet()) {
            JsonObject model = json(MODELS.resolve(expected.getKey() + ".json"));
            assertEquals("minecraft:block/stairs", model.get("parent").getAsString());
            JsonArray elements = model.getAsJsonArray("elements");
            assertEquals(expected.getValue(), elements.size());

            boolean hasHalfVoxelBevel = false;
            for (JsonElement elementValue : elements) {
                JsonObject element = elementValue.getAsJsonObject();
                JsonArray from = element.getAsJsonArray("from");
                JsonArray to = element.getAsJsonArray("to");
                for (int axis = 0; axis < 3; axis++) {
                    double low = from.get(axis).getAsDouble();
                    double high = to.get(axis).getAsDouble();
                    assertTrue(low >= 0.0D && high <= 16.0D && low < high,
                            expected.getKey() + " has invalid element bounds");
                    hasHalfVoxelBevel |= low % 1.0D == 0.5D || high % 1.0D == 0.5D;
                }
                assertEquals(Set.of("down", "up", "north", "south", "west", "east"),
                        element.getAsJsonObject("faces").keySet());
            }
            assertTrue(hasHalfVoxelBevel, expected.getKey() + " regressed to square vanilla geometry");
        }
    }

    @Test
    void stairsDropThemselvesAndJoinTheVanillaBlockAndItemTags() throws Exception {
        JsonArray blockTag = json(RESOURCES.resolve("data/minecraft/tags/block/stairs.json"))
                .getAsJsonArray("values");
        JsonArray itemTag = json(RESOURCES.resolve("data/minecraft/tags/item/stairs.json"))
                .getAsJsonArray("values");
        JsonArray pickaxeTag = json(RESOURCES.resolve(
                "data/minecraft/tags/block/mineable/pickaxe.json")).getAsJsonArray("values");

        for (int variant = 0; variant < 4; variant++) {
            String id = id(variant);
            String resource = "britannia_mod:" + id;
            assertTrue(blockTag.contains(JsonParser.parseString("\"" + resource + "\"")));
            assertTrue(itemTag.contains(JsonParser.parseString("\"" + resource + "\"")));
            assertTrue(pickaxeTag.contains(JsonParser.parseString("\"" + resource + "\"")));

            JsonObject loot = json(RESOURCES.resolve(
                    "data/britannia_mod/loot_table/blocks/" + id + ".json"));
            JsonObject entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals(resource, entry.get("name").getAsString());
        }
    }

    private static String expectedModel(int variant, String state) {
        String suffix = state.contains("shape=inner_") ? "_inner"
                : state.contains("shape=outer_") ? "_outer" : "";
        return "britannia_mod:block/structure/sandstone/custom_sandstone_brick_stairs"
                + suffix + "_" + variant;
    }

    private static int expectedYaw(String state) {
        String facing = FACINGS.stream().filter(value -> state.contains("facing=" + value))
                .findFirst().orElseThrow();
        int yaw = BASE_YAW.get(facing);
        if (state.contains("half=bottom") && state.endsWith("_left")) yaw += 270;
        if (state.contains("half=top") && state.endsWith("_right")) yaw += 90;
        return yaw % 360;
    }

    private static String id(int variant) {
        return "custom_sandstone_brick_stairs_" + variant;
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
