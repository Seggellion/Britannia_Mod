package com.seggellion.britannia_mod.block;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.IEventBus;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class MoongateTickHandler {
    private static final Map<UUID, Integer> teleportCooldowns = new HashMap<>();

    // Register the tick event handler using NeoForge's event bus
    public static void registerTickEvent(IEventBus eventBus) {
        eventBus.addListener(MoongateTickHandler::onServerTick);
    }

    // Handle server tick events
    public static void onServerTick(ServerTickEvent.Post event) {
        // Iterate over cooldowns and decrement them
        Iterator<Map.Entry<UUID, Integer>> iterator = teleportCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                // Remove the player from the recently teleported set using the public method
                MoongateTeleportationHandler.removeTeleportedPlayer(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }

    // Add player to the cooldown list with a specified number of ticks (e.g., 100 ticks = 5 seconds)
    public static void addPlayerCooldown(UUID playerUUID, int ticks) {
        teleportCooldowns.put(playerUUID, ticks);
    }
}
