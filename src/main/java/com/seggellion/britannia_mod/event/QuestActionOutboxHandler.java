package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.quest.action.QuestActionDispatcher;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Rowan farming questline M5: the schedule of the action-event outbox (protocol section 2.2), and
 * the quest half of the dung harvest bridge.
 *
 * <ul>
 *   <li><b>Server start</b> schedules the whole outbox, so an event that outlived a restart is
 *       posted before anything else happens.</li>
 *   <li><b>Login</b> flushes that player's entries first.</li>
 *   <li><b>Every second</b> anything whose backoff has elapsed is retried.</li>
 *   <li><b>Logout</b> and <b>server stop</b> drop the RAM-only bookkeeping; the rows themselves are
 *       durable and are meant to survive both.</li>
 * </ul>
 *
 * <p>{@link #onWildResourceHarvested} is the extension of the existing dung bridge the milestone
 * asks for: {@code WildResourceHarvestService} has posted {@link WildResourceHarvestEvent} on every
 * authoritative harvest since Patch 18 and nothing consumed it. It is consumed here, on the game
 * bus, after the block, the ledger row and the loot have all been settled by the service -- so a
 * denied swing, a node that was not tracked, or a Creative break that grants nothing produces no
 * event, because none of those post the harvest event in the first place.
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class QuestActionOutboxHandler {
    private QuestActionOutboxHandler() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QuestActionDispatcher.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QuestActionDispatcher.tick(event.getServer());
        // M9 item 4 / discovery D11: the bounded skill-data retry runs on the same beat. It is one
        // map lookup per connected player and does nothing at all unless somebody's skill fetch
        // actually failed, which on a healthy server is nobody.
        com.seggellion.britannia_mod.skill.SkillManager.tickSkillDataRetries(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestActionDispatcher.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestActionDispatcher.forgetPlayer(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QuestActionDispatcher.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onWildResourceHarvested(WildResourceHarvestEvent event) {
        if (!(event.player().level() instanceof ServerLevel level)) return;
        QuestActionEvents.wildResourceHarvest(event.player(), level, event.position(),
            event.resourceId().toString(), event.player().getMainHandItem());
    }
}
