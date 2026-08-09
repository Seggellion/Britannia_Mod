package com.seggellion.britannia_mod.dye.preview;

/** Stable localization routing for typed preview and application outcomes. */
public final class DyePreviewMessages {
    private DyePreviewMessages() {
    }

    public static String previewFailure(DyePreviewFailure failure) {
        return switch (failure) {
            case INVALID_HAND_CONTRACT -> "message.britannia_mod.dye_preview.invalid_hands";
            case TUB_EMPTY -> "message.britannia_mod.dye_preview.tub_empty";
            case TUB_DEPLETED -> "message.britannia_mod.dye_preview.tub_depleted";
            case BANNER_UNCONFIGURED -> "message.britannia_mod.dye_preview.banner_unconfigured";
            case REGISTRY_UNAVAILABLE -> "message.britannia_mod.dye_preview.registry_unavailable";
            case PIGMENT_MISSING, PIGMENT_DISABLED -> "message.britannia_mod.dye_preview.pigment_unavailable";
            case MATERIAL_MISSING, MATERIAL_DISABLED -> "message.britannia_mod.dye_preview.material_unavailable";
            case PALETTE_MISSING -> "message.britannia_mod.dye_preview.palette_missing";
            case DEFINITION_MISSING, DEFINITION_DISABLED -> "message.britannia_mod.dye_preview.definition_unavailable";
            case MOUNT_MISSING, MOUNT_DISABLED -> "message.britannia_mod.dye_preview.mount_unavailable";
            case NO_COMPATIBLE_COLOUR -> "message.britannia_mod.dye_preview.no_compatible_colour";
            case BANNER_INVALID, RESOLVER_FAILURE, DYEABLE_REJECTED, SESSION_CREATION_FAILURE ->
                    "message.britannia_mod.dye_preview.application_failure";
            case NONE -> "message.britannia_mod.dye_preview.application_failure";
        };
    }

    public static String applicationResult(DyeApplicationResultCode result) {
        return switch (result) {
            case SUCCESS -> "message.britannia_mod.dye_preview.applied";
            case ALREADY_DYED -> "message.britannia_mod.dye_preview.already_dyed";
            case CANCELLED -> "message.britannia_mod.dye_preview.cancelled";
            case SESSION_EXPIRED -> "message.britannia_mod.dye_preview.expired";
            case SESSION_MISSING, SESSION_MISMATCH, SESSION_REPLAYED ->
                    "message.britannia_mod.dye_preview.stale";
            case MAIN_HAND_CHANGED -> "message.britannia_mod.dye_preview.hand_changed";
            case OFF_HAND_CHANGED, BANNER_STATE_CHANGED -> "message.britannia_mod.dye_preview.banner_changed";
            case TUB_STATE_CHANGED, TUB_DEPLETED -> "message.britannia_mod.dye_preview.tub_changed";
            case REGISTRY_CHANGED, RESOLVER_RESULT_CHANGED -> "message.britannia_mod.dye_preview.registry_changed";
            case BANNER_UPDATE_INVALID, UNEXPECTED_APPLY_FAILURE ->
                    "message.britannia_mod.dye_preview.application_failure";
        };
    }
}
