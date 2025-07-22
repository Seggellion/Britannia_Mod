package com.seggellion.britannia_mod.block.nudgeable;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "britannia_mod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityRegistry.CANDELABRA.get(),
                NudgeableBlockEntityRenderer::new);

        event.registerBlockEntityRenderer(BlockEntityRegistry.CHAIR.get(),
                NudgeableBlockEntityRenderer::new);

        event.registerBlockEntityRenderer(BlockEntityRegistry.ROTATABLE_FURNITURE.get(),
                NudgeableBlockEntityRenderer::new);

        event.registerBlockEntityRenderer(BlockEntityRegistry.DOUBLE_BED.get(),
                NudgeableBlockEntityRenderer::new);
    }
}