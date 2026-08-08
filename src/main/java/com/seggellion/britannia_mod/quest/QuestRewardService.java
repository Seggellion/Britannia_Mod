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

/** Applies only reward data received directly from an authenticated Rails response. */
public final class QuestRewardService {
    static final int MAX_REWARD_ITEMS = 32;
    static final int MAX_ITEM_COUNT = 1_024;
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRewardService() {}

    public static void apply(ServerPlayer player, QuestModels.QuestResponse response) {
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
            int remaining = reward.count;
            int maxStack = new ItemStack(item).getMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(item, count);
                if ("magic_ring".equals(reward.id)) {
                    stack.set(DataComponents.CUSTOM_NAME,
                        Component.literal("a magic gold ring").withStyle(net.minecraft.ChatFormatting.GOLD));
                }
                stamp(stack, reward, response, player);
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

    private static void stamp(ItemStack stack, QuestModels.ItemData reward, QuestModels.QuestResponse response,
                              ServerPlayer player) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("quest_item", reward.id);
        tag.putString("quest_owner_uuid", player.getStringUUID());
        tag.putString("quest_owner_name", player.getGameProfile().getName());
        if (response.quest_id > 0) tag.putLong("quest_id", response.quest_id);
        if (response.questStateId != null && !response.questStateId.isBlank()) {
            tag.putString("quest_state_id", response.questStateId.trim());
        }

        JsonObject destroy = destroyTrigger(response);
        String target = string(destroy, "item_tag");
        if (destroy != null && matches(reward.id, target)) {
            tag.putString("quest_trigger_key", string(destroy, "trigger_key"));
            tag.putString("quest_item", target);
            tag.putInt("quest_min_x", integer(destroy, "min_x"));
            tag.putInt("quest_min_y", integer(destroy, "min_y"));
            tag.putInt("quest_min_z", integer(destroy, "min_z"));
            tag.putInt("quest_max_x", integer(destroy, "max_x"));
            tag.putInt("quest_max_y", integer(destroy, "max_y"));
            tag.putInt("quest_max_z", integer(destroy, "max_z"));
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static JsonObject destroyTrigger(QuestModels.QuestResponse response) {
        if (response.currentNode == null || response.currentNode.metadata == null
            || !response.currentNode.metadata.has("destroy_trigger")
            || !response.currentNode.metadata.get("destroy_trigger").isJsonObject()) return null;
        return response.currentNode.metadata.getAsJsonObject("destroy_trigger");
    }

    private static boolean matches(String itemId, String target) {
        if (itemId == null || target == null || target.isBlank()) return false;
        return itemId.equalsIgnoreCase(target)
            || itemId.replace("britannia_mod:", "").equalsIgnoreCase(target.replace("britannia_mod:", ""));
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static int integer(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
        try { return object.get(key).getAsInt(); }
        catch (RuntimeException ignored) { return 0; }
    }
}
