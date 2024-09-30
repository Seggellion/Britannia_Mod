package com.seggellion.britannia_mod.event;


import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

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
