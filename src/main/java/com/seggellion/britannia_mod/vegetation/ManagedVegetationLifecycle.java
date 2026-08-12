package com.seggellion.britannia_mod.vegetation;

/** Persisted lifecycle states for one canonical managed vegetation position. */
public enum ManagedVegetationLifecycle {
    REGROWING,
    SHORT_GRASS,
    TALL_GRASS,
    FERN,
    BLOOD_MOSS,
    FLOWER;

    public boolean occupied() {
        return this != REGROWING;
    }
}
