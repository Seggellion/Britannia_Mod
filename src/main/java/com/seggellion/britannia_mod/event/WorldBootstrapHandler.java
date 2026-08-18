package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.quest.QuestCleanupService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;

import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public final class WorldBootstrapHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Fish, regions and city inventories are world state: every player receives the same
     * payload, so N simultaneous logins would re-apply the entire Rails catalogue N times
     * on the server thread. Coalesce those re-applies into one per window. Quest state is
     * per-player and is still synced on every login.
     */
    private static final int WORLD_SYNC_MIN_INTERVAL_TICKS = 600; // 30 seconds
    private static final long NEVER_SYNCED = Long.MIN_VALUE;
    private static long lastWorldSyncTick = NEVER_SYNCED;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(WorldBootstrapHandler::onLogin);
    }

    @SubscribeEvent
    private static void onLogin(PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;

        CompletableFuture
            .supplyAsync(() -> WorldBootstrapAPI.fetch(player))
            .thenAcceptAsync(data -> {
                if (shouldApplyWorldState(player)) {
                    applyWorldState(player, data);
                }

                ServerQuestTable.replaceFromBootstrap(player, data.acceptedQuests());
                QuestCleanupService.cleanupStaleLocalQuestState(player, data.acceptedQuests());
                ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));

            }, player.server);
    }

    /**
     * Runs on the server thread, so a plain field is sufficient here.
     */
    private static boolean shouldApplyWorldState(ServerPlayer player) {
        long now = player.server.getTickCount();

        // An integrated server recreated inside the same JVM restarts its tick count, which
        // would otherwise leave lastWorldSyncTick stranded in the future and skip forever.
        boolean tickCountReset = now < lastWorldSyncTick;

        if (lastWorldSyncTick != NEVER_SYNCED
                && !tickCountReset
                && now - lastWorldSyncTick < WORLD_SYNC_MIN_INTERVAL_TICKS) {
            return false;
        }

        lastWorldSyncTick = now;
        return true;
    }

    private static void applyWorldState(ServerPlayer player, WorldBootstrapAPI.WorldBootstrapData data) {
        FishCatalog.clear();
        data.fish().forEach(FishCatalog::put);
        RegionCache.update(data.regions());

        if (!data.cities().isEmpty()) {
            CityManager manager = CityManager.get(player.serverLevel());

            for (WorldBootstrapAPI.CityBootstrapData c : data.cities()) {
                var city = manager.getCity(c.name());
                if (city == null) continue;

                CityInventory inv = city.getInventory();

                inv.updateSupplies(c.food(), c.wood(), c.metal(), c.stone(), c.textile(), c.alcohol(), c.tech());
                inv.updateTreasury(c.gold(), c.silver(), c.copper());

                // Full overwrite of market data to ensure sync with Rails
                inv.clearAllCommodities();

                c.quantities().forEach((cat, subs) ->
                    subs.forEach((sub, items) ->
                        items.forEach((item, qty) -> inv.addCommodity(cat, sub, item, qty))
                    )
                );

                c.weights().forEach((cat, subs) ->
                    subs.forEach((sub, items) ->
                        items.forEach((item, w) -> inv.addCommodityWeight(cat, sub, item, w))
                    )
                );
            }

            manager.setDirty();
            LOGGER.info("Synced {} cities from Rails.", data.cities().size());
        }

        LOGGER.info("🌐 World bootstrap loaded: {} fish, {} regions, {} cities",
            data.fish().size(), data.regions().size(), data.cities().size());
    }
}
