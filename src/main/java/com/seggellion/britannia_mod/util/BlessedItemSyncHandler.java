package com.seggellion.britannia_mod.sync;

import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.common.NeoForge;          // global bus
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import java.util.concurrent.CompletableFuture;


public final class BlessedItemSyncHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        // call this once from BritanniaMod.java during common-setup
        NeoForge.EVENT_BUS.addListener(BlessedItemSyncHandler::onLogin);
    }

    private static void onLogin(PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;

        // Kick the HTTP work off-thread so the login thread never blocks.
        CompletableFuture
            .supplyAsync(() -> BlessedItemSyncAPI.fetch(player))
            .thenAcceptAsync(items -> {                     // back on main thread
                BlessedItemInventorySync.apply(player, items);
            }, player.server);                              // ensures world-thread context
    }
}
