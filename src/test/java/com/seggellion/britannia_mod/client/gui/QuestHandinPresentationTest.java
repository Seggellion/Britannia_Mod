package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestNode;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's half of the strict item hand-in, without Minecraft.
 *
 * <p>The rule with teeth is the last one: a hand-in answers from the node it was asked at, because
 * Rails deliberately does not advance until a shard confirms a removal. That looks exactly like the
 * self-loop the dismissal rule was written for, so without the exemption every shortfall, refund
 * and failure notice would close the dialogue instead of being read -- the whole failure vocabulary
 * of the feature, delivered as a screen that vanishes.
 */
class QuestHandinPresentationTest {

    @Test
    void aResponseWithNoBlockIsTheOrdinaryCaseAndChangesNothing() {
        assertEquals(QuestHandinPresentation.State.NONE,
                QuestHandinPresentation.from(response(1201, null)).state());
        assertFalse(QuestHandinPresentation.from((QuestResponse) null).present());
        assertFalse(QuestHandinPresentation.ABSENT.keepsDialogueOpen());
    }

    @Test
    void aShortfallCarriesTheExactAmountAndTheAuthorsOwnWords() {
        QuestHandinPresentation handin = parse("""
                {"state": "items_missing",
                 "requires": [{"item": "britannia_mod:dung", "count": 1}],
                 "missing": [{"item": "britannia_mod:dung", "count": 1}],
                 "message": "Rowan needs 1 dung in your hands before he will take it."}
                """);

        assertEquals(QuestHandinPresentation.State.ITEMS_MISSING, handin.state());
        assertEquals(1, handin.missing().size());
        assertEquals("britannia_mod:dung", handin.missing().get(0).itemId());
        assertEquals(1, handin.missing().get(0).count());
        assertEquals("Rowan needs 1 dung in your hands before he will take it.", handin.message());
        assertTrue(handin.keepsDialogueOpen());
    }

    @Test
    void aCompletionNamesWhatWasActuallyTakenAndDoesNotHoldTheDialogueOpen() {
        QuestHandinPresentation handin = parse("""
                {"state": "consumed", "removed": [{"item": "britannia_mod:carrots", "count": 1}]}
                """);

        assertEquals(QuestHandinPresentation.State.CONSUMED, handin.state());
        assertEquals("britannia_mod:carrots", handin.removed().get(0).itemId());
        assertFalse(handin.keepsDialogueOpen(), "the node moved, so the next screen is the answer");
    }

    @Test
    void aRefundSaysSoWithoutClaimingTheQuestFinished() {
        QuestHandinPresentation handin = parse("""
                {"state": "refunded", "removed": [{"item": "britannia_mod:dung", "count": 1}],
                 "reason": "abandoned"}
                """);

        assertEquals(QuestHandinPresentation.State.REFUNDED, handin.state());
        assertEquals("abandoned", handin.reason());
        assertTrue(handin.keepsDialogueOpen());
    }

    @Test
    void aFailureCarriesTheCodeThatIsAlsoInTheServerLog() {
        QuestHandinPresentation handin = parse("""
                {"state": "unavailable", "reason": "evidence_rejected"}
                """);

        assertEquals(QuestHandinPresentation.State.UNAVAILABLE, handin.state());
        assertEquals("evidence_rejected", handin.reason());
        assertTrue(handin.keepsDialogueOpen());
    }

    @Test
    void anUnreadableBlockPresentsAsNoBlockRatherThanAsAGuess() {
        assertEquals(QuestHandinPresentation.State.NONE, parse("{}").state());
        assertEquals(QuestHandinPresentation.State.NONE, parse("{\"state\": \"invented\"}").state());
        assertEquals(QuestHandinPresentation.State.NONE,
                QuestHandinPresentation.from(JsonParser.parseString("\"not an object\"")).state());
        assertEquals(QuestHandinPresentation.State.NONE, QuestHandinPresentation.from((com.google.gson.JsonElement) null).state());
    }

    @Test
    void malformedItemLinesAreDroppedWithoutLosingTheGoodOnes() {
        QuestHandinPresentation handin = parse("""
                {"state": "items_missing", "missing": [
                   {"item": "britannia_mod:dung", "count": 1},
                   {"count": 2},
                   {"item": "britannia_mod:dirt", "count": -1},
                   "not an object",
                   {"item": "britannia_mod:dirt", "count": 2}]}
                """);

        assertEquals(2, handin.missing().size());
        assertEquals("britannia_mod:dung", handin.missing().get(0).itemId());
        assertEquals("britannia_mod:dirt", handin.missing().get(1).itemId());
        assertEquals(2, handin.missing().get(1).count());
    }

    @Test
    void aListLongerThanAHandinCanBeIsTruncatedRatherThanDrawnOffTheParchment() {
        StringBuilder rows = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            rows.append(index == 0 ? "" : ", ")
                    .append("{\"item\": \"britannia_mod:dung\", \"count\": 1}");
        }
        assertEquals(8, parse("{\"state\": \"items_missing\", \"missing\": [" + rows + "]}")
                .missing().size());
    }

    @Test
    void aHandinAnswerFromTheSameNodeIsNotADismissal() {
        QuestResponse sameNode = response(1205, """
                {"state": "items_missing", "missing": [{"item": "britannia_mod:dung", "count": 1}]}
                """);

        assertFalse(QuestDialogueTransition.isDismissal(1205, sameNode),
                "closing here would deliver the shortfall as a dialogue that silently vanishes");
        // The rule it is an exemption from is unchanged for everything else.
        assertTrue(QuestDialogueTransition.isDismissal(1205, response(1205, null)));
        assertFalse(QuestDialogueTransition.isDismissal(1205, response(1206, null)));
    }

    @Test
    void aCompletedHandinFromANewNodeStillFollowsTheOrdinaryRule() {
        QuestResponse advanced = response(1206, """
                {"state": "consumed", "removed": [{"item": "britannia_mod:dung", "count": 1}]}
                """);
        assertFalse(QuestDialogueTransition.isDismissal(1205, advanced));
    }

    private static QuestHandinPresentation parse(String block) {
        return QuestHandinPresentation.from(JsonParser.parseString(block));
    }

    private static QuestResponse response(long nodeId, String handinBlock) {
        QuestResponse response = new QuestResponse();
        QuestNode node = new QuestNode();
        node.id = nodeId;
        response.currentNode = node;
        if (handinBlock != null) response.handin = JsonParser.parseString(handinBlock);
        return response;
    }
}
