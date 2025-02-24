package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class BlockRestoreHandler {
    private static final long RESTORE_DELAY = 10 * 1000L; // 10 seconds in milliseconds

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, BrokenBlockData>> iterator = BrokenBlockTracker.getBrokenBlocks().entrySet().iterator();

        Map<UUID, Integer> playerRestoreCount = new HashMap<>();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BrokenBlockData> entry = iterator.next();
            BrokenBlockData data = entry.getValue();

            if (now - data.brokenTime >= RESTORE_DELAY) {
                ResourceKey<Level> dimensionKey = Level.OVERWORLD;
                ServerLevel level = server.getLevel(dimensionKey);

                if (level != null && level.isLoaded(data.pos)) {
                    level.setBlockAndUpdate(data.pos, data.originalState);
                    iterator.remove();

                    // Increment the count for the player who broke this block
                    playerRestoreCount.put(data.playerUUID, playerRestoreCount.getOrDefault(data.playerUUID, 0) + 1);
                }
            }
        }

        // Notify each player of their specific restored blocks
        if (!playerRestoreCount.isEmpty()) {
            sendRestorationMessages(server, playerRestoreCount);
        }
    }

    /**
     * Sends a message to players notifying them of their restored blocks.
     */
    private void sendRestorationMessages(MinecraftServer server, Map<UUID, Integer> playerRestoreCount) {
        for (Map.Entry<UUID, Integer> entry : playerRestoreCount.entrySet()) {
            UUID playerUUID = entry.getKey();
            int restoredCount = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                Component message = Component.literal("You hear a cave collapse, " + restoredCount + " blocks fallen");
                player.sendSystemMessage(message);
            }
        }
    }
}
