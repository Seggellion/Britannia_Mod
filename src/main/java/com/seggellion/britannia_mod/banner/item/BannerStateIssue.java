package com.seggellion.britannia_mod.banner.item;

import java.util.Objects;

public record BannerStateIssue(BannerStateIssueKind kind, String stableId) {
    public BannerStateIssue {
        Objects.requireNonNull(kind, "kind");
        stableId = Objects.requireNonNull(stableId, "stableId");
    }
}
