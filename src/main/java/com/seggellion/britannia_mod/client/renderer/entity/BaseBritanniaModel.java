package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.IBritanniaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BaseBritanniaModel<T extends LivingEntity & IBritanniaEntity> extends GeoModel<T> {

    @Override
    public ResourceLocation getModelResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/" + object.getEntityName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/" + object.getEntityName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/" + object.getEntityName() + ".animation.json");
    }
}