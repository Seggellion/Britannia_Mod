package com.seggellion.britannia_mod.event;

// Notice: We ONLY import QuestServerAPI. QuestClient is completely removed.
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.network.QuestServerAPI;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import com.seggellion.britannia_mod.network.payload.CloseScreenS2CPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

// The GAME bus is for in-game events like block breaking, entity ticks, and deaths
@EventBusSubscriber(modid = "britannia_mod", bus = EventBusSubscriber.Bus.GAME)
public class QuestEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity deceased = event.getEntity();

        // Always handle gameplay logic on the server side
        if (deceased.level().isClientSide()) return;

        // ==========================================
        // 1. ESCORT QUEST LOGIC (Did an escort die?)
        // ==========================================
        String playerUuidStr = null;
        String questStateId = "";
        long questId = -1;

        // Scan the dead entity's tags for our quest markers
        for (String tag : deceased.getTags()) {
            if (tag.startsWith("quest_escort_")) {
                playerUuidStr = tag.substring("quest_escort_".length());
            } else if (tag.startsWith("quest_state_id_")) {
                questStateId = tag.substring("quest_state_id_".length());
            } else if (tag.startsWith("quest_id_")) {
                try {
                    questId = Long.parseLong(tag.substring("quest_id_".length()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // If it has BOTH tags, this was an active escort!
        if (playerUuidStr != null && questId != -1 && !questStateId.isBlank()) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                ServerPlayer player = deceased.level().getServer().getPlayerList().getPlayer(playerUuid);

                if (player != null && ServerQuestTable.hasActiveQuestState(player, questStateId)) {
                    player.sendSystemMessage(Component.literal("§cYour ward has fallen in battle! You have failed to protect them."));
                    player.connection.send(new ClientboundCustomPayloadPacket(new CloseScreenS2CPayload()));
                    
                    // FIXED: Using QuestServerAPI and passing the server instance
                    final long escortQuestId = questId;
                    QuestServerAPI.sendTrigger(player.server, playerUuidStr, questId, "escort_died", response -> {
                        if (response == null || !response.success) {
                            LOGGER.warn("event=quest_server_trigger_failed trigger_key=escort_died player_uuid={} "
                                    + "quest_id={} error={}",
                                player.getStringUUID(), escortQuestId,
                                response == null ? "null_response" : response.error);
                        }
                    });
                }
            } catch (IllegalArgumentException e) {
                // The UUID tag was somehow malformed, safely ignore
            }
        }

        // ==========================================
        // 2. COMBAT QUEST LOGIC (Did a player kill a mob?)
        // ==========================================
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            
            // Extract the mob's registry name (e.g., "mongbat", "zombie")
            String mobType = BuiltInRegistries.ENTITY_TYPE.getKey(deceased.getType()).getPath();
            
            // FIXED: Passing the MinecraftServer object and converting the UUID to a String
            QuestServerAPI.recordKill(player.server, player.getUUID().toString(), mobType, response -> {
                if (response == null || !response.success) {
                    // Finding S-8: kill tracking used to fail invisibly, so a combat objective
                    // that never advanced looked like a quest-design problem.
                    LOGGER.warn("event=quest_record_kill_failed player_uuid={} mob_type={} error={}",
                        player.getStringUUID(), mobType,
                        response == null ? "null_response" : response.error);
                }
            });
        }
    }
}
