package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

public record CityCommodity(
        String category,
        String subcategory,
        String itemName,
        String commodityKey,
        double inventory,
        Double currentPrice,
        Double buyPrice,
        Double sellPrice
) {
    public static CityCommodity fromJson(JsonObject obj) {
        String itemName = stringValue(obj, "item_name", stringValue(obj, "commodity_key", ""));
        String commodityKey = stringValue(obj, "commodity_key", itemName);
        return new CityCommodity(
                normalize(stringValue(obj, "category", "")),
                normalize(stringValue(obj, "subcategory", "")),
                itemName,
                commodityKey,
                doubleValue(obj, "inventory", doubleValue(obj, "weight", doubleValue(obj, "quantity", 0.0D))),
                nullableDouble(obj, "current_price"),
                nullableDouble(obj, "buy_price"),
                nullableDouble(obj, "sell_price")
        );
    }

    public double price() {
        if (currentPrice != null && currentPrice > 0.0D) return currentPrice;
        if (sellPrice != null && sellPrice > 0.0D) return sellPrice;
        if (buyPrice != null && buyPrice > 0.0D) return buyPrice;
        return 0.0D;
    }

    public String identityKey() {
        return category + "|" + subcategory + "|" + normalizedKey();
    }

    public String normalizedKey() {
        return normalize(commodityKey == null || commodityKey.isBlank() ? itemName : commodityKey);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "");
    }

    private static String stringValue(JsonObject obj, String key, String fallback) {
        JsonElement value = obj.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static double doubleValue(JsonObject obj, String key, double fallback) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            return value.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Double nullableDouble(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsDouble();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
