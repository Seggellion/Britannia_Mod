package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.BritanniaMod;
import java.net.URI;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Replaces the title-screen Realms entry with the official UltimaCraft website link. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TitleWebsiteButtonBranding {
    static final String BUTTON_TEXT = "Ultimacraft website";
    static final URI WEBSITE_URI = URI.create("https://www.ultimacraft.com");
    private static final Component REALMS_TEXT = Component.translatable("menu.online");
    private static final Component WEBSITE_TEXT = Component.literal(BUTTON_TEXT);

    private TitleWebsiteButtonBranding() {
    }

    @SubscribeEvent
    public static void replaceRealmsButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }

        Button realmsButton = event.getListenersList().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(TitleWebsiteButtonBranding::isRealmsButton)
                .findFirst()
                .orElse(null);
        if (realmsButton == null) {
            return;
        }

        event.removeListener(realmsButton);
        event.addListener(createWebsiteButton(realmsButton));
    }

    static boolean isRealmsButton(Button button) {
        return REALMS_TEXT.equals(button.getMessage());
    }

    static Button createWebsiteButton(Button replacedButton) {
        return Button.builder(WEBSITE_TEXT, button -> Util.getPlatform().openUri(WEBSITE_URI))
                .bounds(
                        replacedButton.getX(),
                        replacedButton.getY(),
                        replacedButton.getWidth(),
                        replacedButton.getHeight())
                .build();
    }
}
