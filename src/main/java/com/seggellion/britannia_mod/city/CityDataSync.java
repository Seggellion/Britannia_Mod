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


    /**
     * Schedules {@link #registerNpc} without making the calling thread wait for Rails.
     *
     * <h2>Why this exists</h2>
     * The legacy spawn blocks call registerNpc from {@code tick()}, and one of them calls it once
     * per townsperson inside a spawn loop. Each request is bounded, but a merchant plus a full
     * townsperson complement is five sequential bounded requests on the thread that runs the world.
     *
     * <h2>Why fire-and-forget is faithful</h2>
     * registerNpc returns void and swallows its own failures, so no caller has ever observed the
     * outcome; the entity is added to the level regardless. Every argument is an immutable value
     * captured before the hop, so the worker touches no Minecraft state.
     *
     * <p>Ordering against {@link #removeNpcAsync} is safe by construction rather than by luck: a
     * given NPC is registered when it spawns and removed when it despawns, and those are separated
     * by at least one maintenance cycle (1000 ticks) — far longer than the executor's 15-second
     * overall timeout, so the register can never still be queued when the remove is submitted.
     */
    public static void registerNpcAsync(ServerLevel serverLevel, UUID npcId, String npcType, String cityName,
                                        String name, String description, int level, int health, int mana,
                                        boolean isActive, String spawnLocation, String gender) {
        ServerHttpExecutor
                .run(serverLevel.getServer(), () -> registerNpc(serverLevel, npcId, npcType, cityName, name,
                        description, level, health, mana, isActive, spawnLocation, gender))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        // Queue saturation or the overall-timeout cut-off. Debug rather than warn:
                        // the synchronous version was silent on failure too, and the next spawn
                        // cadence re-registers on its own.
                        LOGGER.debug("NPC register did not complete npc={} type={} city={} reason={}",
                                npcId, npcType, cityName, failure.getClass().getSimpleName());
                    }
                });
    }

    /**
     * Schedules {@link #removeNpc} without making the calling thread wait for Rails.
     *
     * <p>Every caller is a {@code despawnAssociatedNpcs} loop running on the server tick, so the
     * cost was the whole tracked population times one bounded request each — and those loops fire
     * precisely when a city reads as starving, which is also what a slow or failing Rails looks
     * like. Like {@link #registerNpcAsync} this returns void, swallows its own failures and takes
     * only immutable arguments, so moving the transport off-thread changes nothing observable.
     */
    public static void removeNpcAsync(ServerLevel serverLevel, UUID npcId) {
        ServerHttpExecutor
                .run(serverLevel.getServer(), () -> removeNpc(serverLevel, npcId))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        LOGGER.debug("NPC remove did not complete npc={} reason={}",
                                npcId, failure.getClass().getSimpleName());
                    }
                });
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
    return sendUpsertPayload(serverLevel, payload, npc.getUUID().toString(), npcType, cityName, sourceId, status);
}

/**
 * The transport half of an upsert, over a payload that has already been captured from the world.
 * Split out so {@link #upsertLiveNpcAsync} can run exactly this on a worker while the payload is
 * still built on the server thread.
 */
private static boolean sendUpsertPayload(ServerLevel serverLevel, JsonObject payload, String npcId,
                                         String npcType, String cityName, String sourceId, String status) {
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
    JsonObject payload = buildInactivePayload(
            serverLevel, npcId, npcType, cityName, sourceId, spawnLocation, apiStatus, reason);
    return sendInactivePayload(serverLevel, payload, npcId, npcType, cityName, sourceId, status, apiStatus, reason);
}

/**
 * Captures an inactive-lifecycle payload. Unlike {@link #buildLiveNpcPayload} this reads no entity
 * state — the caller has usually already decided the entity is gone — but {@code getGameTime()} and
 * the credential lookup are still server-thread reads, so it stays on this side of the boundary.
 */
private static JsonObject buildInactivePayload(ServerLevel serverLevel, UUID npcId, String npcType, String cityName,
                                               String sourceId, String spawnLocation, String apiStatus,
                                               String reason) {
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
    return payload;
}

/**
 * The transport half of an inactive-lifecycle write: the status endpoint, the legacy status
 * endpoint on failure, and — for a despawn — the delete endpoint after that. Three sequential
 * bounded requests, which is why {@link #markLiveNpcInactiveAsync} exists.
 */
