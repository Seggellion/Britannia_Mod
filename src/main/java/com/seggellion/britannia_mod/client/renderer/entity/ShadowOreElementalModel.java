// ShadowOreElementalModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ShadowOreElementalEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;


public class ShadowOreElementalModel extends GeoModel<ShadowOreElementalEntity> {

    @Override
    public ResourceLocation getModelResource(ShadowOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/earth_elemental.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShadowOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/earth_elemental.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShadowOreElementalEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/earth_elemental.animation.json");
    }

}
