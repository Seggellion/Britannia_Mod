package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.network.ClientboundSyncCityTokenPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestEntryParser;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WorldBootstrapAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static WorldBootstrapData fetch(ServerPlayer player) {
        String shard = ModConfig.SHARD_NAME != null && !ModConfig.SHARD_NAME.isBlank()
                ? ModConfig.SHARD_NAME
                : "Britannia";
        try {
            // 1. Resolve Shard Name first

            // 2. Resolve the authenticated Minecraft profile. Rails owns all identity linking.
            GameProfile profile = player.getGameProfile();
            UUID minecraftUuid = profile.getId();
            String playerName = profile.getName();
            if (minecraftUuid == null || playerName == null || playerName.isBlank()) {
                LOGGER.warn("Skipping world bootstrap for player with missing Minecraft profile data.");
                return WorldBootstrapData.cachedFallback(shard, -1, "Missing Minecraft profile data.");
            }

            String playerUuid = minecraftUuid.toString();

            // 3. Encode values
            String encodedShard = URLEncoder.encode(shard, StandardCharsets.UTF_8);
            String encodedUuid  = URLEncoder.encode(playerUuid, StandardCharsets.UTF_8);
            String encodedName  = URLEncoder.encode(playerName, StandardCharsets.UTF_8);

            // 4. Construct URL
            String base = normalizedApiBase(ModConfig.API_BASE_URL);
            final String urlString = base + "/world_bootstrap/" + encodedShard
                    + "?player_uuid=" + encodedUuid
                    + "&minecraft_uuid=" + encodedUuid
                    + "&minecraft_username=" + encodedName;
            LOGGER.info("World bootstrap request URL: {}", urlString);

            // 5. Open Connection
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            CityAPITokenData tok = CityAPITokenData.getOrCreate(player.serverLevel());
            if (!tok.getApiToken().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + tok.getApiToken());
                ClientboundSyncCityTokenPayload.send(player, tok.getApiToken(), tok.getShardSecret());
            }

            int code = conn.getResponseCode();
            LOGGER.info("World bootstrap HTTP status for shard {}: {}", shard, code);
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                String status = "HTTP 304 Not Modified; retaining existing cached bootstrap regions.";
                LOGGER.info("World bootstrap not modified for shard {}; using cached data.", shard);
                return WorldBootstrapData.cachedFallback(shard, code, status);
            }

            if (code != HttpURLConnection.HTTP_OK) {
                String body = readResponseBody(conn, true);
                String status = "HTTP " + code + " bootstrap failure; retained existing cached data.";
                LOGGER.warn("World bootstrap failed for shard {}. HTTP {} Body: {}", shard, code, body);
                return WorldBootstrapData.cachedFallback(shard, code, status);
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader json = new JsonReader(reader)) {

                JsonElement parsedRoot = JsonParser.parseReader(json);
                if (parsedRoot == null || !parsedRoot.isJsonObject()) {
                    LOGGER.warn("World bootstrap response for shard {} was not a JSON object.", shard);
                    return WorldBootstrapData.cachedFallback(shard, code, "Malformed bootstrap root; retained existing cached data.");
                }
                JsonObject root = parsedRoot.getAsJsonObject();
                LOGGER.info("World bootstrap top-level JSON keys for shard {}: {}", shard, root.keySet());

                Map<ResourceLocation, FishCatalog.FishMeta> fishMap = parseFish(root);
                List<RegionData> regions = parseRegions(root, shard);
                if (!regions.isEmpty()) {
                    RegionData first = regions.get(0);
                    LOGGER.info("First region: {} climate={} bounds=({}, {}, {}) -> ({}, {}, {})",
                            first.name, first.climate,
                            first.minX, first.minY, first.minZ,
                            first.maxX, first.maxY, first.maxZ);
                }

                List<CityBootstrapData> citiesData = parseCities(root);
                ShardUserData shardUser = parseShardUser(root, player);
                int grapeCount = parseGrapes(root);
                List<ClientQuestEntry> acceptedQuests = parseAcceptedQuestsSafely(root);

                LOGGER.info("World bootstrap parsed fish={}, regions={}, cities={}, grape varieties={}, acceptedQuests={}",
                        fishMap.size(), regions.size(), citiesData.size(), grapeCount, acceptedQuests.size());

                // Return
                return new WorldBootstrapData(fishMap, regions, shardUser, citiesData, acceptedQuests, false, shard, code,
                        "Bootstrap success: fish=" + fishMap.size()
                                + ", regions=" + regions.size()
                                + ", cities=" + citiesData.size()
                                + ", grapes=" + grapeCount
                                + ", quests=" + acceptedQuests.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed world bootstrap", e);
            return WorldBootstrapData.cachedFallback(shard, -1, "Exception during bootstrap; retained existing cached data.");
        }
    }

    // --- Data Records & Helpers ---

    public record WorldBootstrapData(
            Map<ResourceLocation, FishCatalog.FishMeta> fish,
            List<RegionData> regions, 
            ShardUserData shardUser,
            List<CityBootstrapData> cities,
            List<ClientQuestEntry> acceptedQuests,
            boolean cacheFallback,
            String shard,
            int httpStatus,
            String status
    ) {
        public static WorldBootstrapData empty() {
            return new WorldBootstrapData(Map.of(), List.of(), null, List.of(), List.of(), false, "<none>", -1, "Empty bootstrap data.");
        }

        public static WorldBootstrapData cachedFallback(String shard, int httpStatus, String status) {
            return new WorldBootstrapData(
                    FishCatalog.snapshot(),
                    RegionCache.all(),
                    null,
                    List.of(),
                    List.of(),
                    true,
                    shard,
                    httpStatus,
                    status
            );
        }
    }

    public record CityBootstrapData(
            String name,
            double food, double wood, double metal, double stone, double textile, double alcohol, double tech,
            int gold, int silver, int copper,
            Map<String, Map<String, Map<String, Double>>> weights,
            Map<String, Map<String, Map<String, Integer>>> quantities
    ) {}

    public record ShardUserData(
            String gender,
            int fame,
            int karma,
            int murderCount,
            JsonObject inventory,
            JsonObject stats
    ) {}

    private static Map<ResourceLocation, FishCatalog.FishMeta> parseFish(JsonObject root) {
        Map<ResourceLocation, FishCatalog.FishMeta> fishMap = new HashMap<>();
        if (!hasArray(root, "fish")) {
            return fishMap;
        }

        for (JsonElement element : root.getAsJsonArray("fish")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed fish bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject fish = element.getAsJsonObject();
                String keyString = getStringOrDefault(fish, "item_key", "");
                if (keyString.isBlank()) {
                    LOGGER.warn("Skipping malformed fish bootstrap entry: missing item_key.");
                    continue;
                }
                ResourceLocation key = ResourceLocation.parse(keyString);
                String name = getStringOrDefault(fish, "name", key.getPath());
                double minWeight = getDoubleOrDefault(fish, "min_weight", 0.0D);
                double maxWeight = getDoubleOrDefault(fish, "max_weight", minWeight);
                int minSkill = getIntOrDefault(fish, "min_skill", 0);
                int rarity = getIntOrDefault(fish, "rarity", 0);
                fishMap.put(key, new FishCatalog.FishMeta(name, minWeight, maxWeight, minSkill, rarity));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed fish bootstrap entry: {}", ex.getMessage());
            }
        }
        return fishMap;
    }

    private static List<RegionData> parseRegions(JsonObject root, String shard) {
        List<RegionData> regions = new ArrayList<>();
        int rawRegionCount = hasArray(root, "regions") ? root.getAsJsonArray("regions").size() : 0;
        LOGGER.info("World bootstrap received {} raw regions for shard {}", rawRegionCount, shard);
        if (!hasArray(root, "regions")) {
            LOGGER.warn("World bootstrap response for shard {} did not include a regions array.", shard);
            return regions;
        }

        for (JsonElement element : root.getAsJsonArray("regions")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed region bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject region = element.getAsJsonObject();
                OptionalInt minX = getRequiredInt(region, "min_x");
                OptionalInt maxX = getRequiredInt(region, "max_x");
                OptionalInt minY = getRequiredInt(region, "min_y");
                OptionalInt maxY = getRequiredInt(region, "max_y");
                OptionalInt minZ = getRequiredInt(region, "min_z");
                OptionalInt maxZ = getRequiredInt(region, "max_z");
                if (minX.isEmpty() || maxX.isEmpty() || minY.isEmpty() || maxY.isEmpty() || minZ.isEmpty() || maxZ.isEmpty()) {
                    LOGGER.warn("Skipping malformed region bootstrap entry: missing or invalid bounds.");
                    continue;
                }

                boolean climatePresent = region.has("climate") && !region.get("climate").isJsonNull();
                if (regions.isEmpty()) {
                    LOGGER.info("First bootstrap region climate present for shard {}: {}", shard, climatePresent);
                }
                String name = getStringOrDefault(region, "name", "Unnamed Region");
                String climate = getStringOrDefault(region, "climate", "Temperate");
                List<RegionItemData> items = parseRegionItems(region);
                regions.add(new RegionData(
                        name,
                        climate,
                        minX.getAsInt(),
                        maxX.getAsInt(),
                        minY.getAsInt(),
                        maxY.getAsInt(),
                        minZ.getAsInt(),
                        maxZ.getAsInt(),
                        items
                ));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed region bootstrap entry: {}", ex.getMessage());
            }
        }

        LOGGER.info("World bootstrap parsed {} regions for shard {}", regions.size(), shard);
        return regions;
    }

    private static List<RegionItemData> parseRegionItems(JsonObject region) {
        List<RegionItemData> items = new ArrayList<>();
        if (!hasArray(region, "items")) {
            return items;
        }

        for (JsonElement itemElement : region.getAsJsonArray("items")) {
            if (itemElement == null || !itemElement.isJsonObject()) {
                LOGGER.warn("Skipping malformed region item bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject item = itemElement.getAsJsonObject();
                String type = getStringOrDefault(item, "type", "");
                String key = getStringOrDefault(item, "key", "");
                if (type.isBlank() || key.isBlank()) {
                    LOGGER.warn("Skipping malformed region item bootstrap entry: missing type or key.");
                    continue;
                }
                int weight = getIntOrDefault(item, "weight", 0);
                Integer minSkillOverride = item.has("min_skill_override") && !item.get("min_skill_override").isJsonNull()
                        ? getIntOrDefault(item, "min_skill_override", 0)
                        : null;
                int rarity = getIntOrDefault(item, "rarity", 0);
                items.add(new RegionItemData(type, key, weight, minSkillOverride, rarity));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed region item bootstrap entry: {}", ex.getMessage());
            }
        }
        return items;
    }

    private static List<CityBootstrapData> parseCities(JsonObject root) {
        List<CityBootstrapData> citiesData = new ArrayList<>();
        if (!hasArray(root, "cities")) {
            return citiesData;
        }

        for (JsonElement element : root.getAsJsonArray("cities")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed city bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject city = element.getAsJsonObject();
                String name = getStringOrDefault(city, "name", "");
                if (name.isBlank()) {
                    LOGGER.warn("Skipping malformed city bootstrap entry: missing name.");
                    continue;
                }

                JsonObject supplies = getObjectOrEmpty(city, "supplies");
                double food = getDoubleOrDefault(supplies, "food", 0.0D);
                double wood = getDoubleOrDefault(supplies, "wood", 0.0D);
                double metal = getDoubleOrDefault(supplies, "metal", 0.0D);
                double stone = getDoubleOrDefault(supplies, "stone", 0.0D);
                double textile = getDoubleOrDefault(supplies, "textile", 0.0D);
                double alcohol = getDoubleOrDefault(supplies, "alcohol", 0.0D);
                double tech = getDoubleOrDefault(supplies, "technology", 0.0D);

                JsonObject treasury = getObjectOrEmpty(city, "treasury");
                int gold = getIntOrDefault(treasury, "gold", 0);
                int silver = getIntOrDefault(treasury, "silver", 0);
                int copper = getIntOrDefault(treasury, "copper", 0);

                Map<String, Map<String, Map<String, Double>>> weights = parseWeights(getObjectOrEmpty(city, "market_weights"));
                Map<String, Map<String, Map<String, Integer>>> quantities = parseQuantities(getObjectOrEmpty(city, "market_quantities"));
                citiesData.add(new CityBootstrapData(name, food, wood, metal, stone, textile, alcohol, tech, gold, silver, copper, weights, quantities));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed city bootstrap entry: {}", ex.getMessage());
            }
        }
        return citiesData;
    }

    private static ShardUserData parseShardUser(JsonObject root, ServerPlayer player) {
        if (!hasObject(root, "shard_user")) {
            return null;
        }

        try {
            JsonObject shardUserObject = root.getAsJsonObject("shard_user");
            String gender = getStringOrDefault(shardUserObject, "gender", "");
            int fame = getIntOrDefault(shardUserObject, "fame", 0);
            int karma = getIntOrDefault(shardUserObject, "karma", 0);
            int murderCount = getIntOrDefault(shardUserObject, "murder_count", 0);
            JsonObject inventory = getObjectOrEmpty(shardUserObject, "inventory");
            JsonObject stats = getObjectOrEmpty(shardUserObject, "stats");

            ShardUserData shardUser = new ShardUserData(gender, fame, karma, murderCount, inventory, stats);
            com.seggellion.britannia_mod.player.PlayerData pd = PlayerDataStore.get(player);
            pd.setPlayerName(player.getGameProfile().getName());
            pd.syncFromShardUser(shardUser);
            PlayerDataStore.save(player, pd);
            return shardUser;
        } catch (Exception ex) {
            LOGGER.warn("Skipping malformed shard_user bootstrap data: {}", ex.getMessage());
            return null;
        }
    }

    private static int parseGrapes(JsonObject root) {
        List<com.seggellion.britannia_mod.winery.GrapeVariety> grapesList = new ArrayList<>();
        if (!hasArray(root, "grapes")) {
            return 0;
        }

        for (JsonElement element : root.getAsJsonArray("grapes")) {
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("Skipping malformed grape bootstrap entry: not an object.");
                continue;
            }

            try {
                JsonObject grape = element.getAsJsonObject();
                String id = getStringOrDefault(grape, "id", "");
                if (id.isBlank()) {
                    LOGGER.warn("Skipping malformed grape bootstrap entry: missing id.");
                    continue;
                }
                String displayName = getStringOrDefault(grape, "display_name", id);
                int hydration = getIntOrDefault(grape, "optimal_hydration", 3);

                JsonObject chemistry = getObjectOrEmpty(grape, "chemistry");
                float n = getFloatOrDefault(chemistry, "n", 0.0f);
                float p = getFloatOrDefault(chemistry, "p", 0.0f);
                float k = getFloatOrDefault(chemistry, "k", 0.0f);
                float om = getFloatOrDefault(chemistry, "om", 0.0f);

                String climate = getStringOrDefault(grape, "climate",
                        getStringOrDefault(grape, "region", "Temperate"));

                JsonObject altitude = getObjectOrEmpty(grape, "altitude");
                int minAltitude = getIntOrDefault(altitude, "min", -64);
                int maxAltitude = getIntOrDefault(altitude, "max", 320);
                int color = parseColor(grape);
                int difficulty = getIntOrDefault(grape, "difficulty", 1);

                String colorString = getStringOrDefault(grape, "grape_color", "PURPLE");
                com.seggellion.britannia_mod.winery.GrapeColor grapeColor;
                try {
                    grapeColor = com.seggellion.britannia_mod.winery.GrapeColor.valueOf(colorString.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Invalid grape color enum for {}: {}", id, colorString);
                    grapeColor = com.seggellion.britannia_mod.winery.GrapeColor.PURPLE;
                }

                grapesList.add(new com.seggellion.britannia_mod.winery.GrapeVariety(
                        id, displayName, hydration, n, p, k, om, climate, minAltitude, maxAltitude, color, difficulty, grapeColor
                ));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed grape bootstrap entry: {}", ex.getMessage());
            }
        }

        GrapeVarietyManager.loadFromBootstrap(grapesList);
        LOGGER.info("World bootstrap parsed grape varieties={}", grapesList.size());
        return grapesList.size();
    }

    private static List<ClientQuestEntry> parseAcceptedQuestsSafely(JsonObject root) {
        try {
            return QuestEntryParser.parseAcceptedQuests(root);
        } catch (Exception ex) {
            LOGGER.warn("Skipping malformed accepted quest bootstrap data: {}", ex.getMessage());
            return List.of();
        }
    }

    private static int parseColor(JsonObject grape) {
        JsonElement colorElement = grape == null ? null : grape.get("base_color");
        if (colorElement == null || colorElement.isJsonNull() || !colorElement.isJsonPrimitive()) {
            return 0xFFFFFF;
        }

        try {
            JsonPrimitive primitive = colorElement.getAsJsonPrimitive();
            if (primitive.isString()) {
                return Integer.decode(primitive.getAsString());
            }
            return primitive.getAsInt();
        } catch (Exception ex) {
            LOGGER.warn("Invalid grape color value: {}", colorElement);
            return 0xFFFFFF;
        }
    }

    private static Map<String, Map<String, Map<String, Double>>> parseWeights(JsonObject obj) {
        Map<String, Map<String, Map<String, Double>>> result = new HashMap<>();
        if (obj == null) return result;
        for (Map.Entry<String, JsonElement> categoryEntry : obj.entrySet()) {
            String cat = categoryEntry.getKey();
            JsonElement categoryElement = categoryEntry.getValue();
            if (categoryElement == null || !categoryElement.isJsonObject()) {
                LOGGER.warn("Skipping malformed market weight category {}: not an object.", cat);
                continue;
            }
            Map<String, Map<String, Double>> subMap = new HashMap<>();
            JsonObject catObj = categoryElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> subEntry : catObj.entrySet()) {
                String sub = subEntry.getKey();
                JsonElement subElement = subEntry.getValue();
                if (subElement == null || !subElement.isJsonObject()) {
                    LOGGER.warn("Skipping malformed market weight subcategory {}.{}: not an object.", cat, sub);
                    continue;
                }
                Map<String, Double> itemMap = new HashMap<>();
                JsonObject subObj = subElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> itemEntry : subObj.entrySet()) {
                    String item = itemEntry.getKey();
                    JsonElement value = itemEntry.getValue();
                    if (value == null || value.isJsonNull()) {
                        continue;
                    }
                    try {
                        itemMap.put(item, value.getAsDouble());
                    } catch (Exception ex) {
                        LOGGER.warn("Skipping malformed market weight {}.{}.{}: {}", cat, sub, item, ex.getMessage());
                    }
                }
                subMap.put(sub, itemMap);
            }
            result.put(cat, subMap);
        }
        return result;
    }
    
    private static Map<String, Map<String, Map<String, Integer>>> parseQuantities(JsonObject obj) {
        Map<String, Map<String, Map<String, Integer>>> result = new HashMap<>();
        if (obj == null) return result;
        for (Map.Entry<String, JsonElement> categoryEntry : obj.entrySet()) {
            String cat = categoryEntry.getKey();
            JsonElement categoryElement = categoryEntry.getValue();
            if (categoryElement == null || !categoryElement.isJsonObject()) {
                LOGGER.warn("Skipping malformed market quantity category {}: not an object.", cat);
                continue;
            }
            Map<String, Map<String, Integer>> subMap = new HashMap<>();
            JsonObject catObj = categoryElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> subEntry : catObj.entrySet()) {
                String sub = subEntry.getKey();
                JsonElement subElement = subEntry.getValue();
                if (subElement == null || !subElement.isJsonObject()) {
                    LOGGER.warn("Skipping malformed market quantity subcategory {}.{}: not an object.", cat, sub);
                    continue;
                }
                Map<String, Integer> itemMap = new HashMap<>();
                JsonObject subObj = subElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> itemEntry : subObj.entrySet()) {
                    String item = itemEntry.getKey();
                    JsonElement value = itemEntry.getValue();
                    if (value == null || value.isJsonNull()) {
                        continue;
                    }
                    try {
                        itemMap.put(item, value.getAsInt());
                    } catch (Exception ex) {
                        LOGGER.warn("Skipping malformed market quantity {}.{}.{}: {}", cat, sub, item, ex.getMessage());
                    }
                }
                subMap.put(sub, itemMap);
            }
            result.put(cat, subMap);
        }
        return result;
    }

    private static boolean hasObject(JsonObject obj, String key) {
        return obj != null
                && obj.has(key)
                && !obj.get(key).isJsonNull()
                && obj.get(key).isJsonObject();
    }

    private static boolean hasArray(JsonObject obj, String key) {
        return obj != null
                && obj.has(key)
                && !obj.get(key).isJsonNull()
                && obj.get(key).isJsonArray();
    }

    private static JsonObject getObjectOrEmpty(JsonObject obj, String key) {
        return hasObject(obj, key) ? obj.getAsJsonObject(key) : new JsonObject();
    }

    private static String getStringOrDefault(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int getIntOrDefault(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static OptionalInt getRequiredInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return OptionalInt.empty();
        try {
            return OptionalInt.of(obj.get(key).getAsInt());
        } catch (Exception ex) {
            return OptionalInt.empty();
        }
    }

    private static double getDoubleOrDefault(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static float getFloatOrDefault(JsonObject obj, String key, float fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String normalizedApiBase(String configuredBase) {
        String base = configuredBase == null || configuredBase.isBlank()
                ? "http://127.0.0.1:3000/api"
                : configuredBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/api")) {
            base += "/api";
        }
        return base;
    }

    private static String readResponseBody(HttpURLConnection conn, boolean errorBody) {
        try (InputStream stream = errorBody ? conn.getErrorStream() : conn.getInputStream()) {
            if (stream == null) {
                return "<empty>";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<unreadable: " + ex.getMessage() + ">";
        }
    }
}
