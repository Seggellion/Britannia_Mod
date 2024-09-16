package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class PlayerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        handlePlayerPosition(event.getEntity());
    }

    private void handlePlayerPosition(Player player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            double x = serverPlayer.getX(), z = serverPlayer.getZ();
            if (x <= 0) teleportPlayer(serverPlayer, 14999, serverPlayer.getY(), z);
            else if (x >= 15000) teleportPlayer(serverPlayer, 1, serverPlayer.getY(), z);
            if (z <= 0) teleportPlayer(serverPlayer, x, serverPlayer.getY(), 9998);
            else if (z >= 9999) teleportPlayer(serverPlayer, x, serverPlayer.getY(), 1);
        }
    }

    private void teleportPlayer(ServerPlayer player, double x, double y, double z) {
        LOGGER.info("Teleporting player {} to coordinates: {}, {}, {}", player.getName().getString(), x, y, z);
        player.teleportTo(x, y, z);
    }
}
