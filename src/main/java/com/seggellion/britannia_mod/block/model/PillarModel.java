package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class PillarModel extends GeoModel<PillarBlockEntity> {
                private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getModelResource(PillarBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "geo/pillar.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PillarBlockEntity animatable) {

        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/block/pillar_woman_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PillarBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "animations/pillar.animation.json");
    }

}
