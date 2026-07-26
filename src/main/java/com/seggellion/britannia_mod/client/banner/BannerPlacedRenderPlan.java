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
        return new BannerPlacedRenderPlan(geometry, List.of(
                pass(appearance, 0, BannerPlacedRenderPass.Type.FABRIC_BASE, 0xFFFFFFFF, 0.0005),
                pass(appearance, 1, BannerPlacedRenderPass.Type.DYE_MASK, appearance.displayArgb(), 0.0015),
                pass(appearance, 2, BannerPlacedRenderPass.Type.STATIC_OVERLAY, 0xFFFFFFFF, 0.0025),
                pass(appearance, 3, BannerPlacedRenderPass.Type.MOUNT, 0xFFFFFFFF, 0.0040)), false);
    }

    private static BannerPlacedRenderPass pass(
            BannerLayerPlan appearance,
            int index,
            BannerPlacedRenderPass.Type type,
            int argb,
            double depth) {
        BannerRenderLayer layer = appearance.layers().get(index);
        return new BannerPlacedRenderPass(type, layer.assetId(), argb, depth);
    }
}
