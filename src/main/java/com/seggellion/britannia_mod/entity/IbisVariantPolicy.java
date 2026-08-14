package com.seggellion.britannia_mod.entity;

/** Spawn-time weights for the two synchronized ibis appearances. */
public final class IbisVariantPolicy {
    public static final int TOTAL_WEIGHT = 100;
    public static final int SCARLET_WEIGHT = 35;

    private IbisVariantPolicy() {
    }

    public static boolean usesScarletVariant(int roll) {
        return roll >= 0 && roll < SCARLET_WEIGHT;
    }
}
