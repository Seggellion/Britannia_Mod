// GoldOreElementalRenderer.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.entity.GoldOreElementalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.RenderType;

public class GoldOreElementalRenderer extends GeoEntityRenderer<GoldOreElementalEntity> {
    private final GoldOreElementalModel model = new GoldOreElementalModel(); // Instantiate the model

    public GoldOreElementalRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GoldOreElementalModel());

        this.shadowRadius = 1f;  
    }


public RenderType getRenderType(GoldOreElementalEntity entity, float partialTick, PoseStack stack, MultiBufferSource buffer, int packedLight) {
    return RenderType.entityTranslucent(model.getTextureResource(entity)); // Set to entityTranslucent
}

    @Override
    public Color getRenderColor(GoldOreElementalEntity animatable, float partialTick, int packedLight) {
        float red = animatable.getCustomRed();
        float green = animatable.getCustomGreen();
        float blue = animatable.getCustomBlue();
        float alpha = 1.0F; // Fully opaque

        return Color.ofRGBA(red, green, blue, alpha);
    }


    @Override
    public void preRender(PoseStack poseStack, GoldOreElementalEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

        poseStack.scale(1F, 1F, 1F);
    }
}
