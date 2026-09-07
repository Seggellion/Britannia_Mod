package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDelivery;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryItem;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryParser;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryService;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies only reward data received directly from an authenticated Rails response.
 *
 * <p>Rowan farming questline M1 (discovery D1, protocol section 1.5): a granted stack is stamped for
 * cleanup ONLY when {@link QuestTemporaryItemPolicy} finds it temporary -- Rails says so, or the
 * destination node's pickup/destroy objective names it. Everything else is the player's to keep and
 * carries no stamp, so {@link QuestCleanupService} has nothing to take back when the quest ends.
 * The stamp itself is {@link QuestItemStamp}; a temporary stamp always names its trigger key.
 *
 * <p>M3 (protocol sections 1.3, 1.8): a response that carries {@code reward_delivery} is applied
 * through the durable ledger by {@link QuestRewardDeliveryService}, and {@code granted_items} is
 * ignored -- the delivery's items are the grant. A malformed delivery grants nothing at all: not
 * even {@code granted_items}, because Rails recorded a delivery this side could not read and the
 * pending listing would hand it back later. Only a response without any {@code reward_delivery}
 * (an older Rails) takes the immediate path below, logged as {@code delivery_mode=legacy}.
 */
public final class QuestRewardService {
    static final int MAX_REWARD_ITEMS = 32;
    static final int MAX_ITEM_COUNT = 1_024;
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRewardService() {}

    /** Without the raw response only the destination-node heuristic can decide temporariness. */
    public static void apply(ServerPlayer player, QuestModels.QuestResponse response) {
        apply(player, response, null, "");
    }

    /**
     * @param rawResponse the parsed response body when the caller still has it, so a
     *                    {@code reward_delivery.items[].temporary} verdict is honoured; may be null
     */
    public static void apply(ServerPlayer player, QuestModels.QuestResponse response, JsonObject rawResponse) {
        apply(player, response, rawResponse, "");
    }

