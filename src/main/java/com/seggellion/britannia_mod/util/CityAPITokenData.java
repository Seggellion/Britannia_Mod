package com.seggellion.britannia_mod.util;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;

public class CityAPITokenData extends SavedData {
    private static final String DATA_NAME = "city_api_token_data";

    private String apiToken = "";
    private String shardSecret = "";
    private static String clientToken = ""; // <-- client-side cache
    private static String clientShardSecret = "";
    public CityAPITokenData() {}

    // Server-side loading
    public static CityAPITokenData load(CompoundTag tag, HolderLookup.Provider provider) {
        CityAPITokenData data = new CityAPITokenData();
        if (tag.contains("ApiToken")) {
            data.apiToken = tag.getString("ApiToken");
        }
        
        if (tag.contains("ShardSecret")) {
            data.shardSecret = tag.getString("ShardSecret");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("ApiToken", this.apiToken);
        tag.putString("ShardSecret", this.shardSecret);
        return tag;
    }

    // --- SERVER-SIDE ---
    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String token) {
        this.apiToken = token;
        this.setDirty();
    }

    public String getShardSecret() {
        return shardSecret;
    }

    public void setShardSecret(String secret) {
        this.shardSecret = secret;
        this.setDirty();
    }

    public static CityAPITokenData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                CityAPITokenData::new,
                CityAPITokenData::load
            ),
            DATA_NAME
        );
    }

    // --- CLIENT-SIDE (from sync packet) ---
    public static void setClientToken(String token) {
        clientToken = token;
    }

    public static String getClientToken() {
        return clientToken;
    }

    public static boolean hasClientToken() {
        return !clientToken.isEmpty();
    }

    public static void setClientShardSecret(String secret) {
        clientShardSecret = secret;
    }

    public static String getClientShardSecret() {
        return clientShardSecret;
    }

}