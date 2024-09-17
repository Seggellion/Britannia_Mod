package com.seggellion.britannia_mod.features;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.lifecycle.GatherDataEvent; // Handling NeoForge lifecycle events
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.registries.RegistryEvent; // Use NeoForge's RegistryEvent
import org.slf4j.Logger;

public class RecipeRemovalHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onRegisterRecipes(RegistryEvent.Register<Recipe<?>> event) {
        event.getRegistry().getEntries().removeIf(entry -> 
            entry.getKey().equals(ResourceLocation.tryParse("minecraft:diamond_pickaxe")) ||
            entry.getKey().equals(ResourceLocation.tryParse("minecraft:diamond_axe")) ||
            entry.getKey().equals(ResourceLocation.tryParse("minecraft:diamond_sword")) ||
            entry.getKey().equals(ResourceLocation.tryParse("minecraft:diamond_shovel")) ||
            entry.getKey().equals(ResourceLocation.tryParse("minecraft:diamond_hoe"))
        );

        LOGGER.info("Successfully removed diamond tool recipes from the registry.");
    }
}
