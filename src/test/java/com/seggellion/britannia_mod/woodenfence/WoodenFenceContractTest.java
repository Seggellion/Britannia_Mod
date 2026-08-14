package com.seggellion.britannia_mod.woodenfence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodenFenceContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = PROJECT.resolve("src/main/resources/data");

    @Test
    void blockstateCoversEveryFacingAndConnectionCombination() throws IOException {
        JsonObject variants = json(ASSETS.resolve("blockstates/wooden_fence.json"))
            .getAsJsonObject("variants");
        assertEquals(64, variants.size(), "four facings x sixteen cardinal connection masks");

        Map<String, Integer> topologies = new HashMap<>();
        for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            String key = variant.getKey();
            assertTrue(key.contains("facing=") && key.contains("north=") && key.contains("east=")
                && key.contains("south=") && key.contains("west="), key);
            String model = variant.getValue().getAsJsonObject().get("model").getAsString();
            String topology = model.substring(model.lastIndexOf('/') + 1);
            if (topology.equals("end_mirrored")) topology = "end";
            topologies.merge(topology, 1, Integer::sum);
        }

        assertEquals(Map.of(
            "isolated", 4,
            "end", 16,
            "straight", 8,
            "corner", 16,
            "t_junction", 16,
            "cross", 4
        ), topologies);
    }

    @Test
    void everyTopologyUsesDedicatedUltimaCraftGeometryAndTexture() throws IOException {
        Set<String> models = Set.of(
            "isolated", "end", "end_mirrored", "straight", "corner", "t_junction", "cross");
        for (String name : models) {
            JsonObject model = json(ASSETS.resolve(
                "models/block/structure/wooden_fence/" + name + ".json"));
            assertEquals("britannia_mod:block/structure/wooden_fence",
                model.getAsJsonObject("textures").get("wood").getAsString());
            assertTrue(model.getAsJsonArray("elements").size() >= 6, name);
            model.getAsJsonArray("elements").forEach(element -> {
                JsonObject cuboid = element.getAsJsonObject();
                assertEquals(6, cuboid.getAsJsonObject("faces").size(), name + " has a missing face");
                for (JsonElement coordinate : cuboid.getAsJsonArray("from")) {
                    assertTrue(coordinate.getAsDouble() >= 0, name + " leaves the block horizontally");
                }
                assertTrue(cuboid.getAsJsonArray("to").get(0).getAsDouble() <= 16, name);
                assertTrue(cuboid.getAsJsonArray("to").get(1).getAsDouble() <= 18, name);
                assertTrue(cuboid.getAsJsonArray("to").get(2).getAsDouble() <= 16, name);
            });
        }

        assertEquals("britannia_mod:block/structure/wooden_fence/isolated",
            json(ASSETS.resolve("models/item/wooden_fence.json")).get("parent").getAsString());
    }

    @Test
    void textureMatchesBannisterResolutionButUsesALighterWoodPalette() throws IOException {
        BufferedImage texture = ImageIO.read(ASSETS.resolve(
            "textures/block/structure/wooden_fence.png").toFile());
        BufferedImage bannisterWood = ImageIO.read(ASSETS.resolve(
            "textures/block/structure/plaster/wood_support.png").toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertEquals(bannisterWood.getWidth(), texture.getWidth());
        assertEquals(bannisterWood.getHeight(), texture.getHeight());
        assertTrue(luminance(texture) > luminance(bannisterWood) + 20,
            "wooden_fence should be recognizably lighter without relying on model tint");
    }

    @Test
    void registrationLootTagsLocalizationAndCreativeTabAreComplete() throws IOException {
        assertContains("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java",
            "WOODEN_FENCE = BLOCKS.register(\"wooden_fence\"");
        assertContains("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java",
            "WOODEN_FENCE_ITEM = ITEMS.register(\"wooden_fence\"");
        assertContains("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java",
            "safeAccept(output, ItemRegistry.WOODEN_FENCE_ITEM.get())");
        assertContains("src/main/resources/assets/britannia_mod/lang/en_us.json",
            "\"block.britannia_mod.wooden_fence\": \"Wooden Fence\"");

        assertEquals("britannia_mod:wooden_fence", json(DATA.resolve(
            "britannia_mod/loot_table/blocks/wooden_fence.json"))
            .getAsJsonArray("pools").get(0).getAsJsonObject()
            .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertContains("src/main/resources/data/minecraft/tags/block/mineable/axe.json",
            "britannia_mod:wooden_fence");
        assertContains("src/main/resources/data/minecraft/tags/block/fences.json",
            "britannia_mod:wooden_fence");
        assertContains("src/main/resources/data/minecraft/tags/item/fences.json",
            "britannia_mod:wooden_fence");
    }

    @Test
    void collisionIsEdgeBasedAndBroaderThanBannisterWithoutBeingAFullCube() throws IOException {
        String source = Files.readString(PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/block/WoodenFenceBlock.java"));
        assertTrue(source.contains("THICKNESS = 4.0D"));
        assertTrue(source.contains("Shapes.or(edge("));
        assertTrue(source.contains("getCollisionShape"));
        assertFalse(source.contains("Block.box(0, 0, 0, 16, 16, 16)"));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertContains(String relative, String expected) throws IOException {
        assertTrue(Files.readString(PROJECT.resolve(relative)).contains(expected), relative);
    }

    private static double luminance(BufferedImage image) {
        double total = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                total += 0.2126 * (rgb >> 16 & 255)
                    + 0.7152 * (rgb >> 8 & 255)
                    + 0.0722 * (rgb & 255);
            }
        }
        return total / (image.getWidth() * image.getHeight());
    }
}
