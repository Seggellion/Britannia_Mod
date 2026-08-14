package com.seggellion.britannia_mod.entity;

/** Pure weighted selector kept separate from entity bootstrap for deterministic testing. */
public final class IbisAnimationPolicy {
    public static final int EATING_WEIGHT = 8;
    public static final int IDLE_WEIGHT = 5;
    public static final int TOTAL_WEIGHT = EATING_WEIGHT + IDLE_WEIGHT;

    private IbisAnimationPolicy() {
    }

    /** Eight eating cycles for every five idle cycles is exactly 60% more frequent. */
    public static boolean usesEatingAnimation(int weightedRoll) {
        return weightedRoll >= 0 && weightedRoll < EATING_WEIGHT;
    }
}
