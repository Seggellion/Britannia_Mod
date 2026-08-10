package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.entity.IbisEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class IbisRenderer extends GeoEntityRenderer<IbisEntity> {
    public IbisRenderer(EntityRendererProvider.Context context) {
        super(context, new IbisModel());
        shadowRadius = 0.3F;
    }

    @Nullable
    @Override
    public RenderType getRenderType(
            IbisEntity animatable,
            ResourceLocation texture,
            @Nullable net.minecraft.client.renderer.MultiBufferSource bufferSource,
            float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
