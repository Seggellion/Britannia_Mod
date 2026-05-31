package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.client.model.WoodMerchantGeoModel;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EntityWoodMerchantRenderer extends GeoEntityRenderer<EntityWoodMerchant> {
    private static final String[] EYELID_BONES = { "eyeLidLeft", "eyeLidRight" };

    public EntityWoodMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new WoodMerchantGeoModel());
        this.shadowRadius = 0.5F;
    }

    public RenderType getRenderType(EntityWoodMerchant entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation texture = this.model.getTextureResource(entity);
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, EntityWoodMerchant entity, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        float scale = entity.getScale() * 0.6F;
        poseStack.scale(scale, scale, scale);

        boolean blinking = entity.isBlinking();
        for (String boneName : EYELID_BONES) {
            bakedModel.getBone(boneName).ifPresent(bone -> bone.setHidden(!blinking));
        }

        super.preRender(poseStack, entity, bakedModel, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, color);
    }
}
