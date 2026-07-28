package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Display-only definition data synchronized to clients for item rendering. */
public record BannerRenderDefinition(
        BannerDefinitionId id,
        BannerAssets assets,
        BannerContentStatus contentStatus,
        BannerDimensions dimensions,
        List<BannerOrientation> supportedOrientations,
        List<MountId> supportedMounts,
        Map<BannerOrientation, ResourceLocation> orientationMountGeometry) {
    public BannerRenderDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(contentStatus, "contentStatus");
        Objects.requireNonNull(dimensions, "dimensions");
        supportedOrientations = List.copyOf(Objects.requireNonNull(
                supportedOrientations, "supportedOrientations"));
        supportedMounts = List.copyOf(Objects.requireNonNull(supportedMounts, "supportedMounts"));
        orientationMountGeometry = Map.copyOf(Objects.requireNonNull(
                orientationMountGeometry, "orientationMountGeometry"));
        if (supportedOrientations.isEmpty() || supportedMounts.isEmpty()) {
            throw new IllegalArgumentException("Preview orientation and mount lists must not be empty");
        }
    }

    public BannerRenderDefinition(
            BannerDefinitionId id,
            BannerAssets assets,
            BannerContentStatus contentStatus,
            BannerDimensions dimensions,
            List<BannerOrientation> supportedOrientations,
            List<MountId> supportedMounts) {
        this(id, assets, contentStatus, dimensions, supportedOrientations, supportedMounts, Map.of());
    }
}
