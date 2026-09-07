package com.seggellion.britannia_mod.quest.action;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M5: contract {@code quest_action_event} version 1, both directions.
 *
 * <p>The request half is a wire-contract test in the sense
 * {@code ServiceNpcSpawnWireContractRequestFixtureTest} established: the bytes are re-derived from
 * the production encoder and the production subject builder on every run and diffed against the
 * committed fixture, so neither side can drift without this test saying so.
 */
class QuestActionEventProtocolTest {

    @Test
    void theEncoderProducesTheFrozenCropHarvestRequestByteForByte() {
        QuestActionEvent event = new QuestActionEvent(
            UUID.fromString(QuestActionContractFixtures.EVENT_UUID),
            UUID.fromString(QuestActionContractFixtures.PLAYER_UUID),
            QuestAction.CROP_HARVEST,
            "2026-09-06T21:40:00Z",
            "minecraft:overworld",
            1203, 64, -488,
            QuestActionEvents.cropHarvestSubject(QuestActionContractFixtures.PLOT_KEY, "carrot",
                UUID.fromString(QuestActionContractFixtures.CROP_CYCLE_UUID),
                UUID.fromString(QuestActionContractFixtures.PLAYER_UUID), true, 3),
            new QuestActionEvent.Target("9005", 1305L, "crop_harvested"));

        byte[] actual = QuestActionEventProtocol.encode(event,
            UUID.fromString(QuestActionContractFixtures.REQUEST_UUID));
        byte[] expected =
            QuestActionContractFixtures.normalisedBytes(QuestActionContractFixtures.REQUEST_CROP_HARVEST);

        assertArrayEquals(expected, actual,
            () -> "the encoder no longer produces the frozen request fixture\nexpected:\n"
                + new String(expected, StandardCharsets.UTF_8) + "\nactual:\n"
                + new String(actual, StandardCharsets.UTF_8));
    }

    @Test
    void aTargetWithoutANodeIdOmitsTheFieldRatherThanNamingANodeTheModIsUnsureOf() {
        String body = new String(QuestActionEventProtocol.encode(event(
            QuestActionEvent.Target.of("9005", "crop_harvested")), UUID.randomUUID()), StandardCharsets.UTF_8);

        assertTrue(body.contains("\"target\": {\"quest_state_id\": \"9005\", \"trigger_key\": \"crop_harvested\"}"),
            () -> "an unknown node id must leave the field out entirely, got:\n" + body);
        assertFalse(body.contains("node_id"), "an unknown node id must never be published as a number");
    }

    @Test
    void anEventWithNoTargetStillEncodesAsValidJsonWithNoTrailingComma() {
        String body = new String(QuestActionEventProtocol.encode(event(null), UUID.randomUUID()),
            StandardCharsets.UTF_8);

        assertTrue(body.endsWith("  }\n}\n"), () -> "the subject must close the object cleanly, got:\n" + body);
        assertFalse(body.contains("\"target\""), "no target means no target field");
        assertTrue(body.contains("\"protocol_version\": 1,"), "every request states its protocol version");
    }

    @Test
    void anEventCannotBeBuiltWithoutTheFieldsItsActionRequires() {
        QuestActionSubject incomplete = QuestActionSubject.builder().text("crop_id", "carrot").build();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> new QuestActionEvent(UUID.randomUUID(), UUID.randomUUID(), QuestAction.CROP_HARVEST,
                "2026-09-06T21:40:00Z", "minecraft:overworld", 0, 0, 0, incomplete, null));

