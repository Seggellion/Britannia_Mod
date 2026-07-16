package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CityCommodityApi {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CityCommodityApi() {
    }

    public static List<CityCommodity> fetchServer(ServerLevel level, String city) {
        return fetch(level, city);
    }

    private static List<CityCommodity> fetch(ServerLevel level, String city) {
        if (city == null || city.isBlank()) return List.of();
        try {
            URLResult result = get(level, Map.of("city", city));
            if (result.statusCode >= 200 && result.statusCode < 300) {
                return parseCommodities(result.body);
            }
            LOGGER.warn("City commodity fetch rejected endpoint={} status={} body={}",
                    Endpoint.CITY_COMMODITIES.symbolicName(), result.statusCode, result.body);
        } catch (Exception e) {
            LOGGER.warn("City commodity fetch failed endpoint={} error={}",
                    Endpoint.CITY_COMMODITIES.symbolicName(), e.toString());
        }

        return List.of();
    }

    private static URLResult get(ServerLevel level, Map<String, String> query) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow().apiUrls()
                .resolveQuery(Endpoint.CITY_COMMODITIES, query);
        HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);
        if (!RailsRequestAuthenticator.apply(conn, level.getServer())) {
            throw new IllegalStateException("Server authentication unavailable");
        }
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

    private record URLResult(int statusCode, String body) {}
}
