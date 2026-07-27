package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.client.banner.*;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BannerRenderAssetsCacheAndIsolationTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/britannia_mod");
    private static final List<String> FAMILIES = List.of("large", "medium_wall", "medium", "small", "x_small");

    @Test
    void allFiveTwoFileModelsPackageAndTintOnlyTheirMaskQuads() throws Exception {
        for (String family : FAMILIES) {
            Path path = ASSETS.resolve("models/banner/placeholder/" + family + ".json");
            assertTrue(Files.isRegularFile(path), family);
            JsonObject model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals("britannia_mod:banner/placeholder/base_texture",
                    model.getAsJsonObject("textures").get("base_texture").getAsString());
            assertEquals("britannia_mod:banner/placeholder/dye_mask",
                    model.getAsJsonObject("textures").get("dye_mask").getAsString());
            assertEquals("minecraft:translucent", model.get("render_type").getAsString());
            assertEquals(2, model.getAsJsonArray("elements").size());
            int tintCount = count(model.getAsJsonArray("elements"), "tintindex");
            assertEquals(2, tintCount, family + " north/south dye-mask faces");
            assertEquals(2, countValue(model.getAsJsonArray("elements"), "tintindex", 1));
        }
    }

    @Test
    void baseMaskMissingAndMountAssetsPackageWithoutRemovedTextures() {
        for (String texture : List.of("base_texture", "dye_mask", "missing")) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/banner/placeholder/" + texture + ".png")), texture);
        }
        assertFalse(Files.exists(ASSETS.resolve("textures/banner/placeholder/fabric_base.png")));
        assertFalse(Files.exists(ASSETS.resolve("textures/banner/placeholder/static_overlay.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/banner/placeholder/missing_item.json")));
        for (String mount : List.of("brass", "iron")) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/banner/mount/" + mount + ".json")), mount);
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/banner/mount/" + mount + ".png")), mount);
        }
    }

    @Test
    void compositeBasePreservesDiagnosticPixelsAndMaskRetainsSelectiveRecolourSemantics() throws Exception {
        BufferedImage base = ImageIO.read(ASSETS.resolve(
                "textures/banner/placeholder/base_texture.png").toFile());
        BufferedImage mask = ImageIO.read(ASSETS.resolve("textures/banner/placeholder/dye_mask.png").toFile());
        assertEquals(0xFF232323, base.getRGB(0, 0));
        assertEquals(0xFFF5F5F5, base.getRGB(6, 6));
        assertEquals(0xFFB8B8B8, base.getRGB(4, 4));
        assertEquals(base.getWidth(), mask.getWidth());
        assertEquals(base.getHeight(), mask.getHeight());
        assertTrue(hasAlpha(mask, 0));
        assertTrue(hasAlpha(mask, 255));
        assertTrue(nonTransparentPixelsAreGreyscale(mask));
        assertTrue(activeMaskPixelsCoverOnlyOpaqueBase(base, mask));
        byte[] brass = Files.readAllBytes(ASSETS.resolve("textures/banner/mount/brass.png"));
        byte[] iron = Files.readAllBytes(ASSETS.resolve("textures/banner/mount/iron.png"));
        assertFalse(java.util.Arrays.equals(brass, iron));
    }

    @Test
    void scaffoldMetadataOwnsEveryGeneratedRenderingAsset() throws Exception {
        String metadata = Files.readString(Path.of("content/.banner_scaffold_metadata.json"));
        for (String family : FAMILIES) {
            assertTrue(metadata.contains("models/banner/placeholder/" + family + ".json"), family);
        }
        for (String expected : List.of(
                "models/banner/placeholder/missing_item.json",
                "textures/banner/placeholder/base_texture.png",
                "textures/banner/placeholder/dye_mask.png",
                "models/banner/mount/brass.json", "models/banner/mount/iron.json",
                "textures/banner/mount/brass.png", "textures/banner/mount/iron.png")) {
            assertTrue(metadata.contains(expected), expected);
        }
        assertFalse(metadata.contains("fabric_base.png"));
        assertFalse(metadata.contains("static_overlay.png"));
    }

    @Test
    void boundedCacheReusesKeysEvictsOldEntriesAndClearsObsoleteValues() {
        BannerRenderEntryCache<Object> cache = new BannerRenderEntryCache<>(2);
        Object first = cache.getOrCreate(key("one", 1), ignored -> new Object());
        assertSame(first, cache.getOrCreate(key("one", 1), ignored -> new Object()));
        cache.getOrCreate(key("two", 1), ignored -> new Object());
        cache.getOrCreate(key("three", 1), ignored -> new Object());
        assertEquals(2, cache.size());
        Object reloaded = cache.getOrCreate(key("one", 2), ignored -> new Object());
        assertNotSame(first, reloaded);
        cache.clear();
        assertEquals(0, cache.size());
        assertNotSame(reloaded, cache.getOrCreate(key("one", 2), ignored -> new Object()));
    }

    @Test
    void appearanceKeysContainBaseAndMaskAndNoThirdBannerImageIdentity() {
        var componentNames = java.util.Arrays.stream(BannerAppearanceKey.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertTrue(componentNames.contains("baseTexture"));
        assertTrue(componentNames.contains("dyeMask"));
        assertFalse(componentNames.contains("fabricBase"));
        assertFalse(componentNames.contains("staticOverlay"));
        assertFalse(componentNames.contains("strategy"));

        BannerRenderKey original = key("same", 2, "base_a", "mask_a");
        assertNotEquals(original, key("same", 2, "base_b", "mask_a"));
        assertNotEquals(original, key("same", 2, "base_a", "mask_b"));
    }

    @Test
    void missingDiagnosticLoggingIsDeduplicatedPerGenerationAndResettable() {
        BannerMissingLogTracker tracker = new BannerMissingLogTracker();
        assertTrue(tracker.first(BannerRenderFailure.MISSING_DYE_MASK, "server:mask", 1, 2));
        assertFalse(tracker.first(BannerRenderFailure.MISSING_DYE_MASK, "server:mask", 1, 2));
        assertTrue(tracker.first(BannerRenderFailure.MISSING_DYE_MASK, "server:mask", 1, 3));
        assertEquals(2, tracker.size());
        tracker.clear();
        assertEquals(0, tracker.size());
        assertTrue(tracker.first(BannerRenderFailure.MISSING_DYE_MASK, "server:mask", 1, 3));
    }

    @Test
    void cacheAndRendererAreClientOnlyAndCommonPayloadGraphHasNoClientImports() throws Exception {
        String client = readTree(Path.of("src/main/java/com/seggellion/britannia_mod/client/banner"));
        assertTrue(client.contains("net.minecraft.client"));
        assertFalse(client.contains("ItemStack> MODELS"));
        String common = readTree(Path.of("src/main/java/com/seggellion/britannia_mod/banner/renderdata"))
                + readTree(Path.of("src/main/java/com/seggellion/britannia_mod/network/payload/banner"));
        assertFalse(common.contains("net.minecraft.client"));
        assertFalse(common.contains("com.mojang.blaze3d"));
        assertFalse(common.contains("BannerRenderCache"));
        String mod = Files.readString(Path.of("src/main/java/com/seggellion/britannia_mod/BritanniaMod.java"));
        assertFalse(mod.contains("client.banner"));
    }

    @Test
    void modelPathUsesCurrentBakedModelEventsAndRepositoryStandardTransforms() throws Exception {
        String events = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/banner/BannerClientEvents.java"));
        String model = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/banner/BannerLayeredBakedModel.java"));
        assertTrue(events.contains("ModelEvent.RegisterAdditional"));
        assertTrue(events.contains("ModelEvent.ModifyBakingResult"));
        assertFalse(events.contains("initializeClient"));
        assertTrue(model.contains("getRenderPasses"));
        assertTrue(model.contains("transformModel.applyTransform(context"));
        assertFalse(model.matches("(?is).*(place|blockentity|anchor).*"));
    }

    private static BannerRenderKey key(String definition, long resourceGeneration) {
        return key(definition, resourceGeneration, definition + "_base", definition + "_mask");
    }

    private static BannerRenderKey key(
            String definition, long resourceGeneration, String baseTexture, String dyeMask) {
        ResourceLocation asset = ResourceLocation.parse("britannia_mod:" + definition);
        return new BannerRenderKey(
                Optional.of(BannerDefinitionId.parse("britannia_mod:" + definition)),
                Optional.of(FabricMaterialId.parse("britannia_mod:cotton")),
                Optional.of(ResolvedColourId.parse("britannia_mod:cotton_natural")),
                Optional.of(MountId.parse("britannia_mod:brass")),
                Optional.of(asset),
                Optional.of(ResourceLocation.parse("britannia_mod:" + baseTexture)),
                Optional.of(ResourceLocation.parse("britannia_mod:" + dyeMask)),
                Optional.of(asset), Optional.of(asset), Optional.of(BannerContentStatus.PLACEHOLDER),
                0xEEE4CC, false, BannerRenderFailure.NONE, 4, resourceGeneration);
    }

    private static int count(JsonArray elements, String property) {
        int count = 0;
        for (var element : elements) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                if (face.getValue().getAsJsonObject().has(property)) count++;
            }
        }
        return count;
    }

    private static int countValue(JsonArray elements, String property, int value) {
        int count = 0;
        for (var element : elements) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                JsonObject object = face.getValue().getAsJsonObject();
                if (object.has(property) && object.get(property).getAsInt() == value) count++;
            }
        }
        return count;
    }

    private static boolean hasAlpha(BufferedImage image, int alpha) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) == alpha) return true;
        return false;
    }

    private static boolean nonTransparentPixelsAreGreyscale(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y);
            if ((pixel >>> 24) != 0) {
                int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
                if (red != green || green != blue) return false;
            }
        }
        return true;
    }

    private static boolean activeMaskPixelsCoverOnlyOpaqueBase(BufferedImage base, BufferedImage mask) {
        for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
            if ((mask.getRGB(x, y) >>> 24) != 0 && (base.getRGB(x, y) >>> 24) != 255) return false;
        }
        return true;
    }

    private static String readTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try { return Files.readString(path); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).reduce("", String::concat);
        }
    }
}
