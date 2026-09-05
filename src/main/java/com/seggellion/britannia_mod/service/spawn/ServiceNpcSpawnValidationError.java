package com.seggellion.britannia_mod.service.spawn;

public enum ServiceNpcSpawnValidationError {
    NONE,
    NO_CHANGE,
    UNAUTHORIZED,
    WRONG_MENU,
    WRONG_OWNER,
    CONTAINER_MISMATCH,
    WRONG_DIMENSION,
    TOO_FAR,
    WRONG_POSITION,
    MISSING_BLOCK,
    WRONG_BLOCK,
    WRONG_BLOCK_ENTITY,
    UUID_MISMATCH,
    STALE_REVISION,
    REGISTRY_UNAVAILABLE,
    CITY_UNAVAILABLE,
    TYPE_UNAVAILABLE,
    TYPE_INACTIVE,
    TYPE_NOT_SPAWNABLE,
    OVERSIZED_FIELD,
    PAYLOAD_TOO_LARGE,
    REVISION_OVERFLOW,
    PENDING_STORE_ERROR,
    CLAIM_STORE_ERROR,
    /**
     * No server credentials are configured, so the shard this post belongs to is unknown.
     * Refusing is deliberate: the record is durable, and a guessed shard would be replayed
     * after every restart and rejected by the delivery processor as a mismatch.
     */
    CREDENTIALS_UNAVAILABLE,
    INTERNAL_ERROR
}
