package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DyeTubPreviewRoutingTest {
    @Test
    void roleBasedPigmentRoutingPrecedesMainHandBannerPreviewAndNonmatchesPass() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/dye/item/DyeTubItem.java"));
        int recipes = source.indexOf("HandRecipeInteraction.use(level, player, hand)");
        int bannerCheck = source.indexOf("hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(BannerItemRegistry.BANNER.get())");
        int preview = source.indexOf("DyePreviewRuntime.openPreview(serverPlayer)");
        assertTrue(recipes >= 0 && bannerCheck > recipes && preview > bannerCheck);
        assertTrue(source.contains("if (matched.getResult().consumesAction()) return matched;"));
        assertTrue(source.contains("player instanceof ServerPlayer serverPlayer"));
        assertTrue(source.contains("return InteractionResultHolder.pass(tubStack)"));
    }

    @Test
    void noRedundantPreviewRequestPayloadExists() throws Exception {
        try (var paths = Files.walk(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/network/payload/dye"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains("RequestDyePreview")));
        }
    }
}
