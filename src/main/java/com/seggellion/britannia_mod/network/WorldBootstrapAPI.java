// com/seggellion/britannia_mod/sync/WorldBootstrapAPI.java
package com.seggellion.britannia_mod.sync;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.network.ClientboundSyncCityTokenPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WorldBootstrapAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static WorldBootstrapData fetch(ServerPlayer player) {
        try {
            // Resolve shard (pick your source of truth)
            String shard = ModConfig.SHARD_NAME != null && !ModConfig.SHARD_NAME.isBlank()
                    ? ModConfig.SHARD_NAME
                    : "Britannia"; // sensible default

            String playerUuid = player.getUUID().toString();

            String encodedShard = URLEncoder.encode(shard, StandardCharsets.UTF_8);
            String encodedUuid  = URLEncoder.encode(playerUuid, StandardCharsets.UTF_8);

            String base = ModConfig.API_BASE_URL;
            if (!base.endsWith("/")) base += "/";
            final String urlString = base + "world_bootstrap/" + encodedShard + "?player_uuid=" + encodedUuid;

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

                // 3) ShardUser data
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
                        pd.setPlayerName(player.getGameProfile().getName()); // optional
                        pd.syncFromShardUser(shardUser);
                        com.seggellion.britannia_mod.player.PlayerDataStore.save(player, pd);
                    }
                }

                // Return once, outside the if-block
                return new WorldBootstrapData(fishMap, regions, shardUser);
            }
        } catch (Exception e) {
            LOGGER.error("Failed world bootstrap", e);
            return WorldBootstrapData.empty();
        }
    }

    public record WorldBootstrapData(
            Map<ResourceLocation, FishCatalog.FishMeta> fish,
            List<RegionData> regions,
            ShardUserData shardUser
    ) {
        public static WorldBootstrapData empty() {
            return new WorldBootstrapData(Map.of(), List.of(), null);
        }
    }

    public record ShardUserData(
            String gender,
            int fame,
            int karma,
            int murderCount,
            JsonObject inventory,
            JsonObject stats
    ) {}
}
