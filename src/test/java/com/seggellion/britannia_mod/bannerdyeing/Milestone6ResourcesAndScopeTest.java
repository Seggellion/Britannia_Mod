package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.dye.item.DyeTubLoadResult;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Milestone6ResourcesAndScopeTest {
    private static final Path JAVA = Path.of("src/main/java/com/seggellion/britannia_mod");
    private static final Path ASSETS = Path.of("src/main/resources/assets/britannia_mod");
    private static final List<String> PIGMENTS = List.of(
            "madder_red", "woad_blue", "verdigris", "weld_gold",
            "soot_black", "chalk_white", "ice_blue");

    @Test
    void allItemModelsAndSharedOriginalTexturesExist() throws Exception {
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/dye_tub.json")));
        for (String pigment : PIGMENTS) {
            Path model = ASSETS.resolve("models/item/" + pigment + ".json");
            assertTrue(Files.isRegularFile(model), model.toString());
            JsonObject json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
            assertEquals("britannia_mod:item/pigment",
                    json.getAsJsonObject("textures").get("layer0").getAsString());
        }
        assertPng("dye_tub.png");
        assertPng("pigment.png");
    }

    @Test
    void requiredLocalizationKeysExistWithoutRemovingBannerKeys() throws Exception {
        JsonObject lang = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        assertTrue(lang.has("item.britannia_mod.dye_tub"));
        for (String pigment : PIGMENTS) {
            assertTrue(lang.has("item.britannia_mod." + pigment));
            assertTrue(lang.has("pigment.britannia_mod." + pigment));
        }
        for (String key : List.of(
                "tooltip.britannia_mod.dye_tub.empty",
                "tooltip.britannia_mod.dye_tub.off_hand_hint",
                "tooltip.britannia_mod.dye_tub.contains",
                "tooltip.britannia_mod.dye_tub.uses",
                "tooltip.britannia_mod.dye_tub.unlimited",
                "tooltip.britannia_mod.dye_tub.missing_pigment",
                "message.britannia_mod.dye_tub.loaded",
                "message.britannia_mod.dye_tub.replaced",
                "message.britannia_mod.dye_tub.already_contains",
                "message.britannia_mod.dye_tub.requires_off_hand",
                "message.britannia_mod.dye_tub.invalid_off_hand",
                "message.britannia_mod.dye_tub.missing_pigment")) {
            assertTrue(lang.has(key), key);
        }
        assertEquals(33, lang.keySet().stream().filter(key -> key.startsWith("banner.britannia_mod.")).count());
    }

    @Test
    void interactionIsMainHandOnlyAndServerMutationPrecedesFeedback() throws Exception {
        String source = Files.readString(JAVA.resolve("dye/item/DyeTubItem.java"));
        int handGuard = source.indexOf("hand != InteractionHand.MAIN_HAND");
        int clientGuard = source.indexOf("level.isClientSide()");
        int plan = source.indexOf("DyeTubLoadingService.plan");
        int apply = source.indexOf("DyeTubLoadingService.apply");
        int feedback = source.indexOf("sendFeedback(player, result)");
        int sound = source.indexOf("SoundEvents.BOTTLE_FILL");
        int particles = source.indexOf("serverLevel.sendParticles");
        assertTrue(handGuard >= 0 && handGuard < clientGuard);
        assertTrue(clientGuard < plan && plan < apply && apply < feedback);
        assertTrue(feedback < sound && sound < particles);
        assertTrue(source.contains("player.getOffhandItem()"));
        assertFalse(source.contains("InteractionHand.OFF_HAND"));
    }

    @Test
    void feedbackModelEmitsEffectsOnlyForConfirmedLoads() {
        for (DyeTubLoadResult result : DyeTubLoadResult.values()) {
            assertEquals(
                    result == DyeTubLoadResult.LOADED || result == DyeTubLoadResult.REPLACED,
                    result.emitsSuccessEffects(),
                    result.name());
        }
    }

    @Test
    void commonDyeTubCodeHasNoClientOrMilestone7References() throws Exception {
        List<Path> files;
        try (var paths = Files.walk(JAVA.resolve("dye"))) {
            files = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        String combined = String.join("\n", files.stream().map(path -> {
            try {
                return Files.readString(path);
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }).toList());
        assertFalse(combined.contains("net.minecraft.client"));
        assertFalse(combined.contains("Screen"));
        assertFalse(combined.contains("CustomPacketPayload"));
        assertFalse(combined.contains("DyeableItem"));
        assertFalse(combined.contains("BannerInstanceState"));
    }

    @Test
    void noBannerItemScreenMenuBlockOrPayloadWasAdded() throws Exception {
        try (var paths = Files.walk(JAVA)) {
            List<String> milestone6Files = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("Dye") || name.contains("Pigment"))
                    .toList();
            assertFalse(milestone6Files.stream().anyMatch(name -> name.contains("BannerItem")));
            assertFalse(milestone6Files.stream().anyMatch(name -> name.contains("Screen")));
            assertFalse(milestone6Files.stream().anyMatch(name -> name.contains("Menu")));
            assertFalse(milestone6Files.stream().anyMatch(name -> name.contains("Payload")));
            assertFalse(milestone6Files.stream().anyMatch(name -> name.contains("BlockEntity")));
        }
    }

    private static void assertPng(String name) throws Exception {
        BufferedImage image = ImageIO.read(ASSETS.resolve("textures/item/" + name).toFile());
        assertNotNull(image);
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, (image.getRGB(0, 0) >>> 24) & 0xFF);
    }
}
