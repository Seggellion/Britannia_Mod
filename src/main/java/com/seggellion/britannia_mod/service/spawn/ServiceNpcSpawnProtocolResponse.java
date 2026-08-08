package com.seggellion.britannia_mod.service.spawn;

import java.time.Instant;
import java.util.UUID;

/** Validated Rails protocol envelope. Nullable members represent contract-optional fields. */
public record ServiceNpcSpawnProtocolResponse(
    int protocolVersion,
    boolean success,
    ServiceNpcSpawnOutcome outcome,
    boolean retryable,
    UUID operationId,
    UUID spawnUuid,
    Long submittedRevision,
    Long acknowledgedRevision,
    Long currentRevision,
    RegistrationState registrationState,
    Instant acknowledgedAt,
    Boolean created,
    String field,
    CollisionKind collisionKind,
    Boolean replacementUuidRequired,
    ServiceNpcSpawnCanonicalLocation canonicalLocation,
    UUID occupyingSpawnUuid
) {
    public enum RegistrationState { LIVE, REMOVED }
    public enum CollisionKind { LIVE, TOMBSTONED, REDACTED }
}
