package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SmallForgeModel extends GeoModel<SmallForgeBlockEntity> {
    @Override
    public ResourceLocation getModelResource(SmallForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/small_forge.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SmallForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/block/small_forge.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SmallForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/small_forge.animation.json");
    }
}
