package com.seggellion.britannia_mod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class TitleWebsiteButtonBrandingTest {
    @Test
    void websiteIdentityIsExactAndSecure() {
        assertEquals("Ultimacraft website", TitleWebsiteButtonBranding.BUTTON_TEXT);
        assertEquals("https", TitleWebsiteButtonBranding.WEBSITE_URI.getScheme());
        assertEquals("www.ultimacraft.com", TitleWebsiteButtonBranding.WEBSITE_URI.getHost());
        assertEquals("", TitleWebsiteButtonBranding.WEBSITE_URI.getPath());
    }

    @Test
    void recognizesOnlyTheVanillaRealmsButton() {
        Button realms = Button.builder(Component.translatable("menu.online"), button -> {
        }).build();
        Button multiplayer = Button.builder(Component.translatable("menu.multiplayer"), button -> {
        }).build();

        assertTrue(TitleWebsiteButtonBranding.isRealmsButton(realms));
        assertFalse(TitleWebsiteButtonBranding.isRealmsButton(multiplayer));
    }

    @Test
    void replacementKeepsTheRealmsSlotGeometry() {
        Button realms = Button.builder(Component.translatable("menu.online"), button -> {
        }).bounds(11, 22, 200, 20).build();

        Button website = TitleWebsiteButtonBranding.createWebsiteButton(realms);

        assertEquals(Component.literal("Ultimacraft website"), website.getMessage());
        assertEquals(realms.getX(), website.getX());
        assertEquals(realms.getY(), website.getY());
        assertEquals(realms.getWidth(), website.getWidth());
        assertEquals(realms.getHeight(), website.getHeight());
    }
}
