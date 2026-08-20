package com.seggellion.britannia_mod.economy.crafting;

import com.seggellion.britannia_mod.BritanniaMod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Recipe ingredient kinds this mod adds to the ones vanilla understands. */
public final class ModIngredients {

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, BritanniaMod.MODID);

    /** Matches one quarried stone by the type the mined item carries. */
    public static final DeferredHolder<IngredientType<?>, IngredientType<GradedStoneIngredient>>
            GRADED_STONE = INGREDIENT_TYPES.register(
                    "graded_stone", () -> new IngredientType<>(GradedStoneIngredient.CODEC));

    private ModIngredients() {
    }

    public static void register(IEventBus modEventBus) {
        INGREDIENT_TYPES.register(modEventBus);
    }
}
