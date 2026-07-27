package com.seggellion.britannia_mod.client.banner;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One generated-mesh pass; only DYE_MASK carries the resolved recolour. */
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

    public boolean tintableMask() {
        return type == Type.DYE_MASK;
    }

    public enum Type {
        BASE_TEXTURE,
        DYE_MASK,
        MOUNT,
        FALLBACK
    }
}
