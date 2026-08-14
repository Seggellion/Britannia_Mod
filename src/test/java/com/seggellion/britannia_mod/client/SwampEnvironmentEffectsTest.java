package com.seggellion.britannia_mod.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwampEnvironmentEffectsTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void tintIsSubtleGreenBiasedAndClamped() {
        SwampEnvironmentEffects.FogColor ordinary = SwampEnvironmentEffects.tint(0.50F, 0.50F, 0.50F);
        assertTrue(ordinary.green() > ordinary.red());
        assertTrue(ordinary.green() > ordinary.blue());
        assertEquals(0.12F, SwampEnvironmentEffects.BLEND_STRENGTH);

        SwampEnvironmentEffects.FogColor extremes = SwampEnvironmentEffects.tint(-10.0F, 10.0F, 10.0F);
        assertTrue(extremes.red() >= 0.0F && extremes.red() <= 1.0F);
        assertTrue(extremes.green() >= 0.0F && extremes.green() <= 1.0F);
        assertTrue(extremes.blue() >= 0.0F && extremes.blue() <= 1.0F);
    }

    @Test
    void hookIsClientOnlyColorOnlyAndUsesTheSharedSwampRule() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/client/SwampEnvironmentEffects.java"
        ));
        String setup = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/ClientModSetup.java"
        ));

        assertTrue(source.contains("@OnlyIn(Dist.CLIENT)"));
        assertTrue(source.contains("ViewportEvent.ComputeFogColor"));
        assertTrue(source.contains("SwampBiomeRules.isSwamp"));
        assertTrue(source.contains("FogType.NONE"));
        assertFalse(source.contains("ViewportEvent.RenderFog"));
        assertFalse(source.contains("setNearPlaneDistance"));
        assertFalse(source.contains("setFarPlaneDistance"));
        assertTrue(setup.contains("NeoForge.EVENT_BUS.register(SwampEnvironmentEffects.class)"));
    }
}
