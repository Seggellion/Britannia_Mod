package com.seggellion.britannia_mod.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
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

            // ==========================================
            // ESCORT LOGIC: Find escorts BEFORE the player moves
            // ==========================================
            String escortTag = "quest_escort_" + playerUUID.toString();
            AABB searchBox = player.getBoundingBox().inflate(10.0D); // 10-block radius
            
            List<LivingEntity> escorts = level.getEntitiesOfClass(LivingEntity.class, searchBox, 
                entity -> entity.getTags().contains(escortTag)
            );

            // Teleport the player to the chosen city
            player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleporting to " + cityName));

            // Move the player 1 block away in the direction they are facing
            Vec3 direction = player.getLookAngle().normalize();
            Vec3 moveAwayPos = new Vec3(player.getX() + direction.x, player.getY(), player.getZ() + direction.z);
            player.teleportTo(level, moveAwayPos.x, moveAwayPos.y, moveAwayPos.z, player.getYRot(), player.getXRot());

            // ==========================================
            // ESCORT LOGIC: Pull escorts through the portal
            // ==========================================
            for (LivingEntity escort : escorts) {
                // Give them a slight random offset so they don't perfectly overlap with the player
                double offsetX = (random.nextDouble() - 0.5) * 2.0;
                double offsetZ = (random.nextDouble() - 0.5) * 2.0;
                
                // Teleport the escort using the ServerLevel variant to ensure it works even across dimensions
                escort.teleportTo(level, moveAwayPos.x + offsetX, moveAwayPos.y, moveAwayPos.z + offsetZ, Set.of(), escort.getYRot(), escort.getXRot());
                
                // Reset their navigation so they don't try to walk back to where they came from
                if (escort instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                }
            }

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