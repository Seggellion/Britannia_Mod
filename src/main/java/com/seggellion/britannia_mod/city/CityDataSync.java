package com.seggellion.britannia_mod.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.Map;
import java.util.Scanner;

/**
 * Utility class to send city + commodity + leaderboard data to a Rails endpoint.
 */
public class CityDataSync {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Example: You might store your Rails endpoint here
    //private static final String SYNC_URL = "http://127.0.0.1:3000/api/cities/sync"; 

private static final String SYNC_URL = "https://ultimacraft-c079bdcd2cd0.herokuapp.com/api/cities/sync"; 
    
    // Replace with your actual server/endpoint

    /**
     * Called (for example) every so often, or when city data changes significantly.
     * This method tries to gather city data from CityManager and a PlayerDataManager,
     * then POST it as JSON to your Rails server's /api/cities/sync endpoint.
     */
    public static void postCityDataToRails(ServerLevel serverLevel, String cityName) {
        try {
            CityManager cityManager = CityManager.get(serverLevel);
            City city = cityManager.getCity(cityName);
            if (city == null) {
                LOGGER.warn("City not found: {}", cityName);
                return;
            }
            
            // 1. Build JSON for the city.
            JsonObject cityRoot = new JsonObject();
            cityRoot.addProperty("city_name", city.getName());  // "Britain"
            
            // Example from city inventory
            CityInventory inv = city.getInventory();
            cityRoot.addProperty("population", inv.getPopulation());
            cityRoot.addProperty("is_starving", inv.isStarving());

            // NEW: food_supply, wood_supply
            double currentFood = inv.getCategoryTotalWeight("food"); 
            double currentWood = inv.getCategoryTotalWeight("wood");
            cityRoot.addProperty("food_supply", currentFood);  // <--- new field
            cityRoot.addProperty("wood_supply", currentWood);  // <--- new field

            // 2. Collect commodity data
            JsonArray commoditiesArray = new JsonArray();
            Map<String, Map<String, Map<String, Integer>>> allCommodities = inv.getAllCommodities();
            for (Map.Entry<String, Map<String, Map<String, Integer>>> categoryEntry : allCommodities.entrySet()) {
                String category = categoryEntry.getKey();
                Map<String, Map<String, Integer>> subcats = categoryEntry.getValue();
                
                for (Map.Entry<String, Map<String, Integer>> subcatEntry : subcats.entrySet()) {
                    String subcat = subcatEntry.getKey();
                    Map<String, Integer> items = subcatEntry.getValue();
                    
                    for (Map.Entry<String, Integer> itemEntry : items.entrySet()) {
                        String itemName = itemEntry.getKey();
                        int quantity = itemEntry.getValue();
                        
                        // Optionally fetch the weight from commodityWeights
                        double weight = 0.0;
                        if (inv.getCommodityWeights().containsKey(category) 
                            && inv.getCommodityWeights().get(category).containsKey(subcat) 
                            && inv.getCommodityWeights().get(category).get(subcat).containsKey(itemName)) {
                            weight = inv.getCommodityWeights()
                                        .get(category)
                                        .get(subcat)
                                        .get(itemName);
                        }
                        
                        JsonObject cObj = new JsonObject();
                        cObj.addProperty("category", category);
                        cObj.addProperty("subcategory", subcat);
                        cObj.addProperty("item_name", itemName);
                        cObj.addProperty("quantity", quantity);
                        cObj.addProperty("weight", weight);
                        commoditiesArray.add(cObj);
                    }
                }
            }
            cityRoot.add("commodities", commoditiesArray);

            // 3. Leaderboard data. We'll fetch from PlayerDataManager
            JsonArray leaderboardArray = new JsonArray();
            PlayerDataManager playerDataManager = PlayerDataManager.get(serverLevel);
            // Suppose we store total fish or wood contributions in PlayerData
            // For each player, build a JSON element
            for (PlayerData data : playerDataManager.getAllPlayers()) {
            String playerName = PlayerData.getPlayerName(serverLevel, data.getPlayerUUID());

                String playerUuid = data.getPlayerUUID().toString();


                for (Map.Entry<String, Double> e : data.getTotalContributions().entrySet()) {
                    String commodityName = e.getKey();
                    double totalContrib = e.getValue();
                    double biggest = data.getBiggestFish().getOrDefault(commodityName, 0.0);
                    
                    String commodityType = "unknown";
                        if (commodityName.contains("fish") || commodityName.contains("cod") ||
                            commodityName.contains("swordfish") || 
                            commodityName.contains("salmon") || commodityName.contains("tuna")) {
                            commodityType = "food";
                        } else if (commodityName.contains("log") || commodityName.contains("oak") ||
                        commodityName.contains("spruce") || commodityName.contains("pine") ||
                                commodityName.contains("wood")) {
                            commodityType = "wood";
                        }

                    JsonObject lbObj = new JsonObject();
                    lbObj.addProperty("player_uuid", playerUuid);
                    lbObj.addProperty("player_name", playerName); 
                    lbObj.addProperty("commodity_name", commodityName);
                    lbObj.addProperty("commodity_type", commodityType);  
                    lbObj.addProperty("total_contribution", totalContrib);
                    lbObj.addProperty("biggest_single", biggest);
                    leaderboardArray.add(lbObj);
                }
            }
            cityRoot.add("leaderboard", leaderboardArray);

            // 4. Send the JSON
            postJsonToEndpoint(cityRoot, SYNC_URL);

        } catch (Exception e) {
            LOGGER.error("Error posting city data to Rails: ", e);
        }
    }

    /**
     * Helper method to handle the actual HTTP POST.
     */
    private static void postJsonToEndpoint(JsonObject root, String endpointUrl) throws Exception {
        String jsonPayload = root.toString();

        LOGGER.info("Posting JSON to endpoint: {}", endpointUrl);
        LOGGER.info("Payload: {}", jsonPayload);

        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        LOGGER.info("Response Code: {}", responseCode);

        // Read response body (if any)
        try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine());
            }
            LOGGER.info("Rails server response: {}", sb.toString());
        } catch (Exception ex) {
            LOGGER.warn("No response body or error: {}", ex.getMessage());
        }

        conn.disconnect();
    }
}
