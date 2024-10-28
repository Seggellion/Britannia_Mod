package com.seggellion.britannia_mod.client.renderer;

import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HorseSellerNPCRenderer extends MobRenderer<HorseSellerNPC, VillagerModel<HorseSellerNPC>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/horse_seller_npc.png");

    public HorseSellerNPCRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(HorseSellerNPC entity) {
        return TEXTURE;
    }
}
