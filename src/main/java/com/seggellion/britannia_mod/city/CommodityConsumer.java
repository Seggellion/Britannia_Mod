package com.seggellion.britannia_mod.city;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.util.CityAPITokenData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.OutputStream;
import com.seggellion.britannia_mod.entity.EntityFishMerchant;
import java.net.HttpURLConnection;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.io.InputStream;
import com.seggellion.britannia_mod.config.ModConfig;

import java.io.IOException;

public class CommodityConsumer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int FOOD_CONSUMPTION_INTERVAL = 6000; // 5 minutes at 20 TPS
    private static final int STARVATION_CHECK_INTERVAL = 1200; // 1 minute at 20 TPS

    private static int foodConsumptionTickCounter = 0;
    private static int starvationNotificationTickCounter = 0;

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        ServerLevel serverLevel = server.getLevel(Level.OVERWORLD); // Replace "overworld" if using custom dimensions

        if (serverLevel == null) {
            return;
        }

        handleFoodConsumption(serverLevel);
        // handleStarvationNotifications(serverLevel);
    }

private static void handleFoodConsumption(ServerLevel serverLevel) {
    foodConsumptionTickCounter++;
    if (foodConsumptionTickCounter >= FOOD_CONSUMPTION_INTERVAL) {

        try {
 
            URL url = new URL( ModConfig.API_BASE_URL + "cities/consume_commodities");
    
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            String apiToken = data.getApiToken(); // Might be empty if not set
            // Include the token in a header, for example, "Authorization: Bearer <token>"
            if (!apiToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            }
        

            // Optional payload (e.g. shard info)
            JsonObject payload = new JsonObject();
            payload.addProperty("shard", "Britannia");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Parse the updated city states
                JsonObject response = parseJsonResponse(conn.getInputStream());
                JsonArray citiesArray = response.getAsJsonArray("cities");

                // For each city, check if it's starving, then update fisherman NBT
                for (int i = 0; i < citiesArray.size(); i++) {
                    JsonObject cityObj = citiesArray.get(i).getAsJsonObject();
                    String cityName = cityObj.get("name").getAsString();
                    double foodSupply = cityObj.get("food_supply").getAsDouble();

                    boolean isStarving = (foodSupply <= 0.0);
                    updateFishermanEntities(serverLevel, cityName, isStarving);
                }

            } else {
            }
            conn.disconnect();
        } catch (Exception e) {
        }

        foodConsumptionTickCounter = 0;
    }
}

/**
 * Parses an InputStream to a JsonObject using GSON.
 */
private static JsonObject parseJsonResponse(InputStream stream) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
    }
}

/**
 * Updates fisherman NPCs belonging to a given city, setting or removing a "starving" tag in NBT.
 */
private static void updateFishermanEntities(ServerLevel serverLevel, String cityName, boolean isStarving) {
    // Example: If your CityManager can map city -> associated NPCs, you can do:
    List<UUID> npcs = CityManager.get(serverLevel).getCity(cityName).getInventory().getAssociatedNpcs();

    for (UUID uuid : npcs) {
        Entity entity = serverLevel.getEntity(uuid);
        if (entity instanceof EntityFishMerchant fishMerchant) {
            // This example uses persistent data container to store the "starving" tag
            fishMerchant.getPersistentData().putBoolean("starving", isStarving);

        }
    }
}

    private static void handleStarvationNotifications(ServerLevel serverLevel) {
        starvationNotificationTickCounter++;
        if (starvationNotificationTickCounter >= STARVATION_CHECK_INTERVAL) {

            // Call the Rails API to check for starving cities
            try {
                URL url = new URL(ModConfig.API_BASE_URL + "cities/starving");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Parse the response to determine which cities are starving
                    JsonArray starvingCities = CityDataSync.parseStarvingCitiesResponse(conn.getInputStream());

                    for (int i = 0; i < starvingCities.size(); i++) {
                        JsonObject city = starvingCities.get(i).getAsJsonObject();
                        String cityName = city.get("name").getAsString();
                        notifyPlayersNearMerchants(serverLevel, cityName);
                    }

                } else {
                }
                conn.disconnect();
            } catch (Exception e) {
            }

            starvationNotificationTickCounter = 0;
        }
    }

    private static void notifyPlayersNearMerchants(ServerLevel serverLevel, String cityName) {
        List<UUID> merchants = CityDataSync.getAssociatedMerchants(cityName);
        for (UUID merchantUuid : merchants) {
            Entity merchant = serverLevel.getEntity(merchantUuid);
            if (merchant == null) {
                continue;
            }

            double radius = 20.0;
            List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(Player.class, merchant.getBoundingBox().inflate(radius));

            for (Player player : nearbyPlayers) {
                player.displayClientMessage(
                        Component.literal(cityName + " is starving! Please sell them some food."),
                        true // Display on action bar
                );
            }
        }
    }
}
