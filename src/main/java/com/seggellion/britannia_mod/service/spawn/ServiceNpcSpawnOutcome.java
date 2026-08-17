package com.seggellion.britannia_mod.service.spawn;

/**
 * Exact closed outcome registry, mirroring {@code ServiceNpcSpawnProtocol::OUTCOMES} on Rails.
 *
 * <p>This enum must stay a superset of what Rails can emit. An outcome Rails sends that is
 * missing here parses to null, which the response parser reports as
 * {@code unknown_response_outcome} -- a PROTOCOL_INCOMPATIBLE disposition that
 * {@link ServiceNpcSpawnDeliveryDecision} retries at the slow interval FOREVER, because a
 * protocol mismatch is assumed to be a transient version skew that a server upgrade will
 * clear. That is the right default for a genuinely unknown protocol, and exactly the wrong
 * outcome for a permanent domain rejection wearing an unrecognized name: the operation can
 * never succeed, but nothing surfaces it and the outbox never drains.
 *
 * <p>The economic entries below were added by Vendor/Trader Milestone 5 on the Rails side and
 * were missing here, so every migrated legacy vendor post retried its 422 indefinitely instead
 * of parking as a permanent, operator-actionable failure. When Rails adds an outcome, add it
 * here in the same change.
 */
public enum ServiceNpcSpawnOutcome {
    APPLIED, ALREADY_APPLIED,
    UNAUTHORIZED, SERVER_NOT_AUTHORIZED,
    MALFORMED_REQUEST, UNSUPPORTED_PROTOCOL, UNSUPPORTED_OPERATION, UNEXPECTED_FIELD,
    INVALID_OPERATION_ID, INVALID_SPAWN_UUID, INVALID_REVISION, INVALID_LOCATION,
    INVALID_WORLD_NAME, INVALID_DIMENSION, INVALID_COORDINATES, PAYLOAD_TOO_LARGE,
    INVALID_CITY, INVALID_SERVICE_NPC_TYPE, SERVICE_NPC_TYPE_INACTIVE,
    SERVICE_NPC_TYPE_NOT_SPAWNABLE,
    INVALID_ECONOMIC_NPC_TYPE, ECONOMIC_NPC_TYPE_INACTIVE,
    ECONOMIC_NPC_TYPE_NOT_SPAWNABLE, AMBIGUOUS_NPC_TYPE,
    STALE_REVISION, REVISION_CONFLICT, UUID_COLLISION, LOCATION_OCCUPIED,
    SERVICE_UNAVAILABLE;

    public static ServiceNpcSpawnOutcome parse(String value) {
        try { return valueOf(value); } catch (RuntimeException invalid) { return null; }
    }
}
