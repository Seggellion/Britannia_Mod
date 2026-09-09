package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.handin.QuestHandinLedgerEntry;
import com.seggellion.britannia_mod.quest.handin.QuestItemHandinReconciler;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Drives the hand-in reconciler from the game bus (protocol section 1.5.3).
 *
 * <p>Login is where the two halves of the evidence are both readable -- the world's ledger and the
 * player's own persistent data, which has just come back from disk -- so it is where a transaction
 * interrupted by a crash is repaired. The periodic sweep exists for the ordinary case of a lost
 * response while the player stays online.
 *
 * <p>The boot report is not decoration. A transaction whose items are gone and whose answer never
 * arrived is a player owed either a quest or their goods back, and the one thing that must not
 * happen is for it to sit in a file nobody looks at.
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class QuestItemHandinReconcilerHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestItemHandinReconcilerHandler() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Installed once, so a completion recovered after a restart runs the same journal, reward
        // and achievement path an ordinary choice runs.
        QuestItemHandinReconciler.useCompletionApplier(QuestProxyService.handinCompletionApplier());
        List<QuestHandinLedgerEntry> outstanding = QuestItemHandinReconciler.outstanding(event.getServer());
        if (outstanding.isEmpty()) return;
        LOGGER.warn("event=quest_handin_outstanding_at_boot count={}", outstanding.size());
        for (QuestHandinLedgerEntry entry : outstanding) {
            LOGGER.warn("event=quest_handin_outstanding handin_uuid={} player_uuid={} state={} attempts={} last_error={}",
                    entry.handinUuid(), entry.playerUuid(), entry.localState(), entry.attempts(),
                    entry.lastError());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestItemHandinReconciler.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QuestItemHandinReconciler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestItemHandinReconciler.forgetPlayer(player.server, player.getUUID());
            com.seggellion.britannia_mod.quest.RowanQuestlineHooks.forgetPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QuestItemHandinReconciler.clear(event.getServer());
    }
}
