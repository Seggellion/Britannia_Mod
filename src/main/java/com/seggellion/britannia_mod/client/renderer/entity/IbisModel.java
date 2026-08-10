package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.IbisEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Shared ibis geometry/animation with a synchronized variant texture selection. */
public final class IbisModel extends GeoModel<IbisEntity> {
    private static final ResourceLocation MODEL = resource("geo/ibis.geo.json");
    private static final ResourceLocation ANIMATION = resource("animations/ibis.animation.json");
    private static final ResourceLocation WHITE = resource("textures/entity/ibis_white.png");
    private static final ResourceLocation SCARLET = resource("textures/entity/ibis_scarlet.png");

    @Override
    public ResourceLocation getModelResource(IbisEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(IbisEntity animatable) {
        return animatable.getVariant().id() == 1 ? SCARLET : WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(IbisEntity animatable) {
        return ANIMATION;
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }
}
