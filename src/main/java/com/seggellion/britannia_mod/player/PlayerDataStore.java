// com/seggellion/britannia_mod/player/PlayerDataStore.java
package com.seggellion.britannia_mod.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDataStore {
    private PlayerDataStore() {}
    private static final String KEY = "britannia_player";

    public static PlayerData get(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag dataTag = root.getCompound(KEY);
        if (dataTag.isEmpty()) {
            PlayerData fresh = new PlayerData(player.getUUID());
            save(player, fresh);
            return fresh;
        }
        return PlayerData.load(dataTag);
    }

    public static void save(ServerPlayer player, PlayerData data) {
        CompoundTag root = player.getPersistentData();
        CompoundTag out = new CompoundTag();
        data.save(out);
        root.put(KEY, out);
    }
}
