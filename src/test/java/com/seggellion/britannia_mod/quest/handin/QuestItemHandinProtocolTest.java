package com.seggellion.britannia_mod.quest.handin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract {@code quest_item_handin} version 1, checked against the frozen fixtures rather than
 * against itself.
 *
 * <p>The encoder assertion is a byte comparison on purpose. A removal proof has to be replayable
 * identically after a restart -- Rails answers {@code evidence_conflict} to a report that
 * contradicts the stored one, and that refusal is the only thing standing between a garbled retry
 * and a shard quietly changing its story about what it took. Byte-for-byte equality with a file
 * both repositories check is the strongest form of "identically" available here.
 */
class QuestItemHandinProtocolTest {

    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID REQUEST = UUID.fromString("8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b");
    private static final UUID HANDIN = UUID.fromString("2f8c1d40-9a3b-4c77-8f21-5b6e0d9a1c34");

    // --- the demand ---------------------------------------------------------------------

    @Test
    void aLiteralRequirementParsesToTheItemAndCountItNamed() {
        QuestItemHandinProtocol.Demand demand = QuestItemHandinProtocol.parseDemand(demand("""
                {"handin_uuid": "%s", "choice": "hand_over",
                 "requires": [{"item": "britannia_mod:dung", "count": 1}],
                 "missing_message": "Rowan needs 1 dung in your hands."}
                """.formatted(HANDIN)));

        assertEquals(HANDIN, demand.handinUuid());
        assertEquals("hand_over", demand.choice());
        assertEquals("Rowan needs 1 dung in your hands.", demand.missingMessage());
        assertEquals(1, demand.requirements().size());
        QuestHandinRequirement.Literal literal =
                assertInstanceOfLiteral(demand.requirements().get(0));
        assertEquals(0, literal.index());
        assertEquals("britannia_mod:dung", literal.itemId());
        assertEquals(1, literal.count());
    }

    @Test
    void aResolverRequirementKeepsTheFlagValuePinnedAtPrepareTime() {
        QuestItemHandinProtocol.Demand demand = QuestItemHandinProtocol.parseDemand(demand("""
                {"handin_uuid": "%s", "choice": "hand_over", "requires": [
                   {"resolver": "awarded_crop_harvest_item", "flag": "awarded_crop",
                    "flag_value": "carrot", "count": 1}]}
                """.formatted(HANDIN)));

        QuestHandinRequirement.Resolver resolver =
                (QuestHandinRequirement.Resolver) demand.requirements().get(0);
        assertEquals("awarded_crop_harvest_item", resolver.resolver());
        assertEquals("awarded_crop", resolver.flag());
        assertEquals("carrot", resolver.flagValue());
        assertEquals(1, resolver.count());
    }

    @Test
    void countDefaultsToOneAndIndicesFollowThePublishedOrder() {
        QuestItemHandinProtocol.Demand demand = QuestItemHandinProtocol.parseDemand(demand("""
                {"handin_uuid": "%s", "requires": [
                   {"item": "britannia_mod:dung"},
                   {"item": "britannia_mod:dirt", "count": 2}]}
                """.formatted(HANDIN)));

        assertEquals(1, demand.requirements().get(0).count());
        assertEquals(0, demand.requirements().get(0).index());
        assertEquals(2, demand.requirements().get(1).count());
        assertEquals(1, demand.requirements().get(1).index());
    }

    /**
     * Each of these would, if accepted, take something. The demand is refused whole rather than in
     * part: a hand-in this mod only half understands is the shape that leaves a player short with
     * the quest unfinished and nothing owed back.
     */
    @Test
    void everyMalformedDemandIsRefusedBeforeAnythingCouldBeTaken() {
        assertRefused("no handin block", "{}");
        assertRefused("no requires", """
                {"handin_uuid": "%s"}""".formatted(HANDIN));
        assertRefused("an empty requires", """
                {"handin_uuid": "%s", "requires": []}""".formatted(HANDIN));
        assertRefused("nine requirements", """
                {"handin_uuid": "%s", "requires": [%s]}"""
                .formatted(HANDIN, ("{\"item\": \"britannia_mod:dung\"}, ").repeat(8) + "{\"item\": \"britannia_mod:dirt\"}"));
        assertRefused("both an item and a resolver", """
                {"handin_uuid": "%s", "requires": [{"item": "britannia_mod:dung",
                 "resolver": "awarded_crop_harvest_item", "flag": "awarded_crop", "flag_value": "carrot"}]}"""
                .formatted(HANDIN));
        assertRefused("neither an item nor a resolver", """
                {"handin_uuid": "%s", "requires": [{"count": 1}]}""".formatted(HANDIN));
        assertRefused("a resolver with no pinned flag value", """
                {"handin_uuid": "%s", "requires": [{"resolver": "awarded_crop_harvest_item",
                 "flag": "awarded_crop", "count": 1}]}""".formatted(HANDIN));
        assertRefused("an un-namespaced item", """
                {"handin_uuid": "%s", "requires": [{"item": "dung", "count": 1}]}""".formatted(HANDIN));
        assertRefused("a zero count", """
                {"handin_uuid": "%s", "requires": [{"item": "britannia_mod:dung", "count": 0}]}""".formatted(HANDIN));
        assertRefused("a negative count", """
                {"handin_uuid": "%s", "requires": [{"item": "britannia_mod:dung", "count": -1}]}""".formatted(HANDIN));
        assertRefused("a count past the bound", """
                {"handin_uuid": "%s", "requires": [{"item": "britannia_mod:dung", "count": 1025}]}""".formatted(HANDIN));
        assertRefused("a fractional count", """
                {"handin_uuid": "%s", "requires": [{"item": "britannia_mod:dung", "count": 1.5}]}""".formatted(HANDIN));
        assertRefused("a non-uuid transaction", """
                {"handin_uuid": "not-a-uuid", "requires": [{"item": "britannia_mod:dung"}]}""");
    }

