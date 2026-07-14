package com.seggellion.britannia_mod.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * LocalRecipes is a basic utility for storing
 * "item -> (material -> quantity)" mappings,
 * so you can compute item costs from your market_prices.
 *
 * Example usage:
 *   Map<String, Double> recipe = LocalRecipes.getRecipe("viking_sword_iron");
 *   if (recipe != null) {
 *       // compute total cost from "iron" => 2.0, "wood" => 1.0, etc.
 *   }
 */
public final class LocalRecipes {
    private static final String LEARNED_BLACKSMITHING = "BritanniaLearnedBlacksmithingRecipes";
    
private static final Logger LOGGER = LogManager.getLogger();
    /**
     * Main map:
     *   Key: the short item name/ID (e.g. "viking_sword_iron")
     *   Value: Another map (materialName -> quantityNeeded)
     */
    private static final Map<String, Map<String, Double>> RECIPE_MAP;

      static {
        // Initialize the recipe map
        Map<String, Map<String, Double>> recipeMap = new HashMap<>();

        // Viking Sword (Common Recipe Template)
        Map<String, Double> swordRecipe = new HashMap<>();
        swordRecipe.put("oak", 1.0);  // All swords need 1 wood (oak)
        
        // Different metals, same base recipe plus metal type
        recipeMap.put("viking_sword_iron", Map.of("iron", 2.0, "oak", 1.0));
        recipeMap.put("viking_sword_gold", Map.of("gold", 2.0, "oak", 1.0));
        recipeMap.put("viking_sword_valorite", Map.of("valorite", 2.0, "oak", 1.0));

        // Britannia Pickaxes (same base logic, different naming)
        recipeMap.put("pickaxe_iron", Map.of("iron", 4.0, "oak", 2.0));
        recipeMap.put("pickaxe_gold", Map.of("gold", 4.0, "oak", 2.0));
        recipeMap.put("pickaxe_valorite", Map.of("valorite", 4.0, "oak", 2.0));


        // Make the map immutable
        Map<String, Map<String, Double>> tempUnmodifiable = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : recipeMap.entrySet()) {
            tempUnmodifiable.put(
                entry.getKey(),
                Collections.unmodifiableMap(new HashMap<>(entry.getValue()))
            );
        }
        RECIPE_MAP = Collections.unmodifiableMap(tempUnmodifiable);
    }

    private LocalRecipes() {
        // Prevent instantiation
    }

    /** Extends the existing local-recipe service with persisted per-player unlock keys. */
    public static boolean hasLearned(ServerPlayer player, String recipeKey) {
        if (recipeKey == null || recipeKey.isBlank()) return true;
        return learnedBlacksmithing(player).contains(recipeKey);
    }

    public static boolean learnBlacksmithing(ServerPlayer player, String recipeKey) {
        if (recipeKey == null || recipeKey.isBlank() || hasLearned(player, recipeKey)) return false;
        ListTag list = player.getPersistentData().getList(LEARNED_BLACKSMITHING, 8);
        list.add(StringTag.valueOf(recipeKey));
        player.getPersistentData().put(LEARNED_BLACKSMITHING, list);
        return true;
    }

    public static Set<String> learnedBlacksmithing(ServerPlayer player) {
        ListTag list = player.getPersistentData().getList(LEARNED_BLACKSMITHING, 8);
        Set<String> result = new HashSet<>();
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return Collections.unmodifiableSet(result);
    }

    /**
     * Retrieves the recipe for a given item key (e.g. "viking_sword_iron").
     * Returns an unmodifiable map of material -> quantity.
     * If not found, returns an empty map.
     */
    public static Map<String, Double> getRecipe(String itemKey) {
        String cleanedKey = itemKey.replace("item.britannia_mod.", "").replace("item.minecraft.", "");
        return RECIPE_MAP.getOrDefault(cleanedKey, Collections.emptyMap());
    }

        public static Map<String, Double> getVikingSwordRecipe(String material) {
        return RECIPE_MAP.getOrDefault("viking_sword_" + material.toLowerCase(), Collections.emptyMap());
    }

    public static Map<String, Double> getPickaxeRecipe(String material) {
        return RECIPE_MAP.getOrDefault("pickaxe_" + material.toLowerCase(), Collections.emptyMap());
    }


    /**
     * Retrieves the deducted commodities and their total sum (checksum).
     *
     * @param itemKey The item name (e.g. "viking_sword_iron")
     * @param quantity The quantity being purchased
     * @return JsonObject containing "deductions" and "checksum"
     */
   public static JsonObject getDeductionsWithChecksum(String itemKey, int quantity) {
        JsonArray deductions = new JsonArray();
        double totalSum = 0.0;

        Map<String, Double> recipe = getRecipe(itemKey);

        for (Map.Entry<String, Double> entry : recipe.entrySet()) {
            String material = entry.getKey();
            double requiredQty = entry.getValue() * quantity;
            totalSum += requiredQty;

            JsonObject deduction = new JsonObject();
            deduction.addProperty("item_name", material);
            deduction.addProperty("quantity", requiredQty);
            deductions.add(deduction);
        }

        JsonObject result = new JsonObject();
        result.add("deductions", deductions);
        result.addProperty("checksum", totalSum);
        return result;
    }

}
