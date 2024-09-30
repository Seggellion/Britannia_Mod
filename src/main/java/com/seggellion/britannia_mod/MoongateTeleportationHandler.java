package com.seggellion.britannia_mod.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel; // Import ServerLevel
import net.minecraft.network.chat.Component;  // Import Component

import java.util.*;

public class MoongateTeleportationHandler {

    private static final List<String> CITY_NAMES = List.of(
        "Britain", "Moonglow", "Yew", "Minoc", "Trinsic", "Skara Brae", "Jhelom", "Magincia"
    );

    // Cooldown map to track last teleport time (UUID -> last teleport timestamp)
    private static final Map<UUID, Long> teleportCooldowns = new HashMap<>();

    // Cooldown time in milliseconds (5 seconds)
    private static final long TELEPORT_COOLDOWN = 5000;

    public static void teleportPlayer(ServerPlayer player) {
        long currentTime = System.currentTimeMillis();
        UUID playerUUID = player.getUUID();

        // Check if player is on cooldown
        if (teleportCooldowns.containsKey(playerUUID)) {
            long lastTeleportTime = teleportCooldowns.get(playerUUID);
            if (currentTime - lastTeleportTime < TELEPORT_COOLDOWN) {
                player.sendSystemMessage(Component.literal("Moongate is recharging. Please wait."));
                return;
            }
        }

        Random random = new Random();
        int index = random.nextInt(CITY_NAMES.size());
        String cityName = CITY_NAMES.get(index);
        BlockPos destination = CityCoordinates.getCityCoordinates(cityName);

        if (destination != null) {
            ServerLevel level = player.serverLevel();
            player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleporting to " + cityName));

            // Update last teleport time
            teleportCooldowns.put(playerUUID, currentTime);
        } else {
            player.sendSystemMessage(Component.literal("Invalid moongate destination."));
        }
    }
}
