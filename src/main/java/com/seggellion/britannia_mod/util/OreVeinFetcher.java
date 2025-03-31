package com.seggellion.britannia_mod.util;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.seggellion.britannia_mod.config.ModConfig;
import java.util.ArrayList;
import java.util.List;

public class OreVeinFetcher {
    private static final Logger LOGGER = LogManager.getLogger();

    public static class OreVein {
        public final String oreType;
        public final BlockPos position;
        public final int radius;
        public final String rotation;
        public final String region;

        public OreVein(String oreType, BlockPos position, int radius, String rotation, String region) {
            this.oreType = oreType;
            this.position = position;
            this.radius = radius;
            this.rotation = rotation;
            this.region = region;
        }

        public BlockPos getPosition() {
            return position;
        }
    }

    public static List<OreVein> fetchOreVeins(MinecraftServer server, String shardName) {
        List<OreVein> veins = new ArrayList<>();
        try {
            ServerLevel serverLevel = server.overworld();
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken();

            String endpoint = ModConfig.API_BASE_URL + "ore_veins?shard=" + shardName;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to fetch ore veins. HTTP error code: {}", responseCode);
                return veins;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    JsonObject obj = element.getAsJsonObject();
                    String oreType = obj.get("ore_type").getAsString();
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int z = obj.get("z").getAsInt();
                    int radius = obj.get("radius").getAsInt();                  
                    String rotation = obj.get("rotation").getAsString();
                    String region = obj.get("region").getAsString();

                    veins.add(new OreVein(oreType, new BlockPos(x, y, z), radius, rotation, region));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception while fetching ore veins: ", e);
        }

        return veins;
    }
}
