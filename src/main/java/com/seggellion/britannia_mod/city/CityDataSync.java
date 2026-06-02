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
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.trader.ITrader;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.config.ModConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Locale;


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

            String secret = data.getShardSecret();
            if (secret != null && !secret.isEmpty()) {
                connection.setRequestProperty("Shard-Secret", secret);
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

public static boolean upsertLiveNpc(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                    String sourceId, String spawnLocation, String status) {
    JsonObject payload = buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, status);
    String npcId = npc.getUUID().toString();

    LOGGER.info("NPC upsert request sent npc={} type={} city={} source={} status={} endpoint=npcs/upsert",
            npcId, npcType, cityName, sourceId, status);
    ApiResult upsert = sendJson(serverLevel, "POST", "npcs/upsert", payload);
    if (upsert.isSuccess()) {
        LOGGER.info("NPC upsert success npc={} type={} city={} source={}", npcId, npcType, cityName, sourceId);
        return true;
    }

    if (upsert.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        LOGGER.info("NPC upsert fallback request sent npc={} type={} city={} source={} status={} endpoint=npcs/sync",
                npcId, npcType, cityName, sourceId, status);
        ApiResult legacy = sendJson(serverLevel, "POST", "npcs/sync", payload);
        if (legacy.isSuccess()) {
            LOGGER.info("NPC upsert success via legacy sync npc={} type={} city={} source={}",
                    npcId, npcType, cityName, sourceId);
            return true;
        }
        LOGGER.warn("NPC upsert failure npc={} status={} body={}", npcId, legacy.statusCode(), legacy.body());
        return false;
    }

    LOGGER.warn("NPC upsert failure npc={} status={} body={}", npcId, upsert.statusCode(), upsert.body());
    return false;
}

public static boolean heartbeatLiveNpc(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                       String sourceId, String spawnLocation) {
    JsonObject payload = buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, "active");
    payload.addProperty("sync_action", "heartbeat");
    payload.addProperty("last_seen_game_time", serverLevel.getGameTime());

    ApiResult result = sendJson(serverLevel, "POST", "npcs/" + npc.getUUID() + "/heartbeat", payload);
    if (result.isSuccess()) {
        LOGGER.debug("NPC heartbeat success npc={} city={} source={}", npc.getUUID(), cityName, sourceId);
        return true;
    }

    LOGGER.warn("NPC heartbeat failure npc={} status={} body={}", npc.getUUID(), result.statusCode(), result.body());
    return false;
}

public static boolean markLiveNpcInactive(ServerLevel serverLevel, UUID npcId, String npcType, String cityName,
                                          String sourceId, String spawnLocation, String status, String reason) {
    String apiStatus = inactiveStatusForApi(status);
    JsonObject payload = new JsonObject();
    payload.addProperty("npc_id", npcId.toString());
    payload.addProperty("minecraft_uuid", npcId.toString());
    payload.addProperty("npc_type", npcType);
    payload.addProperty("city_name", cityName);
    payload.addProperty("source_id", sourceId);
    payload.addProperty("spawn_location", spawnLocation);
    payload.addProperty("status", apiStatus);
    payload.addProperty("is_active", false);
    payload.addProperty("sync_action", apiStatus);
    payload.addProperty("despawn_reason", reason);
    payload.addProperty("shard", ModConfig.SHARD_NAME);
    payload.addProperty("last_seen_game_time", serverLevel.getGameTime());

    String endpoint = "npcs/" + npcId + "/" + statusEndpoint(apiStatus);
    LOGGER.info("NPC inactive sync request sent npc={} type={} city={} source={} requestedStatus={} apiStatus={} reason={} endpoint={}",
            npcId, npcType, cityName, sourceId, status, apiStatus, reason, endpoint);
    ApiResult statusSync = sendJson(serverLevel, "POST", endpoint, payload);
    if (statusSync.isSuccess()) {
        LOGGER.info("NPC inactive sync success npc={} status={} reason={}", npcId, apiStatus, reason);
        return true;
    }

    LOGGER.info("NPC inactive fallback request sent npc={} type={} city={} source={} requestedStatus={} apiStatus={} reason={} endpoint=npcs/{}/status",
            npcId, npcType, cityName, sourceId, status, apiStatus, reason, npcId);
    ApiResult legacyStatus = sendJson(serverLevel, "POST", "npcs/" + npcId + "/status", payload);
    if (legacyStatus.isSuccess()) {
        LOGGER.info("NPC inactive sync success via status endpoint npc={} status={} reason={}", npcId, apiStatus, reason);
        return true;
    }

    if ("despawned".equals(apiStatus)) {
        LOGGER.info("NPC delete fallback request sent npc={} type={} city={} source={} reason={} endpoint=npcs/{}",
                npcId, npcType, cityName, sourceId, reason, npcId);
        ApiResult delete = sendJson(serverLevel, "DELETE", "npcs/" + npcId, null);
        if (delete.isSuccess()) {
            LOGGER.info("NPC delete fallback success npc={} reason={}", npcId, reason);
            return true;
        }
        LOGGER.warn("NPC delete fallback failure npc={} statusCode={} body={}",
                npcId, delete.statusCode(), delete.body());
    }

    LOGGER.warn("NPC inactive sync failure npc={} statusCode={} fallbackStatus={} body={}",
            npcId, statusSync.statusCode(), legacyStatus.statusCode(), statusSync.body());
    return false;
}

