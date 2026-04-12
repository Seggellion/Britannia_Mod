package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.quest.network.QuestClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries; // NEW IMPORT
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import com.seggellion.britannia_mod.network.payload.CloseScreenS2CPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

import java.util.UUID;

// The GAME bus is for in-game events like block breaking, entity ticks, and deaths
@EventBusSubscriber(modid = "britannia_mod", bus = EventBusSubscriber.Bus.GAME)
public class QuestEventHandler {

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity deceased = event.getEntity();

        // Always handle gameplay logic on the server side
        if (deceased.level().isClientSide()) return;

        // ==========================================
        // 1. ESCORT QUEST LOGIC (Did an escort die?)
        // ==========================================
        String playerUuidStr = null;
        long questId = -1;

        // Scan the dead entity's tags for our quest markers
        for (String tag : deceased.getTags()) {
            if (tag.startsWith("quest_escort_")) {
                playerUuidStr = tag.substring("quest_escort_".length());
            } else if (tag.startsWith("quest_id_")) {
                try {
                    questId = Long.parseLong(tag.substring("quest_id_".length()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // If it has BOTH tags, this was an active escort!
        if (playerUuidStr != null && questId != -1) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                ServerPlayer player = deceased.level().getServer().getPlayerList().getPlayer(playerUuid);

                if (player != null) {
                    player.sendSystemMessage(Component.literal("§cYour ward has fallen in battle! You have failed to protect them."));
                    player.connection.send(new ClientboundCustomPayloadPacket(new CloseScreenS2CPayload()));
                    QuestClient.sendTrigger(questId, "escort_died", response -> {});
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
            
            // Dispatch the API call to Rails to increment the counter
            QuestClient.recordKill(player.getUUID(), mobType, response -> {
                // Optional: If Rails returns a new count, display a message
                // e.g., if (response.success) player.sendSystemMessage(...);
            });
        }
    }
}