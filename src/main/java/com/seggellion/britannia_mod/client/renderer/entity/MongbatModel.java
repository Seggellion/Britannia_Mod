// MongbatModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MongbatModel extends GeoModel<MongbatEntity> {

    @Override
    public ResourceLocation getModelResource(MongbatEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/mongbat.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MongbatEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/mongbat.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MongbatEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/mongbat.animation.json");
    }
}
