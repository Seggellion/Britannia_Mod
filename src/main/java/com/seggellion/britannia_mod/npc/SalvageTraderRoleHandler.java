package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SalvageTraderRoleHandler extends AbstractSellTraderRoleHandler {
    public SalvageTraderRoleHandler(String role, String city) {
        super(role, city);
    }

    @Override
    protected JsonArray collectSellableInventory(Player player) {
        JsonArray arr = new JsonArray();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

            if (stack.getItem() instanceof MaterialQualityJewelryItem) {
                MaterialQualityJewelryItem.UOMaterial mat = MaterialQualityJewelryItem.getMaterial(stack);
                if (mat != null && isValidMaterial(mat.id())) {
                    addSalvageEntry(arr, itemId, mat.id(), MaterialQualityJewelryItem.getQuality(stack), stack.getCount());
                }
            } else if (stack.getItem() instanceof QualitySwordItem) {
                String material = QualitySwordItem.getMaterial(stack);
                if (isValidMaterial(material)) {
                    addSalvageEntry(arr, itemId, material, QualitySwordItem.getQuality(stack), stack.getCount());
                }
            } else if (path.contains("ingot")) {
                String material = materialFromIngotPath(path);
                if (material != null) {
                    addSalvageEntry(arr, itemId, material, 0, stack.getCount());
                }
            }
        }

        return arr;
    }

    private boolean isValidMaterial(String material) {
        if (material == null) return false;
        return material.equals("copper") || material.equals("silver") || material.equals("gold");
    }

    private String materialFromIngotPath(String path) {
        if (path.equals("copper_ingot") || path.equals("copper_ingots")) return "copper";
        if (path.equals("silver_ingot") || path.equals("silver_ingots")) return "silver";
        if (path.equals("gold_ingot") || path.equals("gold_ingots")) return "gold";
        return null;
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
