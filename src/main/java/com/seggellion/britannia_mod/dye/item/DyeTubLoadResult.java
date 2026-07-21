package com.seggellion.britannia_mod.dye.item;

public enum DyeTubLoadResult {
    LOADED,
    REPLACED,
    ALREADY_CONTAINS,
    INVALID_TUB,
    EMPTY_OFF_HAND,
    INVALID_OFF_HAND_ITEM,
    REGISTRY_UNAVAILABLE,
    PIGMENT_DEFINITION_MISSING,
    PIGMENT_DISABLED,
    STATE_COMPONENT_FAILURE;

    public boolean loadedSuccessfully() {
        return this == LOADED || this == REPLACED;
    }

    public boolean emitsSuccessEffects() {
        return loadedSuccessfully();
    }
}
