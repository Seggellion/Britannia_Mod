package com.seggellion.britannia_mod.economy.crafting;

import com.seggellion.britannia_mod.BritanniaMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe serializers this mod adds beside the vanilla ones. */
public final class ModRecipes {

    /** Default furnace time, the same 200 ticks vanilla gives its own smelts. */
    private static final int DEFAULT_COOKING_TIME = 200;

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BritanniaMod.MODID);

    /**
     * A smelt between two quarried stones that keeps the grade.
     *
     * <p>Built on vanilla's own {@code SimpleCookingSerializer}, so the JSON is the ordinary
     * cooking shape — group, category, ingredient, result, experience, cookingtime — and only the
     * assembled stack differs.
     */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCookingSerializer<GradedStoneSmeltingRecipe>>
            GRADED_STONE_SMELTING = RECIPE_SERIALIZERS.register(
                    "graded_stone_smelting",
                    () -> new SimpleCookingSerializer<>(
                            GradedStoneSmeltingRecipe::new, DEFAULT_COOKING_TIME));

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
