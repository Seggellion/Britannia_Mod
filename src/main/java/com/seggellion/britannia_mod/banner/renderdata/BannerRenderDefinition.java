package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import java.util.Objects;

/** Display-only definition data synchronized to clients for item rendering. */
public record BannerRenderDefinition(
        BannerDefinitionId id,
        BannerAssets assets,
        BannerContentStatus contentStatus) {
    public BannerRenderDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(contentStatus, "contentStatus");
    }
}
