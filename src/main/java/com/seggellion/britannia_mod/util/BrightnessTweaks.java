package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;

public class BrightnessTweaks {
    public static void applyBrightness() {
        // Enable blending to modify color rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.8f, 1.8f, 1.8f, 1.0f); // Brightness multiplier
    }

    public static void resetBrightness() {
        // Reset shader color after rendering
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