    // --- the confirmation ---------------------------------------------------------------

    @Test
    void theConfirmationBodyIsByteIdenticalToTheFrozenFixture() {
        byte[] encoded = QuestItemHandinProtocol.encodeConfirmation(
                new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, true,
                        List.of(QuestHandinRemoval.resolved(0, "britannia_mod:carrot", 1,
                                "awarded_crop_harvest_item", "carrot")),
                        List.of(), REQUEST));

        assertEquals(fixture("handin_result_request.json"),
                new String(encoded, StandardCharsets.UTF_8),
                "the confirmation encoder drifted from quest_contract/v1/handin_result_request.json");
    }

    @Test
    void theSameEvidenceEncodesToTheSameBytesEveryTime() {
        QuestItemHandinProtocol.ConfirmationRequest request =
                new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, true,
                        List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1),
                                QuestHandinRemoval.resolved(1, "britannia_mod:carrots", 2,
                                        "awarded_crop_harvest_item", "carrot")),
                        List.of(), REQUEST);

        assertArrayEqualsAsText(QuestItemHandinProtocol.encodeConfirmation(request),
                QuestItemHandinProtocol.encodeConfirmation(request));
    }

    @Test
    void aLiteralProofNeverDressesItselfUpAsAResolver() {
        String body = new String(QuestItemHandinProtocol.encodeConfirmation(
                new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, true,
                        List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1)),
                        List.of(), REQUEST)), StandardCharsets.UTF_8);

        assertFalse(body.contains("resolver"), "a literal requirement is answered by its own item");
        assertFalse(body.contains("flag_value"));
        assertTrue(body.contains("\"requirement_index\": 0"));
    }

    @Test
    void aRefusalCarriesTheShortfallAndNeverCarriesRemovalProof() {
        String body = new String(QuestItemHandinProtocol.encodeConfirmation(
                new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, false, List.of(),
                        List.of(new QuestItemHandinProtocol.MissingItem("britannia_mod:dung", 1)),
                        REQUEST)), StandardCharsets.UTF_8);

        assertTrue(body.contains("\"removed\": false"));
        assertFalse(body.contains("removed_items"));
        assertTrue(body.contains("\"item\": \"britannia_mod:dung\", \"count\": 1"));

        assertThrows(IllegalArgumentException.class,
                () -> new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, false,
                        List.of(QuestHandinRemoval.literal(0, "britannia_mod:dung", 1)),
                        List.of(), REQUEST),
                "a report that took nothing may not also prove a removal");
        assertThrows(IllegalArgumentException.class,
                () -> new QuestItemHandinProtocol.ConfirmationRequest(HANDIN, PLAYER, true,
                        List.of(), List.of(), REQUEST),
                "a removal without evidence is what makes a resolver hand-in unrefundable");
    }

    @Test
    void aConsumedAnswerCarriesTheCompletionToApplyExactlyOnce() {
        QuestItemHandinProtocol.ConfirmationResponse response =
                QuestItemHandinProtocol.parseConfirmation(fixtureBytes("handin_result_response_consumed.json"));

        assertEquals(QuestItemHandinProtocol.Result.CONSUMED, response.result());
        assertTrue(response.result().carriesCompletion());
        assertTrue(response.result().terminal());
        assertEquals(HANDIN, response.handinUuid());
        assertEquals("consumed", response.state());
        assertNotNull(response.response(), "the nested quest response is what completes the quest");
        assertEquals("9001", response.response().get("quest_state_id").getAsString());
        assertEquals(1, response.removedItems().size());
        assertEquals("britannia_mod:carrot", response.removedItems().get(0).itemId());
        assertTrue(response.removedItems().get(0).answersResolver());
        assertNull(response.refundDeliveryUuid());
    }

    @Test
    void aRefundedAnswerNamesTheDeliveryThatWillReturnTheItems() {
        QuestItemHandinProtocol.ConfirmationResponse response = QuestItemHandinProtocol
                .parseConfirmation(fixtureBytes("handin_result_response_cancelled_refunded.json"));

        assertEquals(QuestItemHandinProtocol.Result.CANCELLED_REFUNDED, response.result());
        assertFalse(response.result().carriesCompletion(),
                "a refund is compensation, not an authored give-back, and completes nothing");
        assertTrue(response.result().terminal());
        assertEquals("abandoned", response.reason());
        assertEquals(UUID.fromString("b1b3d4a0-1f7e-4c0d-8f4c-6a2e9c1f0a11"),
                response.refundDeliveryUuid());
        assertEquals("britannia_mod:carrot", response.removedItems().get(0).itemId());
    }

    @Test
    void aRejectionNamesNoTransactionAtAll() {
        QuestItemHandinProtocol.ConfirmationResponse response = QuestItemHandinProtocol.parseConfirmation(
                """
                {"protocol_version": 1, "result": "rejected", "error": "handin_not_found"}
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals(QuestItemHandinProtocol.Result.REJECTED, response.result());
        assertNull(response.handinUuid(),
                "every rejection answers identically so a shard cannot probe another's ids");
    }

    @Test
    void evidenceRejectedIsNotTerminalBecauseTheTrueReportCanStillBeSent() {
        QuestItemHandinProtocol.ConfirmationResponse response = QuestItemHandinProtocol.parseConfirmation(
                """
                {"protocol_version": 1, "result": "evidence_rejected", "handin_uuid": "%s",
                 "state": "pending", "reason": "evidence_mismatch"}
                """.formatted(HANDIN).getBytes(StandardCharsets.UTF_8));

        assertEquals(QuestItemHandinProtocol.Result.EVIDENCE_REJECTED, response.result());
        assertFalse(response.result().terminal());
        assertFalse(response.result().carriesCompletion());
        assertEquals("evidence_mismatch", response.reason());
    }

    @Test
    void anItemsMissingAnswerReportsWhatIsStillOwed() {
        QuestItemHandinProtocol.ConfirmationResponse response = QuestItemHandinProtocol.parseConfirmation(
                """
                {"protocol_version": 1, "result": "items_missing", "handin_uuid": "%s",
                 "state": "pending", "reason": "items_missing",
                 "missing_items": [{"item": "britannia_mod:dung", "count": 1}]}
                """.formatted(HANDIN).getBytes(StandardCharsets.UTF_8));

        assertEquals(QuestItemHandinProtocol.Result.ITEMS_MISSING, response.result());
        assertFalse(response.result().terminal());
        assertEquals(1, response.missingItems().size());
        assertEquals("britannia_mod:dung", response.missingItems().get(0).itemId());
    }

    /**
     * The shortfall list is read under either spelling.
     *
     * <p>Two vocabularies meet on this one field. Everything Rails publishes says {@code item}, but
     * its shortfall list is built in the ledger's own vocabulary and rendered untranslated, so an
     * {@code id}-keyed row can reach the wire. Refusing it would turn a well-formed answer into
     * {@code malformed_response}, and a transaction whose items are already gone would retry that
     * exchange forever. Rails' own inbound contract is lenient about exactly this field, so this
     * side is too.
     */
    @Test
    void aShortfallIsReadUnderEitherSpellingOfItsItemId() {
        for (String key : List.of("item", "id")) {
            QuestItemHandinProtocol.ConfirmationResponse response = QuestItemHandinProtocol.parseConfirmation(
                    """
                    {"protocol_version": 1, "result": "items_missing", "handin_uuid": "%s",
                     "state": "pending", "missing_items": [{"%s": "britannia_mod:dung", "count": 1}]}
                    """.formatted(HANDIN, key).getBytes(StandardCharsets.UTF_8));

            assertEquals(1, response.missingItems().size(), key);
            assertEquals("britannia_mod:dung", response.missingItems().get(0).itemId(), key);
        }
    }

    @Test
    void anEnvelopeFromAProtocolThisBuildDoesNotImplementIsRefused() {
        String v2 = fixture("handin_result_response_consumed.json")
                .replace("\"protocol_version\": 1", "\"protocol_version\": 2");
        assertThrows(QuestItemHandinProtocol.MalformedHandinException.class,
                () -> QuestItemHandinProtocol.parseConfirmation(v2.getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestItemHandinProtocol.MalformedHandinException.class,
                () -> QuestItemHandinProtocol.parseConfirmation("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(QuestItemHandinProtocol.MalformedHandinException.class,
                () -> QuestItemHandinProtocol.parseConfirmation(
                        "{\"protocol_version\": 1, \"result\": \"invented\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theErrorCodeIsReadOnlyWhenItIsOneRailsCouldHaveWritten() {
        assertEquals("handin_not_found", QuestItemHandinProtocol.errorCode(
                "{\"error\": \"handin_not_found\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestItemHandinProtocol.errorCode("{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestItemHandinProtocol.errorCode(
                "{\"error\": \"<html>404</html>\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", QuestItemHandinProtocol.errorCode(new byte[0]));
    }

    // --- reconciliation -------------------------------------------------------------------

    @Test
    void theReconciliationBodyAsksAboutTheTransactionsThisServerIsHolding() {
        String body = new String(QuestItemHandinProtocol.encodeReconcile(PLAYER, List.of(HANDIN)),
                StandardCharsets.UTF_8);

        assertTrue(body.contains("\"player_uuid\": \"" + PLAYER + "\""));
        assertTrue(body.contains("\"handin_uuids\": [\"" + HANDIN + "\"]"));
    }

    @Test
    void aReconciliationRequestIsBoundedRatherThanEnumerating() {
        List<UUID> many = java.util.stream.Stream.generate(UUID::randomUUID).limit(80).toList();
        String body = new String(QuestItemHandinProtocol.encodeReconcile(PLAYER, many),
                StandardCharsets.UTF_8);

        assertEquals(QuestItemHandinProtocol.MAX_RECONCILE_UUIDS,
                body.split("\"handin_uuids\": \\[")[1].split("]")[0].split(",").length,
                "asking about more than the Rails limit is enumerating, not reconciling");
    }

    @Test
    void aReconciliationAnswerCarriesTheOutcomeAndTheStoredResponse() {
        QuestItemHandinProtocol.ReconcileResponse response = QuestItemHandinProtocol.parseReconcile(
                """
                {"protocol_version": 1, "player_uuid": "%s", "handins": [
                  {"handin_uuid": "%s", "outcome": "cancelled", "cancelled_reason": "abandoned",
                   "removed_items": [{"item": "britannia_mod:dung", "count": 1, "requirement_index": 0}]},
                  {"handin_uuid": "8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b", "outcome": "unknown"}]}
                """.formatted(PLAYER, HANDIN).getBytes(StandardCharsets.UTF_8));

        assertEquals(2, response.handins().size());
        assertEquals(QuestItemHandinProtocol.ReconcileOutcome.CANCELLED,
                response.handins().get(0).outcome());
        assertEquals("abandoned", response.handins().get(0).cancelledReason());
        assertEquals(1, response.handins().get(0).removedItems().size());
        assertEquals(QuestItemHandinProtocol.ReconcileOutcome.UNKNOWN,
                response.handins().get(1).outcome());
        assertTrue(response.handins().get(1).removedItems().isEmpty());
    }

    // --- helpers --------------------------------------------------------------------------

    private static QuestHandinRequirement.Literal assertInstanceOfLiteral(QuestHandinRequirement requirement) {
        assertTrue(requirement instanceof QuestHandinRequirement.Literal,
                "expected a literal requirement, got " + requirement);
        return (QuestHandinRequirement.Literal) requirement;
    }

    private static void assertRefused(String what, String handinBlock) {
        assertThrows(QuestItemHandinProtocol.MalformedHandinException.class,
                () -> QuestItemHandinProtocol.parseDemand(demand(handinBlock)),
                "a demand carrying " + what + " must be refused before any inventory is touched");
    }

    /** Wraps a {@code handin} block in the transition envelope Rails actually sends it in. */
    private static JsonObject demand(String handinBlock) {
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("result", QuestItemHandinProtocol.RESULT_HANDIN_REQUIRED);
        root.addProperty("completed", false);
        if (!handinBlock.trim().equals("{}")) {
            root.add(QuestItemHandinProtocol.HANDIN_KEY,
                    JsonParser.parseString(handinBlock).getAsJsonObject());
        }
        return root;
    }

    private static void assertArrayEqualsAsText(byte[] first, byte[] second) {
        assertEquals(new String(first, StandardCharsets.UTF_8), new String(second, StandardCharsets.UTF_8));
    }

    private static String fixture(String name) {
        return new String(fixtureBytes(name), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static byte[] fixtureBytes(String name) {
        try (InputStream stream = QuestItemHandinProtocolTest.class.getClassLoader()
                .getResourceAsStream("quest_contract/v1/" + name)) {
            if (stream == null) throw new IllegalStateException("missing checked-in fixture " + name);
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
