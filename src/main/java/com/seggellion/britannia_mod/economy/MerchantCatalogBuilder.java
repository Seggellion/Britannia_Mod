package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public final class MerchantCatalogBuilder {
    public static final double MARKUP = 1.70D;

    private MerchantCatalogBuilder() {
    }

    public static List<MerchantCatalogEntry> build(List<CityCommodity> commodities, List<MerchantRecipe> recipes) {
        CommodityLookup lookup = new CommodityLookup(commodities);
        List<MerchantCatalogEntry> entries = new ArrayList<>();

        for (MerchantRecipe recipe : recipes) {
            double inputCost = 0.0D;
            int stock = Integer.MAX_VALUE;
            List<MerchantCatalogEntry.ConsumedCommodity> inputs = new ArrayList<>();
            boolean available = true;

            for (MerchantRecipe.Ingredient ingredient : recipe.ingredients()) {
                CityCommodity commodity = lookup.find(
                        ingredient.category(),
                        ingredient.aliases().toArray(String[]::new)
                ).orElse(null);

                if (commodity == null || commodity.inventory() <= 0.0D || commodity.price() <= 0.0D) {
                    available = false;
                    break;
                }

                int ingredientStock = (int) Math.floor(commodity.inventory() / ingredient.amount());
                if (ingredientStock <= 0) {
                    available = false;
                    break;
                }

                stock = Math.min(stock, ingredientStock);
                inputCost += commodity.price() * ingredient.amount();
                inputs.add(new MerchantCatalogEntry.ConsumedCommodity(
                        commodity.category(),
                        commodity.normalizedKey(),
                        ingredient.amount(),
                        commodity.price()
                ));
            }

            if (!available || stock == Integer.MAX_VALUE) continue;

            int price = Math.max(1, (int) Math.ceil(inputCost * MARKUP));
            ItemStack stack = recipe.createStack();
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
            tag.putInt("max_quantity", stock);
            tag.putString("merchant_recipe", recipe.id());
            tag.putDouble("input_cost", inputCost);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            String itemId = BuiltInRegistries.ITEM.getKey(recipe.outputItem().get()).toString();
            Product product = new Product(itemId, recipe.displayName(), price, "copper", stack);
            entries.add(new MerchantCatalogEntry(product, stock, inputCost, List.copyOf(inputs)));
        }

        return entries;
    }
}