        assertTrue(refused.getMessage().contains("crop_harvest"),
            "the refusal must name the action whose contract was not met");
    }

    @Test
    void aSubjectValueThatCannotBeEncodedIsRefusedAtConstructionNotAtSendTime() {
        assertThrows(IllegalArgumentException.class,
            () -> QuestActionSubject.builder().text("crop_id", "car\"rot").build(),
            "a quote would break the hand-written encoder, so it can never enter a subject");
        assertThrows(IllegalArgumentException.class,
            () -> QuestActionSubject.builder().text("crop_id", "car\nrot").build(),
            "a control character would break the encoder's line layout");
        assertThrows(IllegalArgumentException.class,
            () -> QuestActionSubject.builder().text("Crop Id", "carrot").build(),
            "a field name that is not a contract key is refused");
    }

    @Test
    void everyOneOfTheFiveResultFixturesParses() {
        QuestActionEventProtocol.Response applied =
            QuestActionEventProtocol.parse(QuestActionContractFixtures.bytes(
                QuestActionContractFixtures.RESPONSE_APPLIED));
        assertEquals(QuestActionEventProtocol.Result.APPLIED, applied.result());
        assertEquals(UUID.fromString(QuestActionContractFixtures.EVENT_UUID), applied.eventUuid());
        assertEquals("9005", applied.questStateId());
        assertTrue(applied.carriesState(), "an applied event carries the state Rails just produced");

        QuestActionEventProtocol.Response duplicate =
            QuestActionEventProtocol.parse(QuestActionContractFixtures.bytes(
                QuestActionContractFixtures.RESPONSE_DUPLICATE));
        assertEquals(QuestActionEventProtocol.Result.DUPLICATE, duplicate.result());
        assertEquals("applied", duplicate.originalResult(),
            "a duplicate names the result it is replaying");
        assertTrue(duplicate.carriesState(), "a duplicate replays the stored response idempotently");

        QuestActionEventProtocol.Response irrelevant =
            QuestActionEventProtocol.parse(QuestActionContractFixtures.bytes(
                QuestActionContractFixtures.RESPONSE_IRRELEVANT));
        assertEquals(QuestActionEventProtocol.Result.IRRELEVANT, irrelevant.result());
        assertFalse(irrelevant.carriesState(), "nothing subscribed, so there is no state to install");

        QuestActionEventProtocol.Response stale =
            QuestActionEventProtocol.parse(QuestActionContractFixtures.bytes(
                QuestActionContractFixtures.RESPONSE_STALE));
        assertEquals(QuestActionEventProtocol.Result.STALE, stale.result());
        assertEquals("9005", stale.questStateId());

        QuestActionEventProtocol.Response rejected =
            QuestActionEventProtocol.parse(QuestActionContractFixtures.bytes(
                QuestActionContractFixtures.RESPONSE_REJECTED));
        assertEquals(QuestActionEventProtocol.Result.REJECTED, rejected.result());
        assertEquals("not_planter", rejected.reason(), "a rejection must carry the reason it is logged by");
    }

    @Test
    void malformedEnvelopesAreRefusedRatherThanGuessedAt() {
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse("[]".getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse(response(2, QuestActionContractFixtures.EVENT_UUID, "applied")),
            "a protocol version this mod does not implement is not read as if it did");
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse(response(1, "not-a-uuid", "applied")));
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse(response(1, QuestActionContractFixtures.EVENT_UUID, "maybe")),
            "a result outside the frozen five is not a result");
        assertThrows(QuestActionEventProtocol.MalformedActionEventException.class,
            () -> QuestActionEventProtocol.parse(("{\"protocol_version\": 1, \"event_uuid\": \""
                + QuestActionContractFixtures.EVENT_UUID + "\", \"result\": \"rejected\"}")
                .getBytes(StandardCharsets.UTF_8)),
            "a rejection with no reason cannot be logged, so it is not accepted");
    }

    @Test
    void theErrorEnvelopeCodeIsReadOnlyWhenItIsOneSafeToLog() {
        assertEquals("invalid_action_event", QuestActionEventProtocol.errorCode(
            "{\"error\": \"invalid_action_event\", \"field\": \"subject\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestActionEventProtocol.errorCode(
            "{\"error\": \"<script>alert(1)</script>\"}".getBytes(StandardCharsets.UTF_8)),
            "a code that is not a bare identifier is dropped rather than logged");
        assertEquals("", QuestActionEventProtocol.errorCode("not json".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestActionEventProtocol.errorCode(new byte[0]));
    }

    private static QuestActionEvent event(QuestActionEvent.Target target) {
        return new QuestActionEvent(UUID.fromString(QuestActionContractFixtures.EVENT_UUID),
            UUID.fromString(QuestActionContractFixtures.PLAYER_UUID), QuestAction.CROP_HARVEST,
            "2026-09-06T21:40:00Z", "minecraft:overworld", 1203, 64, -488,
            QuestActionEvents.cropHarvestSubject(QuestActionContractFixtures.PLOT_KEY, "carrot",
                UUID.fromString(QuestActionContractFixtures.CROP_CYCLE_UUID),
                UUID.fromString(QuestActionContractFixtures.PLAYER_UUID), true, 3),
            target);
    }

    private static byte[] response(int version, String eventUuid, String result) {
        return ("{\"protocol_version\": " + version + ", \"event_uuid\": \"" + eventUuid
            + "\", \"result\": \"" + result + "\"}").getBytes(StandardCharsets.UTF_8);
    }
}
