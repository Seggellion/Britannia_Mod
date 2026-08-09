package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import java.util.Objects;

/** One atomic client view and its monotonic replacement generation. */
public record ClientBannerRenderPublication(
        BannerRenderDataSnapshot snapshot,
        long generation,
        boolean available) {
    public ClientBannerRenderPublication {
        Objects.requireNonNull(snapshot, "snapshot");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
    }
}
