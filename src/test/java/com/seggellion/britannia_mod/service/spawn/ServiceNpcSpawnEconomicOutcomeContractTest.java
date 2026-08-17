package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression cover for the defect that stranded every migrated legacy vendor post in Britain.
 *
 * <p>Rails grew four NPC-type outcomes with Vendor/Trader Milestone 5 that
 * {@link ServiceNpcSpawnOutcome} never learned. An unrecognised outcome parses to null, which
 * the parser reports as {@code unknown_response_outcome} -- a PROTOCOL_INCOMPATIBLE disposition
 * classified as {@link ServiceNpcSpawnDeliveryDecision#RETRY_AT_SLOW_INTERVAL}. So a permanent
 * 422 ("this economic type is not spawnable", fixable only by an operator activating the row)
 * was retried every five minutes forever, the outbox never drained, and the real reason never
 * reached the durable record or any operator-visible surface.
 *
 * <p>The first test is the guard that matters: it pins the mod's vocabulary against Rails' own,
 * so the next outcome Rails adds fails here instead of silently becoming an infinite retry.
 */
class ServiceNpcSpawnEconomicOutcomeContractTest {
    /**
     * Verbatim mirror of {@code ServiceNpcSpawnProtocol::OUTCOMES}
     * (ultimacraft-website, app/services/service_npc_spawn_protocol.rb). Update both together.
     */
    private static final String[] RAILS_OUTCOMES = {
        "APPLIED", "ALREADY_APPLIED",
        "UNAUTHORIZED", "SERVER_NOT_AUTHORIZED",
        "MALFORMED_REQUEST", "UNSUPPORTED_PROTOCOL", "UNSUPPORTED_OPERATION", "UNEXPECTED_FIELD",
        "INVALID_OPERATION_ID", "INVALID_SPAWN_UUID", "INVALID_REVISION", "INVALID_LOCATION",
        "INVALID_WORLD_NAME", "INVALID_DIMENSION", "INVALID_COORDINATES", "PAYLOAD_TOO_LARGE",
        "INVALID_CITY", "INVALID_SERVICE_NPC_TYPE", "SERVICE_NPC_TYPE_INACTIVE",
        "SERVICE_NPC_TYPE_NOT_SPAWNABLE",
        "INVALID_ECONOMIC_NPC_TYPE", "ECONOMIC_NPC_TYPE_INACTIVE",
        "ECONOMIC_NPC_TYPE_NOT_SPAWNABLE", "AMBIGUOUS_NPC_TYPE",
        "STALE_REVISION", "REVISION_CONFLICT", "UUID_COLLISION", "LOCATION_OCCUPIED",
        "SERVICE_UNAVAILABLE"
    };

    /** The four Rails answers to an NPC-type problem, every one a 422. */
    private static final String[] NPC_TYPE_REJECTIONS = {
        "INVALID_ECONOMIC_NPC_TYPE", "ECONOMIC_NPC_TYPE_INACTIVE",
        "ECONOMIC_NPC_TYPE_NOT_SPAWNABLE", "AMBIGUOUS_NPC_TYPE"
    };

    private static final UUID OPERATION_ID = UUID.fromString("81cf9c28-768e-49a4-a7c0-f7c1bd0de6c9");
    private static final UUID SPAWN_UUID = UUID.fromString("09284089-e35d-4e4f-be9e-9a3c70a8adbb");

    @Test
    void everyOutcomeRailsCanEmitIsKnownHere() {
        for (String outcome : RAILS_OUTCOMES) {
            assertNotNull(ServiceNpcSpawnOutcome.parse(outcome),
                () -> "Rails can emit '" + outcome + "' but ServiceNpcSpawnOutcome does not know it, "
                    + "so it would be misread as a protocol mismatch and retried forever");
        }
    }

    @Test
    void economicTypeRejectionsParkPermanentlyInsteadOfRetryingForever() {
        for (String outcome : NPC_TYPE_REJECTIONS) {
            ServiceNpcSpawnClientResult result = parseRejection(outcome);
            assertInstanceOf(ServiceNpcSpawnClientResult.Protocol.class, result,
                () -> "'" + outcome + "' must parse as a protocol envelope, not a transport-level failure");
            assertEquals(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE,
                ServiceNpcSpawnDeliveryDecision.classify(result),
                () -> "'" + outcome + "' can never succeed on retry and must stop the outbox retrying it");
            assertNotEquals("protocol_incompatible", ServiceNpcSpawnFailureCodes.from(result),
                () -> "'" + outcome + "' must record its real reason, not a protocol mismatch");
        }
    }

    @Test
    void recordedFailureCodesNameTheRowAnOperatorHasToFix() {
        assertEquals("invalid_economic_npc_type",
            ServiceNpcSpawnFailureCodes.from(parseRejection("INVALID_ECONOMIC_NPC_TYPE")));
        assertEquals("economic_npc_type_inactive",
            ServiceNpcSpawnFailureCodes.from(parseRejection("ECONOMIC_NPC_TYPE_INACTIVE")));
        assertEquals("economic_npc_type_not_spawnable",
            ServiceNpcSpawnFailureCodes.from(parseRejection("ECONOMIC_NPC_TYPE_NOT_SPAWNABLE")));
        assertEquals("ambiguous_npc_type",
            ServiceNpcSpawnFailureCodes.from(parseRejection("AMBIGUOUS_NPC_TYPE")));
    }

    @Test
    void anOutcomeRailsCannotEmitIsStillTreatedAsAProtocolMismatch() {
        ServiceNpcSpawnClientResult result = parse(422, body("SOME_FUTURE_OUTCOME"));
        assertInstanceOf(ServiceNpcSpawnClientResult.HttpFailure.class, result);
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE,
            ((ServiceNpcSpawnClientResult.HttpFailure) result).disposition());
    }

    private static ServiceNpcSpawnClientResult parseRejection(String outcome) {
        return parse(422, body(outcome));
    }

    /** Exactly what {@code ServiceNpcSpawnPoints::Result#as_json} emits for a domain error. */
    private static String body(String outcome) {
        return "{\"protocol_version\":1,\"success\":false,\"outcome\":\"" + outcome + "\","
            + "\"retryable\":false,\"operation_id\":\"" + OPERATION_ID + "\","
            + "\"spawn_uuid\":\"" + SPAWN_UUID + "\",\"submitted_revision\":1}";
    }

    private static ServiceNpcSpawnClientResult parse(int status, String body) {
        return ServiceNpcSpawnResponseParser.parse(
            status, body.getBytes(StandardCharsets.UTF_8), Map.of(), submittedUpsert()
        );
    }

    /** The baker post migrated at 5164, 72, 4147 -- one of the ten this defect stranded. */
    private static ServiceNpcSpawnOperationRequest submittedUpsert() {
        return new ServiceNpcSpawnOperationRequest(
            ServiceNpcSpawnOperationRequest.PROTOCOL_VERSION,
            OPERATION_ID,
            ServiceNpcSpawnOperation.UPSERT,
            SPAWN_UUID,
            1L,
            "Britannia-sept-9",
            ResourceLocation.parse("minecraft:overworld"),
            5164, 72, 4147,
            UUID.fromString("b7ad5baf-c1a2-4081-ae7c-59cd359c6074"),
            "economic:baker",
            true
        );
    }
}
