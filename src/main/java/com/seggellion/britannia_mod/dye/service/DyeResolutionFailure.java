package com.seggellion.britannia_mod.dye.service;

/** Expected, data-oriented failures returned by the common-side resolver. */
public enum DyeResolutionFailure {
    MISSING_PIGMENT,
    MISSING_MATERIAL,
    MISSING_PALETTE,
    PALETTE_OWNER_MISMATCH,
    EMPTY_PALETTE,
    MALFORMED_EXPLICIT_MAPPING,
    MISSING_NATURAL_COLOUR,
    NO_COMPATIBLE_COLOUR
}
