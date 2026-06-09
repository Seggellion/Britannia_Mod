package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.google.gson.JsonArray;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlacksmithTradeHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    public static List<ItemStack> getTradesForTechLevel(int techSupply, JsonArray metalSupply) {
        List<ItemStack> tradeItems = new ArrayList<>();
        Map<String, Double> metalSupplyMap = extractMetalTypes(metalSupply);

        if (metalSupplyMap.isEmpty()) {
            return tradeItems;
        }

        // Calculate quality once per generation cycle
        int weaponQuality = determineQuality(techSupply);

        for (Map.Entry<String, Double> entry : metalSupplyMap.entrySet()) {
            String metalType = entry.getKey();
            double supplyAmount = entry.getValue();

            if (supplyAmount <= 10) continue;

            UOMetalToolMaterial matEnum = UOMetalToolMaterial.getMaterialByName(metalType);
            if (matEnum == null) continue;

            // Tech level conditions
            if (techSupply >= 0) {
                // Use the new WeaponRegistry factory method
                tradeItems.add(WeaponRegistry.createWeapon(WeaponRegistry.VIKING_SWORD.get(), matEnum, weaponQuality));
                
                // Assuming ToolRegistry has a similar createTool factory, or you can leave it as is if untouched
                tradeItems.add(ToolRegistry.createPickaxe(matEnum, techSupply)); 
            }
            if (techSupply >= 500 && supplyAmount >= 5) {
                // To use this, just ensure LONGSWORD is added to your WeaponRegistry exactly like VIKING_SWORD
                // tradeItems.add(WeaponRegistry.createWeapon(WeaponRegistry.LONGSWORD.get(), matEnum, weaponQuality));
            }
            if (techSupply >= 800 && supplyAmount >= 10) {
                // To use this, just ensure BROADSWORD is added to your WeaponRegistry
                // tradeItems.add(WeaponRegistry.createWeapon(WeaponRegistry.BROADSWORD.get(), matEnum, weaponQuality));
            }
        }

        return tradeItems;
    }

    // Fix extractMetalTypes to handle nested arrays
    public static Map<String, Double> extractMetalTypes(JsonArray metalSupply) {
        Map<String, Double> metalSupplyMap = new HashMap<>();
        for (JsonElement element : metalSupply) {
            if (element.isJsonArray()) {
                JsonArray pair = element.getAsJsonArray();
                if (pair.size() == 2) {
                    String metalType = pair.get(0).getAsString().toLowerCase();
                    double quantity = pair.get(1).getAsDouble();
                    metalSupplyMap.put(metalType, quantity);
                }
            }
        }
        return metalSupplyMap;
    }

    private static int determineQuality(int techSupply) {
        if (techSupply < 500) return 2;  // Low
        if (techSupply < 800) return 3;  // Medium
        return 4;                        // High
    }
}