package com.seggellion.britannia_mod.market;

import com.seggellion.britannia_mod.inventory.CityInventory;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



/**
 * MarketManager that handles both fish items and wood items.
 * Price adjusts based on total weight supply in a city’s inventory.
 */
public class MarketManager {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Price cap: once supply (in stones) >= this, price becomes 0. */
    private static final double MAX_SUPPLY_CAP = 1000.0;

    /**
     * A simple map from "itemName" => basePrice. 
     * You can unify fish & wood names, e.g. "cod", "salmon", "oak", "spruce", etc.
     */
    private static final Map<String, Double> basePrices = new HashMap<>();

    /**
     * For each city, we store a map: itemName => current adjusted price.
     * If no city is present, we create it (or fallback to basePrices).
     */
    private static final Map<String, Map<String, Double>> cityPrices = new HashMap<>();

    static {
        // Example base fish prices (stones-based):
        basePrices.put("cod",        0.4);
        basePrices.put("salmon",     1.6);
        basePrices.put("tuna",       1.5);
        basePrices.put("trout",      0.5);
        basePrices.put("swordfish",  2.6);

        // Example wood base prices (stones-based). 
        // You can choose any values that make sense for your economy.
        basePrices.put("oak",       1.0);
        basePrices.put("spruce",    1.1);
        basePrices.put("birch",     1.0);
        basePrices.put("jungle",    1.2);
        basePrices.put("acacia",    1.1);
        basePrices.put("dark_oak",  1.3);
        basePrices.put("mangrove",  1.2);
    }

    //=====================================================
    // Initialization / Basic Access
    //=====================================================

    /** Initialize market prices for a city if not present. */
    public static void initializeCity(String cityName) {
        if (!cityPrices.containsKey(cityName)) {
            // Start them at the base prices
            cityPrices.put(cityName, new HashMap<>(basePrices));
            LOGGER.info("Initialized market prices for city: {}", cityName);
        }
    }

    /**
     * Get the current (already-adjusted) price for itemName in cityName.
     * If no city is found, returns 0.0.
     */
    public static double getMarketPrice(String cityName, String itemName) {
        Map<String, Double> priceMap = cityPrices.get(cityName);
        if (priceMap == null) {
            LOGGER.warn("No city price map found for '{}'. Returning 0.", cityName);
            return 0.0;
        }
        return priceMap.getOrDefault(itemName, 0.0);
    }

    /** Get the entire city price map for debugging or display. */
    public static Map<String, Double> getCityPrices(String cityName) {
        return cityPrices.getOrDefault(cityName, new HashMap<>());
    }

    //=====================================================
    // Supply and Demand Logic
    //=====================================================

    /**
     * Recalculate prices for all known items in city inventory.
     * 
     * - If supply >= MAX_SUPPLY_CAP => price = 0
     * - If supply == 0 => price = basePrice * 1.05
     * - Else => linear interpolation from (basePrice * 1.05) down to 0 as supply approaches 1000.
     * 
     * This will update the cityPrices map so future calls to getMarketPrice() reflect changes.
     */
    public static void adjustPrices(String cityName, CityInventory cityInventory) {
        // Ensure city is in cityPrices
        Map<String, Double> adjustedPriceMap =
                cityPrices.computeIfAbsent(cityName, k -> new HashMap<>(basePrices));

        // We'll iterate over your commodityWeights structure to see what's in the city.
        // commodityWeights:  category -> subcategory -> itemName -> weight (double)
        Map<String, Map<String, Map<String, Double>>> weights = cityInventory.getCommodityWeights();

        LOGGER.info("Adjusting prices in city '{}' based on supply. Base price & formula are used for recognized items.", cityName);

        // Go through each category/subcategory 
        for (Map.Entry<String, Map<String, Map<String, Double>>> catEntry : weights.entrySet()) {
            String category = catEntry.getKey();  // e.g. "food" or "wood"
            Map<String, Map<String, Double>> subMap = catEntry.getValue();

            for (Map.Entry<String, Map<String, Double>> subEntry : subMap.entrySet()) {
                String subcategory = subEntry.getKey();  // e.g. "fish" or "logs"
                Map<String, Double> itemMap = subEntry.getValue();

                for (Map.Entry<String, Double> itemEntry : itemMap.entrySet()) {
                    String itemName = itemEntry.getKey();   // e.g. "cod" or "oak"
                    double supplyInStones = itemEntry.getValue();  // total weight

                    // If we do not track a base price for itemName, skip
                    if (!basePrices.containsKey(itemName)) {
                        LOGGER.debug("No basePrice known for '{}'. Skipping price adjust in city '{}'.", itemName, cityName);
                        continue;
                    }

                    double basePrice = basePrices.get(itemName);

                    double newPrice;
                    if (supplyInStones >= MAX_SUPPLY_CAP) {
                        // Price is 0 if supply is huge
                        newPrice = 0.0;
                    } else if (supplyInStones <= 0.00001) {
                        // If effectively no supply, price is base * 1.05
                        newPrice = basePrice * 1.05;
                    } else {
                        // Linear interpolation from (basePrice * 1.05) at 0 supply
                        // down to 0 at supply = MAX_SUPPLY_CAP
                        // i.e. newPrice = (basePrice*1.05) * (1 - supplyInStones / MAX_SUPPLY_CAP)
                        double factor = 1.0 - (supplyInStones / MAX_SUPPLY_CAP);
                        if (factor < 0.0) factor = 0.0;  // clamp
                        newPrice = (basePrice * 1.05) * factor;
                    }

                    // Store the updated price in the city map
                    adjustedPriceMap.put(itemName, newPrice);
                    LOGGER.debug("Adjusted price for '{}' in city '{}': supply={}, newPrice={}", itemName, cityName, supplyInStones, newPrice);
                }
            }
        }
    }
}