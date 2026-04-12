package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.client.model.QuestGiverGeoModel;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.client.renderer.layer.CitizenClothingLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;

public class QuestGiverEntityRenderer extends GeoEntityRenderer<QuestGiverEntity> {

    private final QuestGiverGeoModel model = new QuestGiverGeoModel();
    private static final String[] EYELID_BONES = { "eyeLidLeft", "eyeLidRight" };

    public QuestGiverEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new QuestGiverGeoModel());
        this.addRenderLayer(new CitizenClothingLayer<>(this));
        this.shadowRadius = 0.5f;
    }

    public RenderType getRenderType(QuestGiverEntity entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation tex = model.getTextureResource(entity);
        return RenderType.entityTranslucent(tex); 
    }

    @Override
    public void preRender(PoseStack poseStack, QuestGiverEntity entity, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        
        boolean blinking = entity.isBlinking();
        for (String name : EYELID_BONES) {
            bakedModel.getBone(name).ifPresent(bone -> {
                bone.setHidden(!blinking);
            });
        }

        // --- THE CRITICAL FIX ---
        // Only apply the physical scale if this is the main body render.
        // If this is a clothing layer re-rendering, the matrix is ALREADY scaled!
        if (!isReRender) {
            float physicsScale = entity.getScale();   
            float MODEL_BASE_SCALE = 0.6f;            
            float s = physicsScale * MODEL_BASE_SCALE;
            poseStack.scale(s, s, s);
        }

        super.preRender(poseStack, entity, bakedModel, bufferSource, buffer, isReRender,
                        partialTick, packedLight, packedOverlay, color);
    }

    // --- THE BONE INTERCEPTOR ---
    // This stops the clothing textures from bleeding onto the naked body cubes.
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, 
                                  int packedLight, int packedOverlay, int color) {
        
        if (CitizenClothingLayer.CURRENT_TARGET_BONES != null && 
           !CitizenClothingLayer.CURRENT_TARGET_BONES.contains(bone.getName())) {
            return; // Skip drawing this specific mesh
        }

        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, color);
    }
}