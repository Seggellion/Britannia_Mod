package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
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
        } else if (normalizedRole.contains("metal") || normalizedRole.contains("miner")) {
            return collectOreFromInventory(player);
        } else if (normalizedRole.contains("hunter") || normalizedRole.contains("butcher") || normalizedRole.contains("meat")) {
            return collectAnimalDrops(player);
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
            j.addProperty("purity", oreItem.getPurity(stack));
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private JsonArray collectAnimalDrops(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            String id = itemId(stack);
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (path.contains("meat") || path.contains("hide") || path.contains("pelt")) {
                JsonObject j = new JsonObject();
                j.addProperty("item_id", id);
                j.addProperty("item_name", path);
                j.addProperty("quantity", stack.getCount());
                arr.add(j);
            }
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
