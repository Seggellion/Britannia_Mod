package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.client.LoadingScreenBackgroundPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only loading/status panoramas; title and portal/end backgrounds keep their own render paths. */
@Mixin(Screen.class)
public abstract class LoadingScreenPanoramaMixin {
    private static final int OPAQUE_BLACK = 0xFF000000;

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true, remap = false)
    private void britanniaMod$renderBlackLoadingBackground(
            GuiGraphics graphics,
            float partialTick,
            CallbackInfo callback) {
        if (!LoadingScreenBackgroundPolicy.replacesPanorama((Screen) (Object) this)) {
            return;
        }

        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), OPAQUE_BLACK);
        callback.cancel();
    }
}
