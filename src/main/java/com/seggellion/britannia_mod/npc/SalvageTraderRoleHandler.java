package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.npc.NpcRoleHandler;
import com.seggellion.britannia_mod.shop.Product;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class SalvageTraderRoleHandler implements NpcRoleHandler {
    private final String role;
    private final String city;

    public SalvageTraderRoleHandler(String role, String city) {
        this.role = role;
        this.city = city;
    }

    @Override
    public void fetchCatalog(Player player, String city, Consumer<java.util.List<Product>> callback) {
        JsonArray jewelryInventory = collectSalvageJewelry(player);
        RailsApi.fetchTraderCatalog(city, role, jewelryInventory, callback);
    }

    @Override
    public void performTransaction(Player player, int entityId,
                                   Map<Product, Integer> cart,
                                   int totalPrice,
                                   Runnable onSuccess) {
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
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/sell_screen.png");
    }

    // ==========================================================
    // Collect jewelry made of copper, silver, or gold
    // ==========================================================
    private JsonArray collectSalvageJewelry(Player player) {
        JsonArray arr = new JsonArray();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof MaterialQualityJewelryItem) {
                MaterialQualityJewelryItem.UOMaterial material = MaterialQualityJewelryItem.getMaterial(stack);
                if (material == null) continue;

                String mat = material.id().toLowerCase(Locale.ROOT);
                if (mat.equals("copper") || mat.equals("silver") || mat.equals("gold")) {
                    JsonObject j = new JsonObject();
                    j.addProperty("item_id", stack.getDescriptionId());
                    j.addProperty("material", mat);
                    j.addProperty("quality", MaterialQualityJewelryItem.getQuality(stack));
                    j.addProperty("quantity", stack.getCount());
                    arr.add(j);
                }
            }
        }

        return arr;
    }
}
