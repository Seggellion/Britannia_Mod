package com.seggellion.britannia_mod.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.config.ModConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.Map;
import java.util.Scanner;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;


public class CityDataSync {
    private static final Logger LOGGER = LogManager.getLogger();

    public static double[] fetchFoodAndWoodSupply(ServerLevel serverLevel, String cityName) {
        try {
            URL url = new URL(ModConfig.API_BASE_URL + "cities/" + cityName + "/food_and_wood_supply"); // New endpoint
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");

            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken();

            if (!apiToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiToken);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    JsonObject json = new com.google.gson.JsonParser().parse(response).getAsJsonObject();

                    double foodSupply = json.has("food_supply") ? json.get("food_supply").getAsDouble() : 0.0;
                    double woodSupply = json.has("wood_supply") ? json.get("wood_supply").getAsDouble() : 0.0;

                    return new double[]{foodSupply, woodSupply};
                }
            }
        } catch (Exception e) {
        }
        return new double[]{0.0, 0.0}; 
    }

    public static double fetchFoodSupply(ServerLevel serverLevel, String cityName) {
        try {
            URL url = new URL(ModConfig.API_BASE_URL + "/cities/" + cityName + "/food_supply");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken(); // Might be empty if not set
                // Include the token in a header, for example, "Authorization: Bearer <token>"
                if (!apiToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + apiToken);
                }
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    JsonObject json = new com.google.gson.JsonParser().parse(response).getAsJsonObject();
                    return json.get("food_supply").getAsDouble();
                }
            }
        } catch (Exception e) {
        }
        return 0.0;
    }

     public static JsonArray parseStarvingCitiesResponse(InputStream inputStream) {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
            if (response.has("starving_cities")) {
                return response.getAsJsonArray("starving_cities");
            } else {
            }
        } catch (Exception e) {
        }
        return new JsonArray();
    }

    public static List<UUID> getAssociatedMerchants(String cityName) {
            try {
                URL url = new URL(ModConfig.API_BASE_URL + "/cities/" + cityName + "/merchants");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JsonObject response = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                    if (response.has("merchants")) {
                        JsonArray merchantsArray = response.getAsJsonArray("merchants");
                        List<UUID> merchants = new ArrayList<>();
                        for (JsonElement element : merchantsArray) {
                            try {
                                merchants.add(UUID.fromString(element.getAsString()));
                            } catch (IllegalArgumentException e) {
                            }
                        }
                        return merchants;
                    }
                } else {
                }
                conn.disconnect();
            } catch (Exception e) {
            }
            return new ArrayList<>();
        }

    public static void registerNpc(ServerLevel serverLevel, UUID npcId, String npcType, String cityName, String name, String description, int level, int health, int mana, boolean isActive, String spawnLocation, String gender) {
        try {
            URL url = new URL(ModConfig.API_BASE_URL + "/npcs");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken(); // Might be empty if not set
            // Include the token in a header, for example, "Authorization: Bearer <token>"
            if (!apiToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiToken);
            }
            String secret = CityAPITokenData.getClientShardSecret();
            if (secret != null && !secret.isEmpty()) {
                connection.setRequestProperty("Shard-Secret", secret);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("npc_id", npcId.toString());
            payload.addProperty("npc_type", npcType);
            payload.addProperty("city_name", cityName);
            payload.addProperty("name", name);
            payload.addProperty("description", description);
            payload.addProperty("level", level);
            payload.addProperty("health", health);
            payload.addProperty("mana", mana);
            payload.addProperty("is_active", isActive);
            payload.addProperty("spawn_location", spawnLocation);
            payload.addProperty("gender", gender);
            payload.addProperty("shard", data.getShardSecret());

            connection.getOutputStream().write(payload.toString().getBytes());
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
            }
        } catch (Exception e) {
        }
    }


    public static void removeNpc(ServerLevel serverLevel, UUID npcId) {
        try {
            URL url = new URL(ModConfig.API_BASE_URL + "/npcs/" + npcId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken(); // Might be empty if not set
                // Include the token in a header, for example, "Authorization: Bearer <token>"
                if (!apiToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + apiToken);
                }
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
            }
        } catch (Exception e) {
        }
    }


    private static boolean isCityStarving(String cityName) {
        try {
            URL url = new URL(ModConfig.API_BASE_URL + "/" + cityName + "/starvation_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                JsonObject response = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                boolean starving = response.get("starving").getAsBoolean();
                conn.disconnect();
                return starving;
            } else {
            }
            conn.disconnect();
        } catch (Exception e) {
        }
        return false;
    }

    // Example stub for listing all city names
    private static List<String> fetchAllCityNames() {
        // Return a static list or call another API endpoint
        return Arrays.asList("Britain", "Trinsic");
    }

public static JsonObject fetchCityDataWithMarketPrices(ServerLevel serverLevel, String cityName) {
    try {
        URL url = new URL(ModConfig.API_BASE_URL + "cities/" + cityName + "/trade_data"); // New endpoint
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");

        CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
        String apiToken = data.getApiToken();
        if (!apiToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiToken);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                String response = scanner.useDelimiter("\\A").next();
                return JsonParser.parseString(response).getAsJsonObject();
            }
        } else {
        }
    } catch (Exception e) {
    }

    return new JsonObject(); // Return empty JSON if an error occurs
}

}
