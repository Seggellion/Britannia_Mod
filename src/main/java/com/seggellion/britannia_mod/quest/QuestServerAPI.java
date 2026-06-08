package com.seggellion.britannia_mod.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class QuestServerAPI {
    private static final String BASE_URL = ModConfig.API_BASE_URL;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /**
     * Move your recordKill method (and any escort tracking methods) from QuestClient into this class.
     * Note: Make sure you pass the `MinecraftServer` into the method!
     */
    public static void recordKill(MinecraftServer server, String playerUuid, String mobType, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // Adjust this URL and payload to match your actual recordKill logic
                String urlString = BASE_URL + "quests/record_kill";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn, server);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", playerUuid);
                payload.addProperty("mob_type", mobType);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                // Call our server-safe handler
                handleServerResponse(conn, server, callback, urlString, payload);
            } catch (Exception e) {
                LOGGER.error("Failed to connect to Quest API for server event", e);
            }
        });
    }

    public static void sendTrigger(MinecraftServer server, String playerUuid, long questId, String triggerKey, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                String urlString = BASE_URL + "quests/" + questId + "/trigger_node";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn, server);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", playerUuid);
                payload.addProperty("trigger_key", triggerKey);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                handleServerResponse(conn, server, callback, urlString, payload);
            } catch (Exception e) {
                LOGGER.error("Failed to send trigger to Quest API player_uuid={} quest_id={} trigger_key={}",
                        playerUuid, questId, triggerKey, e);
            }
        });
    }

    /**
     * A Server-Safe response handler that does NOT use Minecraft.getInstance()
     */
    private static void handleServerResponse(
            HttpURLConnection conn,
            MinecraftServer server,
            Consumer<QuestModels.QuestResponse> callback,
            String requestUrl,
            JsonObject payload
    ) {
        try {
            int status = conn.getResponseCode();
            java.io.InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String rawResponse = "";
            if (stream != null) {
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    int cp;
                    while ((cp = reader.read()) != -1) {
                        sb.append((char) cp);
                    }
                    rawResponse = sb.toString();
                }
            }

            QuestModels.QuestResponse response = GSON.fromJson(rawResponse, QuestModels.QuestResponse.class);
            
            if (status >= 400) {
                LOGGER.warn("Server Quest API Error (HTTP {}): {}", status, response != null ? response.error : "Unknown");
            }

            // Execute the callback safely on the main Server thread
            if (server != null) {
                server.execute(() -> callback.accept(response));
            }
            
        } catch (Exception e) {
            LOGGER.error("Error reading Server Quest API response url={} payload={}", requestUrl, payload, e);
        }
    }

    private static void attachAuthToken(HttpURLConnection conn, MinecraftServer server) {
        if (server != null) {
            CityAPITokenData data = CityAPITokenData.getOrCreate(server.overworld());
            if (data.getApiToken() != null && !data.getApiToken().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + data.getApiToken());
            }
            if (data.getShardSecret() != null && !data.getShardSecret().isEmpty()) {
                conn.setRequestProperty("Shard-Secret", data.getShardSecret());
                return;
            }
        }

        String secret = CityAPITokenData.getClientShardSecret();
        if (secret != null && !secret.isEmpty()) {
            conn.setRequestProperty("Shard-Secret", secret);
        } else {
            LOGGER.warn("Quest API request has no Shard-Secret available.");
        }
    }
}
