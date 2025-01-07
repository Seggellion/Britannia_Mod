package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.util.KarmaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.bus.api.SubscribeEvent;


import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class KarmaReductionHandler {

     @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long currentTime = System.currentTimeMillis();

        MinecraftServer server = event.getServer(); // Get the server instance from the event

        if (server != null) {
            Iterator<Map.Entry<UUID, Long>> iterator = TreeKarmaHandler.treeCutTimestamps.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                UUID playerId = entry.getKey();
                long timeCut = entry.getValue();

                if (currentTime - timeCut > TreeKarmaHandler.REPLANT_TIMEOUT_MS) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        KarmaManager.changeKarma(player, -5); // Deduct 5 karma
                        player.sendSystemMessage(Component.literal("You failed to replant a tree. Karma reduced!"));
                    }
                    iterator.remove(); // Remove the entry after processing
                }
            }
        }
    }
}
