package com.seggellion.britannia_mod.market;

import com.seggellion.britannia_mod.inventory.CityInventory;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



public class MarketManager {
    private static final Map<String, Double> basePrices = new HashMap<>();
    private static final Map<String, Map<String, Double>> cityPrices = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();
    static {
        // Base prices for each fish type
        basePrices.put("cod", 4.8);
        basePrices.put("salmon", 16.1);
        basePrices.put("tuna", 15.4);
        basePrices.put("trout", 5.9);
        basePrices.put("swordfish", 26.6);
    }

    // Initialize city pricing
    public static void initializeCity(String cityName) {
    if (!cityPrices.containsKey(cityName)) {
        cityPrices.put(cityName, new HashMap<>(basePrices));
        System.out.println("Initialized market prices for city: " + cityName);
    }

     //   cityPrices.putIfAbsent(cityName, new HashMap<>(basePrices));
    }

    // Get the current market price for a specific fish in a city
   // public static double getMarketPrice(String cityName, String fishType) {
   //     return cityPrices.getOrDefault(cityName, new HashMap<>()).getOrDefault(fishType, 0.0);
  //  }
    
public static double getMarketPrice(String cityName, String fishType) {
    Map<String, Double> cityPriceMap = cityPrices.get(cityName);
    if (cityPriceMap == null) {
        LOGGER.warn("No prices found for city: {}", cityName);
        return 0.0;
    }
    double price = cityPriceMap.getOrDefault(fishType, 0.0);
    LOGGER.info("Market price fetched for city {} and fish {}: {}", cityName, fishType, price);
    return price;
}


public static Map<String, Double> getCityPrices(String cityName) {
    return cityPrices.getOrDefault(cityName, new HashMap<>());
}



    // Adjust prices based on stock levels in a city
    public static void adjustPrices(String cityName, CityInventory cityInventory) {
            LOGGER.info("Adjusting prices for city: {}", cityName);

        Map<String, Integer> foodStocks = cityInventory.getAllCommodities()
                                                       .getOrDefault("food", new HashMap<>())
                                                       .getOrDefault("fish", new HashMap<>());

        Map<String, Double> prices = cityPrices.computeIfAbsent(cityName, k -> new HashMap<>(basePrices));

        for (Map.Entry<String, Integer> entry : foodStocks.entrySet()) {
            String fishType = entry.getKey();
            int quantity = entry.getValue();
            double basePrice = basePrices.getOrDefault(fishType, 0.0);

            // Adjust prices based on stock
            double priceChange = (quantity < 50 ? 0.1 : -0.1) * (50 - quantity) / 50.0;
            double newPrice = prices.get(fishType) + priceChange;

            // Ensure prices remain within bounds
            newPrice = Math.max(basePrice * 0.5, Math.min(newPrice, basePrice * 2.0));

            prices.put(fishType, newPrice);
        }
    }
}
