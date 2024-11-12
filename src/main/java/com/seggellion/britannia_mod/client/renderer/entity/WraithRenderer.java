// WraithRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.WraithEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class WraithRenderer extends GeoEntityRenderer<WraithEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public WraithRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new WraithModel());
        this.shadowRadius = 0.5f;  // Adjusted shadow size for smaller model
    }

    @Override
    public void preRender(PoseStack poseStack, WraithEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

        poseStack.scale(0.9F, 0.9F, 0.9F);
    }
}
