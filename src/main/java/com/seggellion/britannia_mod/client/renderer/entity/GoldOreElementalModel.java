// GoldOreElementalModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.GoldOreElementalEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;


public class GoldOreElementalModel extends GeoModel<GoldOreElementalEntity> {

    @Override
    public ResourceLocation getModelResource(GoldOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/earth_elemental.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GoldOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/earth_elemental.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GoldOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/earth_elemental.animation.json");
    }

}
