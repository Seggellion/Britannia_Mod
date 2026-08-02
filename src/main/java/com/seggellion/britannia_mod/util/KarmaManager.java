package com.seggellion.britannia_mod.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import com.google.gson.JsonObject;
import java.io.OutputStream;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;


public class KarmaManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void adjustUserStats(MinecraftServer server, UUID userId, int karmaChange, int fameChange,  double x, double y, double z) {
        ServerHttpExecutor.run(server, () -> sendAdjustment(server, userId, karmaChange, fameChange, x, y, z))
            .exceptionally(error -> {
                LOGGER.warn("Shard-user stats request was rejected by the bounded server HTTP queue");
                return null;
            });
    }

    private static void sendAdjustment(MinecraftServer server, UUID userId, int karmaChange, int fameChange,
                                       double x, double y, double z) {
        HttpURLConnection connection = null;
        try {
            var requestUri = ServerAuthRegistry.credentials(server).orElseThrow().apiUrls()
                    .resolvePath(Endpoint.SHARD_USER_ADJUST_STATS,
                            Map.of("user_id", userId.toString()));
            connection = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            JsonObject payload = new JsonObject();
            payload.addProperty("karma", karmaChange);
            payload.addProperty("fame", fameChange);
            payload.addProperty("shard", ServerAuthRegistry.credentials(server).orElseThrow().shardName());
            payload.addProperty("x", x);
            payload.addProperty("y", y);
            payload.addProperty("z", z);
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(connection, server, input)) throw new IllegalStateException("Server authentication unavailable");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300 && connection.getInputStream() != null) {
                BoundedHttp.readUtf8(connection.getInputStream(), 64 * 1024);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to adjust shard-user stats through the authenticated server API");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }


}
