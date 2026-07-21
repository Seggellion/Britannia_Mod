package com.seggellion.britannia_mod.dye.preview;

/** Typed terminal results sent to the preview client. */
public enum DyeApplicationResultCode {
    SUCCESS,
    ALREADY_DYED,
    CANCELLED,
    SESSION_MISSING,
    SESSION_EXPIRED,
    SESSION_MISMATCH,
    SESSION_REPLAYED,
    MAIN_HAND_CHANGED,
    OFF_HAND_CHANGED,
    TUB_STATE_CHANGED,
    BANNER_STATE_CHANGED,
    REGISTRY_CHANGED,
    RESOLVER_RESULT_CHANGED,
    TUB_DEPLETED,
    BANNER_UPDATE_INVALID,
    UNEXPECTED_APPLY_FAILURE;

    public boolean successfulApplication() {
        return this == SUCCESS;
    }
}
