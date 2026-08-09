package com.seggellion.britannia_mod.structure.multiblock;

/** Saved-state/content classification. Stable IDs are never rewritten by this status. */
public enum PlacedStructureStatus {
    VALID,
    MISSING_FAMILY_DEFINITION,
    MISSING_VARIANT_DEFINITION,
    UNINITIALIZED,
    STRUCTURALLY_INVALID,
    UNSUPPORTED_FUTURE_SCHEMA,
    MALFORMED
}
