package com.seggellion.britannia_mod.client;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;

/** Identifies status screens whose decorative panorama is replaced by solid black. */
public final class LoadingScreenBackgroundPolicy {
    private LoadingScreenBackgroundPolicy() {
    }

    public static boolean replacesPanorama(Screen screen) {
        return replacesPanorama(screen.getClass());
    }

    static boolean replacesPanorama(Class<? extends Screen> screenType) {
        return GenericMessageScreen.class.isAssignableFrom(screenType)
                || ReceivingLevelScreen.class.isAssignableFrom(screenType)
                || ConnectScreen.class.isAssignableFrom(screenType)
                || LevelLoadingScreen.class.isAssignableFrom(screenType)
                || GenericWaitingScreen.class.isAssignableFrom(screenType)
                || ProgressScreen.class.isAssignableFrom(screenType);
    }
}
