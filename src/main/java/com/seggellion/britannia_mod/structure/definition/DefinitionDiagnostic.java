package com.seggellion.britannia_mod.structure.definition;

import java.util.Objects;

/** A typed, structured definition validation failure. */
public record DefinitionDiagnostic(Code code, String location, String message) {
    public DefinitionDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    public enum Code {
        DUPLICATE_FAMILY_ID,
        DUPLICATE_VARIANT_ID,
        DUPLICATE_CYCLE_POSITION,
        INVALID_CYCLE_POSITION,
        MISSING_DEFAULT_VARIANT,
        DISABLED_DEFAULT_VARIANT,
        INVALID_DIMENSIONS,
        DIMENSIONS_EXCEED_PART_ENCODING,
        EMPTY_FOOTPRINT,
        DUPLICATE_OCCUPIED_OFFSET,
        INVALID_ANCHOR_OFFSET,
        MISSING_ANCHOR_OFFSET,
        DUPLICATE_ANCHOR_OFFSET,
        OCCUPIED_OFFSET_OUTSIDE_DIMENSIONS,
        OCCUPIED_OFFSET_EXCEEDS_PART_ENCODING,
        INVALID_RESOURCE_NAMESPACE,
        INVALID_RESOURCE_PATH,
        RESOURCE_AVAILABILITY_MISMATCH,
        MISSING_CONTENT_STATUS,
        SHARED_GEOMETRY_REQUIRED,
        SHRINE_FAMILY_CONTRACT_MISMATCH,
        MONOLITH_FAMILY_CONTRACT_MISMATCH,
        SHRINE_GEOMETRY_MISMATCH,
        CROSS_FAMILY_VARIANT,
        VARIANT_DIMENSIONS_MISMATCH,
        VARIANT_FOOTPRINT_MISMATCH,
        VARIANT_PLACEMENT_MODE_MISMATCH,
        VARIANT_COLLISION_PROFILE_MISMATCH,
        VARIANT_RENDER_ORIGIN_MISMATCH,
        VARIANT_RENDER_OFFSET_MISMATCH,
        CYCLE_CANDIDATE_INCOMPATIBLE
    }
}
