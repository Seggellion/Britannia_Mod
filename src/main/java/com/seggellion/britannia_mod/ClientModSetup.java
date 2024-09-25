package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ClientModSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Client setup event called");

        // Register the mana overlay (render it during the HUD)
        ManaOverlayScreen.register();
    }
}
