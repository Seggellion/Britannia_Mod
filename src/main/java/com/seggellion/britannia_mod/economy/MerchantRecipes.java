package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.item.CookedFishSteakItem;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import java.util.ArrayList;
import java.util.List;

public final class MerchantRecipes {
    private static final double STANDARD_FOOD_INPUT_WEIGHT = 0.25D;
    private static final double SMALL_FOOD_INPUT_WEIGHT = 0.10D;
    public static final double STANDARD_FISH_STEAK_WEIGHT = 0.25D;

    private MerchantRecipes() {
    }

    public static List<MerchantRecipe> forRole(String role) {
        String normalized = CityCommodity.normalize(role);
        if (normalized.contains("baker")) return baker();
        if (normalized.contains("tavern")) return tavernkeeper();
        if (normalized.contains("costermonger")) return costermonger();
        return List.of();
    }

    public static List<MerchantRecipe> baker() {
        return List.of(
                recipe("bread", "Bread", ItemRegistry.BREAD::get, milledGrain("flour", "flour")),
                recipe("oat_bread", "Oat Bread", ItemRegistry.OAT_BREAD::get, milledGrain("oat_flour", "oat_flour")),
                recipe("barley_bread", "Barley Bread", ItemRegistry.BARLEY_BREAD::get, wholeGrain("barley", "barley")),
                recipe("rye_bread", "Rye Bread", ItemRegistry.RYE_BREAD::get, milledGrain("rye_flour", "rye_flour"))
        );
    }

    public static List<MerchantRecipe> tavernkeeper() {
        List<MerchantRecipe> recipes = new ArrayList<>(List.of(
                recipe("cooked_chicken", "Cooked Chicken", ItemRegistry.COOKED_CHICKEN::get, meat("chicken", "raw_chicken")),
                recipe("chicken_leg", "Chicken Leg", ItemRegistry.CHICKEN_LEG::get, smallMeat("chicken", "raw_chicken_leg")),
                recipe("chicken_breast", "Cooked Chicken Breast", ItemRegistry.COOKED_CHICKEN_BREAST::get, smallMeat("chicken", "raw_chicken_breast")),
                recipe("chicken_wing", "Cooked Chicken Wing", ItemRegistry.COOKED_CHICKEN_WING::get, smallMeat("chicken", "raw_chicken_wing")),
                recipe("cooked_turkey", "Cooked Turkey", ItemRegistry.COOKED_BIRD::get, meat("bird", "raw_turkey")),
                recipe("cut_of_ribs", "Cut of Ribs", ItemRegistry.CUT_OF_RIBS::get, meat("pork", "raw_pork_ribs")),
                recipe("beef_ribs", "Beef Ribs", ItemRegistry.BEEF_RIBS::get, meat("beef", "raw_beef_ribs")),
                recipe("ham", "Ham", ItemRegistry.HAM::get, meat("pork", "raw_pork_shoulder")),
                recipe("beef_brisket", "Beef Brisket", ItemRegistry.BEEF_BRISKET::get, meat("beef", "raw_brisket")),
                recipe("leg_of_lamb", "Leg of Lamb", ItemRegistry.LEG_OF_LAMB::get, meat("lamb", "raw_leg_of_lamb")),
                recipe("roast_pig", "Roast Pig", ItemRegistry.ROAST_PIG::get, meat("pork", "raw_pork")),
                recipe("slice_of_bacon", "Slice of Bacon", ItemRegistry.SLICE_OF_BACON::get, meat("pork", "raw_pork_belly")),
                recipe("sausage", "Sausage", ItemRegistry.SAUSAGE::get, meat("pork", "raw_pork"))
        ));
        recipes.addAll(fishSteaks());
        return List.copyOf(recipes);
    }

