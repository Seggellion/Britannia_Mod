package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.wildresource.SwampBiomeRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-only, color-only swamp gas atmosphere. Fog distance and density are never changed. */
@OnlyIn(Dist.CLIENT)
public final class SwampEnvironmentEffects {
    static final float BLEND_STRENGTH = 0.12F;
    static final float TARGET_RED = 0.34F;
    static final float TARGET_GREEN = 0.46F;
    static final float TARGET_BLUE = 0.24F;

    private SwampEnvironmentEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || event.getCamera().getFluidInCamera() != FogType.NONE
                || !SwampBiomeRules.isSwamp(minecraft.level, event.getCamera().getBlockPosition())) {
            return;
        }
        FogColor color = tint(event.getRed(), event.getGreen(), event.getBlue());
        event.setRed(color.red());
        event.setGreen(color.green());
        event.setBlue(color.blue());
    }

    static FogColor tint(float red, float green, float blue) {
        return new FogColor(
                blend(red, TARGET_RED),
                blend(green, TARGET_GREEN),
                blend(blue, TARGET_BLUE)
        );
    }

    private static float blend(float source, float target) {
        return clamp(source + (target - source) * BLEND_STRENGTH);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    record FogColor(float red, float green, float blue) {
    }
}
