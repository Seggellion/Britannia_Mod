package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Declared banner occupancy dimensions. Sixteen blocks is a conservative upper bound that keeps
 * future placement validation bounded while comfortably exceeding the current one-to-three-block catalogue.
 */
public record BannerDimensions(int widthBlocks, int heightBlocks, boolean provisional) {
    public static final int MAX_HEIGHT_BLOCKS = 16;
    public static final Codec<BannerDimensions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 3).fieldOf("width_blocks").forGetter(BannerDimensions::widthBlocks),
            Codec.intRange(1, MAX_HEIGHT_BLOCKS).fieldOf("height_blocks").forGetter(BannerDimensions::heightBlocks),
            Codec.BOOL.fieldOf("provisional").forGetter(BannerDimensions::provisional)
    ).apply(instance, BannerDimensions::new));

    public BannerDimensions {
        if (widthBlocks < 1 || widthBlocks > 3) {
            throw new IllegalArgumentException("widthBlocks must be from 1 through 3");
        }
        if (heightBlocks < 1 || heightBlocks > MAX_HEIGHT_BLOCKS) {
            throw new IllegalArgumentException("heightBlocks must be from 1 through " + MAX_HEIGHT_BLOCKS);
        }
    }
}