private static String inactiveStatusForApi(String status) {
    String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
        case "dead", "death" -> "dead";
        case "despawned", "despawn", "replaced", "replacement" -> "despawned";
        default -> "inactive";
    };
}

private static String statusEndpoint(String status) {
    String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
        case "dead", "death" -> "death";
        case "despawned", "despawn", "replaced", "replacement" -> "despawn";
        default -> "inactive";
    };
}

private static JsonObject buildLiveNpcPayload(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                              String sourceId, String spawnLocation, String status) {
    JsonObject payload = new JsonObject();
    payload.addProperty("npc_id", npc.getUUID().toString());
    payload.addProperty("minecraft_uuid", npc.getUUID().toString());
    payload.addProperty("npc_type", npcType);
    payload.addProperty("city_name", cityName);
    payload.addProperty("name", getStringByReflection(npc, "getPersonalName", npc.getName().getString()));
    payload.addProperty("description", npcType + " live in " + cityName);
    payload.addProperty("level", 1);
    payload.addProperty("health", npc instanceof LivingEntity living ? (int) living.getHealth() : 0);
    payload.addProperty("mana", 0);
    payload.addProperty("is_active", true);
    payload.addProperty("status", status);
    payload.addProperty("spawn_location", spawnLocation);
    payload.addProperty("source_id", sourceId);
    payload.addProperty("spawn_block_id", sourceId);
    payload.addProperty("gender", getStringByReflection(npc, "getGender", "unknown"));
    if (npc instanceof ITrader trader) {
        payload.addProperty("role", trader.getTraderRoleTitle());
        payload.addProperty("trader_type", trader.getTraderTypeId());
        payload.addProperty("profession", trader.getTraderTypeId());
    }
    if (npc instanceof CitizenEntity citizen) {
        String modelKey = modelKeyFor(citizen);
        payload.addProperty("model_key", modelKey);
        payload.addProperty("texture_key", citizen.getOutfitKey());
        payload.add("stats", appearanceStats(citizen, modelKey));
    }
    payload.addProperty("shard", ModConfig.SHARD_NAME);
    payload.addProperty("dimension", serverLevel.dimension().location().toString());
    payload.addProperty("x", npc.getX());
    payload.addProperty("y", npc.getY());
    payload.addProperty("z", npc.getZ());
    payload.addProperty("yaw", npc.getYRot());
    payload.addProperty("last_seen_game_time", serverLevel.getGameTime());
    payload.addProperty("sync_action", "upsert");
    return payload;
}

private static JsonObject appearanceStats(CitizenEntity citizen, String modelKey) {
    JsonObject stats = new JsonObject();
    stats.addProperty("model_key", modelKey);
    stats.addProperty("outfit_key", citizen.getOutfitKey());

    JsonObject clothing = new JsonObject();
    clothing.addProperty("hair", citizen.getClothingIndex("hair"));
    clothing.addProperty("facial_hair", citizen.getClothingIndex("facial_hair"));
    clothing.addProperty("shirt", citizen.getClothingIndex("shirt"));
    clothing.addProperty("chest", citizen.getClothingIndex("chest"));
    clothing.addProperty("pants", citizen.getClothingIndex("pants"));
    clothing.addProperty("shoes", citizen.getClothingIndex("shoes"));
    clothing.addProperty("cape", citizen.getClothingIndex("cape"));
    stats.add("clothing", clothing);

    return stats;
}

private static String modelKeyFor(CitizenEntity citizen) {
    return "male".equalsIgnoreCase(citizen.getGender()) ? "human_male" : "human_female";
}

private static String getStringByReflection(Entity npc, String method, String fallback) {
    try {
        Object value = npc.getClass().getMethod(method).invoke(npc);
        if (value instanceof String s && !s.isBlank()) return s;
    } catch (Exception ignored) {
    }
    return fallback;
}

private static ApiResult sendJson(ServerLevel serverLevel, String method, String endpoint, JsonObject payload) {
    HttpURLConnection connection = null;
    try {
        String normalized = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        URL url = new URL(ModConfig.API_BASE_URL + normalized);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        attachAuth(serverLevel, connection);

        if (payload != null && !"GET".equals(method) && !"DELETE".equals(method)) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int responseCode = connection.getResponseCode();
        String body = readResponseBody(responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        return new ApiResult(responseCode, body);
    } catch (Exception e) {
        LOGGER.warn("Rails NPC sync request failed method={} endpoint={} error={}", method, endpoint, e.toString());
        return new ApiResult(0, e.toString());
    } finally {
        if (connection != null) connection.disconnect();
    }
}

private static void attachAuth(ServerLevel serverLevel, HttpURLConnection connection) {
    CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
    String apiToken = data.getApiToken();
    if (apiToken != null && !apiToken.isBlank()) {
        connection.setRequestProperty("Authorization", "Bearer " + apiToken);
    }
    String secret = data.getShardSecret();
    if (secret != null && !secret.isBlank()) {
        connection.setRequestProperty("Shard-Secret", secret);
    }
}

private static String readResponseBody(InputStream inputStream) throws Exception {
    if (inputStream == null) return "";
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }
}

private record ApiResult(int statusCode, String body) {
    boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}

}
