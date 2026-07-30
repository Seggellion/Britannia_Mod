package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Catalogue geometry resources mapped to their exact persisted footprint support. */
public enum BannerPlacedGeometryFamily {
    LARGE(BannerAssetAvailability.id("banner/placeholder/large"), 2, 2, 0.03125, 0.0625),
    MEDIUM_WALL(BannerAssetAvailability.id("banner/placeholder/medium_wall"), 2, 2, 0.125, 0.125),
    MEDIUM(BannerAssetAvailability.id("banner/placeholder/medium"), 1, 2, 0.25, 0.03125),
    SMALL(BannerAssetAvailability.id("banner/placeholder/small"), 1, 1, 0.1875, 0.1875),
    X_SMALL(BannerAssetAvailability.id("banner/placeholder/x_small"), 1, 1, 0.3125, 0.3125),
    ROAD_GUARD(BannerAssetAvailability.id("banner/road_guard/geometry"), 1, 1, 0.3125, 0.3125),
    SMALL_CURTAIN(BannerAssetAvailability.id("banner/small_curtain/geometry"), 1, 1, 0.3125, 0.3125);

    private final ResourceLocation geometryId;
    private final int width;
    private final int height;
    private final double horizontalInset;
    private final double verticalInset;

    BannerPlacedGeometryFamily(
            ResourceLocation geometryId,
            int width,
            int height,
            double horizontalInset,
            double verticalInset) {
        this.geometryId = geometryId;
        this.width = width;
        this.height = height;
        this.horizontalInset = horizontalInset;
        this.verticalInset = verticalInset;
    }

    public ResourceLocation geometryId() {
        return geometryId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double horizontalInset() {
        return horizontalInset;
    }

    public double verticalInset() {
        return verticalInset;
    }

    public boolean supports(int persistedWidth, int persistedHeight) {
        return width == persistedWidth && height == persistedHeight;
    }

    public static Optional<BannerPlacedGeometryFamily> from(ResourceLocation geometryId) {
        return Arrays.stream(values()).filter(value -> value.geometryId.equals(geometryId)).findFirst();
    }

    /**
     * Custom item geometry does not change the placed footprint. When it has no shared-family ID,
     * use the synchronized approved dimensions and the most compact matching placed convention.
     */
    public static Optional<BannerPlacedGeometryFamily> from(
            ResourceLocation geometryId, BannerDimensions dimensions) {
        Optional<BannerPlacedGeometryFamily> exact = from(geometryId);
        if (exact.isPresent() || dimensions == null) {
            return exact;
        }
        if (geometryId.getPath().startsWith("banner/large/")
                && LARGE.supports(dimensions.widthBlocks(), dimensions.heightBlocks())) {
            return Optional.of(LARGE);
        }
        return Arrays.stream(values())
                .filter(value -> value.geometryId.getPath().contains("/placeholder/"))
                .filter(value -> value.supports(dimensions.widthBlocks(), dimensions.heightBlocks()))
                .reduce((first, second) -> second);
    }
}
