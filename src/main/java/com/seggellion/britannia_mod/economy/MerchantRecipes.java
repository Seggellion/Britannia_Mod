package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.item.CookedFishSteakItem;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class MerchantRecipes {
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
                recipe("bread", "Bread", () -> Items.BREAD, grain("wheat", "wheat")),
                recipe("rice_bread", "Rice Bread", ItemRegistry.RICE_BREAD::get, grain("rice", "rice")),
                recipe("oat_bread", "Oat Bread", ItemRegistry.OAT_BREAD::get, grain("oats", "oats", "oat")),
                recipe("barley_bread", "Barley Bread", ItemRegistry.BARLEY_BREAD::get, grain("barley", "barley")),
                recipe("rye_bread", "Rye Bread", ItemRegistry.RYE_BREAD::get, grain("rye", "rye")),
                recipe("sorghum_bread", "Sorghum Bread", ItemRegistry.SORGHUM_BREAD::get, grain("sorghum", "sorghum")),
                recipe("quinoa_bread", "Quinoa Bread", ItemRegistry.QUINOA_BREAD::get, grain("quinoa", "quinoa"))
        );
    }

    public static List<MerchantRecipe> tavernkeeper() {
        List<MerchantRecipe> recipes = new ArrayList<>(List.of(
                recipe("chicken_leg", "Chicken Leg", ItemRegistry.CHICKEN_LEG::get, meat("chicken", "chicken", "chicken_leg")),
                recipe("cooked_bird", "Cooked Bird", ItemRegistry.COOKED_BIRD::get, meat("bird", "bird", "chicken")),
                recipe("cut_of_ribs", "Cut of Ribs", ItemRegistry.CUT_OF_RIBS::get, meat("ribs", "ribs", "cut_of_ribs")),
                recipe("ham", "Ham", ItemRegistry.HAM::get, meat("ham", "ham", "pork")),
                recipe("leg_of_lamb", "Leg of Lamb", ItemRegistry.LEG_OF_LAMB::get, meat("lamb", "lamb", "mutton")),
                recipe("roast_pig", "Roast Pig", ItemRegistry.ROAST_PIG::get, meat("pig", "pig", "pork")),
                recipe("slice_of_bacon", "Slice of Bacon", ItemRegistry.SLICE_OF_BACON::get, meat("bacon", "bacon", "pork")),
                recipe("sausage", "Sausage", ItemRegistry.SAUSAGE::get, meat("sausage", "sausage", "pork", "meat"))
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
                recipe("sweet_pepper", "Sweet Pepper", ItemRegistry.SWEET_PEPPER::get, produce("sweet_pepper", "sweet_pepper", "pepper"))
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
                List.of(fish(normalizedFishId, normalizedFishId)),
                stack -> CookedFishSteakItem.setFishType(stack, normalizedFishId)
        );
    }

    private static List<MerchantRecipe> fishSteaks() {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (String fishId : FishRegistry.fishIds()) {
            recipes.add(fishSteakRecipe(fishId));
        }
        return recipes;
    }

    private static MerchantRecipe.Ingredient grain(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("grains", 1.0D, aliases);
    }

    private static MerchantRecipe.Ingredient meat(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("meat", 1.0D, aliases);
    }

    private static MerchantRecipe.Ingredient fish(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("", 1.0D, aliases);
    }

    private static MerchantRecipe.Ingredient produce(String key, String... aliases) {
        return MerchantRecipe.Ingredient.of("produce", 1.0D, aliases);
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
