// WorldBootstrapHandler.java
package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.client.RegionCache;

import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public final class WorldBootstrapHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(WorldBootstrapHandler::onLogin);
    }

    @SubscribeEvent
    private static void onLogin(PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;

        CompletableFuture
            .supplyAsync(() -> WorldBootstrapAPI.fetch(player))
            .thenAcceptAsync(data -> {
                // apply on main thread
                FishCatalog.clear();
                data.fish().forEach(FishCatalog::put);
                RegionCache.update(data.regions());
                LOGGER.info("🌐 World bootstrap loaded: {} fish, {} regions", data.fish().size(), data.regions().size());
            }, player.server);
    }
}
