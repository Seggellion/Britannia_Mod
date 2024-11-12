// GhoulhModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GhoulModel extends GeoModel<GhoulEntity> {

    @Override
    public ResourceLocation getModelResource(GhoulEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/wraith.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GhoulEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/ghoul.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GhoulEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/wraith.animation.json");
    }
}
