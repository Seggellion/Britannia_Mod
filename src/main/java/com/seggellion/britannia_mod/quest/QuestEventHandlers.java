package com.seggellion.britannia_mod.quest.events;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.quest.network.QuestServerAPI;
import com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.ItemBurnedS2CPayload;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import net.minecraft.advancements.AdvancementHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.fml.loading.FMLLoader;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuestEventHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final long QUEST_ITEM_RETURN_DELAY_TICKS = 200L;
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);
    private static final Map<UUID, PendingQuestItemReturn> PENDING_QUEST_ITEM_RETURNS = new ConcurrentHashMap<>();

    private record PendingQuestItemReturn(
            UUID playerUuid,
            ItemStack stack,
            long returnAtGameTime
    ) {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer && player.tickCount % 20 == 0) {
            processPendingQuestItemReturns(serverPlayer);
        }
        
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
                            player.displayClientMessage(uoMessage("A heavy burden placed upon your soul..."), true);
                        }
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity.level().isClientSide()) return;

        ItemStack stack = itemEntity.getItem();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!isQuestDestroyCandidate(stack, tag)) {
            return;
        }

        ServerPlayer serverPlayer = resolveResponsiblePlayer(itemEntity, tag);
        UUID returnPlayerUuid = serverPlayer != null ? serverPlayer.getUUID() : getStampedOwnerUuid(tag);

        if (returnPlayerUuid == null) {
            event.addExtraLife((int) QUEST_ITEM_RETURN_DELAY_TICKS);
            return;
        }

        long gameTime = itemEntity.level().getGameTime();
        boolean scheduled = scheduleQuestItemReturn(
                itemEntity,
                returnPlayerUuid,
                stack.copy(),
                gameTime + QUEST_ITEM_RETURN_DELAY_TICKS
        );

        if (!scheduled) {
            event.addExtraLife((int) QUEST_ITEM_RETURN_DELAY_TICKS);
            return;
        }

        if (serverPlayer != null) {
            serverPlayer.sendSystemMessage(uoMessage("The quest item fades from the world..."));
        }

        event.addExtraLife(1);
        itemEntity.discard();
    }

   @SubscribeEvent
    public static void onItemEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        if (!itemEntity.isInLava()) return;

        ItemStack stack = itemEntity.getItem();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        if (!isQuestDestroyCandidate(stack, tag)) {
            return;
        }

        String questItemTag = tag.getString("quest_item");
        String triggerKey = tag.getString("quest_trigger_key");
        long questId = tag.getLong("quest_id");
        boolean hasServerContext = questId > 0 && !triggerKey.isBlank();

        ServerPlayer serverPlayer = resolveResponsiblePlayer(itemEntity, tag);
        if (serverPlayer == null) {
            UUID stampedOwnerUuid = getStampedOwnerUuid(tag);
            if (stampedOwnerUuid != null) {
                scheduleQuestItemReturn(
                        itemEntity,
                        stampedOwnerUuid,
                        stack.copy(),
                        itemEntity.level().getGameTime() + QUEST_ITEM_RETURN_DELAY_TICKS
                );
                itemEntity.discard();
                return;
            }

            moveUnresolvedQuestItemOutOfLava(itemEntity);
            return;
        }

        if (!hasServerContext) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new ItemBurnedS2CPayload(stack.copy(), itemEntity.blockPosition())
            );
            itemEntity.discard();
            return;
        }

        if (!isInsideStampedDestroyVolume(itemEntity.blockPosition(), tag)) {
            scheduleQuestItemReturn(
                    itemEntity,
                    serverPlayer.getUUID(),
                    stack.copy(),
                    serverPlayer.serverLevel().getGameTime() + QUEST_ITEM_RETURN_DELAY_TICKS
            );
            serverPlayer.sendSystemMessage(uoMessage("The quest item slips away from the flames..."));
            itemEntity.discard();
            return;
        }

        if (!isQuestItemMatch(stack, questItemTag)) {
            scheduleQuestItemReturn(
                    itemEntity,
                    serverPlayer.getUUID(),
                    stack.copy(),
                    serverPlayer.serverLevel().getGameTime() + QUEST_ITEM_RETURN_DELAY_TICKS
            );
            serverPlayer.sendSystemMessage(uoMessage("The quest item slips away from the flames..."));
            itemEntity.discard();
            return;
        }

        grantQuestAdvancement(serverPlayer, triggerKey);

        MinecraftServer server = serverPlayer.getServer();
        QuestServerAPI.sendTrigger(server, serverPlayer.getStringUUID(), questId, triggerKey, response -> {
            if (response != null && response.success) {
                handleQuestTriggerSuccess(serverPlayer, response, questId, triggerKey);
                serverPlayer.sendSystemMessage(uoMessage("The quest item is consumed."));
            } else {
                LOGGER.warn("Quest destroy Rails trigger failed player={} quest_id={} trigger_key={} error={}",
                        serverPlayer.getStringUUID(), questId, triggerKey, response != null ? response.error : "null response");
            }
        });

        itemEntity.discard();
    }

    // --- Helpers ---

    /**
     * Intelligently checks if an ItemStack matches the target tag required by the quest.
     */
    public static boolean isQuestItemMatch(ItemStack stack, String targetTag) {
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

    public static boolean isInsideZone(BlockPos playerPos, BlockPos min, BlockPos max) {
        if (min.equals(BlockPos.ZERO) && max.equals(BlockPos.ZERO)) return false;

        return playerPos.getX() >= min.getX() && playerPos.getX() <= max.getX() &&
               playerPos.getY() >= min.getY() && playerPos.getY() <= max.getY() &&
               playerPos.getZ() >= min.getZ() && playerPos.getZ() <= max.getZ();
    }

    public static int getSafeInt(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).getAsString().isEmpty()) {
            try {
                return obj.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static Component uoMessage(String text) {
        return Component.literal(text).withStyle(UO_STYLE);
    }

    private static boolean scheduleQuestItemReturn(
            ItemEntity itemEntity,
            UUID playerUuid,
            ItemStack returnStack,
            long returnAtGameTime
    ) {
        if (returnStack.isEmpty()) {
            return false;
        }

        PendingQuestItemReturn pendingReturn = new PendingQuestItemReturn(
                playerUuid,
                returnStack.copy(),
                returnAtGameTime
        );

        PendingQuestItemReturn existing = PENDING_QUEST_ITEM_RETURNS.putIfAbsent(itemEntity.getUUID(), pendingReturn);
        if (existing != null) {
            return false;
        }

        return true;
    }

    private static void handleQuestTriggerSuccess(ServerPlayer player, QuestModels.QuestResponse response, long questId, String triggerKey) {
        String responseJson = GSON.toJson(response);
        PacketDistributor.sendToPlayer(player, new QuestTriggerResultS2CPayload(responseJson, questId, triggerKey));
    }

    private static void processPendingQuestItemReturns(ServerPlayer player) {
        long gameTime = player.serverLevel().getGameTime();

        for (Map.Entry<UUID, PendingQuestItemReturn> entry : List.copyOf(PENDING_QUEST_ITEM_RETURNS.entrySet())) {
            PendingQuestItemReturn pendingReturn = entry.getValue();
            if (!player.getUUID().equals(pendingReturn.playerUuid()) || gameTime < pendingReturn.returnAtGameTime()) {
                continue;
            }

            if (!PENDING_QUEST_ITEM_RETURNS.remove(entry.getKey(), pendingReturn)) {
                continue;
            }

            ItemStack stackToReturn = pendingReturn.stack().copy();
            player.getInventory().add(stackToReturn);
            player.inventoryMenu.broadcastChanges();

            if (stackToReturn.isEmpty()) {
                player.sendSystemMessage(uoMessage("The quest item returns to your pack."));
            } else {
                ItemStack droppedStack = stackToReturn.copy();
                player.drop(droppedStack, false);
                player.sendSystemMessage(uoMessage("The quest item returns at your feet."));
            }
        }
    }

    private static UUID getStampedOwnerUuid(CompoundTag tag) {
        String stampedOwnerUuid = tag.getString("quest_owner_uuid");
        if (stampedOwnerUuid.isBlank()) return null;

        try {
            return UUID.fromString(stampedOwnerUuid);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Quest item has malformed stamped owner uuid stamped_owner_uuid={} quest_item={} quest_id={} trigger_key={}",
                    stampedOwnerUuid, tag.getString("quest_item"), tag.getLong("quest_id"), tag.getString("quest_trigger_key"));
            return null;
        }
    }

    private static void moveUnresolvedQuestItemOutOfLava(ItemEntity itemEntity) {
        itemEntity.setDeltaMovement(0.0D, 0.1D, 0.0D);
        itemEntity.setPos(itemEntity.getX(), itemEntity.getY() + 1.25D, itemEntity.getZ());
        itemEntity.clearFire();
    }

    private static boolean isQuestDestroyCandidate(ItemStack stack, CompoundTag tag) {
        if (stack.isEmpty()) return false;
        return tag.contains("quest_item") || tag.contains("quest_id") || tag.contains("quest_trigger_key");
    }

    private static ServerPlayer resolveResponsiblePlayer(ItemEntity itemEntity, CompoundTag tag) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) return null;

        String stampedOwnerUuid = tag.getString("quest_owner_uuid");
        if (!stampedOwnerUuid.isBlank()) {
            try {
                ServerPlayer stampedOwner = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(stampedOwnerUuid));
                if (stampedOwner != null) {
                    Entity owner = itemEntity.getOwner();
                    if (owner instanceof ServerPlayer ownerPlayer && !ownerPlayer.getUUID().equals(stampedOwner.getUUID())) {
                        LOGGER.warn("Quest destroy owner mismatch item_entity={} stamped_owner={} stamped_owner_name={} item_owner={} item_owner_name={}",
                                itemEntity.getStringUUID(),
                                stampedOwner.getStringUUID(),
                                stampedOwner.getGameProfile().getName(),
                                ownerPlayer.getStringUUID(),
                                ownerPlayer.getGameProfile().getName());
                    }
                    return stampedOwner;
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Quest destroy invalid stamped owner uuid item_entity={} stamped_owner_uuid={}",
                        itemEntity.getStringUUID(), stampedOwnerUuid);
            }
        }

        Entity owner = itemEntity.getOwner();
        if (owner instanceof ServerPlayer ownerPlayer) {
            return ownerPlayer;
        }

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                itemEntity.getBoundingBox().inflate(6.0D)
        );
        if (nearbyPlayers.size() == 1) {
            return nearbyPlayers.get(0);
        }

        return null;
    }

    private static boolean isInsideStampedDestroyVolume(BlockPos pos, CompoundTag tag) {
        BlockPos min = new BlockPos(tag.getInt("quest_min_x"), tag.getInt("quest_min_y"), tag.getInt("quest_min_z"));
        BlockPos max = new BlockPos(tag.getInt("quest_max_x"), tag.getInt("quest_max_y"), tag.getInt("quest_max_z"));
        return isInsideZone(pos, min, max);
    }

    private static boolean grantQuestAdvancement(ServerPlayer player, String triggerKey) {
        String path = sanitizeAdvancementPath(triggerKey);
        if (path.isBlank()) {
            LOGGER.warn("Quest advancement skipped reason=blank_trigger_key player={}", player.getStringUUID());
            return false;
        }

        ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest/" + path);
        AdvancementHolder advancement = player.server.getAdvancements().get(advancementId);
        if (advancement == null) {
            LOGGER.warn("Quest advancement missing player={} advancement_id={}", player.getStringUUID(), advancementId);
            return false;
        }

        boolean awardedAny = false;
        for (String criterion : advancement.value().criteria().keySet()) {
            awardedAny |= player.getAdvancements().award(advancement, criterion);
        }
        return awardedAny;
    }

    private static String sanitizeAdvancementPath(String triggerKey) {
        if (triggerKey == null) return "";
        return triggerKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }
}
