package com.seggellion.britannia_mod.client.renderer;

import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

public class LivingSeatRenderer extends EntityRenderer<LivingSeatEntity> {
    public LivingSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LivingSeatEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // do nothing, but override to prevent render pipeline issues
    }

    @Override
    public boolean shouldRender(LivingSeatEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public Vec3 getRenderOffset(LivingSeatEntity entity, float partialTicks) {
        return Vec3.ZERO;
    }

    @Override
    public ResourceLocation getTextureLocation(LivingSeatEntity entity) {
        return null; // Not needed
    }
}
