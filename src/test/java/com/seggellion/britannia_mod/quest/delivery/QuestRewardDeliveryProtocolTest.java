package com.seggellion.britannia_mod.quest.delivery;

import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Rowan farming questline M3: the acknowledgement round trip against the frozen fixtures. The
 * request body is byte-compared to {@code delivery_result_request.json} (LF-normalised), in the
 * spirit of {@code ServiceNpcSpawnWireContractRequestFixtureTest}: the bytes are re-derived from
 * the fixture's values by the real encoder and diffed against the committed file.
 */
class QuestRewardDeliveryProtocolTest {

    @Test
    void theAcknowledgementBodyIsByteIdenticalToTheFixture() {
        byte[] expected = QuestContractFixtures.text("delivery_result_request.json").getBytes(StandardCharsets.UTF_8);
        byte[] actual = QuestRewardDeliveryProtocol.encodeResult(new QuestRewardDeliveryProtocol.AcknowledgementRequest(
            UUID.fromString(QuestContractFixtures.DELIVERY_UUID),
            UUID.fromString(QuestContractFixtures.PLAYER_UUID),
            QuestRewardDeliveryProtocol.Outcome.APPLIED,
            "2026-09-06T21:10:04Z",
            UUID.fromString(QuestContractFixtures.REQUEST_UUID)));

        assertArrayEquals(expected, actual, () -> "acknowledgement body drifted from delivery_result_request.json"
            + "\nexpected: " + new String(expected, StandardCharsets.UTF_8)
            + "\nactual:   " + new String(actual, StandardCharsets.UTF_8));
    }

    @Test
    void theQueuedOutcomeOnlyChangesTheOutcomeLine() {
        String applied = new String(QuestRewardDeliveryProtocol.encodeResult(request(QuestRewardDeliveryProtocol.Outcome.APPLIED)),
            StandardCharsets.UTF_8);
        String queued = new String(QuestRewardDeliveryProtocol.encodeResult(request(QuestRewardDeliveryProtocol.Outcome.QUEUED)),
            StandardCharsets.UTF_8);
        assertEquals(applied.replace("\"outcome\": \"applied\"", "\"outcome\": \"queued\""), queued);
    }

    @Test
    void recordedAtIsIsoUtcAtWholeSeconds() {
        // Derived from the instant rather than written out as a constant: a hand-computed epoch
        // is exactly where a local-time offset creeps into a contract that is UTC by definition.
        long millis = Instant.parse("2026-09-06T21:10:04.789Z").toEpochMilli();
        assertEquals("2026-09-06T21:10:04Z", QuestRewardDeliveryProtocol.formatRecordedAt(millis),
            "the sub-second part is truncated, never rounded, and the zone is always UTC");
        assertEquals("1970-01-01T00:00:00Z", QuestRewardDeliveryProtocol.formatRecordedAt(0L));
    }

