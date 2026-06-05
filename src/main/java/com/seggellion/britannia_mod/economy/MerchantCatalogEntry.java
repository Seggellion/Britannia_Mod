package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.shop.Product;

import java.util.List;

public record MerchantCatalogEntry(
        Product product,
        int stock,
        double inputCost,
        List<ConsumedCommodity> inputs
) {
    public record ConsumedCommodity(String category, String commodityKey, double amountPerUnit, double unitPrice) {}
}
