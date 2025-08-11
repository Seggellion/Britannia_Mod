package com.seggellion.britannia_mod.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RegionSyncAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<RegionData> fetch(ServerPlayer player) {
        try {
            final String urlString = ModConfig.API_BASE_URL + "regions/Britannia"; // TODO: make shard dynamic
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            CityAPITokenData data = CityAPITokenData.getOrCreate(player.serverLevel());
            if (!data.getApiToken().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + data.getApiToken());
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Region fetch failed for {}. HTTP {}", player.getName().getString(), code);
                return Collections.emptyList();
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader json = new JsonReader(reader)) {

                JsonElement rootEl = JsonParser.parseReader(json);
                if (!rootEl.isJsonArray()) {
                    LOGGER.error("Region API root is not an array: {}", rootEl);
                    return Collections.emptyList();
                }

                JsonArray arr = rootEl.getAsJsonArray();
                LOGGER.info("Region API raw response: {}", arr);

                List<RegionData> regions = new ArrayList<>();

                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        LOGGER.warn("Region element is not an object: {}", el);
                        continue;
                    }
                    JsonObject root = el.getAsJsonObject();

                    // JSON:API -> { "data": { "id": ..., "type": ..., "attributes": { ... } } }
                    JsonObject dataObj = root.has("data") && root.get("data").isJsonObject()
                            ? root.getAsJsonObject("data")
                            : null;
                    if (dataObj == null) {
                        LOGGER.warn("Missing 'data' object in element: {}", root);
                        continue;
                    }

                    JsonObject attrs = dataObj.has("attributes") && dataObj.get("attributes").isJsonObject()
                            ? dataObj.getAsJsonObject("attributes")
                            : null;
                    if (attrs == null) {
                        LOGGER.warn("Missing 'attributes' in 'data': {}", dataObj);
                        continue;
                    }

                    String name = getString(attrs, "name", "unknown");
                    int minX = getInt(attrs, "min_x", 0);
                    int maxX = getInt(attrs, "max_x", 0);
                    int minY = getInt(attrs, "min_y", 0);
                    int maxY = getInt(attrs, "max_y", 0);
                    int minZ = getInt(attrs, "min_z", 0);
                    int maxZ = getInt(attrs, "max_z", 0);

                    List<RegionItemData> items = new ArrayList<>();
                    if (attrs.has("items") && attrs.get("items").isJsonArray()) {
                        JsonArray itemsArr = attrs.getAsJsonArray("items");
                        for (JsonElement ie : itemsArr) {
                            if (!ie.isJsonObject()) continue;
                            JsonObject io = ie.getAsJsonObject();
                            items.add(new RegionItemData(
                                    getString(io, "type", "unknown"),
                                    getString(io, "key", ""),
                                    getInt(io, "weight", 1)
                            ));
                        }
                    }

                    regions.add(new RegionData(name, minX, maxX, minY, maxY, minZ, maxZ, items));
                }

                LOGGER.info("Parsed {} regions from API", regions.size());
                return regions;
            }

        } catch (Exception e) {
            LOGGER.error("Failed to fetch regions for {}", player.getName().getString(), e);
            return Collections.emptyList();
        }
    }

    private static String getString(JsonObject o, String key, String def) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject o, String key, int def) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : def;
    }
}
