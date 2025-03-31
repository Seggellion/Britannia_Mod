package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.SwordRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.google.gson.JsonArray;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import com.seggellion.britannia_mod.util.ToolQualityUtils;

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

        for (Map.Entry<String, Double> entry : metalSupplyMap.entrySet()) {
            String metalType = entry.getKey();
            double supplyAmount = entry.getValue();

            if (supplyAmount <= 10) continue;

            UOMetalToolMaterial matEnum = UOMetalToolMaterial.getMaterialByName(metalType);
            if (matEnum == null) continue;

            // Tech level conditions
            if (techSupply >= 0) {
                tradeItems.add(SwordRegistry.createVikingSword(matEnum, techSupply));
                tradeItems.add(ToolRegistry.createPickaxe(matEnum, techSupply));

            }
            if (techSupply >= 500 && supplyAmount >= 5) {
        //      tradeItems.add(createWeapon(SwordRegistry.getLongsword(matEnum), techSupply));
            }
            if (techSupply >= 800 && supplyAmount >= 10) {
            //    tradeItems.add(createWeapon(SwordRegistry.getBroadsword(matEnum), techSupply));
            }

            // Optional logging for each generated item
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
                } else {
                }
            } else {
            }
        }
        return metalSupplyMap;
    }


    private static ItemStack createWeapon(DeferredHolder<Item, QualitySwordItem> holder, int techSupply) {
        if (holder == null) return ItemStack.EMPTY;
        // Create the item stack
        ItemStack swordStack = new ItemStack(holder.get());
        // Set quality
        int quality = determineQuality(techSupply);
        QualitySwordItem.setQuality(swordStack, quality);
        return swordStack;
    }


    private static int determineQuality(int techSupply) {
        if (techSupply < 500) return 2;  // Low
        if (techSupply < 800) return 3;  // Medium
        return 4;                        // High
    }
}
