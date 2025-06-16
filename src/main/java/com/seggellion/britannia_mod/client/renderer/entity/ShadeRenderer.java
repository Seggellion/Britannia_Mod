// ShadeRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShadeRenderer extends GeoEntityRenderer<ShadeEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ShadeModel model = new ShadeModel(); // Instantiate the model

    public ShadeRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ShadeModel());
        LOGGER.info("Shade Rendering attempted");

        this.shadowRadius = 0.0f;  // Adjusted shadow size for smaller model
    }


public RenderType getRenderType(ShadeEntity entity, float partialTick, PoseStack stack, MultiBufferSource buffer, int packedLight) {
    return RenderType.entityTranslucent(model.getTextureResource(entity)); // Set to entityTranslucent
}


    @Override
    public void preRender(PoseStack poseStack, ShadeEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

        poseStack.scale(1F, 1F, 1F);
        //poseStack.translate(0.0F, 0.2F, 0.0F);
    }
}
