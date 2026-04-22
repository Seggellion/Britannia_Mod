package com.seggellion.britannia_mod.quest.events;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.fml.loading.FMLLoader;

public class QuestEventHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        if (player.level().isClientSide() && player.tickCount % 20 == 0) {
            QuestModels.QuestResponse state = QuestManager.getInstance().getCurrentQuestState();
            
            if (state != null && state.currentNode != null && state.currentNode.metadata != null) {
                if (state.currentNode.metadata.has("location_trigger")) {
                    JsonObject locData = state.currentNode.metadata.getAsJsonObject("location_trigger");
                    String triggerKey = locData.has("trigger_key") ? locData.get("trigger_key").getAsString() : "";
                    
                    if (!triggerKey.isEmpty()) {
                        BlockPos min = new BlockPos(getSafeInt(locData, "min_x"), getSafeInt(locData, "min_y"), getSafeInt(locData, "min_z"));
                        BlockPos max = new BlockPos(getSafeInt(locData, "max_x"), getSafeInt(locData, "max_y"), getSafeInt(locData, "max_z"));
                        
                        if (isInsideZone(player.blockPosition(), min, max)) {
                            QuestClient.sendTrigger(state.quest_id, triggerKey, response -> {
                                if (response.success && FMLLoader.getDist().isClient()) {
                                    ClientNetworkHandler.openQuestDecisionScreen(response, "The Guardian", null);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        Player player = event.getPlayer();
        if (!player.level().isClientSide()) return; 

        QuestModels.QuestResponse state = QuestManager.getInstance().getCurrentQuestState();
        if (state == null || state.currentNode == null || state.currentNode.metadata == null) return;

        if (state.currentNode.metadata.has("pickup_trigger")) {
            JsonObject pickupData = state.currentNode.metadata.getAsJsonObject("pickup_trigger");
            String targetTag = pickupData.has("item_tag") ? pickupData.get("item_tag").getAsString() : "";
            String triggerKey = pickupData.has("trigger_key") ? pickupData.get("trigger_key").getAsString() : "";

            if (!targetTag.isEmpty() && !triggerKey.isEmpty()) {
                ItemStack stack = event.getOriginalStack();
                
                // Use our new smarter matcher!
                if (isQuestItemMatch(stack, targetTag)) {
                    QuestClient.sendTrigger(state.quest_id, triggerKey, response -> {
                        if (response.success) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("A heavy burden placed upon your soul..."), true);
                        }
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() || !(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) {
            return;
        }

        if (!itemEntity.isInLava()) return;
        QuestModels.QuestResponse state = QuestManager.getInstance().getCurrentQuestState();

        if (state == null || state.currentNode == null || state.currentNode.metadata == null) return;
        if (state.currentNode.metadata.has("destroy_trigger")) {
            JsonObject destroyData = state.currentNode.metadata.getAsJsonObject("destroy_trigger");
            String targetTag = destroyData.has("item_tag") ? destroyData.get("item_tag").getAsString() : "";
            String triggerKey = destroyData.has("trigger_key") ? destroyData.get("trigger_key").getAsString() : "";

            if (!targetTag.isEmpty() && !triggerKey.isEmpty()) {
                BlockPos min = new BlockPos(getSafeInt(destroyData, "min_x"), getSafeInt(destroyData, "min_y"), getSafeInt(destroyData, "min_z"));
                BlockPos max = new BlockPos(getSafeInt(destroyData, "max_x"), getSafeInt(destroyData, "max_y"), getSafeInt(destroyData, "max_z"));
                if (isInsideZone(itemEntity.blockPosition(), min, max)) {
                    ItemStack stack = itemEntity.getItem();
                    // Use our new smarter matcher!
                    if (isQuestItemMatch(stack, targetTag)) {
                                                        LOGGER.info("QUEST ITEM MATCHED");
                        QuestManager.getInstance().clearState();
                        
                        QuestClient.sendTrigger(state.quest_id, triggerKey, response -> {
                            if (response.success && FMLLoader.getDist().isClient()) {
                                ClientNetworkHandler.openQuestDecisionScreen(response, "The Guardian", null);
                            }
                        });
                    }
                }
            }
        }
    }

    // --- Helpers ---

    /**
     * Intelligently checks if an ItemStack matches the target tag required by the quest.
     */
    private static boolean isQuestItemMatch(ItemStack stack, String targetTag) {
        if (stack.isEmpty()) return false;

        // 1. Check Custom Data (Robust NBT matching)
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.contains("quest_item") && customData.copyTag().getString("quest_item").equals(targetTag)) {
            return true;
        }

        // 2. Check Custom Name Component (Matches "a magic gold ring")
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            String itemName = stack.get(DataComponents.CUSTOM_NAME).getString();
            if (itemName.equalsIgnoreCase(targetTag)) {
                return true;
            }
        }

        // 3. Check raw Item ID (Matches "minecraft:gold_nugget")
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId.equalsIgnoreCase(targetTag)) {
            return true;
        }

        return false;
    }

    private static boolean isInsideZone(BlockPos playerPos, BlockPos min, BlockPos max) {
        if (min.equals(BlockPos.ZERO) && max.equals(BlockPos.ZERO)) return false;

        return playerPos.getX() >= min.getX() && playerPos.getX() <= max.getX() &&
               playerPos.getY() >= min.getY() && playerPos.getY() <= max.getY() &&
               playerPos.getZ() >= min.getZ() && playerPos.getZ() <= max.getZ();
    }

    private static int getSafeInt(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).getAsString().isEmpty()) {
            try {
                return obj.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}