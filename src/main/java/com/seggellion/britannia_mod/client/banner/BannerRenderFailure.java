package com.seggellion.britannia_mod.client.banner;

/** Typed diagnostic fallback causes retained by render state and render keys. */
public enum BannerRenderFailure {
    NONE,
    INVALID_ITEM,
    MISSING_COMPONENT,
    REGISTRY_UNAVAILABLE,
    MISSING_DEFINITION,
    MISSING_MATERIAL,
    MISSING_COLOUR,
    MISSING_MOUNT,
    MISSING_GEOMETRY,
    MISSING_FABRIC_BASE,
    MISSING_DYE_MASK,
    MISSING_STATIC_OVERLAY,
    MISSING_MOUNT_GEOMETRY,
    MISSING_MOUNT_TEXTURE
}
