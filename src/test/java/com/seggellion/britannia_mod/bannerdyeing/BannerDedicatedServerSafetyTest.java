package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BannerDedicatedServerSafetyTest {
    private static final Path MAIN = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/java/com/seggellion/britannia_mod");

    @Test
    void serverCriticalBannerAndDyeClassesHaveNoClientOnlyLinkage() throws Exception {
        for (String relative : List.of(
                "banner/block/BannerBlock.java",
                "banner/block/BannerPartBlock.java",
                "banner/blockentity/BannerBlockEntity.java",
                "banner/structure/BannerStructureIntegrity.java",
                "banner/structure/BannerStructureIntegrityHandler.java",
                "dye/preview/DyePreviewSessionService.java",
                "dye/preview/DyeApplicationService.java",
                "network/payload/banner/S2CBannerRenderDataPayload.java",
                "network/payload/dye/S2COpenDyePreviewPayload.java")) {
            String source = Files.readString(MAIN.resolve(relative));
            assertFalse(source.contains("net.minecraft.client"), relative);
            assertFalse(source.contains("Minecraft.getInstance"), relative);
            assertFalse(source.contains("com.seggellion.britannia_mod.client"), relative);
        }
    }

    @Test
    void placedBannerUsesOneAnchorEntityNoPartEntityAndNoPerTickTicker() throws Exception {
        String anchor = Files.readString(MAIN.resolve("banner/block/BannerBlock.java"));
        String part = Files.readString(MAIN.resolve("banner/block/BannerPartBlock.java"));
        assertTrue(anchor.contains("extends BaseEntityBlock"));
        assertTrue(anchor.contains("new BannerBlockEntity"));
        assertFalse(anchor.contains("getTicker("));
        assertTrue(part.contains("extends Block"));
        assertFalse(part.contains("extends BaseEntityBlock"));
        assertFalse(part.contains("new BannerBlockEntity"));
        assertFalse(part.contains("getTicker("));
    }

    @Test
    void integrityRepairIsChunkLoadTriggeredAndQueueDrainedNotARecurringWorldScan() throws Exception {
        String source = Files.readString(
                MAIN.resolve("banner/structure/BannerStructureIntegrityHandler.java"));
        assertTrue(source.contains("onChunkLoad"));
        assertTrue(source.contains("ServerTickEvent.Post"));
        assertTrue(source.contains(".clear()"));
        assertFalse(source.contains("getAllLevels"));
        assertFalse(source.contains("getAllEntities"));
        assertFalse(source.contains("for (long tick"));
    }

    @Test
    void titleScreenMixinIsDeclaredClientOnly() throws Exception {
        var config = JsonParser.parseString(
                Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/britannia_mod.mixins.json")))
                .getAsJsonObject();
        assertFalse(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("TitleScreenBackgroundMixin")));
        assertTrue(config.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("TitleScreenBackgroundMixin")));
    }
}
