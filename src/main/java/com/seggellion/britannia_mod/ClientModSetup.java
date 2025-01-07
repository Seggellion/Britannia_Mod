// ClientModSetup.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.client.renderer.entity.HorseSellerNPCRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityFishMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityWoodMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.TownPersonEntityRenderer;
import com.seggellion.britannia_mod.registry.EntityRegistry; 
import com.seggellion.britannia_mod.registry.ItemRegistry; 
import com.seggellion.britannia_mod.client.renderer.entity.MongbatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.DaemonRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.LichRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.RatRenderer;
import net.minecraft.client.renderer.entity.CatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.WraithRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.GhoulRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.ShadeRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.ShadowOreElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.GoldOreElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EarthElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.WispRenderer;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
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
            // Register the entity renderers
            EntityRenderers.register(EntityRegistry.MONGBAT_ENTITY.get(), MongbatRenderer::new);
            EntityRenderers.register(EntityRegistry.DAEMON_ENTITY.get(), DaemonRenderer::new);
            EntityRenderers.register(EntityRegistry.LICH_ENTITY.get(), LichRenderer::new);
            EntityRenderers.register(EntityRegistry.RAT_ENTITY.get(), RatRenderer::new);
            EntityRenderers.register(EntityRegistry.WRAITH_ENTITY.get(), WraithRenderer::new);
            EntityRenderers.register(EntityRegistry.GHOUL_ENTITY.get(), GhoulRenderer::new);
            EntityRenderers.register(EntityRegistry.SHADE_ENTITY.get(), ShadeRenderer::new);
            EntityRenderers.register(EntityRegistry.WISP_ENTITY.get(), WispRenderer::new);
            EntityRenderers.register(EntityRegistry.CUSTOM_CAT_ENTITY.get(), CatRenderer::new);
            EntityRenderers.register(EntityRegistry.EARTH_ELEMENTAL_ENTITY.get(), EarthElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.GOLD_ORE_ELEMENTAL_ENTITY.get(), GoldOreElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.SHADOW_ORE_ELEMENTAL_ENTITY.get(), ShadowOreElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.HORSE_SELLER_NPC.get(), HorseSellerNPCRenderer::new);
            EntityRenderers.register(EntityRegistry.FISH_MERCHANT_ENTITY.get(), EntityFishMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.WOOD_MERCHANT_ENTITY.get(), EntityWoodMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.TOWN_PERSON_ENTITY.get(), TownPersonEntityRenderer::new);

            // Register the blocking property for the Order Shield
            ItemProperties.register(
                ItemRegistry.ORDER_SHIELD.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "blocking"),
                (stack, world, entity, seed) -> {
                    return entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F;
                }
            );
        });
    }
}
