package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.entity.ShadowOreElementalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class ShadowOreElementalRenderer extends GeoEntityRenderer<ShadowOreElementalEntity> {

    public ShadowOreElementalRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ShadowOreElementalModel());
        this.shadowRadius = 1f;
    }

    @Nullable
    @Override
    public RenderType getRenderType(ShadowOreElementalEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(ShadowOreElementalEntity animatable, float partialTick, int packedLight) {
        float red = animatable.getCustomRed();
        float green = animatable.getCustomGreen();
        float blue = animatable.getCustomBlue();
        float alpha = 1.0F; // Fully opaque

        return Color.ofRGBA(red, green, blue, alpha);
    }

    @Override
    public void preRender(PoseStack poseStack, ShadowOreElementalEntity animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, renderColor);

        // Apply scaling here
        poseStack.scale(1.0F, 1.0F, 1.0F);
    }
}
