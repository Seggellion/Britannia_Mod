package com.seggellion.britannia_mod.client.banner;

import java.util.List;

/** Ordered composition contract: neutral fabric, tinted mask, static overlay, then untinted mount. */
public record BannerLayerPlan(List<BannerRenderLayer> layers, int displayArgb) {
    public BannerLayerPlan {
        layers = List.copyOf(layers);
    }

    public static BannerLayerPlan from(BannerItemRenderState state) {
        if (state.fallback()) {
            return new BannerLayerPlan(List.of(), 0xFFFFFFFF);
        }
        return new BannerLayerPlan(List.of(
                new BannerRenderLayer(BannerRenderLayer.Type.FABRIC_BASE,
                        state.fabricBase().orElseThrow(), 0, BannerRenderLayer.NO_TINT),
                new BannerRenderLayer(BannerRenderLayer.Type.DYE_MASK,
                        state.dyeMask().orElseThrow(), 1, BannerRenderLayer.FABRIC_TINT_INDEX),
                new BannerRenderLayer(BannerRenderLayer.Type.STATIC_OVERLAY,
                        state.staticOverlay().orElseThrow(), 2, BannerRenderLayer.NO_TINT),
                new BannerRenderLayer(BannerRenderLayer.Type.MOUNT,
                        state.mountTexture().orElseThrow(), 3, BannerRenderLayer.NO_TINT)),
                0xFF000000 | state.displaySrgb());
    }
}
