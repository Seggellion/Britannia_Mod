package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Asset, registration, geometry, and selective-recolour contracts for the market-stall family. */
class MarketStallContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path DATA = PROJECT.resolve("src/main/resources/data/britannia_mod");
    private static final String[] COLORS = {"red", "blue", "green", "purple"};

    @Test
    void authoritativeGeometryAndPostBakeEnvelopeArePreserved() throws Exception {
        JsonObject model = json(ASSETS.resolve("models/block/new_assets/market_stall.json"));
        assertEquals(12, model.getAsJsonArray("elements").size());
        assertEquals(256, model.getAsJsonArray("texture_size").get(0).getAsInt());
        assertEquals("minecraft:cutout", model.get("render_type").getAsString());
        assertFalse(model.get("ambientocclusion").getAsBoolean());

        Bounds raw = rotatedBounds(model.getAsJsonArray("elements"));
        assertEquals(-16.0D, raw.minX, 0.000001D);
        assertEquals(32.0D, raw.maxX, 0.000001D);
        assertEquals(-12.815764D, raw.minY, 0.000001D);
        assertEquals(32.0D, raw.maxY, 0.000001D);
        assertEquals(-11.5D, raw.minZ, 0.000001D);
        assertEquals(16.031494D, raw.maxZ, 0.000001D);

        String normalizer = javaSource("client/model/MarketStallNormalizedModel.java");
        assertTrue(normalizer.contains("DEPTH_MIN = -0.71875F"));
        assertTrue(normalizer.contains("DEPTH_SCALE = 0.5811526F"));
        assertTrue(normalizer.contains("GROUND_OFFSET = 0.8009853F"));
        assertEquals(48.0D, raw.maxX - raw.minX, 0.000001D);
        assertEquals(16.0D, (raw.maxZ - raw.minZ) * 0.5811526281789339D, 0.000001D);
        assertEquals(0.0D, raw.minY + 0.8009852670521564D * 16.0D, 0.000001D);
        assertTrue(raw.maxY + 0.8009852670521564D * 16.0D < 48.0D);
    }

    @Test
    void fourModelsShareGeometryAndOnlyMaskedRedFabricPixelsChange() throws Exception {
        BufferedImage red = ImageIO.read(texture("red").toFile());
        assertEquals(256, red.getWidth());
        assertEquals(256, red.getHeight());
        for (String color : COLORS) {
            JsonObject child = json(ASSETS.resolve("models/block/new_assets/market_stall_" + color + ".json"));
            assertEquals("britannia_mod:block/new_assets/market_stall", child.get("parent").getAsString());
            assertEquals("britannia_mod:block/new_assets/market_stall_" + color,
                    child.getAsJsonObject("textures").get("texture").getAsString());
            if (color.equals("red")) {
                continue;
            }
            BufferedImage variant = ImageIO.read(texture(color).toFile());
            int changed = 0;
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int original = red.getRGB(x, y);
                    int replacement = variant.getRGB(x, y);
                    assertEquals(original >>> 24, replacement >>> 24, "alpha changed at " + x + "," + y);
                    if (original != replacement) {
                        changed++;
                        assertTrue(isFabricUvPixel(x, y), "non-fabric UV changed at " + x + "," + y);
                        assertTrue(isRedFabricPixel(original), "non-red fabric detail changed at " + x + "," + y);
                    }
                }
            }
            assertTrue(changed > 2500, color + " fabric recolour changed too few pixels");
        }
        assertNotEquals(ImageIO.read(texture("blue").toFile()).getRGB(35, 10),
                ImageIO.read(texture("green").toFile()).getRGB(35, 10));
    }

    @Test
    void resourceAndRegistrationSurfaceIsCompleteAndOrdered() throws Exception {
        String blocks = javaSource("registry/BlockRegistry.java");
        String items = javaSource("registry/ItemRegistry.java");
        String creative = javaSource("registry/CreativeTabRegistry.java");
        String render = javaSource("ClientModSetup.java");
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        int lastCreativeIndex = -1;
        for (String color : COLORS) {
            String id = "market_stall_" + color;
            String constant = id.toUpperCase();
            assertTrue(blocks.contains("BLOCKS.register(\"" + id + "\""));
            assertTrue(items.contains("ITEMS.register(\"" + id + "\""));
            int creativeIndex = creative.indexOf("ItemRegistry." + constant + "_ITEM.get()");
            assertTrue(creativeIndex > lastCreativeIndex, "creative order drift for " + id);
            lastCreativeIndex = creativeIndex;
            assertTrue(render.contains("BlockRegistry." + constant + ".get(), RenderType.cutout()"));
            assertEquals(Character.toUpperCase(color.charAt(0)) + color.substring(1) + " Market Stall",
                    language.get("block.britannia_mod." + id).getAsString());
            assertTrue(Files.isRegularFile(ASSETS.resolve("blockstates/" + id + ".json")));
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")));
            assertEquals(0, json(DATA.resolve("loot_table/blocks/" + id + ".json"))
                    .getAsJsonArray("pools").size());

            JsonArray multipart = json(ASSETS.resolve("blockstates/" + id + ".json"))
                    .getAsJsonArray("multipart");
            assertEquals(4, multipart.size());
            Set<String> facings = new HashSet<>();
            for (var entry : multipart) {
                JsonObject when = entry.getAsJsonObject().getAsJsonObject("when");
                assertEquals("1", when.get("part").getAsString());
                facings.add(when.get("facing").getAsString());
            }
            assertEquals(Set.of("north", "east", "south", "west"), facings);
        }
        assertTrue(blocks.contains("-1, 1, 0, 2, 0, 0"));
        assertTrue(items.contains("new DecorativeMultiblockItem(BlockRegistry.MARKET_STALL_RED.get()"));
    }

    private static boolean isFabricUvPixel(int x, int y) {
        return (x >= 27 && x < 123 && y < 27)
                || (x < 96 && y >= 27 && y < 70)
                || (x < 96 && y >= 70 && y < 76);
    }

    private static boolean isRedFabricPixel(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >>> 16) & 255;
        int green = (argb >>> 8) & 255;
        int blue = argb & 255;
        return alpha > 0 && red >= 48 && red > green * 1.18D && red > blue * 1.12D;
    }

    private static Bounds rotatedBounds(JsonArray elements) {
        Bounds bounds = new Bounds();
        for (var value : elements) {
            JsonObject element = value.getAsJsonObject();
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            JsonObject rotation = element.has("rotation") ? element.getAsJsonObject("rotation") : null;
            double angle = rotation == null ? 0.0D : Math.toRadians(rotation.get("angle").getAsDouble());
            double[] origin = rotation == null ? new double[3] : vector(rotation.getAsJsonArray("origin"));
            for (double x : new double[] {from[0], to[0]}) {
                for (double y : new double[] {from[1], to[1]}) {
                    for (double z : new double[] {from[2], to[2]}) {
                        double rotatedY = origin[1] + (y - origin[1]) * Math.cos(angle)
                                - (z - origin[2]) * Math.sin(angle);
                        double rotatedZ = origin[2] + (y - origin[1]) * Math.sin(angle)
                                + (z - origin[2]) * Math.cos(angle);
                        bounds.include(x, rotatedY, rotatedZ);
                    }
                }
            }
        }
        return bounds;
    }

    private static double[] vector(JsonArray array) {
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static JsonObject json(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path texture(String color) {
        return ASSETS.resolve("textures/block/new_assets/market_stall_" + color + ".png");
    }

    private static String javaSource(String relative) throws Exception {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative));
    }

    private static final class Bounds {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        void include(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }
}
