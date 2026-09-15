package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciler;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Rowan farming questline M3: drives the periodic half of the reward-delivery reconciler
 * (protocol section 1.8) from the game bus -- queued deliveries retry on inventory change and
 * every 30 s, outstanding acknowledgements on the 60 s / 10 min schedule -- and drops the
 * per-player and per-server runtime state when they go away. The login and journal-refresh
 * triggers live with the code that already owns those moments ({@code WorldBootstrapHandler},
 * {@code QuestJournalRefresh}).
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class QuestRewardDeliveryReconcilerHandler {
    private QuestRewardDeliveryReconcilerHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QuestRewardDeliveryReconciler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRewardDeliveryReconciler.forgetPlayer(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QuestRewardDeliveryReconciler.clear(event.getServer());
        QuestRewardDeliveryService.clear(event.getServer());
    }
}
