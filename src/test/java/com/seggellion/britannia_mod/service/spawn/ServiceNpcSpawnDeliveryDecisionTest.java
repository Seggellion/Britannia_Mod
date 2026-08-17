package com.seggellion.britannia_mod.service.spawn;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClientResult.Disposition;

class ServiceNpcSpawnDeliveryDecisionTest {
    @Test
    void successesTransientFailuresAndCancellationMapExactly() {
        assertDecision(ServiceNpcSpawnDeliveryDecision.ACKNOWLEDGE_SUCCESS,
            protocol(ServiceNpcSpawnOutcome.APPLIED, Disposition.SUCCESS));
        assertDecision(ServiceNpcSpawnDeliveryDecision.ACKNOWLEDGE_SUCCESS,
            protocol(ServiceNpcSpawnOutcome.ALREADY_APPLIED, Disposition.SUCCESS));
        for (String code : new String[] {
            "connect_timeout", "read_timeout", "overall_timeout", "executor_saturated", "transport_error"
        }) {
            assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_WITH_BACKOFF,
                new ServiceNpcSpawnClientResult.TransportFailure(code));
        }
        assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_WITH_BACKOFF,
            new ServiceNpcSpawnClientResult.HttpFailure("rate_limited", Disposition.RETRYABLE, Duration.ofSeconds(5)));
        for (int status : new int[] {500, 502, 503, 504}) {
            assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_WITH_BACKOFF,
                new ServiceNpcSpawnClientResult.HttpFailure("server_failure_" + status, Disposition.RETRYABLE, null));
        }
        assertDecision(ServiceNpcSpawnDeliveryDecision.NO_CHANGE, new ServiceNpcSpawnClientResult.Cancelled());
    }

    @Test
    void compatibilityAndMalformedResponsesUseSlowRetry() {
        assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_AT_SLOW_INTERVAL,
            new ServiceNpcSpawnClientResult.HttpFailure("endpoint_unavailable", Disposition.RETRYABLE, null));
        for (String code : new String[] {
            "unsupported_response_protocol", "unknown_response_outcome", "malformed_protocol_response"
        }) {
            assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_AT_SLOW_INTERVAL,
                new ServiceNpcSpawnClientResult.HttpFailure(code, Disposition.PROTOCOL_INCOMPATIBLE, null));
        }
        assertDecision(ServiceNpcSpawnDeliveryDecision.RETRY_AT_SLOW_INTERVAL,
            new ServiceNpcSpawnClientResult.TransportFailure("response_too_large"));
    }

    @Test
    void authenticationAndConfigurationRemainCorrectable() {
        for (String code : new String[] {
            "credentials_unavailable", "minecraft_server_key_missing", "minecraft_server_key_invalid"
        }) {
            assertDecision(ServiceNpcSpawnDeliveryDecision.AUTHENTICATION_BLOCKED,
                new ServiceNpcSpawnClientResult.LocalFailure(code));
        }
        assertDecision(ServiceNpcSpawnDeliveryDecision.AUTHENTICATION_BLOCKED,
            protocol(ServiceNpcSpawnOutcome.UNAUTHORIZED, Disposition.AUTHENTICATION_BLOCKED));
        assertDecision(ServiceNpcSpawnDeliveryDecision.AUTHENTICATION_BLOCKED,
            protocol(ServiceNpcSpawnOutcome.SERVER_NOT_AUTHORIZED, Disposition.AUTHENTICATION_BLOCKED));
    }

    @Test
    void knownDomainFailuresRemainDurablePermanentFailures() {
        for (ServiceNpcSpawnOutcome outcome : new ServiceNpcSpawnOutcome[] {
            ServiceNpcSpawnOutcome.INVALID_CITY,
            ServiceNpcSpawnOutcome.INVALID_SERVICE_NPC_TYPE,
            ServiceNpcSpawnOutcome.SERVICE_NPC_TYPE_INACTIVE,
            ServiceNpcSpawnOutcome.SERVICE_NPC_TYPE_NOT_SPAWNABLE,
            ServiceNpcSpawnOutcome.INVALID_ECONOMIC_NPC_TYPE,
            ServiceNpcSpawnOutcome.ECONOMIC_NPC_TYPE_INACTIVE,
            ServiceNpcSpawnOutcome.ECONOMIC_NPC_TYPE_NOT_SPAWNABLE,
            ServiceNpcSpawnOutcome.AMBIGUOUS_NPC_TYPE,
            ServiceNpcSpawnOutcome.STALE_REVISION,
            ServiceNpcSpawnOutcome.REVISION_CONFLICT,
            ServiceNpcSpawnOutcome.LOCATION_OCCUPIED,
            ServiceNpcSpawnOutcome.INVALID_LOCATION,
            ServiceNpcSpawnOutcome.INVALID_DIMENSION,
            ServiceNpcSpawnOutcome.INVALID_COORDINATES,
            ServiceNpcSpawnOutcome.UNSUPPORTED_OPERATION
        }) {
            assertDecision(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE,
                protocol(outcome, Disposition.PERMANENT));
        }
        assertDecision(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE,
            new ServiceNpcSpawnClientResult.HttpFailure("unexpected_client_response", Disposition.PERMANENT, null));
    }

    @Test
    void onlyValidatedReplacementCollisionEntersRepair() {
        ServiceNpcSpawnProtocolResponse valid = response(ServiceNpcSpawnOutcome.UUID_COLLISION, false,
            ServiceNpcSpawnProtocolResponse.CollisionKind.REDACTED, true);
        assertDecision(ServiceNpcSpawnDeliveryDecision.COLLISION_REPAIR,
            new ServiceNpcSpawnClientResult.Protocol(valid, Disposition.UUID_COLLISION));
        ServiceNpcSpawnProtocolResponse malformed = response(ServiceNpcSpawnOutcome.UUID_COLLISION, false,
            null, null);
        assertNotEquals(ServiceNpcSpawnDeliveryDecision.COLLISION_REPAIR,
            ServiceNpcSpawnDeliveryDecision.classify(
                new ServiceNpcSpawnClientResult.Protocol(malformed, Disposition.UUID_COLLISION)
            ));
        assertDecision(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE,
            new ServiceNpcSpawnClientResult.HttpFailure("unexpected_client_response", Disposition.PERMANENT, null));
    }

    private static ServiceNpcSpawnClientResult.Protocol protocol(
        ServiceNpcSpawnOutcome outcome, Disposition disposition
    ) {
        return new ServiceNpcSpawnClientResult.Protocol(
            response(outcome, outcome == ServiceNpcSpawnOutcome.APPLIED
                || outcome == ServiceNpcSpawnOutcome.ALREADY_APPLIED, null, null),
            disposition
        );
    }

    private static ServiceNpcSpawnProtocolResponse response(
        ServiceNpcSpawnOutcome outcome,
        boolean success,
        ServiceNpcSpawnProtocolResponse.CollisionKind collisionKind,
        Boolean replacement
    ) {
        return new ServiceNpcSpawnProtocolResponse(
            1, success, outcome, outcome == ServiceNpcSpawnOutcome.SERVICE_UNAVAILABLE,
            UUID.randomUUID(), UUID.randomUUID(), 1L, success ? 1L : null, null,
            success ? ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE : null,
            success ? java.time.Instant.ofEpochMilli(100) : null, true, null,
            collisionKind, replacement, null, null
        );
    }

    private static void assertDecision(
        ServiceNpcSpawnDeliveryDecision expected, ServiceNpcSpawnClientResult result
    ) {
        assertEquals(expected, ServiceNpcSpawnDeliveryDecision.classify(result));
    }
}
