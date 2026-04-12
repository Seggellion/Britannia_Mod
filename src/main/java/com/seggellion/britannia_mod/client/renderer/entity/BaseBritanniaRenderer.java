package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.entity.IBritanniaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.util.Color;

public class BaseBritanniaRenderer<T extends LivingEntity & IBritanniaEntity> extends GeoEntityRenderer<T> {
    
    private final float scale;

    public BaseBritanniaRenderer(EntityRendererProvider.Context renderManager, float scale, float shadowRadius) {
        super(renderManager, new BaseBritanniaModel<>());
        this.scale = scale;
        this.shadowRadius = shadowRadius;
    }

    @Nullable
    public RenderType getRenderType(IBritanniaEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }


@Override
public Color getRenderColor(T animatable, float partialTick, int packedLight) {
    float alpha = animatable.getTargetAlpha();
    // Return the Color object directly, don't call .getColor()
    return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha);
}

    @Override
    public void preRender(PoseStack poseStack, T entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);
        
        // Only apply scaling if it's not the default 1.0F
        if (this.scale != 1.0F) {
            poseStack.scale(this.scale, this.scale, this.scale);
        }
    }
}