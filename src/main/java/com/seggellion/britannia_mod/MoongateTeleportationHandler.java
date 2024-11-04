package com.seggellion.britannia_mod.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class MoongateTeleportationHandler {

    private static final Set<UUID> recentlyTeleportedPlayers = new HashSet<>();
    private static final List<String> CITY_NAMES = List.of(
        "Britain", "Moonglow", "Yew", "Minoc", "Trinsic", "Skara Brae", "Jhelom", "Magincia"
    );

    public static void teleportPlayer(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Check if the player has recently teleported
        if (recentlyTeleportedPlayers.contains(playerUUID)) {
            return;  // Prevent immediate re-entry into the moongate
        }

        Random random = new Random();
        int index = random.nextInt(CITY_NAMES.size());
        String cityName = CITY_NAMES.get(index);
        BlockPos destination = CityCoordinates.getCityCoordinates(cityName);

        if (destination != null) {
            ServerLevel level = player.serverLevel();

            // Force load the chunk at the destination synchronously
            level.getChunk(destination.getX() >> 4, destination.getZ() >> 4);

            // Teleport the player to the chosen city
            player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleporting to " + cityName));

            // Move the player 1 block away in the direction they are facing
            Vec3 direction = player.getLookAngle().normalize();
            Vec3 moveAwayPos = new Vec3(player.getX() + direction.x, player.getY(), player.getZ() + direction.z);
            player.teleportTo(level, moveAwayPos.x, moveAwayPos.y, moveAwayPos.z, player.getYRot(), player.getXRot());

            // Add player to the recently teleported set and start cooldown
            recentlyTeleportedPlayers.add(playerUUID);
            MoongateTickHandler.addPlayerCooldown(playerUUID, 100); // 100 ticks = 5 seconds
        } else {
            player.sendSystemMessage(Component.literal("Invalid moongate destination."));
        }
    }

        // Public method to remove a player from the recently teleported set
    public static void removeTeleportedPlayer(UUID playerUUID) {
        recentlyTeleportedPlayers.remove(playerUUID);
    }

    // Optional: Public method to check if a player is in the recently teleported set
    public static boolean hasRecentlyTeleported(UUID playerUUID) {
        return recentlyTeleportedPlayers.contains(playerUUID);
    }
}
