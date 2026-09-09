package com.seggellion.britannia_mod.quest.handin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * How an HTTP status becomes a decision about a transaction whose items are already gone.
 *
 * <p>The case that matters most is the 404 read two ways. Rails answers {@code 404
 * handin_not_found} deliberately -- one shape for unknown, wrong shard and wrong player, so a
 * caller cannot probe another shard's ids -- while an older Rails without the routes answers a 404
 * with nothing in it. Confusing those two turns "the mod was deployed before the seed" into an item
 * quietly stranded, which is exactly what the protocol's release order exists to prevent.
 */
class QuestItemHandinClientTest {

    private static final UUID HANDIN = UUID.fromString("2f8c1d40-9a3b-4c77-8f21-5b6e0d9a1c34");

    @Test
    void twoHundredIsTheAnswerItSaysItIs() {
        QuestItemHandinClient.Answered answered = assertInstanceOf(QuestItemHandinClient.Answered.class,
                QuestItemHandinClient.classifyConfirm(200, bytes("""
                        {"protocol_version": 1, "result": "consumed", "handin_uuid": "%s",
                         "state": "consumed", "response": {"success": true, "completed": true}}
                        """.formatted(HANDIN))));

        assertEquals(QuestItemHandinProtocol.Result.CONSUMED, answered.response().result());
    }

    @Test
    void aConflictIsARefusalOfTheEvidenceAndNotATransportFailure() {
        QuestItemHandinClient.Answered answered = assertInstanceOf(QuestItemHandinClient.Answered.class,
                QuestItemHandinClient.classifyConfirm(409, bytes("""
                        {"protocol_version": 1, "result": "evidence_rejected", "handin_uuid": "%s",
                         "state": "pending", "reason": "evidence_conflict"}
                        """.formatted(HANDIN))));

        assertEquals(QuestItemHandinProtocol.Result.EVIDENCE_REJECTED, answered.response().result());
        assertEquals("evidence_conflict", answered.response().reason());
    }

    @Test
    void aNotFoundNamingTheCodeIsRailsAnsweringAboutTheTransaction() {
        QuestItemHandinClient.Answered answered = assertInstanceOf(QuestItemHandinClient.Answered.class,
                QuestItemHandinClient.classifyConfirm(404, bytes("""
                        {"protocol_version": 1, "result": "rejected", "error": "handin_not_found"}
                        """)));

        assertEquals(QuestItemHandinProtocol.Result.REJECTED, answered.response().result());
    }

    @Test
    void aNotFoundWithNoCodeIsTheRouteMissingRatherThanTheTransaction() {
        assertInstanceOf(QuestItemHandinClient.EndpointUnsupported.class,
                QuestItemHandinClient.classifyConfirm(404, bytes("<html>404 Not Found</html>")));
        assertInstanceOf(QuestItemHandinClient.EndpointUnsupported.class,
                QuestItemHandinClient.classifyConfirm(404, new byte[0]));
        assertInstanceOf(QuestItemHandinClient.EndpointUnsupported.class,
                QuestItemHandinClient.classifyReconcile(404, new byte[0]));
    }

    @Test
    void everyOtherStatusIsAFailureWithASafeCode() {
        assertEquals("invalid_request", failureCode(400));
        assertEquals("authentication_rejected", failureCode(401));
        assertEquals("authentication_rejected", failureCode(403));
        assertEquals("rate_limited", failureCode(429));
        assertEquals("service_unavailable", failureCode(503));
        assertEquals("http_status_failure", failureCode(500));
    }

    @Test
    void aReconciliationAnswerIsParsedIntoItsOutcomes() {
        QuestItemHandinClient.Reconciled reconciled = assertInstanceOf(QuestItemHandinClient.Reconciled.class,
                QuestItemHandinClient.classifyReconcile(200, bytes("""
                        {"protocol_version": 1, "player_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
                         "handins": [{"handin_uuid": "%s", "outcome": "cancelled_refunded"}]}
                        """.formatted(HANDIN))));

        assertEquals(QuestItemHandinProtocol.ReconcileOutcome.CANCELLED_REFUNDED,
                reconciled.response().handins().get(0).outcome());
    }

    @Test
    void onlyThreeAnswersSettleARemovalThatAlreadyHappened() {
        assertEquals(true, QuestItemHandinProtocol.Result.CONSUMED.settlesRemoval());
        assertEquals(true, QuestItemHandinProtocol.Result.DUPLICATE.settlesRemoval());
        assertEquals(true, QuestItemHandinProtocol.Result.CANCELLED_REFUNDED.settlesRemoval());
        // Settled from Rails' point of view, but they prove neither a completion nor a refund, so
        // for a transaction that took something they are not an ending at all.
        assertEquals(false, QuestItemHandinProtocol.Result.REJECTED.settlesRemoval());
        assertEquals(false, QuestItemHandinProtocol.Result.CANCELLED.settlesRemoval());
        assertEquals(false, QuestItemHandinProtocol.Result.EVIDENCE_REJECTED.settlesRemoval());
        assertEquals(false, QuestItemHandinProtocol.Result.ITEMS_MISSING.settlesRemoval());
    }

    private static String failureCode(int status) {
        return assertInstanceOf(QuestItemHandinClient.Failure.class,
                QuestItemHandinClient.classifyConfirm(status, new byte[0])).safeCode();
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
