package com.seggellion.britannia_mod.structure.lifecycle;

/** Exactly one policy switch controls whether complete shrine removal emits its configured item. */
public enum ShrineRemovalCause {
    SURVIVAL_PLAYER(true),
    CREATIVE_PLAYER(false),
    EXPLOSION(false),
    EXTERNAL_REPLACEMENT(false),
    ORPHAN_CLEANUP(false),
    OBSTRUCTED_REPAIR(false),
    PLACEMENT_ROLLBACK(false),
    INVALID_ANCHOR(false);

    private final boolean dropsConfiguredItem;

    ShrineRemovalCause(boolean dropsConfiguredItem) {
        this.dropsConfiguredItem = dropsConfiguredItem;
    }

    public boolean dropsConfiguredItem() {
        return dropsConfiguredItem;
    }
}
