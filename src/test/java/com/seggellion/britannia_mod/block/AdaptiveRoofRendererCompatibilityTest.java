package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdaptiveRoofRendererCompatibilityTest {
    private static final Path RENDERER = Path.of(
            System.getProperty("britannia.projectDir", "."),
            "src/main/java/com/seggellion/britannia_mod/block/renderer/AdaptiveRoofRenderer.java");

    @Test
    void acquiredRoofGeometryUsesTheBlockEntityRenderDomain() throws Exception {
        String source = Files.readString(RENDERER);

        assertTrue(source.contains("buffers.getBuffer(Sheets.solidBlockSheet())"));
        assertFalse(source.contains("getBuffer(RenderType.solid())"));
        assertTrue(source.contains("LevelRenderer.getLightColor(level, pos.relative(face))"));
        assertTrue(source.contains(".setNormal(pose,"));
    }
}
