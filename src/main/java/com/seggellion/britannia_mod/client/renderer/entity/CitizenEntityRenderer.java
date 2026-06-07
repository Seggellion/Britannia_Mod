package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.*;
import com.seggellion.britannia_mod.client.model.CitizenGeoModel;
import com.seggellion.britannia_mod.client.renderer.layer.CitizenClothingLayer;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CitizenEntityRenderer extends GeoEntityRenderer<CitizenEntity> {

    private static final String[] EYELID_BONES = { "eyeLidLeft", "eyeLidRight" };

    public CitizenEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CitizenGeoModel());
        this.addRenderLayer(new CitizenClothingLayer<>(this));
        this.shadowRadius = 0.5f;
    }

    public RenderType getRenderType(CitizenEntity entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation tex = this.model.getTextureResource(entity);
        return RenderType.entityTranslucent(tex);
    }

    @Override
    public void preRender(PoseStack poseStack, CitizenEntity entity, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        if (!isReRender) {
            float physicsScale = entity.getScale();
            float modelBaseScale = 0.6f;
            float s = physicsScale * modelBaseScale;
            poseStack.scale(s, s, s);
        }

        boolean blinking = entity.isBlinking();
        for (String name : EYELID_BONES) {
            bakedModel.getBone(name).ifPresent(bone -> bone.setHidden(!blinking));
        }

        super.preRender(poseStack, entity, bakedModel, bufferSource, buffer,
                        isReRender, partialTick, packedLight, packedOverlay, color);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, int color) {
        if (CitizenClothingLayer.CURRENT_TARGET_BONES != null
                && !CitizenClothingLayer.CURRENT_TARGET_BONES.contains(bone.getName())) {
            return;
        }

        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, color);
    }
}
