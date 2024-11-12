// LichRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.LichEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LichRenderer extends GeoEntityRenderer<LichEntity> {
        private static final Logger LOGGER = LogManager.getLogger();

    public LichRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new LichModel());
        this.shadowRadius = 1.1f;  // Adjusted shadow size for smaller model
    }

    @Override
    public void preRender(PoseStack poseStack, LichEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);
        poseStack.scale(1.1F, 1.1F, 1.1F);
    }
}
