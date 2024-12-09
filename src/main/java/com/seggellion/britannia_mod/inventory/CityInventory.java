package com.seggellion.britannia_mod.inventory;

import com.seggellion.britannia_mod.entity.EntityFishMerchant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CityInventory {

    private static final Logger LOGGER = LogManager.getLogger();
    
private boolean isStarving = false;

public boolean isStarving() {
    return isStarving;
}

    private String cityName; // Store the name of the city this inventory belongs to

    // Map structure: Category -> Subcategory -> Item -> Quantity (int)
    private Map<String, Map<String, Map<String, Integer>>> commodities = new HashMap<>();

    // Parallel structure for weights:
    // Category -> Subcategory -> Item -> Weight (double)
    private Map<String, Map<String, Map<String, Double>>> commodityWeights = new HashMap<>();

    private int npcCount = 0; // Population count for this city

    private final List<EntityFishMerchant> associatedMerchants = new ArrayList<>();

    public CityInventory(String cityName) {
        this.cityName = cityName;
        initializeCity();
    }

    private void initializeCity() {
        commodities.putIfAbsent("food", new HashMap<>());
        commodityWeights.putIfAbsent("food", new HashMap<>());
        LOGGER.warn("Initialized city {} with empty inventory.", cityName);
    }

     // Returns a map of subcategories to their total weights within a category
    public Map<String, Double> getAllCategoryWeights(String category) {
        Map<String, Double> categoryWeights = new HashMap<>();
        Map<String, Map<String, Double>> subcategories = commodityWeights.getOrDefault(category, new HashMap<>());
        for (Map.Entry<String, Map<String, Double>> subcategoryEntry : subcategories.entrySet()) {
            String subcategory = subcategoryEntry.getKey();
            double totalWeight = subcategoryEntry.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
            categoryWeights.put(subcategory, totalWeight);
        }
        return categoryWeights;
    }

    // Returns the total weight for a specific category
    public double getCategoryTotalWeight(String category) {
        return commodityWeights.getOrDefault(category, new HashMap<>())
                .values().stream()
                .flatMap(map -> map.values().stream())
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    // Returns the total quantity for a specific subcategory within a category
    public int getSubcategoryTotal(String category, String subcategory) {
        return commodities.getOrDefault(category, new HashMap<>())
                .getOrDefault(subcategory, new HashMap<>())
                .values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public void associateNpc(EntityFishMerchant merchant) {
        if (!associatedMerchants.contains(merchant)) {
            associatedMerchants.add(merchant);
            npcCount++;
            LOGGER.warn("Merchant {} associated to city {}. New population: {}", merchant.getUUID(), cityName, npcCount);
            setDirty(); // Mark data as changed if using persistence
        }
    }

    public void removeMerchant(EntityFishMerchant merchant) {
        if (associatedMerchants.remove(merchant)) {
            npcCount = Math.max(0, npcCount - 1);
            LOGGER.warn("Merchant {} removed from city {}. New population: {}", merchant.getUUID(), cityName, npcCount);
            setDirty(); // Mark data as changed for persistence
        } else {
            LOGGER.warn("Merchant {} not found in associated merchants", merchant.getUUID());
        }
    }

    public void consumeFood() {
        LOGGER.warn("Food is starting to be consumed for city {}, population: {}", cityName, npcCount);
        double baseConsumptionRate = 0.1;
        double totalConsumption = npcCount * baseConsumptionRate;
        LOGGER.warn("Total consumption for city {}: {}", cityName, totalConsumption);

        Map<String, Map<String, Double>> foodWeights = commodityWeights.getOrDefault("food", new HashMap<>());
        LOGGER.warn("Current food weights for city {}: {}", cityName, foodWeights);

        if (foodWeights.isEmpty()) {
            LOGGER.warn("No food weights available for city {} to consume.", cityName);
            return;
        }

        for (Map.Entry<String, Map<String, Double>> categoryEntry : foodWeights.entrySet()) {
            String subcategory = categoryEntry.getKey();
            Map<String, Double> items = categoryEntry.getValue();

            for (Map.Entry<String, Double> itemEntry : items.entrySet()) {
                String itemName = itemEntry.getKey();
                double currentWeight = itemEntry.getValue();

                LOGGER.warn("Processing consumption for {} in subcategory '{}': current weight = {}", itemName, subcategory, currentWeight);

                // Calculate consumption
                double consumedWeight = Math.min(currentWeight, totalConsumption);
                double newWeight = currentWeight - consumedWeight;
                newWeight = Math.max(newWeight, 0.0); // Prevent negative weights
                items.put(itemName, newWeight);

                LOGGER.warn("Consumed {} of {} in category 'food'. New weight: {}", consumedWeight, itemName, newWeight);
                totalConsumption -= consumedWeight;
                LOGGER.warn("Remaining consumption after consuming {}: {}", consumedWeight, totalConsumption);

                // Optionally, update quantities if you want to track them
                if (commodities.containsKey("food") && commodities.get("food").containsKey("fish") && commodities.get("food").get("fish").containsKey(itemName)) {
                    int currentQuantity = commodities.get("food").get("fish").get(itemName);
                    int consumedQuantity = (int) Math.ceil(consumedWeight); // Adjust based on your conversion logic
                    int newQuantity = Math.max(currentQuantity - consumedQuantity, 0);
                    commodities.get("food").get("fish").put(itemName, newQuantity);
                    LOGGER.warn("Updated quantity of {}: {}", itemName, newQuantity);
                }

                if (totalConsumption <= 0) break;
            }
            if (totalConsumption <= 0) break;
        }
    // Compute total food weight after consumption
        double totalFoodWeight = commodityWeights.getOrDefault("food", new HashMap<>())
                                .values().stream()
                                .flatMap(map -> map.values().stream())
                                .mapToDouble(Double::doubleValue)
                                .sum();

        // Update the isStarving flag based on totalFoodWeight
        if (totalFoodWeight == 0.0 && !isStarving) {
            isStarving = true;
            LOGGER.warn("City {} is now starving.", cityName);
        } else if (totalFoodWeight > 0.0 && isStarving) {
            isStarving = false;
            LOGGER.warn("City {} is no longer starving.", cityName);
        }

        LOGGER.warn("Finished consuming food for city {}", cityName);
    }

    // Save data to NBT
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CityName", cityName);

        // Save commodities
        CompoundTag commoditiesTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Map<String, Integer>>> categoryEntry : commodities.entrySet()) {
            String category = categoryEntry.getKey();
            CompoundTag subcategoriesTag = new CompoundTag();
            for (Map.Entry<String, Map<String, Integer>> subcategoryEntry : categoryEntry.getValue().entrySet()) {
                String subcategory = subcategoryEntry.getKey();
                CompoundTag itemsTag = new CompoundTag();
                for (Map.Entry<String, Integer> itemEntry : subcategoryEntry.getValue().entrySet()) {
                    String itemName = itemEntry.getKey();
                    int quantity = itemEntry.getValue();
                    itemsTag.putInt(itemName, quantity);
                }
                subcategoriesTag.put(subcategory, itemsTag);
            }
            commoditiesTag.put(category, subcategoriesTag);
        }
        tag.put("Commodities", commoditiesTag);

        // Save commodityWeights
        CompoundTag weightsTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Map<String, Double>>> categoryEntry : commodityWeights.entrySet()) {
            String category = categoryEntry.getKey();
            CompoundTag subcategoriesTag = new CompoundTag();
            for (Map.Entry<String, Map<String, Double>> subcategoryEntry : categoryEntry.getValue().entrySet()) {
                String subcategory = subcategoryEntry.getKey();
                CompoundTag itemsTag = new CompoundTag();
                for (Map.Entry<String, Double> itemEntry : subcategoryEntry.getValue().entrySet()) {
                    String itemName = itemEntry.getKey();
                    double weight = itemEntry.getValue();
                    itemsTag.putDouble(itemName, weight);
                }
                subcategoriesTag.put(subcategory, itemsTag);
            }
            weightsTag.put(category, subcategoriesTag);
        }
        tag.put("CommodityWeights", weightsTag);

        // Save population count
        tag.putInt("Population", npcCount);

        LOGGER.warn("Saved commodities, weights, and population for city {} to NBT.", cityName);
        return tag;
    }

    // Load data from NBT
    public void load(CompoundTag tag) {
        // Load city name
        this.cityName = tag.getString("CityName");

        // Load commodities
        commodities.clear();
        CompoundTag commoditiesTag = tag.getCompound("Commodities");
        for (String category : commoditiesTag.getAllKeys()) {
            CompoundTag subcategoriesTag = commoditiesTag.getCompound(category);
            Map<String, Map<String, Integer>> subcategories = new HashMap<>();
            for (String subcategory : subcategoriesTag.getAllKeys()) {
                CompoundTag itemsTag = subcategoriesTag.getCompound(subcategory);
                Map<String, Integer> items = new HashMap<>();
                for (String itemName : itemsTag.getAllKeys()) {
                    int quantity = itemsTag.getInt(itemName);
                    items.put(itemName, quantity);
                }
                subcategories.put(subcategory, items);
            }
            commodities.put(category, subcategories);
        }

        // Load commodityWeights
        commodityWeights.clear();
        if (tag.contains("CommodityWeights")) {
            CompoundTag weightsTag = tag.getCompound("CommodityWeights");
            for (String category : weightsTag.getAllKeys()) {
                CompoundTag subcategoriesTag = weightsTag.getCompound(category);
                Map<String, Map<String, Double>> subcategories = new HashMap<>();
                for (String subcategory : subcategoriesTag.getAllKeys()) {
                    CompoundTag itemsTag = subcategoriesTag.getCompound(subcategory);
                    Map<String, Double> items = new HashMap<>();
                    for (String itemName : itemsTag.getAllKeys()) {
                        double weight = itemsTag.getDouble(itemName);
                        items.put(itemName, weight);
                    }
                    subcategories.put(subcategory, items);
                }
                commodityWeights.put(category, subcategories);
            }
        }

        // Load population count
        this.npcCount = tag.getInt("Population");

        LOGGER.warn("Loaded commodities, weights, and population for city {} from NBT.", cityName);
    }

    public void clearPopulation() {
        // Option 1: Reset npcCount and clear associated merchants
        for (EntityFishMerchant merchant : new ArrayList<>(associatedMerchants)) {
            removeMerchant(merchant); // This method decrements npcCount and removes the merchant
        }
        // Alternatively, Option 2: Directly reset npcCount and clear the list without triggering remove logic
        this.associatedMerchants.clear();
        this.npcCount = 0;
        
        LOGGER.warn("Population for city {} has been cleared. New population: {}", cityName, npcCount);
        setDirty(); // Mark data as changed for persistence
    }

    // Method to add commodities
    public void addCommodity(String category, String subcategory, String itemName, int amount) {
        commodities
            .computeIfAbsent(category, k -> new HashMap<>())
            .computeIfAbsent(subcategory, k -> new HashMap<>())
            .merge(itemName, amount, Integer::sum);
        LOGGER.warn("Added {} of {} in category '{}', subcategory '{}'. New quantity: {}", amount, itemName, category, subcategory, commodities.get(category).get(subcategory).get(itemName));
    }

    // Method to add commodity weights
    public void addCommodityWeight(String category, String subcategory, String itemName, double weight) {
        commodityWeights
            .computeIfAbsent(category, k -> new HashMap<>())
            .computeIfAbsent(subcategory, k -> new HashMap<>())
            .merge(itemName, weight, Double::sum);
        LOGGER.warn("Added weight {} for {} in category '{}', subcategory '{}'. New weight: {}", weight, itemName, category, subcategory, commodityWeights.get(category).get(subcategory).get(itemName));
        setDirty(); // Mark data as changed if needed
    }

    // Getter for commodityWeights
    public Map<String, Map<String, Map<String, Double>>> getCommodityWeights() {
        return commodityWeights;
    }

    // Getter for associatedMerchants
    public List<EntityFishMerchant> getAssociatedMerchants() {
        return Collections.unmodifiableList(associatedMerchants);
    }

    // Getter for all commodities
    public Map<String, Map<String, Map<String, Integer>>> getAllCommodities() {
        return commodities;
    }

    // Getter for population
    public int getPopulation() {
        return npcCount;
    }


public void associateMerchant(EntityFishMerchant merchant) {
    associateNpc(merchant); // Delegate to associateNpc
}

    // Clear all commodities and weights
    public void clearAllCommodities() {
        this.commodities.clear();
        this.commodityWeights.clear();
        LOGGER.warn("Cleared all commodities and weights for city {}.", cityName);
        setDirty(); // Mark data as changed if using persistence
    }

    // Placeholder setDirty() method
    private void setDirty() {
        // Implement saving logic if required, e.g., marking the data as dirty for persistence
    }
}
