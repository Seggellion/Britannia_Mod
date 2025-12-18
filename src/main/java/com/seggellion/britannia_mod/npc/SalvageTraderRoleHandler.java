package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
import com.seggellion.britannia_mod.item.QualitySwordItem; // ✅ Import this
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.shop.Product;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries; // ✅ Import this
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
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
    public void fetchCatalog(Player player, String city, Consumer<List<Product>> callback) {
        // ✅ Updated method name
        JsonArray inventoryData = collectSalvageItems(player);
        RailsApi.fetchTraderCatalog(city, role, inventoryData, callback);
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
    // Collect Jewelry, Swords, and Ingots for Salvage
    // ==========================================================
    private JsonArray collectSalvageItems(Player player) {
        JsonArray arr = new JsonArray();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

            // 1. Handle Jewelry
            if (stack.getItem() instanceof MaterialQualityJewelryItem) {
                MaterialQualityJewelryItem.UOMaterial mat = MaterialQualityJewelryItem.getMaterial(stack);
                if (isValidMaterial(mat)) {
                    addSalvageEntry(arr, itemId, mat.id(), MaterialQualityJewelryItem.getQuality(stack), stack.getCount());
                }
            }
            // 2. Handle Quality Swords
            else if (stack.getItem() instanceof QualitySwordItem) {
                // Reuse MaterialQualityJewelryItem helpers (assuming shared NBT structure)
                MaterialQualityJewelryItem.UOMaterial mat = MaterialQualityJewelryItem.getMaterial(stack);
                if (isValidMaterial(mat)) {
                    addSalvageEntry(arr, itemId, mat.id(), MaterialQualityJewelryItem.getQuality(stack), stack.getCount());
                }
            }
            // 3. Handle Ingots (Copper, Silver, Gold)
            else if (path.contains("ingot")) {
                MaterialQualityJewelryItem.UOMaterial mat = null;

                // Check for singular "ingot" (vanilla) or plural "ingots" (custom)
                if (path.equals("copper_ingot") || path.equals("copper_ingots")) {
                    mat = MaterialQualityJewelryItem.UOMaterial.COPPER;
                } else if (path.equals("silver_ingot") || path.equals("silver_ingots")) {
                    mat = MaterialQualityJewelryItem.UOMaterial.SILVER;
                } else if (path.equals("gold_ingot") || path.equals("gold_ingots")) {
                    mat = MaterialQualityJewelryItem.UOMaterial.GOLD;
                }

                if (mat != null) {
                    // Ingots treated as Quality 0 (Base/Normal)
                    addSalvageEntry(arr, itemId, mat.id(), 0, stack.getCount());
                }
            }
        }

        return arr;
    }

    private boolean isValidMaterial(MaterialQualityJewelryItem.UOMaterial mat) {
        if (mat == null) return false;
        return mat == MaterialQualityJewelryItem.UOMaterial.COPPER ||
               mat == MaterialQualityJewelryItem.UOMaterial.SILVER ||
               mat == MaterialQualityJewelryItem.UOMaterial.GOLD;
    }

    private void addSalvageEntry(JsonArray arr, String itemId, String materialId, int quality, int quantity) {
        JsonObject j = new JsonObject();
        j.addProperty("item_id", itemId);
        j.addProperty("material", materialId);
        j.addProperty("quality", quality);
        j.addProperty("quantity", quantity);
        arr.add(j);
    }
}