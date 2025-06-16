// GhoulRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GhoulRenderer extends GeoEntityRenderer<GhoulEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public GhoulRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GhoulModel());
        this.shadowRadius = 0.5f;  // Adjusted shadow size for smaller model
    }

    @Override
    public void preRender(PoseStack poseStack, GhoulEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

        poseStack.scale(1.0F, 1.0F, 1.0F);
       //  poseStack.translate(0, 1.0F, 0);
    }
}
