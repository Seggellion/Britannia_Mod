package com.seggellion.britannia_mod.banner.structure;

public enum BannerRemovalCause {
    SURVIVAL_PLAYER(true),
    CREATIVE_PLAYER(false),
    SUPPORT_LOSS(true),
    EXPLOSION(false),
    EXTERNAL_REPLACEMENT(false),
    ORPHAN_CLEANUP(false),
    PLACEMENT_ROLLBACK(false),
    ADMINISTRATIVE(false);

    private final boolean dropsConfiguredItem;

    BannerRemovalCause(boolean dropsConfiguredItem) {
        this.dropsConfiguredItem = dropsConfiguredItem;
    }

    public boolean dropsConfiguredItem() {
        return dropsConfiguredItem;
    }
}
