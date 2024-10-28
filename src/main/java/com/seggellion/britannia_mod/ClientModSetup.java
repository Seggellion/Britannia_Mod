// ClientModSetup.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.client.renderer.HorseSellerNPCRenderer;
import com.seggellion.britannia_mod.registry.EntityRegistry; 
import com.seggellion.britannia_mod.client.renderer.entity.MongbatRenderer;
import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ClientModSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Client setup event called");

        // Register the mana overlay (render it during the HUD)
        ManaOverlayScreen.register();

        event.enqueueWork(() -> {
            // Register the entity renderers here
            EntityRenderers.register(EntityRegistry.MONGBAT_ENTITY.get(), MongbatRenderer::new);
            EntityRenderers.register(EntityRegistry.HORSE_SELLER_NPC.get(), HorseSellerNPCRenderer::new);
        });
    }
}
