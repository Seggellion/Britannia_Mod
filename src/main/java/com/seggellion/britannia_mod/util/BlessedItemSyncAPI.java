package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BlessedItemSyncAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** Plain-old Java record for the JSON row */
    public record BlessedRow(String itemName, String deedId, boolean used) {}

    public static List<BlessedRow> fetch(ServerPlayer player) {
        HttpURLConnection conn = null;
        try {
            var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
                    .resolveQuery(Endpoint.BLESSED_ITEMS,
                            Map.of("minecraft_uuid", player.getStringUUID()));
            conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (!RailsRequestAuthenticator.apply(conn, player.server)) throw new IllegalStateException("Server authentication unavailable");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Blessed-item sync failed for {}  HTTP {}", player.getName().getString(), code);
                return List.of();
            }

            JsonArray arr = JsonParser.parseString(BoundedHttp.readUtf8(conn.getInputStream(), 1_048_576)).getAsJsonArray();
            List<BlessedRow> rows = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                rows.add(new BlessedRow(
                        o.get("item").getAsString(),
                        o.get("deed_id").getAsString(),
                        o.get("used").getAsBoolean()));
            }
            return rows;
        } catch (Exception e) {
            LOGGER.error("Unable to fetch blessed items for {}", player.getName().getString(), e);
            return List.of();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
