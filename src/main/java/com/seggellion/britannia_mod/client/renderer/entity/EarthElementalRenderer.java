// EarthElementalRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.EarthElementalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class EarthElementalRenderer extends GeoEntityRenderer<EarthElementalEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final EarthElementalModel model = new EarthElementalModel(); // Instantiate the model

    public EarthElementalRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EarthElementalModel());

        this.shadowRadius = 1f;  
    }


public RenderType getRenderType(EarthElementalEntity entity, float partialTick, PoseStack stack, MultiBufferSource buffer, int packedLight) {
    return RenderType.entityTranslucent(model.getTextureResource(entity)); // Set to entityTranslucent
}


    @Override
    public void preRender(PoseStack poseStack, EarthElementalEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

        poseStack.scale(1F, 1F, 1F);
        //poseStack.translate(0.0F, 0.2F, 0.0F);
    }
}
