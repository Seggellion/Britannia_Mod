package com.seggellion.britannia_mod.wildresource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceAssetContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");

    @Test
    void blockstatesModelsAndTexturesResolve() throws IOException {
        assertBlock("sulphurous_ash_patch", "block/wild_resource/sulphurous_ash_patch");
        assertBlock("black_lipped_oyster", "block/wild_resource/black_lipped_oyster");
        assertBlock("dung", "block/wild_resource/dung");
        assertBlock("blood_moss", "item/reagent_blood_moss");

        assertItem("sulphurous_ash", "item/reagent_sulphurous_ash");
        assertItem("black_pearl", "item/wild_resource/black_pearl");
        assertItem("dung", "block/wild_resource/dung");
        assertItem("blood_moss", "item/reagent_blood_moss");
    }

    @Test
    void placeholderPngsUseProjectScaleAndTransparentBackgrounds() throws IOException {
        assertPng("block/wild_resource/sulphurous_ash_patch", 32);
        assertPng("block/wild_resource/black_lipped_oyster", 32);
        assertPng("block/wild_resource/dung", 32);
        assertPng("item/wild_resource/black_pearl", 16);
        assertPng("item/reagent_blood_moss", 32);
    }

    @Test
    void languageCreativeTabAndLootCoverageIsComplete() throws IOException {
        JsonObject language = readJson(ASSETS.resolve("lang/en_us.json"));
        for (String key : List.of(
                "item.britannia_mod.sulphurous_ash",
                "block.britannia_mod.sulphurous_ash_patch",
                "block.britannia_mod.black_lipped_oyster",
                "item.britannia_mod.black_pearl",
                "item.britannia_mod.dung",
                "block.britannia_mod.dung",
                "item.britannia_mod.blood_moss",
                "block.britannia_mod.blood_moss"
        )) {
            assertTrue(language.has(key), key);
        }

        String creative = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(creative.contains("ItemRegistry.SULPHUROUS_ASH"));
        assertTrue(creative.contains("ItemRegistry.BLACK_PEARL"));
        assertTrue(creative.contains("ItemRegistry.DUNG"));
        assertTrue(creative.contains("ItemRegistry.BLOOD_MOSS"));

        JsonObject ashLoot = readJson(RESOURCES.resolve(
                "data/britannia_mod/loot_table/blocks/sulphurous_ash_patch.json"
        ));
        JsonObject oysterLoot = readJson(RESOURCES.resolve(
                "data/britannia_mod/loot_table/blocks/black_lipped_oyster.json"
        ));
        JsonObject dungLoot = readJson(RESOURCES.resolve(
                "data/britannia_mod/loot_table/blocks/dung.json"
        ));
        assertEquals("britannia_mod:sulphurous_ash", lootItem(ashLoot));
        assertEquals("britannia_mod:black_pearl", lootItem(oysterLoot));
        assertEquals("britannia_mod:dung", lootItem(dungLoot));
    }

    private static void assertBlock(String path, String expectedTexture) throws IOException {
        JsonObject blockstate = readJson(ASSETS.resolve("blockstates/" + path + ".json"));
        String modelId = blockstate.getAsJsonObject("variants").getAsJsonObject("")
                .get("model").getAsString();
        Path modelPath = resolveModel(modelId);
        assertTrue(Files.isRegularFile(modelPath), modelId);
        JsonObject model = readJson(modelPath);
        assertTrue(model.has("render_type"), path);
        assertTrue(model.getAsJsonObject("textures").entrySet().stream()
                .anyMatch(entry -> entry.getValue().getAsString().equals("britannia_mod:" + expectedTexture)));
        assertTrue(Files.isRegularFile(resolveTexture("britannia_mod:" + expectedTexture)));
    }

    private static void assertItem(String path, String expectedTexture) throws IOException {
        JsonObject model = readJson(ASSETS.resolve("models/item/" + path + ".json"));
        assertTrue(model.get("parent").getAsString().endsWith("item/generated"));
        assertEquals("britannia_mod:" + expectedTexture,
                model.getAsJsonObject("textures").get("layer0").getAsString());
        assertTrue(Files.isRegularFile(resolveTexture("britannia_mod:" + expectedTexture)));
    }

    private static void assertPng(String texture, int expectedSize) throws IOException {
        Path path = resolveTexture("britannia_mod:" + texture);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        assertEquals(expectedSize, image.getWidth(), path.toString());
        assertEquals(expectedSize, image.getHeight(), path.toString());
        boolean transparent = false;
        boolean opaque = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                opaque |= alpha > 0;
            }
        }
        assertTrue(transparent, path + " needs a transparent background");
        assertTrue(opaque, path + " needs visible artwork");
    }

    private static String lootItem(JsonObject loot) {
        return loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .get("name").getAsString();
    }

    private static Path resolveModel(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return RESOURCES.resolve("assets").resolve(location.getNamespace()).resolve("models")
                .resolve(location.getPath() + ".json");
    }

    private static Path resolveTexture(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return RESOURCES.resolve("assets").resolve(location.getNamespace()).resolve("textures")
                .resolve(location.getPath() + ".png");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
