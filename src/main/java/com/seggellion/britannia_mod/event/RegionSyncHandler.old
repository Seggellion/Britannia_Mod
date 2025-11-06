package com.seggellion.britannia_mod.sync;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionData;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public final class RegionSyncHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RegionSyncHandler::onLogin);
    }

    private static void onLogin(PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;

        CompletableFuture
            .supplyAsync(() -> RegionSyncAPI.fetch(player))     // off-thread
            .thenAcceptAsync(data -> {                          // back on server thread
                LOGGER.info("🌐 Region grid fetched: {} entries", data.size());
                RegionCache.update(data);
            }, player.server);                                  // ensures main-thread safety
    }
}
