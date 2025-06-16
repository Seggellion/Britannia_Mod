package com.seggellion.britannia_mod.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;


public class CityAPITokenData extends SavedData {
    private static final String DATA_NAME = "city_api_token_data";

    private String apiToken = "";

    public CityAPITokenData() {}

    // Loader for existing data
    public static CityAPITokenData load(CompoundTag tag, HolderLookup.Provider provider) {
        CityAPITokenData data = new CityAPITokenData();
        if (tag.contains("ApiToken")) {
            data.apiToken = tag.getString("ApiToken");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("ApiToken", this.apiToken);
        return tag;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String token) {
        this.apiToken = token;
        this.setDirty(); 
    }

    public static CityAPITokenData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<CityAPITokenData>(
                CityAPITokenData::new, 
                (tag, provider) -> CityAPITokenData.load(tag, provider)
            ),
            DATA_NAME
        );
    }
}