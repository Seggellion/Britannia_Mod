package com.seggellion.britannia_mod.client.renderer.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.IBritanniaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BaseBritanniaModel<T extends LivingEntity & IBritanniaEntity> extends GeoModel<T> {

    @Override
    public ResourceLocation getModelResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/" + getAssetName(object) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/" + getAssetName(object) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/" + getAssetName(object) + ".animation.json");
    }

    private String getAssetName(T object) {
        return switch (object.getEntityName()) {
            case "elemental_gold_ore", "gold_ore_elemental", "shadow_ore_elemental" -> "earth_elemental";
            default -> object.getEntityName();
        };
    }
}
