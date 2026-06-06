package com.seggellion.britannia_mod.economy;

public record CommodityMapping(
        String itemId,
        String category,
        String subcategory,
        String itemName,
        String displayName,
        CommodityUnit unit
) {
    public String normalizedKey() {
        return category + "|" + subcategory + "|" + itemName;
    }
}
