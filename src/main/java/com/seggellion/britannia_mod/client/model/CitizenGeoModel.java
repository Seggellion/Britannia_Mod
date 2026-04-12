package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CitizenGeoModel extends GeoModel<CitizenEntity> {

    @Override
    public ResourceLocation getModelResource(CitizenEntity entity) {
        // Same geometry for both genders unless you made two .geo.json files
        return switch (entity.getGender().toLowerCase()) {
            case "male" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/human_male.geo.json");
            case "female" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/human_female.geo.json");
            default -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/human_female.geo.json");
        };
    }

@Override
    public ResourceLocation getTextureResource(CitizenEntity entity) {
        // Updated to match your exact folder structure
        return switch (entity.getGender().toLowerCase()) {
            case "male" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/male/base.png");
            case "female" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/female/base.png");
            default -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/female/base.png");
        };
    }


    private static String baseName(CitizenEntity e) {
        // "human_female" (default) or "human_male"
        String gender = e.getGender(); // synced from entity
        boolean male = "male".equalsIgnoreCase(gender);
        return male ? "human_male" : "human_female";
    }

    @Override
    public ResourceLocation getAnimationResource(CitizenEntity e) {
        String base = baseName(e);
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "animations/" + base + ".animation.json");
    }
}
