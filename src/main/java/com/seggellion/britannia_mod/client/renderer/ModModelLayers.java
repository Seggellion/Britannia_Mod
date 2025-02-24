package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.client.renderer.entity.NoHatVillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

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
