package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.shop.Product;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class MerchantCatalogBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final double MARKUP = 1.70D;

    private MerchantCatalogBuilder() {
    }

    public static List<MerchantCatalogEntry> build(List<CityCommodity> commodities, List<MerchantRecipe> recipes) {
        CommodityLookup lookup = new CommodityLookup(commodities);
        List<MerchantCatalogEntry> entries = new ArrayList<>();
        int fishAccepted = 0;
        int fishRejectedMissing = 0;
        int fishRejectedInsufficient = 0;
        int fishRejectedPrice = 0;

        for (MerchantRecipe recipe : recipes) {
            boolean fishSteak = recipe.id().startsWith("fish_steak_");
            double inputCost = 0.0D;
            int stock = Integer.MAX_VALUE;
            List<MerchantCatalogEntry.ConsumedCommodity> inputs = new ArrayList<>();
            boolean available = true;

            for (MerchantRecipe.Ingredient ingredient : recipe.ingredients()) {
                CityCommodity commodity = lookup.find(
                        ingredient.category(),
                        ingredient.subcategory(),
                        ingredient.aliases().toArray(String[]::new)
                ).orElse(null);

                if (commodity == null) {
                    if (fishSteak) {
                        fishRejectedMissing++;
                        LOGGER.debug("Merchant fish steak rejected output={} required={}|{}|{} reason=missing_commodity",
                                recipe.id(), ingredient.category(), ingredient.subcategory(), ingredient.aliases());
                    } else {
                        LOGGER.info("Merchant recipe rejected output={} required={}|{}|{} reason=missing_commodity",
                                recipe.id(), ingredient.category(), ingredient.subcategory(), ingredient.aliases());
                    }
                    available = false;
                    break;
                }

                if (commodity.inventory() <= 0.0D) {
                    if (fishSteak) {
                        fishRejectedInsufficient++;
                        LOGGER.debug("Merchant fish steak rejected output={} commodity={} availableWeight={} requiredWeight={} reason=insufficient_weight",
                                recipe.id(), commodity.identityKey(), commodity.inventory(), ingredient.amount());
                    } else {
                        LOGGER.info("Merchant recipe rejected output={} commodity={} availableWeight={} requiredWeight={} reason=insufficient_weight",
                                recipe.id(), commodity.identityKey(), commodity.inventory(), ingredient.amount());
                    }
                    available = false;
                    break;
                }

                if (commodity.price() <= 0.0D) {
                    if (fishSteak) {
                        fishRejectedPrice++;
                        LOGGER.debug("Merchant fish steak rejected output={} commodity={} availableWeight={} requiredWeight={} reason=missing_price",
                                recipe.id(), commodity.identityKey(), commodity.inventory(), ingredient.amount());
                    } else {
                        LOGGER.info("Merchant recipe rejected output={} commodity={} availableWeight={} requiredWeight={} reason=missing_price",
                                recipe.id(), commodity.identityKey(), commodity.inventory(), ingredient.amount());
                    }
                    available = false;
                    break;
                }

                int ingredientStock = (int) Math.floor(commodity.inventory() / ingredient.amount());
                if (ingredientStock <= 0) {
                    if (fishSteak) fishRejectedInsufficient++;
                    available = false;
                    break;
                }

                stock = Math.min(stock, ingredientStock);
                inputCost += commodity.price() * ingredient.amount();
                inputs.add(new MerchantCatalogEntry.ConsumedCommodity(
                        commodity.category(),
                        commodity.subcategory(),
                        commodity.itemName(),
                        consumedCommodityKey(commodity),
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
            if (fishSteak) {
                fishAccepted++;
                LOGGER.debug("Merchant fish steak accepted output={} stock={} inputCost={} price={} inputs={}",
                        recipe.id(), stock, inputCost, price, inputs);
            } else {
                LOGGER.info("Merchant recipe accepted output={} stock={} inputCost={} price={} inputs={}",
                        recipe.id(), stock, inputCost, price, inputs);
            }
        }

        int fishRejected = fishRejectedMissing + fishRejectedInsufficient + fishRejectedPrice;
        if (fishAccepted > 0 || fishRejected > 0) {
            LOGGER.info("Tavernkeeper fish steak catalog built: accepted={} rejected_missing={} rejected_insufficient={} rejected_price={}",
                    fishAccepted, fishRejectedMissing, fishRejectedInsufficient, fishRejectedPrice);
        }

        return entries;
    }

    private static String consumedCommodityKey(CityCommodity commodity) {
        String itemName = CityCommodity.normalize(commodity.itemName());
        if (itemName.isBlank()) itemName = commodity.normalizedKey();
        return commodity.category() + "|" + commodity.subcategory() + "|" + itemName;
    }
}
