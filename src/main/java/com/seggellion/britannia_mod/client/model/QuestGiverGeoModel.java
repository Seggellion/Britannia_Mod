// QuestGiverGeoModel.java
package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class QuestGiverGeoModel extends GeoModel<QuestGiverEntity> {

    private static String baseName(QuestGiverEntity e) {
        // "human_female" (default) or "human_male"
        String gender = e.getGender(); // synced from entity
        boolean male = "male".equalsIgnoreCase(gender);
        return male ? "human_male" : "human_female";
    }

    @Override
    public ResourceLocation getModelResource(QuestGiverEntity e) {
        String base = baseName(e);
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "geo/" + base + ".geo.json");
    }

@Override
    public ResourceLocation getTextureResource(QuestGiverEntity e) {
        // Point to the correct base body texture based on gender
        return switch (e.getGender().toLowerCase()) {
            case "male" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/male/base.png");
            case "female" -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/female/base.png");
            default -> ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/human/female/base.png");
        };
    }

    @Override
    public ResourceLocation getAnimationResource(QuestGiverEntity e) {
        String base = baseName(e);
        return ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "animations/" + base + ".animation.json");
    }
}