    @Test
    void theResponseFixtureParses() {
        QuestRewardDeliveryProtocol.AcknowledgementResponse response =
            QuestRewardDeliveryProtocol.parseResult(QuestContractFixtures.bytes("delivery_result_response.json"));
        assertEquals(1, response.protocolVersion());
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID), response.deliveryUuid());
        assertEquals("acknowledged", response.state());
        assertEquals("applied", response.outcome());
        assertFalse(response.duplicate());
    }

    @Test
    void aResponseFromAnotherProtocolVersionIsMalformed() {
        String body = QuestContractFixtures.text("delivery_result_response.json").replace("\"protocol_version\": 1", "\"protocol_version\": 2");
        assertThrows(QuestRewardDeliveryProtocol.MalformedProtocolException.class,
            () -> QuestRewardDeliveryProtocol.parseResult(body.getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestRewardDeliveryProtocol.MalformedProtocolException.class,
            () -> QuestRewardDeliveryProtocol.parseResult("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestRewardDeliveryProtocol.MalformedProtocolException.class,
            () -> QuestRewardDeliveryProtocol.parseResult("{\"protocol_version\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theErrorEnvelopeCodeIsReadAndBounded() {
        assertEquals("delivery_not_found", QuestRewardDeliveryProtocol.errorCode(
            "{\"error\":\"delivery_not_found\",\"detail\":\"x\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestRewardDeliveryProtocol.errorCode("<html>Not Found</html>".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestRewardDeliveryProtocol.errorCode(new byte[0]));
        assertEquals("", QuestRewardDeliveryProtocol.errorCode("{\"error\":\"bad code!\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theStatusMappingSeparatesTerminalFromRetryableFromMissingRoute() {
        byte[] notFound = "{\"error\":\"delivery_not_found\"}".getBytes(StandardCharsets.UTF_8);
        byte[] conflict = "{\"error\":\"conflicting_delivery_result\"}".getBytes(StandardCharsets.UTF_8);
        byte[] mismatch = "{\"error\":\"player_mismatch\"}".getBytes(StandardCharsets.UTF_8);
        byte[] routing = "<html>404</html>".getBytes(StandardCharsets.UTF_8);

        assertEquals("delivery_not_found", assertInstanceOf(QuestRewardDeliveryClient.TerminalRejection.class,
            QuestRewardDeliveryClient.classifyAcknowledge(404, notFound)).code());
        assertEquals("conflicting_delivery_result", assertInstanceOf(QuestRewardDeliveryClient.TerminalRejection.class,
            QuestRewardDeliveryClient.classifyAcknowledge(409, conflict)).code());
        assertEquals("player_mismatch", assertInstanceOf(QuestRewardDeliveryClient.TerminalRejection.class,
            QuestRewardDeliveryClient.classifyAcknowledge(409, mismatch)).code());
        assertInstanceOf(QuestRewardDeliveryClient.EndpointUnsupported.class,
            QuestRewardDeliveryClient.classifyAcknowledge(404, routing));
        assertEquals("service_unavailable", assertInstanceOf(QuestRewardDeliveryClient.Failure.class,
            QuestRewardDeliveryClient.classifyAcknowledge(503, new byte[0])).safeCode());
        assertEquals("http_status_failure", assertInstanceOf(QuestRewardDeliveryClient.Failure.class,
            QuestRewardDeliveryClient.classifyAcknowledge(500, new byte[0])).safeCode());
        assertEquals("rate_limited", assertInstanceOf(QuestRewardDeliveryClient.Failure.class,
            QuestRewardDeliveryClient.classifyAcknowledge(429, new byte[0])).safeCode());
        assertEquals("authentication_rejected", assertInstanceOf(QuestRewardDeliveryClient.Failure.class,
            QuestRewardDeliveryClient.classifyAcknowledge(401, new byte[0])).safeCode());
        assertInstanceOf(QuestRewardDeliveryClient.Acknowledged.class,
            QuestRewardDeliveryClient.classifyAcknowledge(200, QuestContractFixtures.bytes("delivery_result_response.json")));

        assertInstanceOf(QuestRewardDeliveryClient.PendingFetched.class,
            QuestRewardDeliveryClient.classifyPending(200, QuestContractFixtures.bytes("pending_deliveries_response.json")));
        assertInstanceOf(QuestRewardDeliveryClient.EndpointUnsupported.class,
            QuestRewardDeliveryClient.classifyPending(404, routing));
        assertInstanceOf(QuestRewardDeliveryClient.Failure.class,
            QuestRewardDeliveryClient.classifyPending(503, new byte[0]));
    }

    @Test
    void theTwoEndpointsResolveToTheContractPaths() {
        RailsApiUrlResolver resolver = RailsApiUrlResolver.fromServiceOrigin(URI.create("https://ultimacraft.example"));
        assertEquals("https://ultimacraft.example/api/v2/quest_reward_deliveries/pending?player_uuid="
                + QuestContractFixtures.PLAYER_UUID,
            resolver.resolveQuery(RailsApiUrlResolver.Endpoint.QUEST_REWARD_DELIVERIES_PENDING,
                Map.of("player_uuid", QuestContractFixtures.PLAYER_UUID)).toString());
        assertEquals("https://ultimacraft.example/api/v2/quest_reward_deliveries/"
                + QuestContractFixtures.DELIVERY_UUID + "/result",
            resolver.resolvePath(RailsApiUrlResolver.Endpoint.QUEST_REWARD_DELIVERY_RESULT,
                Map.of("delivery_uuid", QuestContractFixtures.DELIVERY_UUID)).toString());
    }

    @Test
    void theRequestRefusesATimestampThatCouldBreakTheBody() {
        assertThrows(IllegalArgumentException.class, () -> new QuestRewardDeliveryProtocol.AcknowledgementRequest(
            UUID.randomUUID(), UUID.randomUUID(), QuestRewardDeliveryProtocol.Outcome.APPLIED,
            "2026-09-06T21:10:04Z\",\"outcome\":\"queued", UUID.randomUUID()));
    }

    private static QuestRewardDeliveryProtocol.AcknowledgementRequest request(QuestRewardDeliveryProtocol.Outcome outcome) {
        return new QuestRewardDeliveryProtocol.AcknowledgementRequest(
            UUID.fromString(QuestContractFixtures.DELIVERY_UUID),
            UUID.fromString(QuestContractFixtures.PLAYER_UUID),
            outcome, "2026-09-06T21:10:04Z", UUID.fromString(QuestContractFixtures.REQUEST_UUID));
    }
}
