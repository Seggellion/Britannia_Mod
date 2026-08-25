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

    /**
     * How far each face sits from the cloth plane, so front and back together are exactly
     * {@link BannerPlacedGeometryPlan#CLOTH_THICKNESS} apart.
     */
    private static final double CLOTH_FACE = BannerPlacedGeometryPlan.CLOTH_THICKNESS / 2.0;
    /**
     * The dye mask and the mount band are overlays on the cloth's own surface, not extra
     * thickness: they clear the base face by a hair so the depth test keeps them in front,
     * and are deliberately far smaller than the pixel they sit on.
     */
    private static final double MASK_CLEARANCE = 0.0005;
    private static final double MOUNT_CLEARANCE = 0.0025;

    public static BannerPlacedRenderPlan from(BannerPlacedRenderState state) {
        BannerPlacedGeometryPlan geometry = BannerPlacedGeometryPlan.create(
                state.orientation(), state.facing(), state.persistedWidth(), state.persistedHeight(),
                state.geometryFamily(), state.fallback(), state.orientationMountGeometry());
        if (state.fallback()) {
            return new BannerPlacedRenderPlan(geometry, List.of(
                    new BannerPlacedRenderPass(BannerPlacedRenderPass.Type.FALLBACK,
                            BannerAssetAvailability.MISSING_TEXTURE, 0xFFFFFFFF, CLOTH_FACE)), true);
        }
        BannerLayerPlan appearance = BannerAppearanceCache.plan(state.appearance());
        List<BannerPlacedRenderPass> passes = appearance.layers().stream().map(layer -> switch (layer.type()) {
            case BASE_TEXTURE -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.BASE_TEXTURE, layer.assetId(), 0xFFFFFFFF, CLOTH_FACE);
            case DYE_MASK -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.DYE_MASK, layer.assetId(), appearance.displayArgb(),
                    CLOTH_FACE + MASK_CLEARANCE);
            case MOUNT -> new BannerPlacedRenderPass(
                    BannerPlacedRenderPass.Type.MOUNT, layer.assetId(), 0xFFFFFFFF,
                    CLOTH_FACE + MOUNT_CLEARANCE);
        }).toList();
        return new BannerPlacedRenderPlan(geometry, passes, false);
    }
}
