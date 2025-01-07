// RatModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.RatEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RatModel extends GeoModel<RatEntity> {

    @Override
    public ResourceLocation getModelResource(RatEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/rat.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RatEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/rat.png");
    }


    @Override
    public ResourceLocation getAnimationResource(RatEntity object) {
       return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/rat.animation.json");
    }
    
}
