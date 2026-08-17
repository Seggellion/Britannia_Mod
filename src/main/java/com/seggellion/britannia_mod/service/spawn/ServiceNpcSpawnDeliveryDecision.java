package com.seggellion.britannia_mod.service.spawn;

public enum ServiceNpcSpawnDeliveryDecision {
    ACKNOWLEDGE_SUCCESS,
    RETRY_WITH_BACKOFF,
    RETRY_AT_SLOW_INTERVAL,
    AUTHENTICATION_BLOCKED,
    PERMANENT_FAILURE,
    COLLISION_REPAIR,
    NO_CHANGE;

    public static ServiceNpcSpawnDeliveryDecision classify(ServiceNpcSpawnClientResult result) {
        if (result instanceof ServiceNpcSpawnClientResult.Cancelled) return NO_CHANGE;
        if (result instanceof ServiceNpcSpawnClientResult.Protocol protocol) {
            return classifyProtocol(protocol);
        }
        if (result instanceof ServiceNpcSpawnClientResult.TransportFailure transport) {
            return transport.safeCode().equals("response_too_large")
                ? RETRY_AT_SLOW_INTERVAL : RETRY_WITH_BACKOFF;
        }
        if (result instanceof ServiceNpcSpawnClientResult.LocalFailure local) {
            return isConfigurationCode(local.safeCode()) ? AUTHENTICATION_BLOCKED : PERMANENT_FAILURE;
        }
        if (result instanceof ServiceNpcSpawnClientResult.HttpFailure http) {
            if (http.disposition() == ServiceNpcSpawnClientResult.Disposition.AUTHENTICATION_BLOCKED) {
                return AUTHENTICATION_BLOCKED;
            }
            if (http.safeCode().equals("endpoint_unavailable")
                || http.disposition() == ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE) {
                return RETRY_AT_SLOW_INTERVAL;
            }
            return http.retryable() ? RETRY_WITH_BACKOFF : PERMANENT_FAILURE;
        }
        return NO_CHANGE;
    }

    private static ServiceNpcSpawnDeliveryDecision classifyProtocol(ServiceNpcSpawnClientResult.Protocol protocol) {
        ServiceNpcSpawnOutcome outcome = protocol.response().outcome();
        return switch (outcome) {
            case APPLIED, ALREADY_APPLIED ->
                protocol.response().success() && !protocol.response().retryable()
                    && protocol.disposition() == ServiceNpcSpawnClientResult.Disposition.SUCCESS
                    ? ACKNOWLEDGE_SUCCESS : RETRY_AT_SLOW_INTERVAL;
            case UNAUTHORIZED, SERVER_NOT_AUTHORIZED -> AUTHENTICATION_BLOCKED;
            case SERVICE_UNAVAILABLE -> RETRY_WITH_BACKOFF;
            case UUID_COLLISION ->
                !protocol.response().success()
                    && protocol.response().collisionKind() != null
                    && Boolean.TRUE.equals(protocol.response().replacementUuidRequired())
                    ? COLLISION_REPAIR
                    : RETRY_AT_SLOW_INTERVAL;
            // Every remaining outcome is a domain rejection Rails will make again for an
            // identical payload, so retrying cannot help; only an operator changing the
            // configuration behind it can. Deliberately exhaustive with no default: adding an
            // outcome to ServiceNpcSpawnOutcome must fail compilation here rather than fall
            // through to a silently wrong classification.
            case UNSUPPORTED_PROTOCOL, UNSUPPORTED_OPERATION, MALFORMED_REQUEST, UNEXPECTED_FIELD,
                 INVALID_OPERATION_ID, INVALID_SPAWN_UUID, INVALID_REVISION, INVALID_LOCATION,
                 INVALID_WORLD_NAME, INVALID_DIMENSION, INVALID_COORDINATES, PAYLOAD_TOO_LARGE,
                 INVALID_CITY, INVALID_SERVICE_NPC_TYPE, SERVICE_NPC_TYPE_INACTIVE,
                 SERVICE_NPC_TYPE_NOT_SPAWNABLE, INVALID_ECONOMIC_NPC_TYPE,
                 ECONOMIC_NPC_TYPE_INACTIVE, ECONOMIC_NPC_TYPE_NOT_SPAWNABLE, AMBIGUOUS_NPC_TYPE,
                 STALE_REVISION, REVISION_CONFLICT,
                 LOCATION_OCCUPIED -> PERMANENT_FAILURE;
        };
    }

    private static boolean isConfigurationCode(String code) {
        return code != null && (code.equals("credentials_unavailable")
            || code.equals("minecraft_server_key_missing")
            || code.equals("minecraft_server_key_invalid"));
    }
}
