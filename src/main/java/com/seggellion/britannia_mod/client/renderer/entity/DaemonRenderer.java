// DaemonRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.entity.DaemonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DaemonRenderer extends GeoEntityRenderer<DaemonEntity> {
    public DaemonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new DaemonModel());
        this.shadowRadius = 1.5f;  // Adjusted shadow size for smaller model
    }

    @Override
    public void preRender(PoseStack poseStack, DaemonEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);
        // Scale the model down to 50% of its original size
        poseStack.scale(1.5F, 1.5F, 1.5F);
    }
}
