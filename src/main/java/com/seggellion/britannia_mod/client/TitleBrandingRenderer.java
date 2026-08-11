package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Draws the version subtitle after vanilla has rendered the main menu. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TitleBrandingRenderer {
    private static final Component SUBTITLE = Component.literal(TitleBrandingLayout.SUBTITLE);
    private static final int SUBTITLE_COLOR = 0xFFF0E2B6;

    private TitleBrandingRenderer() {
    }

    @SubscribeEvent
    public static void renderTitleSubtitle(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }

        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                SUBTITLE,
                TitleBrandingLayout.subtitleCenterX(event.getScreen().width),
                TitleBrandingLayout.subtitleY(),
                SUBTITLE_COLOR);
    }
}
