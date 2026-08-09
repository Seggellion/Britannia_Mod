package com.seggellion.britannia_mod.client.banner;

import java.util.List;

/** Ordered two-file composition: untinted complete base, optional tinted mask, then untinted mount. */
public record BannerLayerPlan(List<BannerRenderLayer> layers, int displayArgb) {
    public BannerLayerPlan {
        layers = List.copyOf(layers);
    }

    public static BannerLayerPlan from(BannerItemRenderState state) {
        return from(state.appearance());
    }

    public static BannerLayerPlan from(BannerAppearanceState state) {
        if (state.fallback()) {
            return new BannerLayerPlan(List.of(), 0xFFFFFFFF);
        }
        BannerRenderLayer base = new BannerRenderLayer(BannerRenderLayer.Type.BASE_TEXTURE,
                state.baseTexture().orElseThrow(), 0, BannerRenderLayer.NO_TINT);
        BannerRenderLayer mount = new BannerRenderLayer(BannerRenderLayer.Type.MOUNT,
                state.mountTexture().orElseThrow(), state.recolourActive() ? 2 : 1, BannerRenderLayer.NO_TINT);
        if (!state.recolourActive()) {
            return new BannerLayerPlan(List.of(base, mount), state.displayArgb());
        }
        return new BannerLayerPlan(List.of(base,
                new BannerRenderLayer(BannerRenderLayer.Type.DYE_MASK,
                        state.dyeMask().orElseThrow(), 1, BannerRenderLayer.DYE_MASK_TINT_INDEX),
                mount),
                state.displayArgb());
    }
}
