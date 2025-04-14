package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class PurpleTentModel extends GeoModel<PurpleTentBlockEntity> {
                private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getModelResource(PurpleTentBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/purple_tent.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PurpleTentBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/block/purple_tent.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PurpleTentBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "animations/purple_tent.animation.json");
    }
}
