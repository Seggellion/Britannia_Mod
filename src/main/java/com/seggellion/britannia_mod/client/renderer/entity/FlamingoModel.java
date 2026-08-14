package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.FlamingoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Shared animated Flamingo rig with synchronized Pink, Rose, and White textures. */
public final class FlamingoModel extends GeoModel<FlamingoEntity> {
    private static final ResourceLocation MODEL = resource("geo/flamingo.geo.json");
    private static final ResourceLocation ANIMATION = resource("animations/flamingo.animation.json");
    private static final ResourceLocation PINK = resource("textures/entity/flamingo_pink.png");
    private static final ResourceLocation ROSE = resource("textures/entity/flamingo_rose.png");
    private static final ResourceLocation WHITE = resource("textures/entity/flamingo_white.png");

    @Override
    public ResourceLocation getModelResource(FlamingoEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FlamingoEntity animatable) {
        return switch (animatable.getVariant()) {
            case ROSE -> ROSE;
            case WHITE -> WHITE;
            case PINK -> PINK;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(FlamingoEntity animatable) {
        return ANIMATION;
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }
}
