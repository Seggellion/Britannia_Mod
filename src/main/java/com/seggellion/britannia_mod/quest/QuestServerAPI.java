package com.seggellion.britannia_mod.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class QuestServerAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int MAX_RESPONSE_BYTES = 262_144;

    private QuestServerAPI() {}

    public static void recordKill(MinecraftServer server, String playerUuid, String mobType,
                                  Consumer<QuestModels.QuestResponse> callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", playerUuid);
        payload.addProperty("mob_type", mobType);
        submit(server, () -> postQuest(server, Endpoint.QUEST_RECORD_KILL, Map.of(), payload),
            callback, questFailure());
    }

    public static void sendTrigger(MinecraftServer server, String playerUuid, long questId, String triggerKey,
                                   Consumer<QuestModels.QuestResponse> callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", playerUuid);
        payload.addProperty("trigger_key", triggerKey);
        submit(server, () -> postQuest(server, Endpoint.QUEST_TRIGGER,
                Map.of("quest_id", Long.toString(questId)), payload),
            callback, questFailure());
    }

    public static void quitQuest(MinecraftServer server, String playerUuid, String questStateId,
                                 Consumer<QuestQuitResult> callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", playerUuid);
        submit(server, () -> postQuit(server, Endpoint.QUEST_QUIT,
                Map.of("quest_state_id", questStateId), payload), callback,
            new QuestQuitResult(false, "Quest quit request failed."));
    }

    private static QuestModels.QuestResponse postQuest(MinecraftServer server, Endpoint endpoint,
                                                       Map<String, String> pathParameters, JsonObject payload) {
        try {
            HttpResult result = post(server, endpoint, pathParameters, payload);
            QuestModels.QuestResponse response = result.body().isBlank()
                ? null : GSON.fromJson(result.body(), QuestModels.QuestResponse.class);
            if (response == null) response = questFailure();
            if (result.statusCode() < 200 || result.statusCode() >= 300) response.success = false;
            return response;
        } catch (Exception error) {
            LOGGER.warn("Server quest request failed for endpoint={}: {}", endpoint.symbolicName(), error.toString());
            return questFailure();
        }
    }

    private static QuestQuitResult postQuit(MinecraftServer server, Endpoint endpoint,
                                            Map<String, String> pathParameters, JsonObject payload) {
        try {
            HttpResult result = post(server, endpoint, pathParameters, payload);
            JsonObject root = result.body().isBlank() ? new JsonObject() : GSON.fromJson(result.body(), JsonObject.class);
            boolean success = result.statusCode() >= 200 && result.statusCode() < 300
                && root != null && root.has("success") && root.get("success").getAsBoolean();
            String message = string(root, "message");
            if (message.isBlank()) message = string(root, "error");
            return new QuestQuitResult(success, message);
        } catch (Exception error) {
            LOGGER.warn("Server quest quit request failed: {}", error.toString());
            return new QuestQuitResult(false, "Quest quit request failed.");
        }
    }

    private static HttpResult post(MinecraftServer server, Endpoint endpoint,
                                   Map<String, String> pathParameters, JsonObject payload) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(server).orElseThrow().apiUrls()
            .resolvePath(endpoint, pathParameters);
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (!RailsRequestAuthenticator.apply(connection, server, body)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        try (OutputStream output = connection.getOutputStream()) { output.write(body); }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
            ? connection.getInputStream() : connection.getErrorStream();
        return new HttpResult(status, BoundedHttp.readUtf8(input, MAX_RESPONSE_BYTES));
    }

    private static <T> void submit(MinecraftServer server, Supplier<T> request, Consumer<T> callback, T failureValue) {
        if (server == null) {
            callback.accept(failureValue);
            return;
        }
        try {
            ServerHttpExecutor.submit(server, request).whenComplete((result, failure) ->
                server.execute(() -> callback.accept(failure == null && result != null ? result : failureValue)));
        } catch (RejectedExecutionException rejected) {
            server.execute(() -> callback.accept(failureValue));
        }
    }

    private static QuestModels.QuestResponse questFailure() {
        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = false;
        response.error = "Quest service unavailable.";
        return response;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private record HttpResult(int statusCode, String body) {}

    public record QuestQuitResult(boolean success, String message) {
        public String messageOr(String fallback) {
            return message == null || message.isBlank() ? fallback : message;
        }
    }
}
