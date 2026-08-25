package com.seggellion.britannia_mod.patch18;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FertileDirtRegistryAssetContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");

    private static final Map<String, String> ITEM_TEXTURES = new LinkedHashMap<>();

    static {
        ITEM_TEXTURES.put("dirt", "minecraft:block/dirt");
        ITEM_TEXTURES.put("dung", "britannia_mod:block/wild_resource/dung");
        ITEM_TEXTURES.put("empty_bowl", "britannia_mod:item/patch18/empty_bowl");
        ITEM_TEXTURES.put("bowl_of_dirt", "britannia_mod:item/patch18/bowl_of_dirt");
        ITEM_TEXTURES.put("bowl_of_fertile_dirt", "britannia_mod:item/patch18/bowl_of_fertile_dirt");
        ITEM_TEXTURES.put("bowl_of_water", "britannia_mod:item/patch18/bowl_of_water");
    }

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void deferredRegistryIdsAreCanonicalAndDoNotDuplicateFertilizedDirt() {
        assertEquals("britannia_mod:dirt", ItemRegistry.DIRT.getId().toString());
        assertEquals("britannia_mod:dung", ItemRegistry.DUNG.getId().toString());
        assertEquals("britannia_mod:empty_bowl", ItemRegistry.EMPTY_BOWL.getId().toString());
        assertEquals("britannia_mod:bowl_of_dirt", ItemRegistry.BOWL_OF_DIRT.getId().toString());
        assertEquals("britannia_mod:bowl_of_fertile_dirt",
                ItemRegistry.BOWL_OF_FERTILE_DIRT.getId().toString());
        assertEquals("britannia_mod:bowl_of_water", ItemRegistry.BOWL_OF_WATER.getId().toString());
        assertEquals("britannia_mod:dung", BlockRegistry.DUNG.getId().toString());

        assertEquals("britannia_mod:fertilized_dirt", ItemRegistry.FERTILIZED_DIRT.getId().toString());
        assertTrue(ItemRegistry.ITEMS.getEntries().stream()
                .noneMatch(holder -> holder.getId().getPath().equals("fertile_dirt")));
        assertNotEquals("minecraft:bowl", ItemRegistry.EMPTY_BOWL.getId().toString());
        assertNotEquals(ItemRegistry.EMPTY_PEWTER_BOWL.getId(), ItemRegistry.EMPTY_BOWL.getId());
    }

    @Test
    void itemModelsLanguageAndCreativeExposureAreComplete() throws IOException {
        ITEM_TEXTURES.forEach((item, texture) -> {
            try {
                JsonObject model = readJson(ASSETS.resolve("models/item/" + item + ".json"));
                assertTrue(model.get("parent").getAsString().endsWith("item/generated"), item);
                assertEquals(texture, model.getAsJsonObject("textures").get("layer0").getAsString(), item);
            } catch (IOException failure) {
                throw new AssertionError(item, failure);
            }
        });

        JsonObject language = readJson(ASSETS.resolve("lang/en_us.json"));
        for (String key : List.of(
                "item.britannia_mod.dirt",
                "item.britannia_mod.dung",
                "block.britannia_mod.dung",
                "item.britannia_mod.empty_bowl",
                "item.britannia_mod.bowl_of_dirt",
                "item.britannia_mod.bowl_of_fertile_dirt",
                "item.britannia_mod.bowl_of_water"
        )) {
            assertTrue(language.has(key), key);
        }

        String creative = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"),
                StandardCharsets.UTF_8
        );
        for (String holder : List.of(
                "DIRT", "DUNG", "EMPTY_BOWL", "BOWL_OF_DIRT", "BOWL_OF_FERTILE_DIRT", "BOWL_OF_WATER"
        )) {
            assertEquals(1, occurrences(creative, "safeAccept(output, ItemRegistry." + holder + ".get())"), holder);
        }
    }

    @Test
    void dungBlockAssetsAndLootResolveToOneCommodity() throws IOException {
        JsonObject blockstate = readJson(ASSETS.resolve("blockstates/dung.json"));
        assertEquals("britannia_mod:block/dung", blockstate.getAsJsonObject("variants")
                .getAsJsonObject("").get("model").getAsString());

        JsonObject model = readJson(ASSETS.resolve("models/block/dung.json"));
        assertEquals("minecraft:cutout", model.get("render_type").getAsString());
        assertEquals("britannia_mod:block/wild_resource/dung",
                model.getAsJsonObject("textures").get("pile").getAsString());
        assertPng(ASSETS.resolve("textures/block/wild_resource/dung.png"), 32);

        JsonObject loot = readJson(RESOURCES.resolve("data/britannia_mod/loot_table/blocks/dung.json"));
        JsonObject entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("britannia_mod:dung", entry.get("name").getAsString());
        assertEquals(1, loot.getAsJsonArray("pools").size());

        String items = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"),
                StandardCharsets.UTF_8
        );
        assertFalse(items.contains("BlockItem(BlockRegistry.DUNG.get()"),
                "dung commodity must remain a plain Item, not an untracked placeable BlockItem");
    }

    @Test
    void customBowlTexturesAreDistinctTransparentProjectScaleAssets()
            throws IOException, NoSuchAlgorithmException {
        List<Path> bowls = List.of(
                ASSETS.resolve("textures/item/patch18/empty_bowl.png"),
                ASSETS.resolve("textures/item/patch18/bowl_of_dirt.png"),
                ASSETS.resolve("textures/item/patch18/bowl_of_fertile_dirt.png"),
                ASSETS.resolve("textures/item/patch18/bowl_of_water.png")
        );
        for (Path bowl : bowls) {
            assertPng(bowl, 16);
        }
        Set<String> hashes = bowls.stream().map(path -> {
            try {
                return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            } catch (IOException | NoSuchAlgorithmException failure) {
                throw new AssertionError(path.toString(), failure);
            }
        }).collect(Collectors.toSet());
        assertEquals(bowls.size(), hashes.size(), "every bowl state needs distinct readable artwork");
    }

    private static void assertPng(Path path, int expectedSize) throws IOException {
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
                opaque |= alpha == 255;
            }
        }
        assertTrue(transparent, path + " needs a transparent background");
        assertTrue(opaque, path + " needs visible artwork");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
