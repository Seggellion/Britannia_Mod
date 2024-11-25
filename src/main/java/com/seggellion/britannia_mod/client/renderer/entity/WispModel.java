// WispModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.WispEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WispModel extends GeoModel<WispEntity> {

    @Override
    public ResourceLocation getModelResource(WispEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/wisp.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WispEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/wisp.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WispEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/wisp.animation.json");
    }
}
