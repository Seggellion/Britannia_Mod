package com.seggellion.britannia_mod.quest;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.quest.network.QuestServerAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side detection of environmental quest objectives (Milestone 6, findings Q-02, Q-05, Q-06).
 *
 * <p>These objectives -- walking into a place, picking an item up, destroying one -- used to be
 * detected on the CLIENT, which then asserted the trigger to the server. Three things followed
 * from that:
 *
 * <ul>
 *   <li>the client drove them from a single static, {@code QuestManager.currentQuestState}, whose
 *       writers are a successful in-session quest action and a server trigger result -- neither of
 *       which runs at login. After any relog nothing fired at all, and with two active quests only
 *       the most recently touched one could progress (Q-02);</li>
 *   <li>a modified client could fire any objective it liked (Q-05);</li>
 *   <li>the location check re-fired every twenty ticks for as long as a player stood in the zone,
 *       with no debounce (Q-06).</li>
 * </ul>
 *
 * <p>Detection now runs here, over the SERVER's journal, which covers every active quest and
 * survives a relog because it is refetched rather than remembered. The client is told what
 * happened; it is never asked.
 */
public final class QuestObjectiveWatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    /** Matches the cadence the old client-side check used; objectives are not frame-critical. */
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final long FAILURE_COOLDOWN_MILLIS = 10_000L;

    /** One in-flight trigger per (quest state, trigger key): the debounce Q-06 asks for. */
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private QuestObjectiveWatcher() {}

    /** Called every server player tick; does its work on a fixed cadence. */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        BlockPos position = player.blockPosition();
        for (ClientQuestEntry entry : ServerQuestTable.snapshot(player)) {
            QuestObjectiveTriggers.Location location = entry.triggers().location();
            if (location == null || !location.contains(position)) continue;
            fire(player, entry, location.triggerKey(), "location");
        }
    }

    /**
     * Called after an item actually entered this player's inventory. The event carries the stack
     * the server moved, so nothing here depends on the client agreeing that it happened.
     */
    public static void onItemPickedUp(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;

        for (ClientQuestEntry entry : ServerQuestTable.snapshot(player)) {
            QuestObjectiveTriggers.Pickup pickup = entry.triggers().pickup();
            if (pickup == null) continue;
            if (!QuestItemMatcher.matches(stack, pickup.itemTag())) continue;
            fire(player, entry, pickup.triggerKey(), "pickup");
        }
    }

    /**
     * Called when a quest item is destroyed. The caller has already established that the item was
     * this player's and that it died inside the objective's volume.
     */
    public static void onQuestItemDestroyed(ServerPlayer player, ItemStack stack, BlockPos where) {
        if (player == null || stack == null || stack.isEmpty()) return;

        for (ClientQuestEntry entry : ServerQuestTable.snapshot(player)) {
            QuestObjectiveTriggers.Destroy destroy = entry.triggers().destroy();
            if (destroy == null) continue;
            if (!destroy.contains(where)) continue;
            if (!QuestItemMatcher.matches(stack, destroy.itemTag())) continue;
            fire(player, entry, destroy.triggerKey(), "destroy");
        }
    }

    /** A server-decided trigger with no environmental source, such as an escort arriving. */
    public static void fireDirect(ServerPlayer player, long questId, String questStateId,
                                  String triggerKey, String source) {
        if (player == null || questId <= 0 || triggerKey == null || triggerKey.isBlank()) return;
        send(player, questId, questStateId == null ? "" : questStateId, triggerKey, source);
    }

    private static void fire(ServerPlayer player, ClientQuestEntry entry, String triggerKey, String source) {
        long questId = questId(entry);
        if (questId <= 0 || triggerKey == null || triggerKey.isBlank()) return;
        send(player, questId, entry.questStateId(), triggerKey, source);
    }

    private static void send(ServerPlayer player, long questId, String questStateId,
                             String triggerKey, String source) {
        String key = player.getStringUUID() + "|" + questStateId + "|" + triggerKey;

        Long cooldownUntil = COOLDOWN_UNTIL.get(key);
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) return;
        if (!IN_FLIGHT.add(key)) return;

        LOGGER.info("event=quest_objective_detected player_uuid={} quest_id={} quest_state_id={} "
                + "trigger_key={} source={}",
            player.getStringUUID(), questId, questStateId, triggerKey, source);

        QuestServerAPI.sendTrigger(player.server, player.getStringUUID(), questId, triggerKey,
            response -> {
                IN_FLIGHT.remove(key);
                if (response == null || !response.success) {
                    // Cool down rather than retry every second against a service that just said no.
                    COOLDOWN_UNTIL.put(key, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
                    LOGGER.warn("event=quest_objective_rejected player_uuid={} quest_id={} "
                            + "trigger_key={} source={} error={}",
                        player.getStringUUID(), questId, triggerKey, source,
                        response == null ? "null_response" : response.error);
                    return;
                }
                applySuccess(player, response, questId, questStateId, triggerKey);
            });
    }

    /**
     * Applies an accepted objective: rewards, then the journal, then the client.
     *
     * <p>Updating the journal is what stops the trigger firing again on the next tick. Rails has
     * advanced the node, and the response carries the new one, so the objective this player just
     * met is gone from the server's view without waiting for a refetch.
     */
    private static void applySuccess(ServerPlayer player, QuestModels.QuestResponse response,
                                     long questId, String questStateId, String triggerKey) {
        QuestRewardService.apply(player, response);

        if (response.completed) {
            ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, questId);
        } else {
            QuestObjectiveTriggers updated = response.currentNode == null
                ? QuestObjectiveTriggers.NONE
                : QuestObjectiveTriggers.fromNodeMetadata(response.currentNode.metadata);
            ServerQuestTable.updateTriggers(player, questStateId, updated);
        }

        PacketDistributor.sendToPlayer(player,
            new QuestTriggerResultS2CPayload(GSON.toJson(response), questId, triggerKey));

        LOGGER.info("event=quest_objective_applied player_uuid={} quest_id={} quest_state_id={} "
                + "trigger_key={} completed={}",
            player.getStringUUID(), questId, questStateId, triggerKey, response.completed);
    }

    /** Drops everything remembered about a player; called when they log out. */
    public static void forget(UUID playerUuid) {
        if (playerUuid == null) return;
        String prefix = playerUuid + "|";
        IN_FLIGHT.removeIf(key -> key.startsWith(prefix));
        COOLDOWN_UNTIL.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static long questId(ClientQuestEntry entry) {
        try {
            return Long.parseLong(entry.questId());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
