package com.seggellion.britannia_mod.banner.preview;

/** Typed advisory result. Only the server placement planner can authorize mutation. */
public enum BannerPlacementPreviewStatus {
    VALID,
    BLOCKED_CELL,
    INVALID_SUPPORT,
    UNSUPPORTED_FACE,
    UNSUPPORTED_ORIENTATION,
    UNSUPPORTED_MOUNT,
    UNCONFIGURED_ITEM,
    REGISTRY_DATA_UNAVAILABLE,
    REQUIRED_CHUNK_UNAVAILABLE,
    WORLD_BOUNDS,
    UNKNOWN_SERVER_PROTECTION
}
