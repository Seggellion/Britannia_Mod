package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.entity.FlamingoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Animated renderer for the shared Pink, Rose, and White Flamingo entity. */
public final class FlamingoRenderer extends GeoEntityRenderer<FlamingoEntity> {
    public FlamingoRenderer(EntityRendererProvider.Context context) {
        super(context, new FlamingoModel());
        shadowRadius = 0.3F;
    }
}
