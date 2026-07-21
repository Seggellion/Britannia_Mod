package com.seggellion.britannia_mod.client.banner;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Logical layer proof used by the baked model and narrow tint-separation tests. */
public record BannerRenderLayer(Type type, ResourceLocation assetId, int order, int tintIndex) {
    public static final int NO_TINT = -1;
    public static final int FABRIC_TINT_INDEX = 1;

    public BannerRenderLayer {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(assetId, "assetId");
        if (order < 0) throw new IllegalArgumentException("order must be non-negative");
    }

    public boolean tintable() {
        return tintIndex == FABRIC_TINT_INDEX;
    }

    public enum Type {
        FABRIC_BASE,
        DYE_MASK,
        STATIC_OVERLAY,
        MOUNT
    }
}
