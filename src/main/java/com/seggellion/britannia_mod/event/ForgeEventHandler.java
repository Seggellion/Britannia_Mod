package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

public class ForgeEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ForgeEventHandler() {
        LOGGER.info("ForgeEventHandler instantiated");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Britannia Starting");
    }
}
