package com.seggellion.britannia_mod.client.house;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HouseRotationData {
    private static final Map<UUID, Integer> playerRotationMap = new HashMap<>();

    public static int getRotation(Player player) {
        return playerRotationMap.getOrDefault(player.getUUID(), 0);
    }

    public static void rotateClockwise(Player player) {
        int current = getRotation(player);
        int next = (current + 90) % 360;
        playerRotationMap.put(player.getUUID(), next);
    }

    public static void clear(Player player) {
        playerRotationMap.remove(player.getUUID());
    }
}
