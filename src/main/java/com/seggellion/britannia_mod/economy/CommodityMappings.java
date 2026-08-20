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
    /**
     * Raw-ore commodities Rails actually seeds under {@code ore/raw} (CommoditySeeder). Mining
     * milestone 5: mined ore used to be posted under its display name ("Silver ore"), which no
     * seeded row carries, so an exact ore lookup could never resolve.
     */
    private static final Set<String> SUPPORTED_ORE_COMMODITIES = Set.of(
            "tin",
            "copper",
            "iron",
            "silver",
            "gold",
            "shadow_iron",
            "agapite",
            "verite",
            "valorite"
    );

    /**
     * Each stone commodity and the subcategory Rails actually seeds it under (CommoditySeeder
     * groups stone by material family, not by Minecraft block form). Mining milestone 8: the mod
     * used to post the literal subcategory {@code "blocks"}, which no seeded row carries, so the
     * exact lookup Rails performs when a category and subcategory are present could never resolve
     * and every stone sale failed. This map is the single source of truth for both the supported
     * set and the family each stone belongs to.
     */
    private static final Map<String, String> STONE_COMMODITY_FAMILIES = Map.ofEntries(
            Map.entry("cobblestone", "rubble"),
            Map.entry("stone", "common"),
            Map.entry("andesite", "igneous"),
            Map.entry("diorite", "igneous"),
            Map.entry("granite", "igneous"),
            Map.entry("tuff", "volcanic"),
            Map.entry("basalt", "volcanic"),
            Map.entry("blackstone", "volcanic"),
            Map.entry("limestone", "sedimentary"),
            Map.entry("sandstone", "sedimentary"),
            Map.entry("dripstone", "sedimentary"),
            Map.entry("glacial_rock", "sedimentary"),
            Map.entry("deepslate", "metamorphic"),
            Map.entry("metamorphic_rock", "metamorphic"),
            Map.entry("cobbled_deepslate", "rubble"),
            Map.entry("igneous_rock", "igneous"),
            Map.entry("volcanic_rock", "volcanic"),
            Map.entry("quartz", "mineral")
    );

    private static final Set<String> SUPPORTED_STONE_COMMODITIES = STONE_COMMODITY_FAMILIES.keySet();

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

        // Housing clay supply. Rails seeds exactly one clay row -- `clay|raw|clay` -- and the
        // mason is the only trader whose policy accepts it; the mod's job is to describe the
        // commodity, never to name the buyer.
        //
        // WEIGHT because `clay` is one of Rails' BULK_CATEGORIES, so the row is weight-canonical
        // and BuybackValuation reads the posted weight when it is positive. A clay ball carries
        // no weight component, so the fallback in classifyMappedCommodity applies and one ball
        // is one unit -- the same relation Rails would have derived from unit_weight anyway,
        // stated by the sender instead of inferred by the receiver.
        //
        // The finished goods stay out of this deliberately. `brick_wall_bottom`, `brick_wall_top`,
        // `tile_roof` and `tile_roof_flat` are construction blocks, and Rails' own negative probe
        // `clay|processed|fired_brick` says a fired clay good is not raw clay. Salvage, if it is
        // ever wanted, is a separate system with its own commodity.
        map("minecraft:clay_ball", "clay", "raw", "clay", "Clay", CommodityUnit.WEIGHT);

        // Housing supply, the remaining four building materials. Each is the bulk material a
        // builder works, never the finished block it becomes: raw glass rather than a window,
        // plaster rather than a plaster wall, thatch rather than a thatch roof.
        //
        // `straw` is the cereal harvest's existing byproduct -- FarmingBlock pops one from every
        // grain-blade harvest -- and it is thatch to the economy while wheat stays grain. One
        // plant, two products, which is what keeps roofing off the city's food supply.
        map("straw", "textile", "raw", "thatch", "Thatch", CommodityUnit.WEIGHT);
        map("raw_glass", "glass", "raw", "raw_glass", "Raw Glass", CommodityUnit.WEIGHT);
        map("plaster", "stone", "processed", "plaster", "Plaster", CommodityUnit.WEIGHT);

        // Silica is the player's feedstock, not the Architect's commodity. It carries Rails'
        // `glass|raw|sand` identity so a sale is honestly described, and the only trader whose
        // policy accepts that identity is the dormant Glassblower -- deliberately, because the
        // intended loop is that a player fires their own silica into raw glass and sells that.
        // No live fallback is added here; adding one would delete the processing step.
        map("silica_sand", "glass", "raw", "sand", "Silica Sand", CommodityUnit.WEIGHT);
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

    /**
     * The mapping for a registry id or bare path, without needing the item itself.
     *
     * <p>Same two-step lookup {@link #forStack} performs. Exists so a caller holding an id can ask
     * the table directly -- notably a test running without a loaded mod, where the mod's own items
     * are not in the registry and an {@code ItemStack} of one cannot be built.
     */
    public static Optional<CommodityMapping> forId(String itemIdOrPath) {
        if (itemIdOrPath == null || itemIdOrPath.isBlank()) return Optional.empty();
        String id = itemIdOrPath.toLowerCase(Locale.ROOT);
        CommodityMapping byId = BY_ITEM_ID.get(id);
        if (byId != null) return Optional.of(byId);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Optional.ofNullable(BY_PATH.get(path));
    }

    public static String fishCommodityKey(String fishType) {
        return canonicalFishKey(CityCommodity.normalize(fishType));
    }

    /**
     * The fish commodity key for a stack, falling back to the item's registry path when the
     * stack carries no {@code FishType} tag.
     *
     * <p>Only fish caught through {@code FishingEventHandler} are stamped with a type; anything
     * spawned by {@code /give}, taken from creative, or placed and re-broken before the tag
     * existed reads back {@code "unknown"}, which Rails can only answer with
     * {@code commodity_not_found}. The registry path IS the canonical key for every one of the
     * mod's fish, so preferring it over a dead sentinel turns a guaranteed miss into an exact
     * match — and quote and settlement both take this path, so they cannot disagree.
     */
    public static String fishCommodityKey(String fishType, String itemPath) {
        String tagged = CityCommodity.normalize(fishType);
        if (!tagged.isBlank() && !tagged.equals("unknown")) return canonicalFishKey(tagged);
        return canonicalFishKey(CityCommodity.normalize(itemPath));
    }

    /**
     * Rails seeds two fish under spellings the mod does not use: {@code mackeral} (one "e") and
     * {@code kingfish} (unspaced). The lookup is exact with no fallback, so these two must be
     * translated or they never price.
     */
    private static String canonicalFishKey(String normalized) {
        return switch (normalized) {
            case "mackerel" -> "mackeral";
            case "king_fish" -> "kingfish";
            default -> normalized;
        };
    }

    /**
     * The Rails {@code ore/raw} commodity a mined ore resolves to, from the display name
     * {@code PurityOreItem} carries ("Silver ore", "Shadow Iron ore").
     *
     * <p>High-Purity Silver deliberately resolves to plain {@code silver}: the design forbids a
     * second Silver economic identity, and the premium is already expressed by the {@code purity}
     * value posted alongside the sale rather than by a separate commodity.
     */
    public static Optional<String> oreCommodityKey(String oreType) {
        String normalized = CityCommodity.normalize(oreType);
        if (normalized.isBlank()) return Optional.empty();

        String canonical = normalized.endsWith("_ore")
                ? normalized.substring(0, normalized.length() - "_ore".length())
                : normalized;
        if ("high_purity_silver".equals(canonical)) {
            canonical = "silver";
        }
        return SUPPORTED_ORE_COMMODITIES.contains(canonical) ? Optional.of(canonical) : Optional.empty();
    }

    public static Optional<String> stoneCommodityKey(String stoneType) {
        String normalized = CityCommodity.normalize(stoneType);
        if (normalized.isBlank()) return Optional.empty();

        String canonical = switch (normalized) {
            // The managed break flow names Blackstone's drop "Blackrock", so without this the one
            // stone Rails does seed under `volcanic/blackstone` arrived as an unknown commodity.
            case "black_stone", "blackrock", "black_rock" -> "blackstone";
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

    /** The Rails subcategory a stone commodity is seeded under, e.g. {@code basalt -> volcanic}. */
    public static Optional<String> stoneCommoditySubcategory(String stoneCommodityKey) {
        return Optional.ofNullable(STONE_COMMODITY_FAMILIES.get(CityCommodity.normalize(stoneCommodityKey)));
    }

    /** {@code category|subcategory|item_name}, matching the identity Rails parses and looks up. */
    public static String stoneCommodityIdentityKey(String stoneType) {
        return "stone|" + stoneCommoditySubcategory(stoneType).orElse("") + "|" + stoneType;
    }

    private static void mapProduce(String itemName, String subcategory, String displayName) {
        map(itemName, "produce", subcategory, itemName, displayName, CommodityUnit.QUANTITY);
    }

    private static void mapMeat(String itemName, String subcategory, String displayName) {
        map(itemName, "meat", subcategory, itemName, displayName, CommodityUnit.WEIGHT);
    }

    private static void mapStone(String itemId, String itemName, String displayName) {
        map(itemId, "stone", STONE_COMMODITY_FAMILIES.get(itemName), itemName, displayName,
                CommodityUnit.QUANTITY);
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
