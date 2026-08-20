package com.seggellion.britannia_mod.util;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.structure.HouseStyle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;

public class HouseDataAPI {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Housing Deed Milestone 2: the house row now carries its region.
     *
     * <p>{@code record} and {@code origin} are what let the shard rebuild
     * {@link com.seggellion.britannia_mod.structure.StructureRegionManager} after a restart.
     * Rotation in particular was written down nowhere before this -- not here, not in the lot
     * block entity -- and the bounding box cannot be derived from the house position without it.
     */
    public static void sendHouseDataToRails(ServerLevel level, BlockPos housePos, Player owner,
                                            HouseStyle style, StructureRecord record, BlockPos origin) {
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
            var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow().apiUrls()
                    .resolve(Endpoint.HOUSE_CREATE);
            HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

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
            payload.addProperty("shard", ServerAuthRegistry.credentials(level.getServer()).orElseThrow().shardName());
            payload.addProperty("for_sale", false);
            payload.addProperty("price", 0);

            JsonArray emptyAccessList = new JsonArray();
            payload.add("friends_list", emptyAccessList);

            // placed_at: needs a datetime (ISO8601). 
            String timestamp = Instant.now().toString(); 
            payload.addProperty("placed_at", timestamp);

            // The region itself. Rails stores this blob verbatim and never reads inside it;
            // StructureRegionCodec owns the shape on both sides of the wire.
            payload.add("structure", StructureRegionCodec.encode(record, origin));

            // Send it
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (!RailsRequestAuthenticator.apply(conn, level.getServer(), bodyBytes)) throw new IllegalStateException("Server authentication unavailable");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            // Handle response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                JsonObject response = JsonParser.parseString(
                        BoundedHttp.readUtf8(conn.getInputStream(), 256 * 1024)).getAsJsonObject();
                LOGGER.info("House created successfully: {}", response);
            } else {
                // Milestone 2: this is not a cosmetic failure any more. Rails is what the
                // shard rebuilds house regions from at boot, so a house that never lands
                // here comes back after the next restart with no region at all: its doors
                // resolve no lock, and its owner cannot dig beneath it. The lot block still
                // holds the rotation and owner needed to rebuild it by hand.
                LOGGER.warn("Failed to record house {} with Rails (HTTP {}). Its region will "
                        + "not survive a restart until Rails learns about it.",
                        houseUuid, responseCode);
            }

        } catch (Exception e) {
            LOGGER.error("Error sending house data to API: ", e);
        }
    }

/**
 * Housing Deed Milestone 2: every house on this shard, for region rehydration at boot.
 *
 * <p>Returns {@code null} on any failure rather than an empty array, because the two mean
 * very different things to the caller: an empty array is a shard with no houses, and null is
 * a shard that could not find out. Restoring nothing because Rails was unreachable would
 * strip every player of the rights inside their own house.
 *
 * <p>Runs off the server thread -- see {@code StructureRegionRehydrator}. Logs nothing; the
 * caller reports the outcome exactly once whichever way it went.
 */
public static JsonArray fetchShardHouses(MinecraftServer server) {
    HttpURLConnection conn = null;
    try {
        var credentials = ServerAuthRegistry.credentials(server).orElseThrow();
        var requestUri = credentials.apiUrls().resolveQuery(
                Endpoint.HOUSE_INDEX, Map.of("shard", credentials.shardName()));
        conn = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(conn);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (!RailsRequestAuthenticator.apply(conn, server, new byte[0])) return null;

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

        JsonElement parsed = JsonParser.parseString(
                BoundedHttp.readUtf8(conn.getInputStream(), 4 * 1024 * 1024));
        return parsed.isJsonArray() ? parsed.getAsJsonArray() : null;
    } catch (Exception unreachable) {
        return null;
    } finally {
        if (conn != null) conn.disconnect();
    }
}

public static void deleteHouseRecord(ServerPlayer player, StructureRecord record) {
    try {
        var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
                .resolve(Endpoint.HOUSE_DELETE);
        HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(conn);
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        JsonObject payload = new JsonObject();
        payload.addProperty("owner", player.getName().getString());
        payload.addProperty("uuid", record.getHouseUuid().toString());

        if (record.getDeedId() != null) {
            payload.addProperty("deed_id", record.getDeedId().toString());
        }
        byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        if (!RailsRequestAuthenticator.apply(conn, player.server, bodyBytes)) throw new IllegalStateException("Server authentication unavailable");

        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
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
