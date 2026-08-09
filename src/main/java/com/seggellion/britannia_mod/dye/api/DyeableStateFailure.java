package com.seggellion.britannia_mod.dye.api;

/** Expected, common-side failures when inspecting or planning a dyeable item update. */
public enum DyeableStateFailure {
    NONE,
    UNCONFIGURED,
    REGISTRY_UNAVAILABLE,
    INVALID_STATE,
    MATERIAL_UNAVAILABLE,
    PALETTE_UNAVAILABLE,
    COLOUR_UNAVAILABLE,
    PIGMENT_UNAVAILABLE,
    UPDATE_NOT_ALLOWED,
    STALE_PLAN
}
