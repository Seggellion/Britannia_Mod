package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record BannerAssets(
        ResourceLocation geometry,
        ResourceLocation fabricBase,
        ResourceLocation dyeMask,
        ResourceLocation staticOverlay) {
    public static final Codec<BannerAssets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("geometry").forGetter(BannerAssets::geometry),
            ResourceLocation.CODEC.fieldOf("fabric_base").forGetter(BannerAssets::fabricBase),
            ResourceLocation.CODEC.fieldOf("dye_mask").forGetter(BannerAssets::dyeMask),
            ResourceLocation.CODEC.fieldOf("static_overlay").forGetter(BannerAssets::staticOverlay)
    ).apply(instance, BannerAssets::new));

    public BannerAssets {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(fabricBase, "fabricBase");
        Objects.requireNonNull(dyeMask, "dyeMask");
        Objects.requireNonNull(staticOverlay, "staticOverlay");
    }
}
