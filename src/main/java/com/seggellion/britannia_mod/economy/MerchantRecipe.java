package com.seggellion.britannia_mod.economy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record MerchantRecipe(
        String id,
        String displayName,
        Supplier<Item> outputItem,
        int outputQuantity,
        List<Ingredient> ingredients,
        Consumer<ItemStack> stackCustomizer
) {
    public MerchantRecipe(String id, String displayName, Supplier<Item> outputItem,
                          int outputQuantity, List<Ingredient> ingredients) {
        this(id, displayName, outputItem, outputQuantity, ingredients, stack -> {});
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(outputItem().get(), Math.max(1, outputQuantity()));
        stackCustomizer().accept(stack);
        return stack;
    }

    public record Ingredient(String category, double amount, List<String> aliases) {
        public static Ingredient of(String category, double amount, String... aliases) {
            return new Ingredient(category, amount, List.of(aliases));
        }
    }
}
