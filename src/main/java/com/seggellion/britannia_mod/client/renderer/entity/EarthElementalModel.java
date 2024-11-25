// EarthElementalModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.EarthElementalEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;


public class EarthElementalModel extends GeoModel<EarthElementalEntity> {

    @Override
    public ResourceLocation getModelResource(EarthElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/earth_elemental.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EarthElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/earth_elemental.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EarthElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/earth_elemental.animation.json");
    }

}
