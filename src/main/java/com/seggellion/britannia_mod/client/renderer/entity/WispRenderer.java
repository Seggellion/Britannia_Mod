package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.WispEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class WispRenderer extends GeoEntityRenderer<WispEntity> {
    public WispRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new WispModel());
        this.shadowRadius = 0.0F; // Wisps do not cast shadows
    }

    @Override
    public void preRender(PoseStack poseStack, WispEntity animatable, BakedGeoModel bakedModel,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        // Set custom light level (full brightness)
        int customPackedLight = 0xF000F0; // Maximum brightness

        // Call the superclass with the correct arguments
        super.preRender(poseStack, animatable, bakedModel, bufferSource, buffer,
                        isReRender, partialTick, customPackedLight, packedOverlay, renderColor);

        // Scale the Wisp down to a smaller size
        poseStack.scale(0.75F, 0.75F, 0.75F);
    }
}
