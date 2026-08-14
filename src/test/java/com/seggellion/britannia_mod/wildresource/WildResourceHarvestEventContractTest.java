package com.seggellion.britannia_mod.wildresource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceHarvestEventContractTest {
    private static final Path MAIN = Path.of(
            System.getProperty("britannia.projectDir", "."),
            "src/main/java/com/seggellion/britannia_mod"
    );

    @Test
    void eventCarriesRequiredImmutableHarvestContext() throws IOException {
        String event = Files.readString(MAIN.resolve("event/WildResourceHarvestEvent.java"));
        assertTrue(event.contains("ServerPlayer player"));
        assertTrue(event.contains("ResourceLocation resourceId"));
        assertTrue(event.contains("BlockPos position"));
        assertTrue(event.contains("ResourceKey<Level> dimension"));
        assertTrue(event.contains("ItemStack result"));
        assertTrue(event.contains("ToolCategory toolCategory"));
        assertTrue(event.contains("this.result = Objects.requireNonNull(result"));
        assertTrue(event.contains("return result.copy()"));
    }

    @Test
    void onlySuccessfulLootBearingPathsPostThroughOneHelper() throws IOException {
        String service = Files.readString(MAIN.resolve("wildresource/WildResourceHarvestService.java"));
        assertEquals(1, occurrences(service, "NeoForge.EVENT_BUS.post(new WildResourceHarvestEvent"));
        assertTrue(service.contains("if (!result.isEmpty())"));
        assertTrue(service.contains("if (!player.getAbilities().instabuild)"));
        assertTrue(service.contains("DaggerTools.isDagger(tool)"));
        assertFalse(service.contains("QualitySwordItem"));
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
