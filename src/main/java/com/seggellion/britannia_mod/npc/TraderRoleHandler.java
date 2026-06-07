package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.economy.CommodityMapping;
import com.seggellion.britannia_mod.economy.CommodityMappings;
import com.seggellion.britannia_mod.economy.CommodityUnit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class TraderRoleHandler extends AbstractSellTraderRoleHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TraderRoleHandler(String role, String city) {
        super(role, city);
    }

    @Override
    protected JsonArray collectSellableInventory(Player player) {
        String normalizedRole = role.toLowerCase();
        LOGGER.info("Generic trader role handler loading role={}", role);

        if (normalizedRole.contains("wood") || normalizedRole.contains("lumber")) {
            return collectWoodFromInventory(player);
        } else if (normalizedRole.contains("fish")) {
            return collectFishFromInventory(player);
        } else if (normalizedRole.contains("ore") || normalizedRole.contains("metal") || normalizedRole.contains("miner")) {
            return collectOreFromInventory(player);
        } else if (normalizedRole.contains("stone")) {
            return collectSimpleCommodities(player, "stone", "blocks");
        } else if (normalizedRole.contains("grain")) {
            return collectSimpleCommodities(player, "grain", "");
        } else if (normalizedRole.contains("produce") || normalizedRole.contains("costermonger")) {
            return collectSimpleCommodities(player, "produce", "");
        } else if (normalizedRole.contains("fur") || normalizedRole.contains("leather")) {
            return collectFurLeatherFromInventory(player);
        } else if (normalizedRole.contains("hunter") || normalizedRole.contains("butcher") || normalizedRole.contains("meat")) {
            return collectSimpleCommodities(player, "meat", "");
        }
        return collectGenericInventory(player);
    }

    private JsonArray collectWoodFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof WeightedWoodItem woodItem)) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", woodItem.getWoodType(stack));
            j.addProperty("commodity_key", woodItem.getWoodType(stack));
            j.addProperty("category", "wood");
            j.addProperty("subcategory", "logs");
            j.addProperty("weight", woodItem.getWeight(stack));
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private JsonArray collectFishFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof WeightedFishItem fishItem)) continue;
            String fishKey = CommodityMappings.fishCommodityKey(fishItem.getFishType(stack));

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", fishKey);
            j.addProperty("commodity_key", fishKey);
            j.addProperty("category", "fish");
            j.addProperty("subcategory", "raw");
            j.addProperty("weight", fishItem.getWeight(stack));
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private JsonArray collectOreFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof PurityOreItem oreItem)) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", oreItem.getOreType(stack));
            j.addProperty("commodity_key", oreItem.getOreType(stack));
            j.addProperty("category", "ore");
            j.addProperty("subcategory", "raw");
            j.addProperty("purity", oreItem.getPurity(stack));
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private JsonArray collectSimpleCommodities(Player player, String category, String fallbackSubcategory) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (category.equals("stone") && addGradeStoneCommodity(arr, stack)) continue;

            String id = itemId(stack);
            CommodityMapping commodity = CommodityMappings.forStack(stack).orElse(null);
            if (commodity == null || !commodity.category().equals(category)) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", id);
            j.addProperty("item_name", commodity.itemName());
            j.addProperty("commodity_key", commodity.itemName());
            j.addProperty("category", commodity.category());
            j.addProperty("subcategory", commodity.subcategory().isBlank() ? fallbackSubcategory : commodity.subcategory());
            j.addProperty("quantity", stack.getCount());
            if (commodity.unit() == CommodityUnit.WEIGHT) {
                double weight = stack.getItem() instanceof WeightedCommodityItem
                        ? WeightedCommodityItem.getWeight(stack) * stack.getCount()
                        : stack.getCount();
                j.addProperty("weight", weight);
            }
            arr.add(j);
        }
        return arr;
    }

    private boolean addGradeStoneCommodity(JsonArray arr, ItemStack stack) {
        if (!(stack.getItem() instanceof GradeStoneItem stoneItem)) return false;

        String rawStoneType = stoneItem.getStoneType(stack);
        String stoneType = CommodityMappings.stoneCommodityKey(rawStoneType).orElse(null);
        if (stoneType == null) {
            LOGGER.warn("Stone trader ignored unsupported GradeStoneItem StoneType={}", rawStoneType);
            return true;
        }

        JsonObject j = new JsonObject();
        j.addProperty("item_id", itemId(stack));
        j.addProperty("item_name", stoneType);
        j.addProperty("commodity_key", CommodityMappings.stoneCommodityIdentityKey(stoneType));
        j.addProperty("category", "stone");
        j.addProperty("subcategory", "blocks");
        j.addProperty("quantity", stack.getCount());
        j.addProperty("weight", stack.getCount());
        arr.add(j);
        return true;
    }

    private JsonArray collectFurLeatherFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            CommodityMapping commodity = CommodityMappings.forStack(stack).orElse(null);
            if (commodity == null || (!commodity.category().equals("fur") && !commodity.category().equals("leather"))) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", commodity.itemName());
            j.addProperty("commodity_key", commodity.itemName());
            j.addProperty("category", commodity.category());
            j.addProperty("subcategory", commodity.subcategory());
            j.addProperty("quantity", stack.getCount());
            if (commodity.unit() == CommodityUnit.WEIGHT) {
                double weight = stack.getItem() instanceof WeightedCommodityItem
                        ? WeightedCommodityItem.getWeight(stack) * stack.getCount()
                        : stack.getCount();
                j.addProperty("weight", weight);
            }
            arr.add(j);
        }
        return arr;
    }

    private JsonArray collectGenericInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

}
