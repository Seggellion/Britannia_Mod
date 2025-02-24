package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.entity.EntityMetalMerchant;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class EntityMetalMerchantRenderer extends MobRenderer<EntityMetalMerchant, VillagerModel<EntityMetalMerchant>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/metal_merchant.png");

    public EntityMetalMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityMetalMerchant entity) {
        return TEXTURE;
    }
}
