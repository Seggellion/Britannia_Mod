package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import java.util.List;

/**
 * Applies only reward data received directly from an authenticated Rails response.
 *
 * <p>Rowan farming questline M1 (discovery D1, protocol section 1.5): a granted stack is stamped for
 * cleanup ONLY when {@link QuestTemporaryItemPolicy} finds it temporary -- Rails says so, or the
 * destination node's pickup/destroy objective names it. Everything else is the player's to keep and
 * carries no stamp, so {@link QuestCleanupService} has nothing to take back when the quest ends.
 * The stamp itself is {@link QuestItemStamp}; a temporary stamp always names its trigger key.
 */
public final class QuestRewardService {
    static final int MAX_REWARD_ITEMS = 32;
    static final int MAX_ITEM_COUNT = 1_024;
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRewardService() {}

    /** Without the raw response only the destination-node heuristic can decide temporariness. */
    public static void apply(ServerPlayer player, QuestModels.QuestResponse response) {
        apply(player, response, null);
    }

    /**
     * @param rawResponse the parsed response body when the caller still has it, so a
     *                    {@code reward_delivery.items[].temporary} verdict is honoured; may be null
     */
    public static void apply(ServerPlayer player, QuestModels.QuestResponse response, JsonObject rawResponse) {
        if (player == null || response == null || !response.success || response.granted_items == null) return;
        List<QuestModels.ItemData> rewards = response.granted_items;
        if (rewards.size() > MAX_REWARD_ITEMS) {
            LOGGER.warn("Rejected oversized authoritative quest reward list player={} count={}",
                player.getStringUUID(), rewards.size());
            return;
        }

        for (QuestModels.ItemData reward : rewards) {
            if (!valid(reward)) {
                LOGGER.warn("Rejected invalid authoritative quest reward player={} quest_id={}",
                    player.getStringUUID(), response.quest_id);
                continue;
            }
            Item item = resolveItem(reward.id);
            if (item == Items.AIR) {
                LOGGER.warn("Quest reward item could not be resolved player={} item_id={} quest_id={}",
                    player.getStringUUID(), reward.id, response.quest_id);
                continue;
            }

            QuestTemporaryItemPolicy.Decision decision = QuestTemporaryItemPolicy.decide(
                reward.id, BuiltInRegistries.ITEM.getKey(item), response, rawResponse);
            if (decision.temporary()) {
                LOGGER.info("event=quest_reward_stamped_temporary player_uuid={} quest_id={} quest_state_id={} "
                        + "item_tag={} trigger_key={} source={}",
                    player.getStringUUID(), response.quest_id, questStateId(response),
                    decision.itemTag(), decision.triggerKey(), decision.source());
            }

            int remaining = reward.count;
            int maxStack = new ItemStack(item).getMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(item, count);
                if ("magic_ring".equals(reward.id)) {
                    stack.set(DataComponents.CUSTOM_NAME,
                        Component.literal("a magic gold ring").withStyle(net.minecraft.ChatFormatting.GOLD));
                }
                if (decision.temporary()) stampTemporary(stack, decision, response, player);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                remaining -= count;
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    static boolean valid(QuestModels.ItemData reward) {
        return reward != null && reward.id != null && !reward.id.isBlank() && reward.id.length() <= 128
            && reward.id.chars().noneMatch(Character::isISOControl)
            && reward.count > 0 && reward.count <= MAX_ITEM_COUNT;
    }

    private static Item resolveItem(String rawId) {
        if ("magic_ring".equals(rawId)) return ItemRegistry.ONE_RING.get();
        ResourceLocation id = ResourceLocation.tryParse(rawId.contains(":") ? rawId : "britannia_mod:" + rawId);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    private static String questStateId(QuestModels.QuestResponse response) {
        return response.questStateId == null ? "" : response.questStateId.trim();
    }

    /**
     * The temporary stamp: what the objective calls the item, who it was granted to, which quest
     * state it belongs to, the objective's trigger key, and -- for a destroy objective -- the volume
     * it must die in. No player name: the UUID is the only identity the stamp needs.
     */
    private static void stampTemporary(ItemStack stack, QuestTemporaryItemPolicy.Decision decision,
                                       QuestModels.QuestResponse response, ServerPlayer player) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(QuestItemStamp.ITEM, decision.itemTag());
        tag.putString(QuestItemStamp.OWNER_UUID, player.getStringUUID());
        if (response.quest_id > 0) tag.putLong(QuestItemStamp.QUEST_ID, response.quest_id);
        String questStateId = questStateId(response);
        if (!questStateId.isBlank()) tag.putString(QuestItemStamp.QUEST_STATE_ID, questStateId);
        tag.putString(QuestItemStamp.TRIGGER_KEY, decision.triggerKey());

        QuestTemporaryItemPolicy.Volume volume = decision.volume();
        if (volume != null) {
            tag.putInt(QuestItemStamp.MIN_X, volume.minX());
            tag.putInt(QuestItemStamp.MIN_Y, volume.minY());
            tag.putInt(QuestItemStamp.MIN_Z, volume.minZ());
            tag.putInt(QuestItemStamp.MAX_X, volume.maxX());
            tag.putInt(QuestItemStamp.MAX_Y, volume.maxY());
            tag.putInt(QuestItemStamp.MAX_Z, volume.maxZ());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
