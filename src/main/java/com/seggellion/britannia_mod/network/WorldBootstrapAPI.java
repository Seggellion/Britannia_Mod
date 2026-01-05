package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.network.ClientboundSyncCityTokenPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WorldBootstrapAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static WorldBootstrapData fetch(ServerPlayer player) {
        try {
            // 1. Resolve Shard Name first
            String shard = ModConfig.SHARD_NAME != null && !ModConfig.SHARD_NAME.isBlank()
                    ? ModConfig.SHARD_NAME
                    : "Britannia"; 
            
            // 2. Resolve Player UUID
            String playerUuid = player.getUUID().toString();

            // 3. Encode values
            String encodedShard = URLEncoder.encode(shard, StandardCharsets.UTF_8);
            String encodedUuid  = URLEncoder.encode(playerUuid, StandardCharsets.UTF_8);

            // 4. Construct URL
            String base = ModConfig.API_BASE_URL;
            if (!base.endsWith("/")) base += "/";
            final String urlString = base + "world_bootstrap/" + encodedShard + "?player_uuid=" + encodedUuid;

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
            if (code != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("World bootstrap failed for shard {}. HTTP {}", shard, code);
                return WorldBootstrapData.empty();
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader json = new JsonReader(reader)) {

                JsonObject root = JsonParser.parseReader(json).getAsJsonObject();

                // 1) Fish catalog
                Map<ResourceLocation, FishCatalog.FishMeta> fishMap = new HashMap<>();
                if (root.has("fish") && root.get("fish").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("fish")) {
                        JsonObject f = el.getAsJsonObject();
                        String keyStr = f.get("item_key").getAsString();
                        ResourceLocation key = ResourceLocation.parse(keyStr);
                        String name = f.has("name") && !f.get("name").isJsonNull()
                                ? f.get("name").getAsString()
                                : key.getPath();
                        double minW = f.get("min_weight").getAsDouble();
                        double maxW = f.get("max_weight").getAsDouble();
                        int minSkill = f.has("min_skill") ? f.get("min_skill").getAsInt() : 0;
                        int rarity = f.has("rarity") ? f.get("rarity").getAsInt() : 0;
                        fishMap.put(key, new FishCatalog.FishMeta(name, minW, maxW, minSkill, rarity));
                    }
                }

                // 2) Regions
                List<RegionData> regions = new ArrayList<>();
                if (root.has("regions") && root.get("regions").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("regions")) {
                        JsonObject r = el.getAsJsonObject();
                        String name = r.get("name").getAsString();
                        int minX = r.get("min_x").getAsInt();
                        int maxX = r.get("max_x").getAsInt();
                        int minY = r.get("min_y").getAsInt();
                        int maxY = r.get("max_y").getAsInt();
                        int minZ = r.get("min_z").getAsInt();
                        int maxZ = r.get("max_z").getAsInt();

                        List<RegionItemData> items = new ArrayList<>();
                        if (r.has("items") && r.get("items").isJsonArray()) {
                            for (JsonElement ie : r.getAsJsonArray("items")) {
                                JsonObject io = ie.getAsJsonObject();
                                String type = io.get("type").getAsString();
                                String key = io.get("key").getAsString();
                                int weight = io.get("weight").getAsInt();
                                Integer minSkillOverride =
                                        io.has("min_skill_override") && !io.get("min_skill_override").isJsonNull()
                                                ? io.get("min_skill_override").getAsInt()
                                                : null;
                                int rarity = 0;
                                items.add(new RegionItemData(type, key, weight, minSkillOverride, rarity));
                            }
                        }
                        regions.add(new RegionData(name, minX, maxX, minY, maxY, minZ, maxZ, items));
                    }
                }

                // 3) City Data
                List<CityBootstrapData> citiesData = new ArrayList<>();
                if (root.has("cities") && root.get("cities").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("cities")) {
                        JsonObject c = el.getAsJsonObject();
                        String name = c.get("name").getAsString();

                        JsonObject s = c.getAsJsonObject("supplies");
                        double food = s.get("food").getAsDouble();
                        double wood = s.get("wood").getAsDouble();
                        double metal = s.get("metal").getAsDouble();
                        double stone = s.get("stone").getAsDouble();
                        double textile = s.get("textile").getAsDouble();
                        double alcohol = s.get("alcohol").getAsDouble();
                        double tech = s.get("technology").getAsDouble();

                        JsonObject t = c.getAsJsonObject("treasury");
                        int gold = t.get("gold").getAsInt();
                        int silver = t.get("silver").getAsInt();
                        int copper = t.get("copper").getAsInt();

                        Map<String, Map<String, Map<String, Double>>> weights = parseWeights(c.getAsJsonObject("market_weights"));
                        Map<String, Map<String, Map<String, Integer>>> quantities = parseQuantities(c.getAsJsonObject("market_quantities"));

                        citiesData.add(new CityBootstrapData(name, food, wood, metal, stone, textile, alcohol, tech, gold, silver, copper, weights, quantities));
                    }
                }

                // 4) ShardUser data
                ShardUserData shardUser = null;
                if (root.has("shard_user") && root.get("shard_user").isJsonObject()) {
                    JsonObject su = root.getAsJsonObject("shard_user");
                    String gender        = su.get("gender").getAsString();
                    int fame             = su.get("fame").getAsInt();
                    int karma            = su.get("karma").getAsInt();
                    int murderCount      = su.get("murder_count").getAsInt();
                    JsonObject inventory = su.getAsJsonObject("inventory");
                    JsonObject stats     = su.getAsJsonObject("stats");

                    shardUser = new ShardUserData(gender, fame, karma, murderCount, inventory, stats);
                    if (shardUser != null) {
                        com.seggellion.britannia_mod.player.PlayerData pd =
                            com.seggellion.britannia_mod.player.PlayerDataStore.get(player);
                        pd.setPlayerName(player.getGameProfile().getName());
                        pd.syncFromShardUser(shardUser);
                        com.seggellion.britannia_mod.player.PlayerDataStore.save(player, pd);
                    }
                }

                // 5) Grape Varieties
                List<com.seggellion.britannia_mod.winery.GrapeVariety> grapesList = new ArrayList<>();
                if (root.has("grapes") && root.get("grapes").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("grapes")) {
                        JsonObject g = el.getAsJsonObject();
                        
                        String id = g.get("id").getAsString();
                        String displayName = g.get("display_name").getAsString();
                        int hydration = g.get("optimal_hydration").getAsInt();
                        
                        // Chemistry
                        JsonObject chem = g.getAsJsonObject("chemistry");
                        float n = chem.get("n").getAsFloat();
                        float p = chem.get("p").getAsFloat();
                        float k = chem.get("k").getAsFloat();
                        float om = chem.get("om").getAsFloat();

                        String region = g.has("region") ? g.get("region").getAsString() : "Temperate";
                        
                        // Altitude
                        JsonObject alt = g.getAsJsonObject("altitude");
                        int minAlt = alt.get("min").getAsInt();
                        int maxAlt = alt.get("max").getAsInt();

                        // --- FIX STARTS HERE ---
                        // Handle Hex Strings (0x...) or standard Integers
                        int color = 0xFFFFFF; // Default white
                        if (g.has("base_color")) {
                            JsonElement cEl = g.get("base_color");
                            if (cEl.getAsJsonPrimitive().isString()) {
                                try {
                                    // Integer.decode handles "0x", "#", and plain numbers automatically
                                    color = Integer.decode(cEl.getAsString());
                                } catch (NumberFormatException e) {
                                    LOGGER.warn("Invalid grape color hex: " + cEl.getAsString());
                                    color = 0xFFFFFF;
                                }
                            } else {
                                color = cEl.getAsInt();
                            }
                        }
                        // --- FIX ENDS HERE ---

                        int diff = g.get("difficulty").getAsInt();
                        
                        // Enum Parsing
                        String colorStr = g.has("grape_color") ? g.get("grape_color").getAsString() : "PURPLE";
                        com.seggellion.britannia_mod.winery.GrapeColor grapeColorEnum;
                        try {
                            grapeColorEnum = com.seggellion.britannia_mod.winery.GrapeColor.valueOf(colorStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            grapeColorEnum = com.seggellion.britannia_mod.winery.GrapeColor.PURPLE;
                        }

                        grapesList.add(new com.seggellion.britannia_mod.winery.GrapeVariety(
                            id, displayName, hydration, n, p, k, om, region, minAlt, maxAlt, color, diff, grapeColorEnum
                        ));
                    }
                    // IMPORTANT: Pass the data to the Manager!
                    GrapeVarietyManager.loadFromBootstrap(grapesList);
                }

                // Return
                return new WorldBootstrapData(fishMap, regions, shardUser, citiesData);
            }
        } catch (Exception e) {
            LOGGER.error("Failed world bootstrap", e);
            return WorldBootstrapData.empty();
        }
    }

    // --- Data Records & Helpers ---

    public record WorldBootstrapData(
            Map<ResourceLocation, FishCatalog.FishMeta> fish,
            List<RegionData> regions, 
            ShardUserData shardUser,
            List<CityBootstrapData> cities
    ) {
        public static WorldBootstrapData empty() {
            return new WorldBootstrapData(Map.of(), List.of(), null, List.of());
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

    private static Map<String, Map<String, Map<String, Double>>> parseWeights(JsonObject obj) {
        Map<String, Map<String, Map<String, Double>>> result = new HashMap<>();
        if (obj == null) return result;
        for (String cat : obj.keySet()) {
            Map<String, Map<String, Double>> subMap = new HashMap<>();
            JsonObject catObj = obj.getAsJsonObject(cat);
            for (String sub : catObj.keySet()) {
                Map<String, Double> itemMap = new HashMap<>();
                JsonObject subObj = catObj.getAsJsonObject(sub);
                for (String item : subObj.keySet()) {
                    itemMap.put(item, subObj.get(item).getAsDouble());
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
        for (String cat : obj.keySet()) {
            Map<String, Map<String, Integer>> subMap = new HashMap<>();
            JsonObject catObj = obj.getAsJsonObject(cat);
            for (String sub : catObj.keySet()) {
                Map<String, Integer> itemMap = new HashMap<>();
                JsonObject subObj = catObj.getAsJsonObject(sub);
                for (String item : subObj.keySet()) {
                    itemMap.put(item, subObj.get(item).getAsInt());
                }
                subMap.put(sub, itemMap);
            }
            result.put(cat, subMap);
        }
        return result;
    }
}