package com.seggellion.britannia_mod.quest.delivery;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * The three player-facing states of a delivery (protocol section 1.8, M3 item 6): the reward
 * arrived, the reward is waiting for room in the pack, a waiting reward has now been handed
 * over. Plain translatable lines; M8 owns any richer presentation. A notice can never fail a
 * delivery: the items and the ledger are already in place when it is sent.
 */
public final class QuestRewardDeliveryNotices {
    public static final String RECEIVED = "message.britannia_mod.quest.reward.received";
    public static final String QUEUED = "message.britannia_mod.quest.reward.queued";
    public static final String DELIVERED = "message.britannia_mod.quest.reward.delivered";
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRewardDeliveryNotices() {}

    /** "Reward received": a transient action-bar line. */
    public static void received(ServerPlayer player) {
        send(player, RECEIVED, true);
    }

    /** "Reward waiting for inventory space": a chat line, so it survives the moment. */
    public static void queued(ServerPlayer player) {
        send(player, QUEUED, false);
    }

    /** "Reward delivered": a queued reward has landed. */
    public static void delivered(ServerPlayer player) {
        send(player, DELIVERED, true);
    }

    private static void send(ServerPlayer player, String key, boolean actionBar) {
        if (player == null) return;
        try {
            player.displayClientMessage(Component.translatable(key), actionBar);
        } catch (RuntimeException unreachableClient) {
            LOGGER.debug("event=quest_delivery_notice_skipped player_uuid={} key={} error={}",
                player.getStringUUID(), key, unreachableClient.toString());
        }
    }
}