    /**
     * @param requestUuid the correlation id of the transition that produced this response, for
     *                    the delivery log lines; empty when the caller has none
     */
    public static void apply(ServerPlayer player, QuestModels.QuestResponse response,
                             @Nullable JsonObject rawResponse, String requestUuid) {
        if (player == null || response == null || !response.success) return;
        String rid = requestUuid == null ? "" : requestUuid;

        JsonObject deliveryRoot = rawResponse != null ? rawResponse : deliveryRootOf(response);
        QuestRewardDeliveryParser.TransitionResult parsed = QuestRewardDeliveryParser.parseTransition(deliveryRoot);
        if (parsed instanceof QuestRewardDeliveryParser.Present present) {
            QuestRewardDeliveryService.applyFromTransition(player, present.delivery(), present.replayed(),
                rid, response, deliveryRoot);
            return;
        }
        if (parsed instanceof QuestRewardDeliveryParser.Malformed malformed) {
            LOGGER.warn("event=quest_delivery_rejected player_uuid={} quest_id={} quest_state_id={} outcome=rejected "
                    + "reason={} request_uuid={} source=transition",
                player.getStringUUID(), response.quest_id, questStateId(response), malformed.reason(), rid);
            return;
        }

        if (response.granted_items == null) return;
        List<QuestModels.ItemData> rewards = response.granted_items;
        if (rewards.size() > MAX_REWARD_ITEMS) {
            LOGGER.warn("Rejected oversized authoritative quest reward list player={} count={}",
                player.getStringUUID(), rewards.size());
            return;
        }
        if (!rewards.isEmpty()) {
            LOGGER.info("event=quest_reward_applied delivery_mode=legacy player_uuid={} quest_id={} quest_state_id={} "
                    + "item_count={} request_uuid={}",
                player.getStringUUID(), response.quest_id, questStateId(response), rewards.size(), rid);
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
                ItemStack stack = newRewardStack(item, count, reward.id);
                if (decision.temporary()) stampTemporary(stack, decision, response, player);
                // Legacy compatibility only: a delivery never drops (M3); this path predates the ledger.
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                remaining -= count;
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * The stacks a delivery grants, resolved and stamped exactly as the legacy path stamps a
     * granted item: Rails' {@code temporary} verdict wins, otherwise the destination node of the
     * response (when there is one) decides. Every stack is at most one stack's worth, so the
     * all-or-nothing insertion can place them slot by slot.
     *
     * @param response    the transition response, when the delivery arrived inside one; null for a
     *                    delivery applied at login, from the pending listing, or from the ledger
     * @param rawResponse the parsed body of that response; null in the same cases
     * @return the stacks, or null when an item cannot be resolved -- the delivery is then refused
     *         whole, because a partial grant could never be reconciled against its identity
     */
    public static List<ItemStack> buildDeliveryStacks(ServerPlayer player, QuestRewardDelivery delivery,
                                                      @Nullable QuestModels.QuestResponse response,
                                                      @Nullable JsonObject rawResponse) {
        QuestModels.QuestResponse context = deliveryContext(delivery, response);
        JsonObject verdicts = rawResponse != null ? rawResponse : delivery.asTransitionRoot();
        List<ItemStack> stacks = new ArrayList<>();
        for (QuestRewardDeliveryItem item : delivery.items()) {
            Item resolved = resolveItem(item.id());
            if (resolved == Items.AIR) {
                LOGGER.warn("Quest delivery item could not be resolved player_uuid={} item_id={} quest_id={} delivery_uuid={}",
                    player.getStringUUID(), item.id(), delivery.questId(), delivery.deliveryUuid());
                return null;
            }
            QuestTemporaryItemPolicy.Decision decision = QuestTemporaryItemPolicy.decide(
                item.id(), BuiltInRegistries.ITEM.getKey(resolved), context, verdicts);
            if (decision.temporary()) {
                LOGGER.info("event=quest_reward_stamped_temporary player_uuid={} quest_id={} quest_state_id={} "
                        + "item_tag={} trigger_key={} source={} delivery_uuid={}",
                    player.getStringUUID(), context.quest_id, questStateId(context),
                    decision.itemTag(), decision.triggerKey(), decision.source(), delivery.deliveryUuid());
            }
            int remaining = item.count();
            int maxStack = new ItemStack(resolved).getMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemStack stack = newRewardStack(resolved, count, item.id());
                if (decision.temporary()) stampTemporary(stack, decision, context, player);
                stacks.add(stack);
                remaining -= count;
            }
        }
        return stacks;
    }

    static boolean valid(QuestModels.ItemData reward) {
        return reward != null && reward.id != null && !reward.id.isBlank() && reward.id.length() <= 128
            && reward.id.chars().noneMatch(Character::isISOControl)
            && reward.count > 0 && reward.count <= MAX_ITEM_COUNT;
    }

    private static Item resolveItem(String rawId) {
        if (isMagicRing(rawId)) return ItemRegistry.ONE_RING.get();
        ResourceLocation id = ResourceLocation.tryParse(rawId.contains(":") ? rawId : "britannia_mod:" + rawId);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    /** The one authored id that is not a registry id; Rails namespaces it like any other. */
    private static boolean isMagicRing(String rawId) {
        if (rawId == null) return false;
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        return id.equals("magic_ring") || id.equals("britannia_mod:magic_ring");
    }

    private static ItemStack newRewardStack(Item item, int count, String rawId) {
        ItemStack stack = new ItemStack(item, count);
        if (isMagicRing(rawId)) {
            stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("a magic gold ring").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        return stack;
    }

    private static String questStateId(QuestModels.QuestResponse response) {
        return response.questStateId == null ? "" : response.questStateId.trim();
    }

    /**
     * The transition root a parsed-only response still carries; null when it carries none. A JSON
     * {@code null} is "none", exactly as an absent key is: Rails sends it for a transition that
     * granted nothing, and treating it as a delivery would refuse a legal response.
     */
    private static JsonObject deliveryRootOf(QuestModels.QuestResponse response) {
        if (response.reward_delivery == null || response.reward_delivery.isJsonNull()) return null;
        JsonObject root = new JsonObject();
        root.add("reward_delivery", response.reward_delivery.deepCopy());
        if (response.replayed != null) root.addProperty("replayed", response.replayed);
        return root;
    }

    /**
     * The response the stamp and the heuristic read: the delivery's own identity first, the
     * transition response's node (and any identity the delivery lacks) second.
     */
    private static QuestModels.QuestResponse deliveryContext(QuestRewardDelivery delivery,
                                                             @Nullable QuestModels.QuestResponse response) {
        QuestModels.QuestResponse context = new QuestModels.QuestResponse();
        context.success = true;
        context.quest_id = delivery.questId() > 0 ? delivery.questId() : response == null ? 0L : response.quest_id;
        context.questStateId = !delivery.questStateId().isBlank() ? delivery.questStateId()
            : response == null ? "" : response.questStateId;
        context.currentNode = response == null ? null : response.currentNode;
        return context;
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
