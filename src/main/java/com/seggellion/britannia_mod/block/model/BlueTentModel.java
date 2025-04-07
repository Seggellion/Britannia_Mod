package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class BlueTentModel extends GeoModel<BlueTentBlockEntity> {
                private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getModelResource(BlueTentBlockEntity animatable) {
            LOGGER.info("📦 Loading model resource for BlueTent");

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/blue_tent.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BlueTentBlockEntity animatable) {
            LOGGER.info("🎨 Loading texture resource for BlueTent");

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/block/blue_tent.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BlueTentBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "animations/blue_tent.animation.json");
    }
}
