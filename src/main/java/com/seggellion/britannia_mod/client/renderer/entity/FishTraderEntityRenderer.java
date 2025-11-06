package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.client.model.FishMerchantGeoModel;
import com.seggellion.britannia_mod.entity.FishTraderEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import software.bernie.geckolib.cache.object.GeoBone;

public class FishTraderEntityRenderer extends GeoEntityRenderer<FishTraderEntity> {

    private final FishMerchantGeoModel model = new FishMerchantGeoModel();
    private static final String[] EYELID_BONES = { "eyeLidLeft", "eyeLidRight" };

    public FishTraderEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new FishMerchantGeoModel());
        this.shadowRadius = 0.5f;
    }



    // Optional: pick a render type; otherwise GeckoLib will choose one for you.
    public RenderType getRenderType(FishTraderEntity entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation tex = model.getTextureResource(entity);
        return RenderType.entityTranslucent(tex); // or entityCutout(tex) if fully opaque
    }

    @Override
    public void preRender(PoseStack poseStack, FishTraderEntity entity, BakedGeoModel bakedModel,
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


    float s = physicsScale * MODEL_BASE_SCALE;
    poseStack.scale(s, s, s);

        super.preRender(poseStack, entity, bakedModel, bufferSource, buffer, isReRender,
                        partialTick, packedLight, packedOverlay, color);
    }
}
