package com.seggellion.britannia_mod.banner.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A deterministic wall-parallel cell coordinate measured rightward and downward from the anchor. */
public record BannerLocalOffset(int horizontal, int vertical) implements Comparable<BannerLocalOffset> {
    public static final int MAX_HORIZONTAL = 2;
    public static final int MAX_VERTICAL = 1;
    public static final BannerLocalOffset ANCHOR = new BannerLocalOffset(0, 0);
    public static final Codec<BannerLocalOffset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, MAX_HORIZONTAL).fieldOf("horizontal").forGetter(BannerLocalOffset::horizontal),
            Codec.intRange(0, MAX_VERTICAL).fieldOf("vertical").forGetter(BannerLocalOffset::vertical)
    ).apply(instance, BannerLocalOffset::new));

    public BannerLocalOffset {
        if (horizontal < 0 || horizontal > MAX_HORIZONTAL) {
            throw new IllegalArgumentException("horizontal must be from 0 through " + MAX_HORIZONTAL);
        }
        if (vertical < 0 || vertical > MAX_VERTICAL) {
            throw new IllegalArgumentException("vertical must be from 0 through " + MAX_VERTICAL);
        }
    }

    public boolean isAnchor() {
        return equals(ANCHOR);
    }

    @Override
    public int compareTo(BannerLocalOffset other) {
        int verticalOrder = Integer.compare(vertical, other.vertical);
        return verticalOrder != 0 ? verticalOrder : Integer.compare(horizontal, other.horizontal);
    }
}
