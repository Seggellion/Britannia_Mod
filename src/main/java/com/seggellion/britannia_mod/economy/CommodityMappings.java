package com.seggellion.britannia_mod.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CommodityMappings {
    private static final Map<String, CommodityMapping> BY_ITEM_ID = new LinkedHashMap<>();
    private static final Map<String, CommodityMapping> BY_PATH = new LinkedHashMap<>();
    private static final Set<String> SUPPORTED_STONE_COMMODITIES = Set.of(
            "cobblestone",
            "stone",
            "andesite",
            "diorite",
            "granite",
            "tuff",
            "basalt",
            "blackstone",
            "limestone",
            "quartz"
    );

    static {
        map("minecraft:wheat", "grain", "whole", "wheat", "Wheat", CommodityUnit.QUANTITY);
        map("barley", "grain", "whole", "barley", "Barley", CommodityUnit.QUANTITY);
        map("oats", "grain", "whole", "oats", "Oats", CommodityUnit.QUANTITY);
        map("rye", "grain", "whole", "rye", "Rye", CommodityUnit.QUANTITY);
        map("flour", "grain", "milled", "flour", "Flour", CommodityUnit.QUANTITY);
        map("oat_flour", "grain", "milled", "oat_flour", "Oat Flour", CommodityUnit.QUANTITY);
        map("rye_flour", "grain", "milled", "rye_flour", "Rye Flour", CommodityUnit.QUANTITY);

        mapProduce("apple", "fruit", "Apple");
        mapProduce("banana", "fruit", "Banana");
        mapProduce("concord_grapes", "fruit", "Concord Grapes");
        mapProduce("peaches", "fruit", "Peaches");
        mapProduce("pears", "fruit", "Pears");
        mapProduce("berries", "fruit", "Berries");
        mapProduce("squash", "vegetable", "Squash");
        mapProduce("carrots", "vegetable", "Carrots");
        map("minecraft:carrot", "produce", "vegetable", "carrots", "Carrots", CommodityUnit.QUANTITY);
        mapProduce("corn", "vegetable", "Corn");
        mapProduce("cabbage", "vegetable", "Cabbage");
        mapProduce("lettuce", "vegetable", "Lettuce");
        mapProduce("onion", "vegetable", "Onion");
        mapProduce("pumpkin", "vegetable", "Pumpkin");
        map("minecraft:pumpkin", "produce", "vegetable", "pumpkin", "Pumpkin", CommodityUnit.QUANTITY);
        mapProduce("potato", "vegetable", "Potato");
        map("minecraft:potato", "produce", "vegetable", "potato", "Potato", CommodityUnit.QUANTITY);
        mapProduce("tomato", "vegetable", "Tomato");

        mapMeat("raw_pork", "pork", "Raw Pork");
        mapMeat("raw_pork_ribs", "pork", "Raw Pork Ribs");
        mapMeat("raw_pork_belly", "pork", "Raw Pork Belly");
        mapMeat("raw_pork_shoulder", "pork", "Raw Pork Shoulder");
        mapMeat("raw_beef", "beef", "Raw Beef");
        mapMeat("raw_beef_ribs", "beef", "Raw Beef Ribs");
        mapMeat("raw_beef_steak", "beef", "Raw Beef Steak");
        mapMeat("raw_brisket", "beef", "Raw Brisket");
        mapMeat("raw_chicken", "chicken", "Raw Chicken");
        mapMeat("raw_chicken_leg", "chicken", "Raw Chicken Leg");
        mapMeat("raw_chicken_breast", "chicken", "Raw Chicken Breast");
        mapMeat("raw_chicken_wing", "chicken", "Raw Chicken Wing");
        mapMeat("raw_lamb", "lamb", "Raw Lamb");
        mapMeat("raw_lamb_chop", "lamb", "Raw Lamb Chop");
        mapMeat("raw_leg_of_lamb", "lamb", "Raw Leg of Lamb");
        mapMeat("raw_turkey", "bird", "Raw Turkey");
        mapMeat("raw_turkey_leg", "bird", "Raw Turkey Leg");
        mapMeat("raw_turkey_breast", "bird", "Raw Turkey Breast");
        mapMeat("raw_venison", "venison", "Raw Venison");
        mapMeat("raw_venison_haunch", "venison", "Raw Venison Haunch");
        mapMeat("raw_venison_steak", "venison", "Raw Venison Steak");
        mapMeat("raw_rabbit", "rabbit", "Raw Rabbit");
        mapMeat("raw_rabbit_leg", "rabbit", "Raw Rabbit Leg");

        map("minecraft:bone", "animal_product", "bone", "bone", "Bone", CommodityUnit.QUANTITY);
        map("animal_fat", "animal_product", "fat", "animal_fat", "Animal Fat", CommodityUnit.WEIGHT);
        map("minecraft:egg", "animal_product", "egg", "chicken_egg", "Chicken Egg", CommodityUnit.QUANTITY);
        map("chicken_egg", "animal_product", "egg", "chicken_egg", "Chicken Egg", CommodityUnit.QUANTITY);
        map("milk", "animal_product", "dairy", "milk", "Milk", CommodityUnit.QUANTITY);
        map("cheese", "animal_product", "dairy", "cheese", "Cheese", CommodityUnit.QUANTITY);
        map("butter", "animal_product", "dairy", "butter", "Butter", CommodityUnit.QUANTITY);

        map("rabbit_pelt", "fur", "pelts", "rabbit_pelt", "Rabbit Pelt", CommodityUnit.WEIGHT);
        map("minecraft:rabbit_hide", "fur", "pelts", "rabbit_pelt", "Rabbit Pelt", CommodityUnit.WEIGHT);
        map("wolf_pelt", "fur", "pelts", "wolf_pelt", "Wolf Pelt", CommodityUnit.WEIGHT);
        map("bear_pelt", "fur", "pelts", "bear_pelt", "Bear Pelt", CommodityUnit.WEIGHT);
        map("deer_hide", "fur", "pelts", "deer_hide", "Deer Hide", CommodityUnit.WEIGHT);
        map("raw_hide", "leather", "raw", "raw_hide", "Raw Hide", CommodityUnit.WEIGHT);
        map("minecraft:leather", "leather", "processed", "leather", "Leather", CommodityUnit.QUANTITY);
        map("leather", "leather", "processed", "leather", "Leather", CommodityUnit.QUANTITY);
        map("tanned_leather", "leather", "processed", "tanned_leather", "Tanned Leather", CommodityUnit.QUANTITY);

        mapStone("minecraft:cobblestone", "cobblestone", "Cobblestone");
        mapStone("minecraft:stone", "stone", "Stone");
        mapStone("minecraft:andesite", "andesite", "Andesite");
        mapStone("minecraft:diorite", "diorite", "Diorite");
        mapStone("minecraft:granite", "granite", "Granite");
        mapStone("minecraft:tuff", "tuff", "Tuff");
        mapStone("minecraft:basalt", "basalt", "Basalt");
        mapStone("minecraft:blackstone", "blackstone", "Blackstone");
        mapStone("limestone", "limestone", "Limestone");
        mapStone("minecraft:quartz", "quartz", "Quartz");
    }

    private CommodityMappings() {
    }

    public static Optional<CommodityMapping> forStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        CommodityMapping byId = BY_ITEM_ID.get(itemId);
        if (byId != null) return Optional.of(byId);

        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return Optional.ofNullable(BY_PATH.get(path));
    }

    public static String fishCommodityKey(String fishType) {
        String normalized = CityCommodity.normalize(fishType);
        return switch (normalized) {
            case "mackerel" -> "mackeral";
            case "king_fish" -> "kingfish";
            default -> normalized;
        };
    }

    public static Optional<String> stoneCommodityKey(String stoneType) {
        String normalized = CityCommodity.normalize(stoneType);
        if (normalized.isBlank()) return Optional.empty();

        String canonical = switch (normalized) {
            case "black_stone" -> "blackstone";
            case "smooth_stone", "regular_stone" -> "stone";
            case "nether_quartz", "quartz_block" -> "quartz";
            default -> normalized;
        };
        if (SUPPORTED_STONE_COMMODITIES.contains(canonical)) return Optional.of(canonical);

        if (canonical.endsWith("_stone")) {
            String stripped = canonical.substring(0, canonical.length() - "_stone".length());
            if (SUPPORTED_STONE_COMMODITIES.contains(stripped)) return Optional.of(stripped);
        }
        if (canonical.endsWith("_block")) {
            String stripped = canonical.substring(0, canonical.length() - "_block".length());
            if (SUPPORTED_STONE_COMMODITIES.contains(stripped)) return Optional.of(stripped);
        }

        return Optional.empty();
    }

    public static String stoneCommodityIdentityKey(String stoneType) {
        return "stone|blocks|" + stoneType;
    }

    private static void mapProduce(String itemName, String subcategory, String displayName) {
        map(itemName, "produce", subcategory, itemName, displayName, CommodityUnit.QUANTITY);
    }

    private static void mapMeat(String itemName, String subcategory, String displayName) {
        map(itemName, "meat", subcategory, itemName, displayName, CommodityUnit.WEIGHT);
    }

    private static void mapStone(String itemId, String itemName, String displayName) {
        map(itemId, "stone", "blocks", itemName, displayName, CommodityUnit.QUANTITY);
    }

    private static void map(String itemIdOrPath, String category, String subcategory, String itemName,
                            String displayName, CommodityUnit unit) {
        String id = itemIdOrPath.contains(":") ? itemIdOrPath : "britannia_mod:" + itemIdOrPath;
        CommodityMapping mapping = new CommodityMapping(
                id,
                CityCommodity.normalize(category),
                CityCommodity.normalize(subcategory),
                CityCommodity.normalize(itemName),
                displayName,
                unit
        );
        BY_ITEM_ID.put(id.toLowerCase(Locale.ROOT), mapping);
        BY_PATH.put(id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT), mapping);
    }
}
