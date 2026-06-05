package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import net.minecraft.world.item.Items;
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
            return collectSimpleCommodities(player, "grains", "raw");
        } else if (normalizedRole.contains("produce") || normalizedRole.contains("costermonger")) {
            return collectSimpleCommodities(player, "produce", "raw");
        } else if (normalizedRole.contains("fur") || normalizedRole.contains("leather")) {
            return collectSimpleCommodities(player, "fur", "leather");
        } else if (normalizedRole.contains("hunter") || normalizedRole.contains("butcher") || normalizedRole.contains("meat")) {
            return collectSimpleCommodities(player, "meat", "raw");
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

            JsonObject j = new JsonObject();
            j.addProperty("item_id", itemId(stack));
            j.addProperty("item_name", fishItem.getFishType(stack));
            j.addProperty("commodity_key", fishItem.getFishType(stack));
            j.addProperty("category", "food");
            j.addProperty("subcategory", "fish");
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

            String id = itemId(stack);
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            CommodityClass commodity = classify(path, stack);
            if (commodity == null || !commodity.category().equals(category)) continue;

            JsonObject j = new JsonObject();
            j.addProperty("item_id", id);
            j.addProperty("item_name", commodity.key());
            j.addProperty("commodity_key", commodity.key());
            j.addProperty("category", commodity.category());
            j.addProperty("subcategory", commodity.subcategory().isBlank() ? fallbackSubcategory : commodity.subcategory());
            j.addProperty("quantity", stack.getCount());
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

    private CommodityClass classify(String path, ItemStack stack) {
        if (stack.getItem() == Items.WHEAT || isAny(path, "rice", "oats", "oat", "barley", "rye", "sorghum", "quinoa")) {
            return new CommodityClass("grains", "raw", path);
        }
        if (isAny(path, "apple", "banana", "concord_grapes", "grapes", "peaches", "peach", "pears", "pear",
                "squash", "carrot", "carrots", "corn", "cabbage", "lettuce", "onion", "pumpkin", "sweet_pepper")) {
            return new CommodityClass("produce", "raw", path);
        }
        if (path.contains("meat") || path.contains("beef") || path.contains("pork") || path.contains("mutton")
                || path.contains("chicken") || path.contains("rabbit") || path.contains("ham")
                || path.contains("bacon") || path.contains("sausage") || path.contains("ribs")) {
            return new CommodityClass("meat", "raw", path);
        }
        if (path.contains("leather") || path.contains("hide") || path.contains("pelt") || path.contains("fur")) {
            return new CommodityClass("fur", "leather", path);
        }
        if (path.contains("stone") || path.contains("granite") || path.contains("diorite") || path.contains("andesite")
                || path.contains("quartz") || path.contains("basalt") || path.contains("marble")
                || path.contains("cobble") || path.contains("deepslate")) {
            return new CommodityClass("stone", "blocks", path);
        }
        return null;
    }

    private boolean isAny(String path, String... keys) {
        for (String key : keys) {
            if (path.equals(key)) return true;
        }
        return false;
    }

    private record CommodityClass(String category, String subcategory, String key) {}
}
