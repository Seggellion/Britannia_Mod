package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public final class BlessedItemSyncAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** Plain-old Java record for the JSON row */
    public record BlessedRow(String itemName, String deedId, boolean used) {}

    public static List<BlessedRow> fetch(ServerPlayer player) {
        try {
            String urlStr = ModConfig.API_BASE_URL +
                    "blessed_items?minecraft_uuid=" + player.getStringUUID();
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            // optional bearer
            CityAPITokenData data = CityAPITokenData.getOrCreate(player.serverLevel());
            if (!data.getApiToken().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + data.getApiToken());
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Blessed-item sync failed for {}  HTTP {}", player.getName().getString(), code);
                return List.of();
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonArray arr = JsonParser.parseReader(br).getAsJsonArray();
                List<BlessedRow> rows = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    rows.add(new BlessedRow(
                            o.get("item").getAsString(),
                            o.get("deed_id").getAsString(),
                            o.get("used").getAsBoolean()));
                }
                return rows;
            }
        } catch (Exception e) {
            LOGGER.error("Unable to fetch blessed items for {}", player.getName().getString(), e);
            return List.of();
        }
    }
}
