package com.seggellion.britannia_mod.vegetation;

/** Outcome of comparing persisted node ownership with its live world representation. */
public enum ManagedVegetationReconciliationResult {
    VALID,
    REPAIRABLE,
    OBSTRUCTED
}
