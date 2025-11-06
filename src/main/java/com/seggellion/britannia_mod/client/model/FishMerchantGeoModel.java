// FishMerchantGeoModel.java
package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.FishTraderEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FishMerchantGeoModel extends GeoModel<FishTraderEntity> {

    private static String baseName(FishTraderEntity e) {
        // "human_female" (default) or "human_male"
        String gender = e.getGender(); // synced from entity
        boolean male = "male".equalsIgnoreCase(gender);
        return male ? "human_male" : "human_female";
    }

    @Override
    public ResourceLocation getModelResource(FishTraderEntity e) {
        String base = baseName(e);
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "geo/" + base + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FishTraderEntity e) {
        String base = baseName(e);
        // You can branch further here (e.g., city, profession, clothing variants)
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "textures/entity/" + base + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(FishTraderEntity e) {
        String base = baseName(e);
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "animations/" + base + ".animation.json");
    }
}
