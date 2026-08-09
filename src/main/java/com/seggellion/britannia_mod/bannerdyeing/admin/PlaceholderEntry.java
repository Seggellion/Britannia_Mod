package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import java.util.Objects;

public record PlaceholderEntry(
        BannerDefinitionId definitionId,
        String displayNameKey,
        int width,
        int height,
        BannerContentStatus contentStatus,
        boolean provisionalName,
        boolean provisionalDimensions) {
    public PlaceholderEntry {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(contentStatus, "contentStatus");
    }
}