private static boolean sendInactivePayload(ServerLevel serverLevel, JsonObject payload, UUID npcId, String npcType,
                                           String cityName, String sourceId, String status, String apiStatus,
                                           String reason) {
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

/**
 * NPCs whose upsert is currently in flight, keyed by entity UUID. Every add is paired with a
 * remove in the {@code whenComplete} below, on both the normal and exceptional paths, so a failed
 * request never permanently suppresses a later one.
 */
private static final java.util.Set<UUID> UPSERTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

/**
 * NPCs whose inactive-lifecycle write is currently in flight, keyed by entity UUID. Same paired
 * add/remove discipline as {@link #UPSERTS_IN_FLIGHT}.
 */
private static final java.util.Set<UUID> INACTIVE_SYNCS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

/**
 * Schedules an NPC upsert without making the calling thread wait for Rails.
 *
 * <h2>Why this exists</h2>
 * {@link #upsertLiveNpc} is synchronous, and the spawners call it from {@code serverTick()} on
 * every spawn. One request is already bounded ({@link BoundedHttp}: 5s connect, 10s read), but the
 * upsert retries against the legacy endpoint on a 404, so a single call can hold the server thread
 * for two full request budgets - 30 seconds - with the world frozen behind it.
 *
 * <h2>Why fire-and-forget is faithful here</h2>
 * Every {@code serverTick()} caller already discards the boolean. The entity is added to the level
 * and recorded locally <em>before</em> the request is made, and no spawn decision, local state or
 * lifecycle transition reads the result - its only consumer is a log line. Moving the transport
 * off-thread and logging from the completion callback therefore preserves observable behaviour
 * exactly. {@code ServerEconomyService} does consume the result, which is why the synchronous
 * method remains; that caller already runs inside {@link ServerHttpExecutor}.
 *
 * <h2>The thread boundary</h2>
 * {@link #buildLiveNpcPayload} reads live, mutable Minecraft state - UUID, personal name, health,
 * gender, trader role, outfit, plus the level's game time - so the payload is captured here on the
 * calling server thread and only the transport crosses over. Nothing hops back afterwards: an
 * upsert mutates no world state.
 */
public static void upsertLiveNpcAsync(ServerLevel serverLevel, Entity npc, String npcType, String cityName,
                                      String sourceId, String spawnLocation, String status) {
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        LOGGER.warn("{} skipped: no server credentials configured", "NPC upsert sync");
        return;
    }

    UUID npcId = npc.getUUID();

    // An upsert is idempotent and re-sent on the next spawn cadence anyway, so if this NPC's
    // previous one has not come back yet, skipping is strictly better than queueing behind it.
    if (!UPSERTS_IN_FLIGHT.add(npcId)) {
        LOGGER.debug("NPC upsert skipped, previous still in flight npc={} city={}", npcId, cityName);
        return;
    }

    boolean submitted = false;
    try {
        // Captured on the server thread, before anything reaches the executor.
        JsonObject payload =
                buildLiveNpcPayload(serverLevel, npc, npcType, cityName, sourceId, spawnLocation, status);

        ServerHttpExecutor
                .submit(serverLevel.getServer(),
                        () -> sendUpsertPayload(serverLevel, payload, npcId.toString(), npcType, cityName,
                                sourceId, status))
                .whenComplete((ok, failure) -> {
                    UPSERTS_IN_FLIGHT.remove(npcId);
                    if (failure != null) {
                        // Covers queue saturation (ServerHttpExecutor rejects rather than growing a
                        // backlog) and the overall-timeout cut-off. The next spawn cadence retries on
                        // its own; there is deliberately no immediate retry here.
                        LOGGER.warn("NPC upsert did not complete npc={} type={} city={} source={} reason={}",
                                npcId, npcType, cityName, sourceId, failure.getClass().getSimpleName());
                    } else {
                        LOGGER.info("NPC upsert sync completed npc={} type={} city={} source={} success={}",
                                npcId, npcType, cityName, sourceId, Boolean.TRUE.equals(ok));
                    }
                });
        submitted = true;
    } finally {
        // Only false if payload construction threw before the future existed, in which case
        // whenComplete never runs and the guard would otherwise latch forever.
        if (!submitted) UPSERTS_IN_FLIGHT.remove(npcId);
    }
}

/**
 * Schedules an inactive-lifecycle write without making the calling thread wait for Rails.
 *
 * <h2>Why this exists</h2>
 * This is the worst of the lifecycle calls to run synchronously. {@link #markLiveNpcInactive} tries
 * the status endpoint, then the legacy status endpoint, then - for a despawn - the delete endpoint:
 * three sequential bounded requests, up to 45 seconds on one call. Its callers make it worse by
 * looping it over every tracked townsperson, and reach it not only from {@code serverTick()} but
 * from the spawner-config and resync packet handlers, where a single player interaction could
 * otherwise stall the whole server well past the vanilla client timeout.
 *
 * <h2>Why fire-and-forget is faithful here</h2>
 * As with {@link #upsertLiveNpcAsync}, every spawner caller uses the boolean solely for a log line.
 * The entity removal, the id bookkeeping and the {@code setChanged()} that follow are unconditional
 * - no caller waits for Rails to confirm before mutating Minecraft state. The write is not
 * discarded: it is still sent, still retried through the same fallback chain, and its outcome is
 * still logged, just from the completion callback.
 *
 * <h2>The thread boundary</h2>
 * The payload is pure immutable data plus {@code getGameTime()}, all captured here; the caller is
 * free to remove the entity the instant this returns.
 */
