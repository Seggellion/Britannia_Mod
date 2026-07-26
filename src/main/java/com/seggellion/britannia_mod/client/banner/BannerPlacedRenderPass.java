package com.seggellion.britannia_mod.client.banner;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One generated-mesh pass; only DYE_MASK carries the resolved fabric colour. */
public record BannerPlacedRenderPass(
        Type type,
        ResourceLocation texture,
        int argb,
        double depthOffset) {
    public BannerPlacedRenderPass {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(texture, "texture");
        if (depthOffset < 0 || !Double.isFinite(depthOffset)) {
            throw new IllegalArgumentException("depthOffset must be finite and non-negative");
        }
    }

    public boolean tintableFabric() {
        return type == Type.DYE_MASK;
    }

    public enum Type {
        FABRIC_BASE,
        DYE_MASK,
        STATIC_OVERLAY,
        MOUNT,
        FALLBACK
    }
}
