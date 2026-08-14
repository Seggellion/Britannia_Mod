package com.seggellion.britannia_mod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitleBrandingLayoutTest {
    @Test
    void subtitleCopyMatchesTheApprovedVersionLabel() {
        assertEquals("Version 18", TitleBrandingLayout.SUBTITLE);
    }

    @Test
    void subtitleIsCenteredForOddAndEvenScreenWidths() {
        assertEquals(480, TitleBrandingLayout.subtitleCenterX(960));
        assertEquals(213, TitleBrandingLayout.subtitleCenterX(427));
    }

    @Test
    void subtitleSitsTwoPixelsBelowTheVanillaLogoBox() {
        assertEquals(76, TitleBrandingLayout.subtitleY());
    }

    @Test
    void subtitleClearsButtonsAcrossTheValidationMatrix() {
        int vanillaFontLineHeight = 9;
        assertClearsFirstButton(240, vanillaFontLineHeight);
        assertClearsFirstButton(540, vanillaFontLineHeight);
        assertClearsFirstButton(480, vanillaFontLineHeight);
        assertClearsFirstButton(360, vanillaFontLineHeight);
    }

    private static void assertClearsFirstButton(int screenHeight, int lineHeight) {
        int firstButtonY = screenHeight / 4 + 32;
        assertTrue(TitleBrandingLayout.subtitleY() + lineHeight <= firstButtonY);
    }
}
