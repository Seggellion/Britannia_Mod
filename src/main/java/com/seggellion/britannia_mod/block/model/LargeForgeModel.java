package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LargeForgeModel extends GeoModel<LargeForgeBlockEntity> {
    @Override
    public ResourceLocation getModelResource(LargeForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "geo/large_forge.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LargeForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/block/large_forge.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LargeForgeBlockEntity object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "animations/large_forge.animation.json");
    }
}