public static void markLiveNpcInactiveAsync(ServerLevel serverLevel, UUID npcId, String npcType, String cityName,
                                            String sourceId, String spawnLocation, String status, String reason) {
    if (ServerAuthRegistry.credentials(serverLevel.getServer()).isEmpty()) {
        LOGGER.warn("{} skipped: no server credentials configured", "NPC inactive sync");
        return;
    }

    // Unlike a heartbeat, this is a terminal transition rather than a periodic sample, so a
    // duplicate is wasteful rather than merely redundant - suppress while one is outstanding.
    if (!INACTIVE_SYNCS_IN_FLIGHT.add(npcId)) {
        LOGGER.debug("NPC inactive sync skipped, previous still in flight npc={} city={}", npcId, cityName);
        return;
    }

    boolean submitted = false;
    try {
        String apiStatus = inactiveStatusForApi(status);
        // Captured on the server thread, before anything reaches the executor.
        JsonObject payload = buildInactivePayload(
                serverLevel, npcId, npcType, cityName, sourceId, spawnLocation, apiStatus, reason);

        ServerHttpExecutor
                .submit(serverLevel.getServer(),
                        () -> sendInactivePayload(serverLevel, payload, npcId, npcType, cityName, sourceId,
                                status, apiStatus, reason))
                .whenComplete((ok, failure) -> {
                    INACTIVE_SYNCS_IN_FLIGHT.remove(npcId);
                    if (failure != null) {
                        LOGGER.warn("NPC inactive sync did not complete npc={} type={} city={} status={} reason={} cause={}",
                                npcId, npcType, cityName, status, reason, failure.getClass().getSimpleName());
                    } else {
                        LOGGER.info("Rails NPC despawn/delete sent npc={} type={} city={} source={} status={} reason={} success={}",
                                npcId, npcType, cityName, sourceId, status, reason, Boolean.TRUE.equals(ok));
                    }
                });
        submitted = true;
    } finally {
        if (!submitted) INACTIVE_SYNCS_IN_FLIGHT.remove(npcId);
    }
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

/**
 * Writes the spawn source, or omits it when there isn't one.
 *
 * <p>Both callers used to write {@code sourceId} unconditionally, and their callers used to
 * substitute the entity's own UUID when no real spawn source was known. A spawn source is a
 * physical, persistent thing — a spawn block that keeps its NBT id across restarts, or a Rails
 * spawn point — and a mob's UUID is neither. Rails recorded the substitute as the NPC's spawn
 * block, which is why the corrupted production rows have {@code spawn_block_id == npc_id}.
 * Omitting an unknown field lets Rails keep whatever it already knows instead of overwriting it
 * with a value that changes every time the mob is re-created.
 */
private static void addSpawnSource(JsonObject payload, String sourceId) {
    if (sourceId == null || sourceId.isBlank()) return;

    payload.addProperty("source_id", sourceId);
    payload.addProperty("spawn_block_id", sourceId);
}

/**
 * Attaches the LOGICAL identity of a Rails-authoritative NPC, when this entity is one.
 *
 * <h2>Why both identities travel</h2>
 * {@code npc_id} above is the Minecraft entity UUID: it names one incarnation of the NPC and
 * legitimately changes whenever the entity is re-materialized — after a death and re-staffing,
 * after an entity/chunk reload, or when the assignment reconciler resolves a duplicate. The World
 * NPC public id names the NPC itself and survives all of that, changing only when the NPC is
 * genuinely a different NPC.
 *
 * <p>Rails prefers this field over every entity identifier when it is present
 * ({@code Npcs::LogicalIdentityResolver}), so a retry, a reconnect, a restart and a reconciliation
 * all land on the one row Rails already owns instead of minting another. It is sent on every live
 * sync — not only economic projections — so any Service NPC that reports itself in future is
 * recognised by the same contract with no further protocol work.
 */
private static void addLogicalIdentity(JsonObject payload, Entity npc) {
    if (!(npc instanceof CitizenEntity citizen)) return;

    UUID worldNpcPublicId = citizen.getWorldNpcPublicId();
    if (worldNpcPublicId != null) {
        payload.addProperty("world_npc_public_id", worldNpcPublicId.toString());
    }
    if (npc instanceof com.seggellion.britannia_mod.entity.ServiceNpcEntity serviceNpc
            && serviceNpc.getSpawnPointId() != null) {
        payload.addProperty("spawn_point_public_id", serviceNpc.getSpawnPointId().toString());
    }
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
    addSpawnSource(payload, sourceId);
    addLogicalIdentity(payload, npc);
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
