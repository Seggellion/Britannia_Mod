package com.seggellion.britannia_mod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ModModelLayers {


    // Define a unique ModelLayerLocation for the No Hat Villager Model
    public static final ModelLayerLocation NO_HAT_VILLAGER_LAYER = 
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("britannia_mod", "no_hat_villager"), "main");

    // Register the Layer Definition with the NeoForge Event Bus
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
            NO_HAT_VILLAGER_LAYER, 
            () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64)
        );
    }
}
