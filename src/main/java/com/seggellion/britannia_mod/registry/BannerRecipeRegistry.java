package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingRecipe;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BannerRecipeRegistry {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BritanniaMod.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BannerCraftingRecipe>>
            BANNER_CRAFTING_SERIALIZER = RECIPE_SERIALIZERS.register(
                    "banner_crafting", BannerCraftingRecipeSerializer::new);

    private BannerRecipeRegistry() {
    }

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
