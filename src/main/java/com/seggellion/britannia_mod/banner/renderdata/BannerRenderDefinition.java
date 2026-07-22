package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import java.util.List;
import java.util.Objects;

/** Display-only definition data synchronized to clients for item rendering. */
public record BannerRenderDefinition(
        BannerDefinitionId id,
        BannerAssets assets,
        BannerContentStatus contentStatus,
        BannerDimensions dimensions,
        List<BannerOrientation> supportedOrientations,
        List<MountId> supportedMounts) {
    public BannerRenderDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(contentStatus, "contentStatus");
        Objects.requireNonNull(dimensions, "dimensions");
        supportedOrientations = List.copyOf(Objects.requireNonNull(
                supportedOrientations, "supportedOrientations"));
        supportedMounts = List.copyOf(Objects.requireNonNull(supportedMounts, "supportedMounts"));
        if (supportedOrientations.isEmpty() || supportedMounts.isEmpty()) {
            throw new IllegalArgumentException("Preview orientation and mount lists must not be empty");
        }
    }
}
