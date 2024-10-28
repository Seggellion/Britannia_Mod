// MongbatRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class MongbatRenderer extends GeoEntityRenderer<MongbatEntity> {
    public MongbatRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new MongbatModel());
        this.shadowRadius = 0.25f;  // Adjusted shadow size for smaller model
    }

    @Override
    public void preRender(PoseStack poseStack, MongbatEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);
        // Scale the model down to 50% of its original size
        poseStack.scale(0.5F, 0.5F, 0.5F);
    }
}