    public static List<MerchantRecipe> costermonger() {
        return List.of(
                recipe("apple", "Apple", ItemRegistry.APPLE::get, produce("apple", "apple")),
                recipe("banana", "Banana", ItemRegistry.BANANA::get, produce("banana", "banana")),
                recipe("concord_grapes", "Concord Grapes", ItemRegistry.CONCORD_GRAPES::get, produce("concord_grapes", "concord_grapes", "grapes")),
                recipe("peaches", "Peaches", ItemRegistry.PEACHES::get, produce("peaches", "peaches", "peach")),
                recipe("pears", "Pears", ItemRegistry.PEARS::get, produce("pears", "pears", "pear")),
                recipe("squash", "Squash", ItemRegistry.SQUASH::get, produce("squash", "squash")),
                recipe("carrots", "Carrots", ItemRegistry.CARROTS::get, produce("carrots", "carrots", "carrot")),
                recipe("corn", "Corn", ItemRegistry.CORN::get, produce("corn", "corn")),
                recipe("cabbage", "Cabbage", ItemRegistry.CABBAGE::get, produce("cabbage", "cabbage")),
                recipe("lettuce", "Lettuce", ItemRegistry.LETTUCE::get, produce("lettuce", "lettuce")),
                recipe("onion", "Onion", ItemRegistry.ONION::get, produce("onion", "onion")),
                recipe("pumpkin", "Pumpkin", ItemRegistry.PUMPKIN::get, produce("pumpkin", "pumpkin")),
                recipe("berries", "Berries", ItemRegistry.BERRIES::get, produce("berries", "berries")),
                recipe("potato", "Potato", ItemRegistry.POTATO::get, produce("potato", "potato")),
                recipe("tomato", "Tomato", ItemRegistry.TOMATO::get, produce("tomato", "tomato"))
        );
    }

    private static MerchantRecipe recipe(String id, String name, java.util.function.Supplier<net.minecraft.world.item.Item> output,
                                         MerchantRecipe.Ingredient ingredient) {
        return new MerchantRecipe(id, name, output, 1, List.of(ingredient));
    }

    private static MerchantRecipe fishSteakRecipe(String fishId) {
        String normalizedFishId = CityCommodity.normalize(fishId);
        String displayName = fishSteakDisplayName(normalizedFishId);
        return new MerchantRecipe(
                "fish_steak_" + normalizedFishId,
                displayName,
                ItemRegistry.COOKED_FISH_STEAK::get,
                1,
                List.of(fish(normalizedFishId, CommodityMappings.fishCommodityKey(normalizedFishId), normalizedFishId)),
                stack -> {
                    CookedFishSteakItem.setFishType(stack, normalizedFishId);
                    WeightedCommodityItem.setWeight(stack, STANDARD_FISH_STEAK_WEIGHT);
                }
        );
    }

    private static List<MerchantRecipe> fishSteaks() {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (String fishId : FishRegistry.fishIds()) {
            recipes.add(fishSteakRecipe(fishId));
        }
        return recipes;
    }

    private static MerchantRecipe.Ingredient wholeGrain(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("grain", "whole", 1.0D, aliases);
    }

    private static MerchantRecipe.Ingredient milledGrain(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("grain", "milled", 1.0D, aliases);
    }

    private static MerchantRecipe.Ingredient meat(String subcategory, String itemName) {
        return MerchantRecipe.Ingredient.of("meat", subcategory, STANDARD_FOOD_INPUT_WEIGHT, itemName);
    }

    private static MerchantRecipe.Ingredient smallMeat(String subcategory, String itemName) {
        return MerchantRecipe.Ingredient.of("meat", subcategory, SMALL_FOOD_INPUT_WEIGHT, itemName);
    }

    private static MerchantRecipe.Ingredient animalProduct(String subcategory, String itemName) {
        return MerchantRecipe.Ingredient.of("animal_product", subcategory, 1.0D, itemName);
    }

    private static MerchantRecipe.Ingredient fish(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("fish", "raw", STANDARD_FISH_STEAK_WEIGHT, aliases);
    }

    private static MerchantRecipe.Ingredient produce(String key, String... aliases) {
        String subcategory = switch (CityCommodity.normalize(key)) {
            case "apple", "banana", "concord_grapes", "peaches", "pears", "berries" -> "fruit";
            default -> "vegetable";
        };
        return MerchantRecipe.Ingredient.of("produce", subcategory, 1.0D, aliases);
    }

    private static String fishSteakDisplayName(String fishId) {
        String fishName = titleCase(fishId);
        return fishName.endsWith(" Fish") ? fishName + " Steak" : fishName + " Fish Steak";
    }

    private static String titleCase(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : CityCommodity.normalize(raw).split("_")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? "Fish" : out.toString();
    }
}
