package com.seggellion.britannia_mod.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record TeleportDestination(
        ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        Float yaw,
        Float pitch,
        String source
) {
    public static TeleportDestination inCurrentLevel(ServerPlayer player, double x, double y, double z, String source) {
        return new TeleportDestination(player.serverLevel().dimension(), x, y, z, null, null, source);
    }
}
