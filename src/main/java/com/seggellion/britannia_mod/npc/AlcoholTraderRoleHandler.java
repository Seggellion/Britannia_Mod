package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class AlcoholTraderRoleHandler implements NpcRoleHandler {
    private final String role;
    private final String city;
    private static final Logger LOGGER = LogUtils.getLogger();

    public AlcoholTraderRoleHandler(String role, String city) {
        this.role = role;
        this.city = city;
    }

    @Override
    public void fetchCatalog(Player player, String city, Consumer<List<Product>> callback) {
        JsonArray inventoryData = collectAlcoholItems(player);
        LOGGER.info("Inventory Items: {}", inventoryData);
        RailsApi.fetchTraderCatalog(city, role, inventoryData, callback);
    }

    @Override
    public void performTransaction(Player player, int entityId,
                                   Map<Product, Integer> cart,
                                   int totalPrice,
                                   Runnable onSuccess) {
        // Re-use the standard transaction logic
        RailsApi.sellItems(player, city, role, entityId, cart, totalPrice, success -> {
            if (success) onSuccess.run();
        });
    }

    @Override
    public String getActionLabel() {
        return "Sell";
    }

    @Override
    public ResourceLocation getBackground() {
        // Re-use the standard sell screen background
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/sell_screen.png");
    }

    // ==========================================================
    // Collect Alcohol (Wines) for Valuation
    // ==========================================================
    private JsonArray collectAlcoholItems(Player player) {
    JsonArray arr = new JsonArray();

    for (ItemStack stack : player.getInventory().items) {
        if (stack.isEmpty()) continue;

        // RELAXED CHECK: We only care if it HAS wine data, not strictly the class type.
        if (stack.has(DataComponentRegistry.WINE_DATA)) {
            WineData data = WineBottleBlockItem.getWineData(stack);
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            addWineEntry(arr, itemId, data, stack.getCount());
        }
    }
    return arr;
}

    private void addWineEntry(JsonArray arr, String itemId, WineData data, int quantity) {
        JsonObject j = new JsonObject();
        
        j.addProperty("item_id", itemId);
        j.addProperty("quantity", quantity);

        // 1. Extract Bottle Color from ID (britannia_mod:wine_bottle_green -> "green")
        String bottleColor = "green"; // default safety
        if (itemId.contains("wine_bottle_")) {
            // fast parsing: get the part after "wine_bottle_"
            String[] parts = itemId.split("wine_bottle_");
            if (parts.length > 1) {
                bottleColor = parts[1]; // "green", "brown", etc.
            }
        }
        j.addProperty("bottle_color", bottleColor);
        
        // Pass the specific wine details to Rails so it can calculate price based on rarity/quality
        j.addProperty("winery_name", data.wineryName());
        j.addProperty("grape_type", data.grapeType());
        j.addProperty("year", data.year());
        j.addProperty("region", data.region());
        j.addProperty("quality", data.quality());
        j.addProperty("label_color", data.labelColor());

        arr.add(j);
    }
}