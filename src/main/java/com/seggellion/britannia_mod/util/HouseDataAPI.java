package com.seggellion.britannia_mod.util;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.StructureRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.structure.HouseStyle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;

public class HouseDataAPI {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void sendHouseDataToRails(ServerLevel level, BlockPos housePos, Player owner, HouseStyle style) {
        try {
            HouseLotBlockEntity lot = (HouseLotBlockEntity) level.getBlockEntity(housePos);
            if (lot == null) {
                LOGGER.error("HouseLotBlockEntity not found at position: {}", housePos);
                return;
            }
            UUID houseUuid = lot.getHouseUuid(); // ✅ Use the actual in-game UUID
            UUID deedUuid = lot.getDeedUuid(); 

            // We'll assume "small" for the house_type, matching your MIGRATION (house_type can be "villa", "cottage", etc.)
            String houseType = lot.getHouseType();

            // The Rails endpoint
            String urlString = ModConfig.API_BASE_URL + "houses";  // e.g. .../api/houses

            // Prepare connection
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            
            // Set authorization if needed
            CityAPITokenData data = CityAPITokenData.getOrCreate(level);
            String apiToken = data.getApiToken();
            if (!apiToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            }
            conn.setDoOutput(true);

            // Build JSON
            JsonObject payload = new JsonObject();
            // Required by House model:
            payload.addProperty("uuid", houseUuid.toString());           // e.g. "48e5-..."
            payload.addProperty("house_type", houseType);                // "small" for day 3
            payload.addProperty("style", style.name().toLowerCase());      // "small", "patio", etc.
            payload.addProperty("x", housePos.getX());
            payload.addProperty("y", housePos.getY());
            payload.addProperty("z", housePos.getZ());
            if (deedUuid != null) {
                payload.addProperty("deed_id", deedUuid.toString());
            }

            // belongs_to :shard_user => you must pass something that your Rails app uses to identify the shard_user
            // e.g. if your server finds shard_user by "player_uuid", or you have a known "shard_user_id"
            // For example:
            // payload.addProperty("shard_user_id", 123); 
            // or
            // payload.addProperty("player_uuid", owner.getStringUUID());
            // Then handle it in your Rails controller so it can do:
            //   House.create!(shard_user_id: ShardUser.find_by(uuid: params[:player_uuid]).id, ...)
            
            // For Day 3, let's assume your server will handle "player_uuid" -> shard_user lookups
            payload.addProperty("owner_uuid", owner.getStringUUID());

            // Additional fields (per your migration):
            payload.addProperty("owner_username", owner.getName().getString());
            payload.addProperty("region_name", "Trinsic");
            payload.addProperty("shard", "Britannia");
            payload.addProperty("for_sale", false);
            payload.addProperty("price", 0);

            JsonArray emptyAccessList = new JsonArray();
            payload.add("friends_list", emptyAccessList);

            // placed_at: needs a datetime (ISO8601). 
            String timestamp = Instant.now().toString(); 
            payload.addProperty("placed_at", timestamp);

            // Send it
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            // Handle response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                    LOGGER.info("House created successfully: {}", response);
                }
            } else {
                LOGGER.warn("Failed to create house. Response code: {}", responseCode);
            }

        } catch (Exception e) {
            LOGGER.error("Error sending house data to API: ", e);
        }
    }

public static void deleteHouseRecord(ServerPlayer player, StructureRecord record) {
    try {
        URL url = new URL(ModConfig.API_BASE_URL + "houses/delete");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        CityAPITokenData data = CityAPITokenData.getOrCreate(player.serverLevel());
        String apiToken = data.getApiToken();
        if (!apiToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
        }

        conn.setDoOutput(true);
        JsonObject payload = new JsonObject();
        payload.addProperty("owner", player.getName().getString());
        payload.addProperty("uuid", record.getHouseUuid().toString());

        if (record.getDeedId() != null) {
            payload.addProperty("deed_id", record.getDeedId().toString());
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            LOGGER.warn("Failed to delete house: " + responseCode);
        }
    } catch (Exception e) {
        LOGGER.error("Failed to delete house record: ", e);
    }
}



}
