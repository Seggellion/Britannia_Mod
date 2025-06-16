package com.seggellion.britannia_mod.util;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;


public class KarmaManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void adjustUserStats(MinecraftServer server, UUID userId, int karmaChange, int fameChange,  double x, double y, double z) {
        try {
            ServerLevel serverLevel = server.overworld();
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken();
   
            URL url = new URL(ModConfig.API_BASE_URL  + "shard_users/" + userId + "/adjust_stats");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiToken);
            connection.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("karma", karmaChange);
            payload.addProperty("fame", fameChange);
            payload.addProperty("shard_id", 1);
            payload.addProperty("x", x);
            payload.addProperty("y", y);
            payload.addProperty("z", z);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
            } else {
            }
        } catch (Exception e) {
        }
    }


}
