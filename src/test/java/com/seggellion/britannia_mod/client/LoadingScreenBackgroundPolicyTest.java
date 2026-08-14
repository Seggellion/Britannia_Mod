package com.seggellion.britannia_mod.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.junit.jupiter.api.Test;

class LoadingScreenBackgroundPolicyTest {
    @Test
    void replacesEveryLoadingAndStatusPanorama() {
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(GenericMessageScreen.class));
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(ReceivingLevelScreen.class));
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(ConnectScreen.class));
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(LevelLoadingScreen.class));
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(GenericWaitingScreen.class));
        assertTrue(LoadingScreenBackgroundPolicy.replacesPanorama(ProgressScreen.class));
    }

    @Test
    void preservesTitleOnboardingAndOrdinaryScreenPanoramas() {
        assertFalse(LoadingScreenBackgroundPolicy.replacesPanorama(TitleScreen.class));
        assertFalse(LoadingScreenBackgroundPolicy.replacesPanorama(AccessibilityOnboardingScreen.class));
        assertFalse(LoadingScreenBackgroundPolicy.replacesPanorama(OptionsScreen.class));
    }
}
