package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record BannerAssets(
        ResourceLocation geometry,
        ResourceLocation baseTexture,
        ResourceLocation dyeMask) {
    public static final Codec<BannerAssets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("geometry").forGetter(BannerAssets::geometry),
            ResourceLocation.CODEC.fieldOf("base_texture").forGetter(BannerAssets::baseTexture),
            ResourceLocation.CODEC.fieldOf("dye_mask").forGetter(BannerAssets::dyeMask)
    ).apply(instance, BannerAssets::new));

    public BannerAssets {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(baseTexture, "baseTexture");
        Objects.requireNonNull(dyeMask, "dyeMask");
    }
}
