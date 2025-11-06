package com.seggellion.britannia_mod.network;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.util.CityAPITokenData;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

public class RailsUpdateServer extends NanoHTTPD {
    private static final Logger LOGGER = LoggerFactory.getLogger("RailsUpdateServer");
    private final MinecraftServer server;

    public RailsUpdateServer(int port, MinecraftServer server) {
        super(port);
        this.server = server;
    }

    @Override
public Response serve(IHTTPSession session) {
    LOGGER.info("Incoming request: {} {}", session.getMethod(), session.getUri());

    if (session.getMethod() == Method.POST && "/api/population_update".equals(session.getUri())) {
        try {
            String providedSecret = session.getHeaders().get("x-britannia-secret");
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);

            if (overworld == null) {
                LOGGER.error("Overworld not found! Cannot validate shard secret.");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                        "{\"error\":\"Overworld not found\"}");
            }

            String storedSecret = CityAPITokenData.getOrCreate(overworld).getShardSecret();
            if (storedSecret == null || storedSecret.isEmpty()) {
                LOGGER.warn("No Shard Secret configured on server. Rejecting request.");
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json",
                        "{\"error\":\"Shard secret not configured\"}");
            }

            if (providedSecret == null || !providedSecret.equals(storedSecret)) {
                LOGGER.warn("Invalid Shard Secret received: {}", providedSecret);
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json",
                        "{\"error\":\"Invalid Shard Secret\"}");
            }


            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");

            if (body == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Missing POST data\"}");
            }

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("cities")) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Missing 'cities' array\"}");
            }

            JsonArray cities = root.getAsJsonArray("cities");

            for (var cityElement : cities) {
                JsonObject cityObj = cityElement.getAsJsonObject();
                String cityName = cityObj.get("name").getAsString();

                JsonObject treasury = cityObj.getAsJsonObject("treasury");
                int gold = treasury.has("gold") ? treasury.get("gold").getAsInt() : 0;
                int silver = treasury.has("silver") ? treasury.get("silver").getAsInt() : 0;
                int copper = treasury.has("copper") ? treasury.get("copper").getAsInt() : 0;
                int totalTreasury = gold + silver + copper;

                double foodSupply = getDoubleSafe(cityObj, "food_supply");
                double woodSupply = getDoubleSafe(cityObj, "wood_supply");
                double metalSupply = getDoubleSafe(cityObj, "metal_supply");
                double stoneSupply = getDoubleSafe(cityObj, "stone_supply");
                double textileSupply = getDoubleSafe(cityObj, "textile_supply");
                double alcoholSupply = getDoubleSafe(cityObj, "alcohol_supply");
                double technologySupply = getDoubleSafe(cityObj, "technology_supply");

                LOGGER.info(
                        "City update for {} | Treasury: {}g {}s {}c | Supplies: food={} wood={} metal={} stone={} textile={} alcohol={} tech={}",
                        cityName, gold, silver, copper,
                        foodSupply, woodSupply, metalSupply, stoneSupply,
                        textileSupply, alcoholSupply, technologySupply
                );

                // Execute server-side update
                server.execute(() -> {
                    CityManager cityManager = CityManager.get(overworld);
                    City city = cityManager.getCity(cityName);

                    if (city == null) {
                        LOGGER.warn("City {} not found in CityManager. Creating new entry.", cityName);
                        cityManager.addCity(cityName);
                        city = cityManager.getCity(cityName);
                    }

                    CityInventory inv = city.getInventory();
                    inv.setManager(cityManager);
                    inv.updateSupplies(foodSupply, woodSupply, metalSupply, stoneSupply, textileSupply, alcoholSupply, technologySupply);
                    inv.updateTreasury(gold, silver, copper);
                    cityManager.setDirty();
                    LOGGER.info("Updated city {} supplies in {}", cityName, overworld.dimension().location());

                    // Now update all trader spawn entities visually
                    for (ServerLevel level : server.getAllLevels()) {
                        int viewDistance = level.getServer().getPlayerList().getViewDistance();

                        for (ServerPlayer player : level.players()) {
                            ChunkPos playerChunk = player.chunkPosition();

                            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                                    int cx = playerChunk.x + dx;
                                    int cz = playerChunk.z + dz;

                                    if (!level.hasChunk(cx, cz)) continue;
                                    var chunk = level.getChunk(cx, cz);

                                    chunk.getBlockEntities().values().forEach(be -> {
                                        if (be instanceof TraderSpawnBlockEntity spawn &&
                                                spawn.getCityName().equalsIgnoreCase(cityName)) {

                                            spawn.applyCityUpdate((int) foodSupply, totalTreasury); 
                                            spawn.setSupplyLevels(
                                                    foodSupply, woodSupply, metalSupply,
                                                    stoneSupply, textileSupply, alcoholSupply, technologySupply
                                            );
                                        }
                                    });
                                }
                            }
                        }
                    }
                }); // end execute()
            } // end for cities

            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"ok\"}");

        } catch (Exception e) {
            LOGGER.error("Error handling /api/population_update", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"Server exception\"}");
        }
    }

    LOGGER.warn("Unhandled request: {}", session.getUri());
    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
}

private double getDoubleSafe(JsonObject obj, String key) {
    return obj.has(key) ? obj.get(key).getAsDouble() : 0.0;
}

}
