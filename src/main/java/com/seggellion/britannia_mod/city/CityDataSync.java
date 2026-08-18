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
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;

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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Locale;


public class CityDataSync {
    private static final Logger LOGGER = LogManager.getLogger();

    public static double[] fetchFoodAndWoodSupply(ServerLevel serverLevel, String cityName) {
        try {
            var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                    .resolvePath(Endpoint.CITY_FOOD_AND_WOOD_SUPPLY, Map.of("city", cityName));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");

            attachAuth(serverLevel, connection, new byte[0]);

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
            var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                    .resolvePath(Endpoint.CITY_FOOD_SUPPLY, Map.of("city", cityName));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            attachAuth(serverLevel, connection, new byte[0]);

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

    public static void registerNpc(ServerLevel serverLevel, UUID npcId, String npcType, String cityName, String name, String description, int level, int health, int mana, boolean isActive, String spawnLocation, String gender) {
        try {
            var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                    .resolve(Endpoint.NPC_CREATE);
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

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
            payload.addProperty("shard",
                    com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.credentials(serverLevel.getServer())
                            .orElseThrow().shardName());
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            attachAuth(serverLevel, connection, bodyBytes);

            connection.getOutputStream().write(bodyBytes);
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
            }
        } catch (Exception e) {
        }
    }


    public static void removeNpc(ServerLevel serverLevel, UUID npcId) {
        try {
            var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                    .resolvePath(Endpoint.NPC_DELETE, Map.of("npc_id", npcId.toString()));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("DELETE");
            attachAuth(serverLevel, connection, new byte[0]);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
            }
        } catch (Exception e) {
        }
    }


public static JsonObject fetchCityDataWithMarketPrices(ServerLevel serverLevel, String cityName) {
    try {
        var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                .resolvePath(Endpoint.CITY_TRADE_DATA, Map.of("city", cityName));
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");

        attachAuth(serverLevel, connection, new byte[0]);

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
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        // No Rails credentials configured: every other sync path in this class already
        // degrades gracefully without a backend; NPC lifecycle sync must not crash the
        // ticking server either.
        LOGGER.warn("{} skipped: no server credentials configured", "NPC upsert sync");
        return false;
    }
    JsonObject payload = buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, status);
    String npcId = npc.getUUID().toString();

    LOGGER.info("NPC upsert request sent npc={} type={} city={} source={} status={} endpoint=npcs/upsert",
            npcId, npcType, cityName, sourceId, status);
    ApiResult upsert = sendJson(serverLevel, "POST", Endpoint.NPC_UPSERT, Map.of(), payload);
    if (upsert.isSuccess()) {
        LOGGER.info("NPC upsert success npc={} type={} city={} source={}", npcId, npcType, cityName, sourceId);
        return true;
    }

