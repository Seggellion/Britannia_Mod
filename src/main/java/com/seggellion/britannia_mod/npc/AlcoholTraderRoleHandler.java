package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
    public void performTransaction(Player player, int entityId, Map<Product, Integer> cart,
                                   int totalPrice, Runnable onSuccess) {
        NetworkHandler.sendToServer(new SellItemsC2SPayload(city, role, entityId, toRequests(player, cart)));
        onSuccess.run();
    }

    @Override
    public String getActionLabel() {
        return "Sell";
    }

    @Override
    public ResourceLocation getBackground() {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/sell_screen.png");
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

    private List<SellItemsC2SPayload.ItemRequest> toRequests(Player player, Map<Product, Integer> cart) {
        List<SellItemsC2SPayload.ItemRequest> requests = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product product = entry.getKey();
            CompoundTag tag = null;
            try {
                tag = (CompoundTag) product.stack().save(player.registryAccess());
            } catch (Exception ignored) {
            }
            requests.add(new SellItemsC2SPayload.ItemRequest(product.itemId(), product.name(), entry.getValue(), tag));
        }
        return requests;
    }
}
