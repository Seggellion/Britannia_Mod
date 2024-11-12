// ShadeModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;


public class ShadeModel extends GeoModel<ShadeEntity> {

    @Override
    public ResourceLocation getModelResource(ShadeEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/wraith.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShadeEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/shade.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShadeEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/shade.animation.json");
    }

}
