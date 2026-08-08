package com.seggellion.britannia_mod.service.spawn.wirecontract;

import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClientResult;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnDeliveryDecision;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOperationRequest;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOutcome;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRequestAdapter;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnProtocolResponse;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnResponseParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 5 wire-contract closeout, Part C: loads the real Rails responses (captured
 * verbatim from the live {@code Api::ServiceNpcSpawnOperationsController} in the
 * ultimacraft-website worktree and copied into {@code src/test/resources/wire_contract/responses/})
 * through the real {@link ServiceNpcSpawnResponseParser} — not a hand-mocked response object —
 * and asserts each is interpreted exactly as the documented contract requires. This closes the
 * loop: Part A proved NeoForge's real bytes out, Part B proved Rails' real handling of those
 * bytes, and this proves NeoForge's real parsing of Rails' real bytes back.
 */
class ServiceNpcSpawnWireContractResponseFixtureTest {
    private static final String RESOURCE_ROOT = "wire_contract/responses/";

    @Test
    void freshRegistrationUpsertIsAppliedAndLive() {
        ServiceNpcSpawnProtocolResponse response = parse("fresh_registration_upsert", 200);
        assertEquals(ServiceNpcSpawnOutcome.APPLIED, response.outcome());
        assertTrue(response.success());
        assertFalse(response.retryable());
        assertEquals(ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE, response.registrationState());
        assertEquals(1L, response.acknowledgedRevision());
        assertNotNull(response.acknowledgedAt());
    }

    @Test
    void identicalRetryUpsertIsAlreadyAppliedAndLive() {
        ServiceNpcSpawnProtocolResponse response = parse("identical_retry_upsert", 200);
        assertEquals(ServiceNpcSpawnOutcome.ALREADY_APPLIED, response.outcome());
        assertTrue(response.success());
        assertEquals(ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE, response.registrationState());
        assertEquals(1L, response.acknowledgedRevision());
    }

    @Test
    void revisionPlusOneUpsertIsAppliedAtNewRevision() {
        ServiceNpcSpawnProtocolResponse response = parse("revision_plus_one_upsert", 200);
        assertEquals(ServiceNpcSpawnOutcome.APPLIED, response.outcome());
        assertEquals(ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE, response.registrationState());
        assertEquals(2L, response.acknowledgedRevision());
    }

