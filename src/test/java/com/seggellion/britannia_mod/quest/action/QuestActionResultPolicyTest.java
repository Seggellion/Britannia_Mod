package com.seggellion.britannia_mod.quest.action;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Rowan farming questline M5: the table of protocol sections 2.3 and 6 -- which answers end an
 * outbox row and which mean "ask again later".
 *
 * <p>The distinction is the whole reason the outbox exists. Retrying something terminal wastes a
 * row forever; dropping something retryable loses the objective the player actually earned.
 */
class QuestActionResultPolicyTest {

    @Test
    void allFiveResultsAreAnsweredAndTerminal() {
        for (String name : new String[] {
            QuestActionContractFixtures.RESPONSE_APPLIED,
            QuestActionContractFixtures.RESPONSE_DUPLICATE,
            QuestActionContractFixtures.RESPONSE_IRRELEVANT,
            QuestActionContractFixtures.RESPONSE_STALE,
            QuestActionContractFixtures.RESPONSE_REJECTED
        }) {
            QuestActionEventClient.SendResult result =
                QuestActionEventClient.classify(200, QuestActionContractFixtures.bytes(name));
            assertInstanceOf(QuestActionEventClient.Answered.class, result,
                () -> name + " is a contract answer, not a transport failure");
        }
    }

    @Test
    void anInvalidActionEventIsTerminalBecauseNoRetryCanFixAnEncoderDefect() {
        QuestActionEventClient.SendResult result = QuestActionEventClient.classify(400,
            "{\"error\": \"invalid_action_event\", \"field\": \"subject.crop_id\"}".getBytes(StandardCharsets.UTF_8));

        assertInstanceOf(QuestActionEventClient.TerminalRejection.class, result);
        assertEquals(QuestActionEventProtocol.ERROR_INVALID_ACTION_EVENT,
            ((QuestActionEventClient.TerminalRejection) result).code());
    }

    @Test
    void playerNotFoundIsRetryableAndAMissingRouteIsNot() {
        QuestActionEventClient.SendResult unknownPlayer = QuestActionEventClient.classify(404,
            "{\"error\": \"player_not_found\"}".getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(QuestActionEventClient.Failure.class, unknownPlayer,
            "a player Rails has not seen yet may exist by the next attempt");
        assertEquals(QuestActionEventProtocol.ERROR_PLAYER_NOT_FOUND,
            ((QuestActionEventClient.Failure) unknownPlayer).safeCode());

        assertInstanceOf(QuestActionEventClient.EndpointUnsupported.class,
            QuestActionEventClient.classify(404, "Not Found".getBytes(StandardCharsets.UTF_8)),
            "a 404 with no contract code is an older Rails without the route (section 4)");
    }

    @Test
    void authenticationRateLimitAndOutageAreAllRetryableWithSafeCodes() {
        assertEquals("authentication_rejected", failureCode(401));
        assertEquals("authentication_rejected", failureCode(403));
        assertEquals("rate_limited", failureCode(429));
        assertEquals("service_unavailable", failureCode(503));
        assertEquals("http_status_failure", failureCode(500));
        assertEquals("http_status_failure", failureCode(502));
    }

    @Test
    void aTwoHundredThatIsNotAContractEnvelopeIsNotSilentlyTreatedAsApplied() {
        assertInstanceOf(QuestActionEventClient.Answered.class,
            QuestActionEventClient.classify(200,
                QuestActionContractFixtures.bytes(QuestActionContractFixtures.RESPONSE_APPLIED)));

        // The client's own execute() turns this into a Failure; classify is where it is detected.
        org.junit.jupiter.api.Assertions.assertThrows(
            QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventClient.classify(200, "{\"ok\": true}".getBytes(StandardCharsets.UTF_8)));
    }

    private static String failureCode(int status) {
        QuestActionEventClient.SendResult result = QuestActionEventClient.classify(status, new byte[0]);
        assertInstanceOf(QuestActionEventClient.Failure.class, result, "status " + status + " must be retryable");
        return ((QuestActionEventClient.Failure) result).safeCode();
    }
}
