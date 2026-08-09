package com.seggellion.britannia_mod.client.banner;

/** Placed-context failures layered on top of the shared appearance fallback. */
public enum BannerPlacedRenderFailure {
    NONE,
    MISSING_BANNER_STATE,
    STRUCTURALLY_INVALID_STATE,
    MISSING_PLACED_STRUCTURE,
    INVALID_ANCHOR_FACING,
    UNSUPPORTED_GEOMETRY_FAMILY,
    GEOMETRY_FOOTPRINT_MISMATCH,
    APPEARANCE_FALLBACK
}
