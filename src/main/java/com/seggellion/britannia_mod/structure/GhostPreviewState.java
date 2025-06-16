package com.seggellion.britannia_mod.client.house;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GhostPreviewState {
    private static final Map<UUID, BlockPos> anchorMap = new HashMap<>();

    public static void setAnchor(Player player, BlockPos pos) {
        anchorMap.put(player.getUUID(), pos);
    }

    public static BlockPos getAnchor(Player player) {
        return anchorMap.getOrDefault(player.getUUID(), player.blockPosition());
    }

    public static void clear(Player player) {
        anchorMap.remove(player.getUUID());
    }

    public static boolean hasAnchor(Player player) {
        return anchorMap.containsKey(player.getUUID());
    }
}
