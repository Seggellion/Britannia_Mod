package com.seggellion.britannia_mod.wildresource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceRuntimeSafetyTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MAIN = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    @Test
    void commonWildResourceCodeHasNoClientClassReferences() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN.resolve("wildresource"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("net.minecraft.client"), path.toString());
                assertFalse(source.contains("com.mojang.blaze3d"), path.toString());
                assertFalse(source.contains("SwampEnvironmentEffects"), path.toString());
            }
        }

        String commonBootstrap = Files.readString(MAIN.resolve("BritanniaMod.java"));
        assertFalse(commonBootstrap.contains("SwampEnvironmentEffects"));
        String clientBootstrap = Files.readString(MAIN.resolve("ClientModSetup.java"));
        assertTrue(clientBootstrap.contains("value = Dist.CLIENT"));
        assertTrue(clientBootstrap.contains("NeoForge.EVENT_BUS.register(SwampEnvironmentEffects.class)"));
    }

    @Test
    void schedulerWorkIsGloballyBoundedLoadedOnlyAndDimensionFair() throws IOException {
        String manager = Files.readString(MAIN.resolve("wildresource/WildResourceManager.java"));
        String proximity = Files.readString(MAIN.resolve("wildresource/WildResourceProximity.java"));

        assertTrue(manager.contains("MAX_CHUNKS_PER_TICK = 16"));
        assertTrue(manager.contains("MAX_RESOURCE_ATTEMPTS_PER_TICK = 32"));
        assertTrue(manager.contains("MAX_RECONCILIATIONS_PER_TICK = 16"));
        assertTrue(manager.contains("dimensionCursor"));
        assertTrue(manager.contains("getChunkNow"));
        assertFalse(manager.contains("getChunkSource().getChunk("));
        assertFalse(manager.contains("BlockPos.betweenClosed"));
        assertFalse(manager.contains("getAllEntities"));

        assertTrue(proximity.contains("ENVIRONMENT_RADIUS = 20"));
        assertTrue(proximity.contains("getChunkNow"));
        assertTrue(proximity.contains("QueryResult.INCOMPLETE"));
        assertFalse(proximity.contains("getChunkSource().getChunk("));
    }
}
