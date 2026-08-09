package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DyeTubPreviewRoutingTest {
    @Test
    void pigmentRoutingPrecedesBannerRoutingAndUnknownItemsRetainLoadingFailure() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/dye/item/DyeTubItem.java"));
        int pigmentCheck = source.indexOf("DyeItemRegistry.pigmentId(pigmentStack.getItem()).isEmpty()");
        int bannerCheck = source.indexOf("pigmentStack.getItem() == BannerItemRegistry.BANNER.get()");
        int loadingPlan = source.indexOf("DyeTubLoadingService.plan(");
        assertTrue(pigmentCheck >= 0);
        assertTrue(bannerCheck > pigmentCheck);
        assertTrue(loadingPlan > bannerCheck);
        assertTrue(source.contains("hand != InteractionHand.MAIN_HAND"));
        assertTrue(source.contains("level.isClientSide()"));
        assertTrue(source.indexOf("level.isClientSide()") < source.indexOf("DyePreviewRuntime.openPreview"));
    }

    @Test
    void noRedundantPreviewRequestPayloadExists() throws Exception {
        try (var paths = Files.walk(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/network/payload/dye"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains("RequestDyePreview")));
        }
    }
}
