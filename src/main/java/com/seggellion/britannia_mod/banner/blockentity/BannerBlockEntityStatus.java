package com.seggellion.britannia_mod.banner.blockentity;

/** Diagnostic classification; it never repairs or substitutes stored banner state. */
public enum BannerBlockEntityStatus {
    CONFIGURED_VALID,
    CONFIGURED_MISSING_REFERENCES,
    UNCONFIGURED,
    STRUCTURALLY_INVALID
}
