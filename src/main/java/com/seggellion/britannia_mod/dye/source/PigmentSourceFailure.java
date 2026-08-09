package com.seggellion.britannia_mod.dye.source;

public enum PigmentSourceFailure {
    NONE,
    REGISTRY_UNAVAILABLE,
    PIGMENT_MISSING,
    PIGMENT_DISABLED,
    ITEM_MAPPING_MISSING,
    INVALID_COUNT,
    COUNT_EXCEEDS_STACK_LIMIT
}
