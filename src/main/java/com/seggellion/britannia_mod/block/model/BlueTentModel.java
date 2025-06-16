package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import software.bernie.geckolib.model.GeoModel;

public class BlueTentModel extends GeoModel<BlueTentBlockEntity> {
                private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getModelResource(BlueTentBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/blue_tent.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BlueTentBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/block/blue_tent.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BlueTentBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "animations/blue_tent.animation.json");
    }
}
