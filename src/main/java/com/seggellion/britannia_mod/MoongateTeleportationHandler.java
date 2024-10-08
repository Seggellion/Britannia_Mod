package com.seggellion.britannia_mod.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public class MoongateTeleportationHandler {

    private static final List<String> CITY_NAMES = List.of(
        "Britain", "Moonglow", "Yew", "Minoc", "Trinsic", "Skara Brae", "Jhelom", "Magincia"
    );

    public static void teleportPlayer(ServerPlayer player) {
        Random random = new Random();
        int index = random.nextInt(CITY_NAMES.size());
        String cityName = CITY_NAMES.get(index);
        BlockPos destination = CityCoordinates.getCityCoordinates(cityName);

        if (destination != null) {
            ServerLevel level = player.serverLevel();

            // Teleport the player to the chosen city
            player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleporting to " + cityName));

            // Move the player 1 block away in the direction they are facing to avoid re-entering the moongate
            Vec3 direction = player.getLookAngle().normalize();  // Get the player's look direction
            Vec3 moveAwayPos = new Vec3(player.getX() + direction.x, player.getY(), player.getZ() + direction.z);  // Move 1 block away

            // Teleport the player 1 block away within the same level
            player.teleportTo(level, moveAwayPos.x, moveAwayPos.y, moveAwayPos.z, player.getYRot(), player.getXRot());
        } else {
            player.sendSystemMessage(Component.literal("Invalid moongate destination."));
        }
    }
}
