package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CityCommodityApi {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CityCommodityApi() {
    }

    public static List<CityCommodity> fetchServer(ServerLevel level, String city) {
        return fetch(city, conn -> attachServerAuth(level, conn));
    }

    public static List<CityCommodity> fetchClient(String city) {
        return fetch(city, CityCommodityApi::attachClientAuth);
    }

    private static List<CityCommodity> fetch(String city, AuthAttacher authAttacher) {
        if (city == null || city.isBlank()) return List.of();
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String[] endpoints = {
                "city_commodities?city=" + encodedCity,
                "commodities?city=" + encodedCity,
                "market/commodities?city=" + encodedCity
        };

        for (String endpoint : endpoints) {
            try {
                URLResult result = get(endpoint, authAttacher);
                if (result.statusCode >= 200 && result.statusCode < 300) {
                    return parseCommodities(result.body);
                }
                if (result.statusCode != HttpURLConnection.HTTP_NOT_FOUND) {
                    LOGGER.warn("City commodity fetch rejected endpoint={} status={} body={}",
                            endpoint, result.statusCode, result.body);
                }
            } catch (Exception e) {
                LOGGER.warn("City commodity fetch failed endpoint={} error={}", endpoint, e.toString());
            }
        }

        return List.of();
    }

    private static URLResult get(String endpoint, AuthAttacher authAttacher) throws Exception {
        java.net.URL url = new java.net.URL(ModConfig.API_BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        authAttacher.attach(conn);
        int status = conn.getResponseCode();
        String body;
        try (InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()) {
            body = readBody(stream);
        }
        return new URLResult(status, body);
    }

    public static List<CityCommodity> parseCommodities(String body) {
        if (body == null || body.isBlank()) return List.of();
        JsonElement root = JsonParser.parseString(body);
        JsonArray array = asCommodityArray(root);
        List<CityCommodity> commodities = new ArrayList<>();
        if (array == null) return commodities;
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                commodities.add(CityCommodity.fromJson(element.getAsJsonObject()));
            }
        }
        return commodities;
    }

    @Nullable
    private static JsonArray asCommodityArray(JsonElement root) {
        if (root == null || root.isJsonNull()) return null;
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (!root.isJsonObject()) return null;

        JsonObject obj = root.getAsJsonObject();
        for (String key : List.of("commodities", "city_commodities", "data", "market")) {
            JsonElement child = obj.get(key);
            if (child != null && child.isJsonArray()) return child.getAsJsonArray();
        }
        return null;
    }

    private static String readBody(@Nullable InputStream stream) throws Exception {
        if (stream == null) return "";
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                out.append(buffer, 0, read);
            }
            return out.toString();
        }
    }

    private static void attachServerAuth(ServerLevel level, HttpURLConnection conn) {
        CityAPITokenData data = CityAPITokenData.getOrCreate(level);
        if (data.getApiToken() != null && !data.getApiToken().isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + data.getApiToken());
        }
        if (data.getShardSecret() != null && !data.getShardSecret().isBlank()) {
            conn.setRequestProperty("Shard-Secret", data.getShardSecret());
        }
    }

    private static void attachClientAuth(HttpURLConnection conn) {
        String token = CityAPITokenData.getClientToken();
        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        String secret = CityAPITokenData.getClientShardSecret();
        if (secret != null && !secret.isBlank()) {
            conn.setRequestProperty("Shard-Secret", secret);
        }
    }

    private interface AuthAttacher {
        void attach(HttpURLConnection conn);
    }

    private record URLResult(int statusCode, String body) {}
}