    if (upsert.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        LOGGER.info("NPC upsert fallback request sent npc={} type={} city={} source={} status={} endpoint=npcs/sync",
                npcId, npcType, cityName, sourceId, status);
        ApiResult legacy = sendJson(serverLevel, "POST", Endpoint.NPC_SYNC, Map.of(), payload);
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

/**
 * NPCs whose heartbeat request is currently in flight, keyed by entity UUID.
 *
 * <p>Every add is paired with a remove in the {@code whenComplete} below, on both the normal and
 * exceptional paths, so this never accumulates entries for despawned NPCs, and a failed request
 * never permanently suppresses later heartbeats. Its size is bounded by what the executor can
 * hold at once, not by how many NPCs exist in the world.
 */
private static final java.util.Set<UUID> HEARTBEATS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

/**
 * Schedules a heartbeat without making the calling thread wait for Rails.
 *
 * <h2>Why this exists</h2>
 * {@link #heartbeatLiveNpc} is synchronous and its callers are block-entity {@code serverTick()}
 * methods. Every request is already bounded ({@link BoundedHttp}: 5s connect, 10s read), but a
 * bounded wait is still a wait. The spawners share one 600-tick cadence, so a slow Rails turned
 * N spawners into N sequential multi-second stalls inside a single tick. Bounding the request
 * capped the damage per call; it did not stop the tick thread paying for it.
 *
 * <h2>The thread boundary</h2>
 * {@link #buildLiveNpcPayload} reads live, mutable Minecraft state — the entity's UUID, personal
 * name, health, gender, trader role, citizen outfit and appearance, plus the level's game time.
 * None of that may be touched from a worker, so the payload is built here on the calling server
 * thread and only the transport crosses over; the worker receives a finished {@link JsonObject}.
 *
 * <p>Nothing hops back to the server thread afterwards because a heartbeat mutates no world
 * state — its result is a diagnostic log and nothing more. A completion callback purely for
 * symmetry would be inventing work.
 */
public static void heartbeatLiveNpcAsync(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                         String sourceId, String spawnLocation) {
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        LOGGER.warn("{} skipped: no server credentials configured", "NPC heartbeat sync");
        return;
    }

    UUID npcId = npc.getUUID();

    // A heartbeat is periodic and replaceable: if this NPC's previous one has not come back yet,
    // skipping the interval is strictly better than queueing a second one behind it.
    if (!HEARTBEATS_IN_FLIGHT.add(npcId)) {
        LOGGER.debug("NPC heartbeat skipped, previous still in flight npc={} city={}", npcId, cityName);
        return;
    }

    boolean submitted = false;
    try {
        // Captured on the server thread, before anything reaches the executor.
        JsonObject payload =
                buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, "active");
        payload.addProperty("sync_action", "heartbeat");
        payload.addProperty("last_seen_game_time", serverLevel.getGameTime());
        Map<String, String> pathParams = Map.of("npc_id", npcId.toString());

        ServerHttpExecutor
                .submit(serverLevel.getServer(),
                        () -> sendJson(serverLevel, "POST", Endpoint.NPC_HEARTBEAT, pathParams, payload))
                .whenComplete((result, failure) -> {
                    HEARTBEATS_IN_FLIGHT.remove(npcId);
                    if (failure != null) {
                        // Covers queue saturation (ServerHttpExecutor rejects rather than growing a
                        // backlog) and the overall-timeout cut-off. Debug rather than warn: this is
                        // expected while Rails is slow, it self-corrects, and the next interval retries
                        // on its own — no immediate retry here, by design.
                        LOGGER.debug("NPC heartbeat did not complete npc={} city={} reason={}",
                                npcId, cityName, failure.getClass().getSimpleName());
                    } else if (result != null && result.isSuccess()) {
                        LOGGER.debug("NPC heartbeat success npc={} city={} source={}", npcId, cityName, sourceId);
                    } else {
                        LOGGER.debug("NPC heartbeat failure npc={} status={}",
                                npcId, result == null ? -1 : result.statusCode());
                    }
                });
        submitted = true;
    } finally {
        // Only false if payload construction threw before the future existed, in which case
        // whenComplete never runs and the guard would otherwise latch forever.
        if (!submitted) HEARTBEATS_IN_FLIGHT.remove(npcId);
    }
}

public static boolean heartbeatLiveNpc(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                       String sourceId, String spawnLocation) {
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        // No Rails credentials configured: every other sync path in this class already
        // degrades gracefully without a backend; NPC lifecycle sync must not crash the
        // ticking server either.
        LOGGER.warn("{} skipped: no server credentials configured", "NPC heartbeat sync");
        return false;
    }
    JsonObject payload = buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, "active");
    payload.addProperty("sync_action", "heartbeat");
    payload.addProperty("last_seen_game_time", serverLevel.getGameTime());

    ApiResult result = sendJson(serverLevel, "POST", Endpoint.NPC_HEARTBEAT,
            Map.of("npc_id", npc.getUUID().toString()), payload);
    if (result.isSuccess()) {
        LOGGER.debug("NPC heartbeat success npc={} city={} source={}", npc.getUUID(), cityName, sourceId);
        return true;
    }

    LOGGER.warn("NPC heartbeat failure npc={} status={} body={}", npc.getUUID(), result.statusCode(), result.body());
    return false;
}

