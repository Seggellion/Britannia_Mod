// DaemonModel.java
package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.DaemonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DaemonModel extends GeoModel<DaemonEntity> {

    @Override
    public ResourceLocation getModelResource(DaemonEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/daemon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DaemonEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/daemon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DaemonEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/daemon.animation.json");
    }
}
