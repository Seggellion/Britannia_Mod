package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WoodMerchantGeoModel extends GeoModel<EntityWoodMerchant> {
    private static String baseName(EntityWoodMerchant entity) {
        return "male".equalsIgnoreCase(entity.getGender()) ? "human_male" : "human_female";
    }

    @Override
    public ResourceLocation getModelResource(EntityWoodMerchant entity) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/" + baseName(entity) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityWoodMerchant entity) {
        String folder = "male".equalsIgnoreCase(entity.getGender()) ? "male" : "female";
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/entity/human/" + folder + "/base.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EntityWoodMerchant entity) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/" + baseName(entity) + ".animation.json");
    }
}
