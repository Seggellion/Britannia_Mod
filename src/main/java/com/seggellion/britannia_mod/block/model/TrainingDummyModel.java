package com.seggellion.britannia_mod.block.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.TrainingDummyBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class TrainingDummyModel extends GeoModel<TrainingDummyBlockEntity> {
    private static final ResourceLocation MODEL = resource("geo/training_dummy.geo.json");
    private static final ResourceLocation TEXTURE = resource("textures/block/new_assets/training_dummy.png");
    private static final ResourceLocation ANIMATION = resource("animations/training_dummy.animation.json");

    @Override
    public ResourceLocation getModelResource(TrainingDummyBlockEntity animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(TrainingDummyBlockEntity animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(TrainingDummyBlockEntity animatable) { return ANIMATION; }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }
}
