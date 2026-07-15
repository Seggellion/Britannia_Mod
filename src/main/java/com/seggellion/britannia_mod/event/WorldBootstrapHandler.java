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
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;

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
                ServiceNpcRegistryCache.replace(data.serviceNpcRegistry());

                // 1. Existing Logic
                FishCatalog.clear();
                data.fish().forEach(FishCatalog::put);
                RegionCache.update(data.regions());

                // 2. NEW: City Synchronization
                if (!data.cities().isEmpty()) {
                    CityManager manager = CityManager.get(player.serverLevel());
                    
                    for (WorldBootstrapAPI.CityBootstrapData c : data.cities()) {
                        // Get or create the city
                        var city = manager.getCity(c.name());
                        if (city == null) continue; // Or create if your logic allows

                        CityInventory inv = city.getInventory();
                        
                        // Update basic stats
                        inv.updateSupplies(c.food(), c.wood(), c.metal(), c.stone(), c.textile(), c.alcohol(), c.tech());
                        inv.updateTreasury(c.gold(), c.silver(), c.copper());

                        // Full overwrite of market data to ensure sync with Rails
                        inv.clearAllCommodities(); 
                        
                        // Re-populate Commodities (Integers)
                        // FIX: Use .quantities() to match the Record definition
                        c.quantities().forEach((cat, subs) -> 
                            subs.forEach((sub, items) -> 
                                items.forEach((item, qty) -> inv.addCommodity(cat, sub, item, qty))
                            )
                        );

                        // Re-populate Weights (Doubles)
                        c.weights().forEach((cat, subs) -> 
                            subs.forEach((sub, items) -> 
                                items.forEach((item, w) -> inv.addCommodityWeight(cat, sub, item, w))
                            )
                        );
                    }
                    
                    // Mark dirty to save to disk immediately
                    manager.setDirty();
                    LOGGER.info("Synced {} cities from Rails.", data.cities().size());
                }

                LOGGER.info("🌐 World bootstrap loaded: {} fish, {} regions, {} cities", 
                    data.fish().size(), data.regions().size(), data.cities().size());

                ServerQuestTable.replaceFromBootstrap(player, data.acceptedQuests());
                QuestCleanupService.cleanupStaleLocalQuestState(player, data.acceptedQuests());
                ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));

            }, player.server);
    }
}
