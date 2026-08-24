package com.seggellion.britannia_mod.util;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionCodec;
import com.seggellion.britannia_mod.structure.persistence.HousePersistenceService;

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
            JsonObject payload = houseCreatePayload(level, lot, owner, style, record, origin);
            // Queue first, then send. The queue write is what makes a house survive a crash, a Rails
            // restart, or a 500 -- see HousePersistenceService for why this stopped being an inline
            // call with a logged warning for failure handling.
            HousePersistenceService.submit(level, lot.getHouseUuid(), payload.toString());
        } catch (Exception e) {
            LOGGER.error("Error preparing house data for Rails: ", e);
        }
    }

    /**
     * The house row exactly as Rails' {@code Api::HousesController#create} reads it.
     *
     * <p>Built once, at placement, and then stored verbatim by the outbox: a retry has to send the
     * house that was placed, not a house re-derived from a world that has since been dug up, sold
     * or restarted.
     *
     * <p>{@code size} is sent because the column exists and was never populated -- every house row
     * this shard has ever written has a null {@code size} -- and {@code house_type} carries the size
     * id while {@code style} carries the style, which is the mapping the controller actually applies.
     */
    static JsonObject houseCreatePayload(ServerLevel level, HouseLotBlockEntity lot, Player owner,
                                         HouseStyle style, StructureRecord record, BlockPos origin) {
        UUID houseUuid = lot.getHouseUuid();
        UUID deedUuid = lot.getDeedUuid();
        BlockPos housePos = lot.getBlockPos();

        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", houseUuid.toString());
        payload.addProperty("house_type", lot.getHouseType());
        payload.addProperty("style", style.name().toLowerCase());
        payload.addProperty("size", style.getSize().id());
        payload.addProperty("x", housePos.getX());
        payload.addProperty("y", housePos.getY());
        payload.addProperty("z", housePos.getZ());
        if (deedUuid != null) {
            payload.addProperty("deed_id", deedUuid.toString());
        }
        payload.addProperty("owner_uuid", owner.getStringUUID());
        payload.addProperty("owner_username", owner.getName().getString());
        payload.addProperty("region_name", "Trinsic");
        payload.addProperty("shard",
                ServerAuthRegistry.credentials(level.getServer()).orElseThrow().shardName());
        payload.addProperty("for_sale", false);
        payload.addProperty("price", 0);
        payload.add("friends_list", new JsonArray());
        payload.addProperty("placed_at", Instant.now().toString());

        // The region itself. Rails stores this blob verbatim and never reads inside it;
        // StructureRegionCodec owns the shape on both sides of the wire.
        payload.add("structure", StructureRegionCodec.encode(record, origin));
        return payload;
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
