package com.seggellion.britannia_mod.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.QuestPayloadHandler;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class QuestProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int MAX_RESPONSE_BYTES = 262_144;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private QuestProxyService() {}

    public static void handle(ServerPlayer player, QuestActionC2SPayload request) {
        ResolvedIntent intent = resolve(player, request);
        if (intent == null || !authorizedForCurrentJournal(player, request)) {
            send(player, request == null ? 0L : request.requestId(), 422,
                "{\"success\":false,\"error\":\"invalid_quest_action\"}");
            return;
        }

        MinecraftServer server = player.server;
        String playerUuid = player.getStringUUID();
        UUID connectedPlayerId = player.getUUID();
        try {
            ServerHttpExecutor.submit(server, () -> callRails(server, playerUuid, request, intent))
                .whenComplete((result, failure) -> server.execute(() -> {
                    if (server.getPlayerList().getPlayer(connectedPlayerId) != player) return;
                    if (failure != null || result == null) {
                        send(player, request.requestId(), 503,
                            "{\"success\":false,\"error\":\"quest_service_unavailable\"}");
                        return;
                    }
                    applyAuthoritativeResult(player, request, intent, result);
                    send(player, request.requestId(), result.statusCode, result.body);
                }));
        } catch (RejectedExecutionException rejected) {
            send(player, request.requestId(), 503, "{\"success\":false,\"error\":\"quest_queue_full\"}");
        }
    }

    private static Result callRails(MinecraftServer server, String playerUuid, QuestActionC2SPayload request,
                                    ResolvedIntent intent) {
        try {
            Endpoint endpoint = endpoint(request);
            var requestUri = ServerAuthRegistry.credentials(server).orElseThrow().apiUrls()
                .resolvePath(endpoint, pathParameters(request));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            JsonObject payload = new JsonObject();
            payload.addProperty("player_uuid", playerUuid);
            switch (request.action()) {
                case INTERACT -> payload.addProperty("npc_name", intent.argument());
                case TRIGGER -> payload.addProperty("trigger_key", intent.argument());
                case CHOOSE -> payload.addProperty("choice_id", intent.argument());
                default -> { }
            }
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(connection, server, bytes)) {
                throw new IllegalStateException("Server authentication unavailable");
            }

            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) return new Result(status, "{\"success\":false,\"error\":\"empty_response\"}");
            String body = BoundedHttp.readUtf8(input, MAX_RESPONSE_BYTES);
            try { JsonParser.parseString(body).getAsJsonObject(); }
            catch (RuntimeException invalid) {
                return new Result(502, "{\"success\":false,\"error\":\"invalid_service_response\"}");
            }
            return new Result(status, body);
        } catch (Exception error) {
            LOGGER.warn("Quest proxy request failed for action={}: {}", request.action(), error.toString());
            return new Result(503, "{\"success\":false,\"error\":\"quest_service_unavailable\"}");
        }
    }

    private static void applyAuthoritativeResult(ServerPlayer player, QuestActionC2SPayload request,
                                                 ResolvedIntent intent, Result result) {
        if (result.statusCode < 200 || result.statusCode >= 300) return;
        try {
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            if (!booleanValue(root, "success", true)) return;

            List<ClientQuestEntry> accepted = QuestEntryParser.parseRailsAcceptSuccess(root, intent.questGiverName());
            accepted.forEach(entry -> ServerQuestTable.addFromRailsAcceptSuccess(player, entry));

            QuestModels.QuestResponse response = GSON.fromJson(root, QuestModels.QuestResponse.class);
            QuestRewardService.apply(player, response);

            if (request.action() == QuestActionC2SPayload.Action.CHOOSE && hasClientAction(root, "spawn_escort")) {
                String questStateId = string(root, "quest_state_id");
                if (questStateId.isBlank() && !accepted.isEmpty()) questStateId = accepted.get(0).questStateId();
                if (questStateId.isBlank()) {
                    ClientQuestEntry active = ServerQuestTable.findByQuestId(player, request.questId());
                    if (active != null) questStateId = active.questStateId();
                }
                if (!questStateId.isBlank() && !ZERO_UUID.equals(intent.questGiverUuid())) {
                    QuestPayloadHandler.activateEscort(player, request.questId(), questStateId, intent.questGiverUuid());
                }
            }

            if (request.action() == QuestActionC2SPayload.Action.ABANDON
                || booleanValue(root, "completed", false)) {
                ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, request.questId());
            }
            ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));
        } catch (RuntimeException invalid) {
            LOGGER.warn("Quest proxy response could not update the authoritative server journal");
        }
    }

    private static ResolvedIntent resolve(ServerPlayer player, QuestActionC2SPayload request) {
        if (!isValidShape(request)) return null;
        if (request.action() != QuestActionC2SPayload.Action.INTERACT) {
            String displayName = "";
            if (!ZERO_UUID.equals(request.questGiverUuid())) {
                Entity entity = player.serverLevel().getEntity(request.questGiverUuid());
                if (entity instanceof QuestGiverEntity questGiver && entity.isAlive()
                    && player.distanceToSqr(entity) <= 64.0D) {
                    displayName = displayName(questGiver.getPersonalName());
                }
            }
            return new ResolvedIntent(request.argument(), displayName, request.questGiverUuid());
        }

        Entity entity = player.serverLevel().getEntity(request.questGiverEntityId());
        if (!(entity instanceof QuestGiverEntity questGiver) || !entity.isAlive()
            || player.distanceToSqr(entity) > 64.0D || !entity.getUUID().equals(request.questGiverUuid())) return null;
        String rawName = questGiver.getPersonalName();
        String apiId = internalApiId(rawName);
        if (!validArgument(apiId)) return null;
        return new ResolvedIntent(apiId, displayName(rawName), entity.getUUID());
    }

    static boolean isValidShape(QuestActionC2SPayload request) {
        if (request == null || request.requestId() <= 0 || request.action() == null
            || request.questGiverUuid() == null) return false;
        if (request.action() == QuestActionC2SPayload.Action.INTERACT) {
            return request.questId() == 0L && request.argument() != null && request.argument().isEmpty()
                && request.questGiverEntityId() > 0 && !ZERO_UUID.equals(request.questGiverUuid());
        }
        if (request.questId() <= 0 || request.questGiverEntityId() != -1) return false;
        return switch (request.action()) {
            case TRIGGER, CHOOSE -> validArgument(request.argument());
            case START, ABANDON -> request.argument() != null && request.argument().isEmpty();
            case INTERACT -> false;
        };
    }

    private static boolean authorizedForCurrentJournal(ServerPlayer player, QuestActionC2SPayload request) {
        return switch (request.action()) {
            case CHOOSE, TRIGGER, ABANDON -> ServerQuestTable.hasActiveQuestId(player, request.questId());
            default -> true;
        };
    }

    private static Endpoint endpoint(QuestActionC2SPayload request) {
        return switch (request.action()) {
            case START -> Endpoint.QUEST_START;
            case TRIGGER -> Endpoint.QUEST_TRIGGER;
            case CHOOSE -> Endpoint.QUEST_TRANSITION;
            case ABANDON -> Endpoint.QUEST_ABANDON;
            case INTERACT -> Endpoint.QUEST_INTERACT;
        };
    }

    private static Map<String, String> pathParameters(QuestActionC2SPayload request) {
        return request.action() == QuestActionC2SPayload.Action.INTERACT
            ? Map.of() : Map.of("quest_id", Long.toString(request.questId()));
    }

    private static boolean validArgument(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean hasClientAction(JsonObject root, String actionName) {
        if (!root.has("client_actions") || !root.get("client_actions").isJsonArray()) return false;
        JsonArray actions = root.getAsJsonArray("client_actions");
        if (actions.size() > 32) return false;
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            if (actionName.equals(string(action, "action")) || actionName.equals(string(action, "type"))) return true;
        }
        return false;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static String internalApiId(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        return rawName.contains(":") ? rawName.split(":", 2)[1].trim() : rawName.trim();
    }

    private static String displayName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        return rawName.contains(":") ? rawName.split(":", 2)[0].trim() : rawName.trim();
    }

    private static void send(ServerPlayer player, long requestId, int status, String body) {
        NetworkHandler.sendToPlayer(player, new QuestActionResultS2CPayload(requestId, status, body));
    }

    private record ResolvedIntent(String argument, String questGiverName, UUID questGiverUuid) {}
    private record Result(int statusCode, String body) {}
}