public static boolean markLiveNpcInactive(ServerLevel serverLevel, UUID npcId, String npcType, String cityName,
                                          String sourceId, String spawnLocation, String status, String reason) {
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        // No Rails credentials configured: every other sync path in this class already
        // degrades gracefully without a backend; NPC lifecycle sync must not crash the
        // ticking server either.
        LOGGER.warn("{} skipped: no server credentials configured", "NPC inactive sync");
        return false;
    }
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
    payload.addProperty("shard", ServerAuthRegistry.credentials(serverLevel.getServer())
            .orElseThrow().shardName());
    payload.addProperty("last_seen_game_time", serverLevel.getGameTime());

    Endpoint endpoint = statusEndpoint(apiStatus);
    Map<String, String> pathParameters = Map.of("npc_id", npcId.toString());
    LOGGER.info("NPC inactive sync request sent npc={} type={} city={} source={} requestedStatus={} apiStatus={} reason={} endpoint={}",
            npcId, npcType, cityName, sourceId, status, apiStatus, reason, endpoint.symbolicName());
    ApiResult statusSync = sendJson(serverLevel, "POST", endpoint, pathParameters, payload);
    if (statusSync.isSuccess()) {
        LOGGER.info("NPC inactive sync success npc={} status={} reason={}", npcId, apiStatus, reason);
        return true;
    }

    LOGGER.info("NPC inactive fallback request sent npc={} type={} city={} source={} requestedStatus={} apiStatus={} reason={} endpoint=npcs/{}/status",
            npcId, npcType, cityName, sourceId, status, apiStatus, reason, npcId);
    ApiResult legacyStatus = sendJson(serverLevel, "POST", Endpoint.NPC_STATUS, pathParameters, payload);
    if (legacyStatus.isSuccess()) {
        LOGGER.info("NPC inactive sync success via status endpoint npc={} status={} reason={}", npcId, apiStatus, reason);
        return true;
    }

    if ("despawned".equals(apiStatus)) {
        LOGGER.info("NPC delete fallback request sent npc={} type={} city={} source={} reason={} endpoint=npcs/{}",
                npcId, npcType, cityName, sourceId, reason, npcId);
        ApiResult delete = sendJson(serverLevel, "DELETE", Endpoint.NPC_DELETE, pathParameters, null);
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

private static Endpoint statusEndpoint(String status) {
    String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
        case "dead", "death" -> Endpoint.NPC_DEATH;
        case "despawned", "despawn", "replaced", "replacement" -> Endpoint.NPC_DESPAWN;
        default -> Endpoint.NPC_INACTIVE;
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
    payload.addProperty("shard", ServerAuthRegistry.credentials(serverLevel.getServer())
            .orElseThrow().shardName());
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

private static ApiResult sendJson(ServerLevel serverLevel, String method, Endpoint endpoint,
                                  Map<String, String> pathParameters, JsonObject payload) {
    HttpURLConnection connection = null;
    try {
        var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                .resolvePath(endpoint, pathParameters);
        connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        boolean hasBody = payload != null && !"GET".equals(method) && !"DELETE".equals(method);
        byte[] bodyBytes = hasBody ? payload.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
        attachAuth(serverLevel, connection, bodyBytes);

        if (hasBody) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
            }
        }

        int responseCode = connection.getResponseCode();
        String body = BoundedHttp.readUtf8(responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream(), 1_048_576);
        return new ApiResult(responseCode, body);
    } catch (Exception e) {
        LOGGER.warn("Rails NPC sync request failed method={} endpoint={} error={}",
                method, endpoint.symbolicName(), e.toString());
        return new ApiResult(0, e.toString());
    } finally {
        if (connection != null) connection.disconnect();
    }
}

private static void attachAuth(ServerLevel serverLevel, HttpURLConnection connection, byte[] body) {
    BoundedHttp.configure(connection);
    if (!RailsRequestAuthenticator.apply(connection, serverLevel.getServer(), body)) {
        throw new IllegalStateException("Server authentication unavailable");
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
