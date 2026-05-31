package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class AlcoholTraderRoleHandler extends AbstractSellTraderRoleHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public AlcoholTraderRoleHandler(String role, String city) {
        super(role, city);
    }

    @Override
    protected JsonArray collectSellableInventory(Player player) {
        JsonArray inventoryData = collectAlcoholItems(player);
        LOGGER.info("Inventory Items: {}", inventoryData);
        return inventoryData;
    }

    private JsonArray collectAlcoholItems(Player player) {
        JsonArray arr = new JsonArray();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

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

        String bottleColor = "green";
        if (itemId.contains("wine_bottle_")) {
            String[] parts = itemId.split("wine_bottle_");
            if (parts.length > 1) bottleColor = parts[1];
        }
        j.addProperty("bottle_color", bottleColor);
        j.addProperty("winery_name", data.wineryName());
        j.addProperty("grape_type", data.grapeType());
        j.addProperty("year", data.year());
        j.addProperty("region", data.region());
        j.addProperty("quality", data.quality());
        j.addProperty("label_color", data.labelColor());

        arr.add(j);
    }
}
