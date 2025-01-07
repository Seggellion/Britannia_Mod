package com.seggellion.britannia_mod.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;

public class KarmaManager {
    private static final String KARMA_TAG = "karma";

    public static int getKarma(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.getInt(KARMA_TAG);
    }

    public static void setKarma(ServerPlayer player, int value) {
        CompoundTag data = player.getPersistentData();
        data.putInt(KARMA_TAG, value);
    }

    public static void changeKarma(ServerPlayer player, int delta) {
        int currentKarma = getKarma(player);
        setKarma(player, currentKarma + delta);
    }
}
