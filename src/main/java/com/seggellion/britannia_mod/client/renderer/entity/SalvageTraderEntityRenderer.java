package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.client.model.SalvageTraderGeoModel;
import com.seggellion.britannia_mod.client.renderer.layer.CitizenClothingLayer;
import com.seggellion.britannia_mod.entity.SalvageTraderEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import software.bernie.geckolib.cache.object.GeoBone;

public class SalvageTraderEntityRenderer extends GeoEntityRenderer<SalvageTraderEntity> {

    private final SalvageTraderGeoModel model = new SalvageTraderGeoModel();
    private static final String[] EYELID_BONES = { "eyeLidLeft", "eyeLidRight" };

    public SalvageTraderEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SalvageTraderGeoModel());
        this.addRenderLayer(new CitizenClothingLayer<>(this));
        this.shadowRadius = 0.5f;
    }



    // Optional: pick a render type; otherwise GeckoLib will choose one for you.
    public RenderType getRenderType(SalvageTraderEntity entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation tex = model.getTextureResource(entity);
        return RenderType.entityTranslucent(tex); // or entityCutout(tex) if fully opaque
    }

    @Override
    public void preRender(PoseStack poseStack, SalvageTraderEntity entity, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        // Apply your attribute-driven scale here
    float physicsScale = entity.getScale();   // intended 0.5
    float MODEL_BASE_SCALE = 0.6f;            // force 50% shrink visually


boolean blinking = entity.isBlinking();

for (String name : EYELID_BONES) {
    bakedModel.getBone(name).ifPresent(bone -> {
        // Blink ON  -> show lids (closed)
        // Blink OFF -> hide lids (open)
        bone.setHidden(!blinking);
    });
}


    if (!isReRender) {
        float s = physicsScale * MODEL_BASE_SCALE;
        poseStack.scale(s, s, s);
    }

        super.preRender(poseStack, entity, bakedModel, bufferSource, buffer, isReRender,
                        partialTick, packedLight, packedOverlay, color);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, int color) {
        if (CitizenClothingLayer.CURRENT_TARGET_BONES != null &&
                !CitizenClothingLayer.CURRENT_TARGET_BONES.contains(bone.getName())) {
            return;
        }

        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, color);
    }
}