    @Test
    void staleLowerRevisionUpsertIsRejectedAsPermanentFailure() {
        ServiceNpcSpawnClientResult.Protocol protocol =
            parseProtocol("stale_lower_revision_upsert", 409, "stale_lower_revision_upsert");
        ServiceNpcSpawnProtocolResponse response = protocol.response();
        assertEquals(ServiceNpcSpawnOutcome.STALE_REVISION, response.outcome());
        assertFalse(response.success());
        assertEquals(2L, response.currentRevision());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.PERMANENT, protocol.disposition());
        assertEquals(ServiceNpcSpawnDeliveryDecision.PERMANENT_FAILURE, ServiceNpcSpawnDeliveryDecision.classify(protocol));
    }

    @Test
    void removeTombstoneIsAppliedAndRemoved() {
        ServiceNpcSpawnProtocolResponse response = parse("remove_tombstone", 200, "remove_tombstone");
        assertEquals(ServiceNpcSpawnOutcome.APPLIED, response.outcome());
        assertEquals(ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED, response.registrationState());
        assertEquals(1L, response.acknowledgedRevision());
    }

    @Test
    void restoredTombstoneResubmissionTriggersCollisionRepairNotResurrection() {
        ServiceNpcSpawnClientResult.Protocol protocol =
            parseProtocol("restored_tombstone_resubmission", 409, "restored_tombstone_resubmission");
        ServiceNpcSpawnProtocolResponse response = protocol.response();
        assertEquals(ServiceNpcSpawnOutcome.UUID_COLLISION, response.outcome());
        assertFalse(response.success());
        assertEquals(ServiceNpcSpawnProtocolResponse.CollisionKind.TOMBSTONED, response.collisionKind());
        assertEquals(Boolean.TRUE, response.replacementUuidRequired());
        assertNull(response.canonicalLocation());
        assertEquals(ServiceNpcSpawnClientResult.Disposition.UUID_COLLISION, protocol.disposition());

        // The exact decision the delivery processor's complete()/applyCollision() switch relies on:
        // this must route into the existing collision-repair path, never a local "still LIVE" read.
        assertEquals(ServiceNpcSpawnDeliveryDecision.COLLISION_REPAIR, ServiceNpcSpawnDeliveryDecision.classify(protocol));
    }

    @Test
    void sameShardCollisionAtDifferentCoordinateCarriesCanonicalLocationAndTriggersRepair() {
        ServiceNpcSpawnClientResult.Protocol protocol = parseProtocol(
            "same_shard_uuid_collision_different_coordinate", 409,
            "same_shard_uuid_collision_different_coordinate"
        );
        ServiceNpcSpawnProtocolResponse response = protocol.response();
        assertEquals(ServiceNpcSpawnOutcome.UUID_COLLISION, response.outcome());
        assertEquals(ServiceNpcSpawnProtocolResponse.CollisionKind.LIVE, response.collisionKind());
        assertEquals(Boolean.TRUE, response.replacementUuidRequired());
        assertNotNull(response.canonicalLocation());
        assertEquals(ServiceNpcSpawnWireContractScenarios.DIMENSION, response.canonicalLocation().dimension());
        assertEquals(100_000, response.canonicalLocation().x());
        assertEquals(64, response.canonicalLocation().y());
        assertEquals(200_000, response.canonicalLocation().z());
        assertEquals(ServiceNpcSpawnDeliveryDecision.COLLISION_REPAIR, ServiceNpcSpawnDeliveryDecision.classify(protocol));
    }

    @Test
    void crossShardCollisionIsRedactedWithNoLocationDetailButStillTriggersRepair() {
        ServiceNpcSpawnClientResult.Protocol protocol =
            parseProtocol("cross_shard_collision", 409, "cross_shard_collision");
        ServiceNpcSpawnProtocolResponse response = protocol.response();
        assertEquals(ServiceNpcSpawnOutcome.UUID_COLLISION, response.outcome());
        assertEquals(ServiceNpcSpawnProtocolResponse.CollisionKind.REDACTED, response.collisionKind());
        assertEquals(Boolean.TRUE, response.replacementUuidRequired());
        assertNull(response.canonicalLocation());
        assertNull(response.currentRevision());
        assertEquals(ServiceNpcSpawnDeliveryDecision.COLLISION_REPAIR, ServiceNpcSpawnDeliveryDecision.classify(protocol));
    }

    private static ServiceNpcSpawnProtocolResponse parse(String scenarioName, int status) {
        return parse(scenarioName, status, scenarioName);
    }

    private static ServiceNpcSpawnProtocolResponse parse(String scenarioName, int status, String requestScenarioName) {
        return parseProtocol(scenarioName, status, requestScenarioName).response();
    }

    private static ServiceNpcSpawnClientResult.Protocol parseProtocol(
        String scenarioName, int status, String requestScenarioName
    ) {
        byte[] body = readFixture(scenarioName);
        ServiceNpcSpawnOperationRequest submitted = ServiceNpcSpawnPendingRequestAdapter.adapt(
            ServiceNpcSpawnWireContractScenarios.all().get(requestScenarioName)
        );
        ServiceNpcSpawnClientResult result =
            ServiceNpcSpawnResponseParser.parse(status, body, Map.of(), submitted);
        assertInstanceOf(ServiceNpcSpawnClientResult.Protocol.class, result,
            () -> "expected a parsed protocol envelope for '" + scenarioName + "' but got " + result);
        return (ServiceNpcSpawnClientResult.Protocol) result;
    }

    private static byte[] readFixture(String scenarioName) {
        String path = RESOURCE_ROOT + scenarioName + ".json";
        try (InputStream in = ServiceNpcSpawnWireContractResponseFixtureTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (in == null) fail("missing checked-in response fixture: " + path);
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
