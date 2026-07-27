package com.seggellion.britannia_mod.client.banner;

import java.util.List;
import java.util.Objects;

/** Cached position-free geometry and ordered layer passes for the anchor renderer. */
public record BannerPlacedRenderPlan(
        BannerPlacedGeometryPlan geometry,
        List<BannerPlacedRenderPass> passes,
        boolean fallback) {
    public BannerPlacedRenderPlan {
        Objects.requireNonNull(geometry, "geometry");
        passes = List.copyOf(Objects.requireNonNull(passes, "passes"));
        if (passes.isEmpty()) {
            throw new IllegalArgumentException("Placed render plan requires at least one pass");
        }
    }

    public static BannerPlacedRenderPlan from(BannerPlacedRenderState state) {
        BannerPlacedGeometryPlan geometry = BannerPlacedGeometryPlan.create(
                state.orientation(), state.facing(), state.persistedWidth(), state.persistedHeight(),
                state.geometryFamily(), state.fallback());
        if (state.fallback()) {
            return new BannerPlacedRenderPlan(geometry, List.of(
                    new BannerPlacedRenderPass(BannerPlacedRenderPass.Type.FALLBACK,
                            BannerAssetAvailability.MISSING_TEXTURE, 0xFFFFFFFF, 0.001)), true);
        }
        BannerLayerPlan appearance = BannerAppearanceCache.plan(state.appearance());
        List<BannerPlacedRenderPass> passes = appearance.layers().stream().map(layer -> switch (layer.type()) {
            case BASE_TEXTURE -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.BASE_TEXTURE, layer.assetId(), 0xFFFFFFFF, 0.0005);
            case DYE_MASK -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.DYE_MASK, layer.assetId(), appearance.displayArgb(), 0.0015);
            case MOUNT -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.MOUNT, layer.assetId(), 0xFFFFFFFF, 0.0040);
        }).toList();
        return new BannerPlacedRenderPlan(geometry, passes, false);
    }
}
