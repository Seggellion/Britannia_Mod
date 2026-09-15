package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.quest.equipment.QuestEquipmentReissuePolicy;
import com.seggellion.britannia_mod.quest.equipment.QuestEquipmentReissueService;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M9 item 7: what the game will and will not ask Rails to replace, and
 * what the player is told about each answer.
 *
 * <p>The bound itself is Rails' and is tested there ({@code QuestEquipmentReissueTest}); these are
 * the rules the game applies before it asks and after it hears back.
 */
class QuestEquipmentReissuePolicyTest {

    /** The one kit item the questline takes back, at stage three. */
    private static final String HANDED_IN_BUCKET = "minecraft:bucket";

    // ------------------------------------------------------------------ what a stage is owed

    @Test
    void aStageOnlyOffersEquipmentAnEarlierStageAlreadyGave() {
        // The questline issues the shovel on accepting stage one, the bowls and bucket on completing
        // stage two, the watering can on completing stage three and the hoe on completing stage four.
        // A player partway through stage one is correctly without a hoe, and recovery must not treat
        // that as something to replace.
        assertEquals(List.of("britannia_mod:britannia_shovel"),
            QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("rowan_farming_1"),
            "stage one has only ever handed out the shovel");
        assertEquals(List.of("britannia_mod:britannia_shovel"),
            QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("rowan_farming_2"),
            "stage two's bowls and bucket are paid on completion, so they are not owed during it");

        for (String stage : List.of("rowan_farming_1", "rowan_farming_2", "rowan_farming_3")) {
            assertFalse(QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get(stage)
                    .contains("britannia_mod:farming_hoe"),
                "the hoe is stage four's reward and must not be reachable on " + stage);
        }
        for (String stage : List.of("rowan_farming_1", "rowan_farming_2")) {
            assertFalse(QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get(stage)
                    .contains("britannia_mod:watering_can"),
                "the watering can is stage three's reward and must not be reachable on " + stage);
        }
    }

    @Test
    void everyStageOnlyEverNamesItemsTheAllowListPermits() {
        QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.forEach((stage, items) ->
            items.forEach(item -> assertTrue(QuestEquipmentReissuePolicy.REQUIRED_EQUIPMENT.containsKey(item),
                stage + " names " + item + ", which is not reissuable equipment")));
    }

    /**
     * By stage five the player has been given every piece of the kit and still holds all of it --
     * except the bucket, which stage three's hand-in takes and does not give back.
     *
     * <p>That exception is the whole point of the assertion. While the bucket was still listed here,
     * every conversation with Rowan on stage four or five found it "missing" and either spent a
     * bounded replacement on an item the questline had deliberately confiscated, or repeated
     * "limit reached" forever once the bound was gone. It stays reissuable for stage three, which is
     * the stage that actually needs one.
     */
    @Test
    void theFinalStageOwesTheWholeKitExceptTheBucketItHandedIn() {
        List<String> stageFive = QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("rowan_farming_5");

        assertEquals(QuestEquipmentReissuePolicy.REQUIRED_EQUIPMENT.keySet().size() - 1, stageFive.size(),
            "stage five owes every piece of equipment but the one it no longer has");
        assertFalse(stageFive.contains(HANDED_IN_BUCKET),
            "the bucket stage three consumed must not be reissued afterwards");
        assertFalse(QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("rowan_farming_4")
            .contains(HANDED_IN_BUCKET), "nor on stage four");
        assertTrue(QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("rowan_farming_3")
            .contains(HANDED_IN_BUCKET), "but stage three is the stage that fills one, so it keeps it");
        assertTrue(QuestEquipmentReissuePolicy.reissuable(HANDED_IN_BUCKET),
            "and a bucket lost before stage three's hand-in is still replaceable");

        QuestEquipmentReissuePolicy.CURRENCY_ITEM_IDS.forEach(coin ->
            assertFalse(stageFive.contains(coin), "no stage may owe currency, found " + coin));
    }

    @Test
    void aQuestlineThisPolicyDoesNotKnowIsOwedNothingRatherThanEverything() {
        assertTrue(QuestEquipmentReissuePolicy.EQUIPMENT_BY_STAGE.get("some_other_questline_3") == null,
            "precondition: an unrecognised stage key");
        // The lookup returning null is what makes missingEquipment answer with an empty list, so a
        // questline this policy has never heard of cannot claim the farming kit.
    }

    // ------------------------------------------------------------------ currency is never in

    @Test
    void noCoinIsReissuableUnderAnyCircumstances() {
        for (String coin : QuestEquipmentReissuePolicy.CURRENCY_ITEM_IDS) {
            assertFalse(QuestEquipmentReissuePolicy.reissuable(coin), coin + " must never be reissued");
        }
        assertEquals(3, QuestEquipmentReissuePolicy.CURRENCY_ITEM_IDS.size());
    }

    @Test
    void theAllowListContainsNoCurrencyAtAll() {
        for (String allowed : QuestEquipmentReissuePolicy.REQUIRED_EQUIPMENT.keySet()) {
            assertFalse(QuestEquipmentReissuePolicy.CURRENCY_ITEM_IDS.contains(allowed),
                    allowed + " is currency and must not be in the equipment list");
        }
    }

    @Test
    void onlyMandatoryTutorialEquipmentIsReissuable() {
        assertTrue(QuestEquipmentReissuePolicy.reissuable("britannia_mod:farming_hoe"));
        assertTrue(QuestEquipmentReissuePolicy.reissuable("britannia_mod:britannia_shovel"));
        assertTrue(QuestEquipmentReissuePolicy.reissuable("britannia_mod:watering_can"));
        assertTrue(QuestEquipmentReissuePolicy.reissuable("britannia_mod:empty_bowl"));
        assertTrue(QuestEquipmentReissuePolicy.reissuable("minecraft:bucket"));

        // Renewable by regathering (item 8), so replacing them would be duplication, not recovery.
        assertFalse(QuestEquipmentReissuePolicy.reissuable("britannia_mod:dung"));
        assertFalse(QuestEquipmentReissuePolicy.reissuable("britannia_mod:dirt"));
        assertFalse(QuestEquipmentReissuePolicy.reissuable("britannia_mod:fertilized_dirt"));
        assertFalse(QuestEquipmentReissuePolicy.reissuable("minecraft:carrot"));

        assertFalse(QuestEquipmentReissuePolicy.reissuable(null));
        assertFalse(QuestEquipmentReissuePolicy.reissuable(""));
        assertFalse(QuestEquipmentReissuePolicy.reissuable("minecraft:diamond"));
    }

    @Test
    void aStageThatNeedsTwoBowlsSaysSo() {
        assertEquals(2, QuestEquipmentReissuePolicy.REQUIRED_EQUIPMENT.get("britannia_mod:empty_bowl"));
        assertEquals(1, QuestEquipmentReissuePolicy.REQUIRED_EQUIPMENT.get("britannia_mod:farming_hoe"));
    }

    // ------------------------------------------------------------------ reading Rails' answers

    @Test
    void aGrantIsAGrant() {
        assertEquals(QuestEquipmentReissuePolicy.Outcome.GRANTED,
                QuestEquipmentReissuePolicy.outcomeFor(true, null));
        assertEquals(QuestScreenText.EQUIPMENT_REISSUED,
                QuestEquipmentReissuePolicy.messageKeyFor(QuestEquipmentReissuePolicy.Outcome.GRANTED));
    }

    @Test
    void eachRefusalTheePlayerCanActOnGetsItsOwnSentence() {
        assertEquals(QuestEquipmentReissuePolicy.Outcome.ALREADY_CARRIED,
                QuestEquipmentReissuePolicy.outcomeFor(false, "still_carried"));
        assertEquals(QuestEquipmentReissuePolicy.Outcome.LIMIT_REACHED,
                QuestEquipmentReissuePolicy.outcomeFor(false, "limit_reached"));
        assertEquals(QuestScreenText.EQUIPMENT_ALREADY_CARRIED,
                QuestEquipmentReissuePolicy.messageKeyFor(QuestEquipmentReissuePolicy.Outcome.ALREADY_CARRIED));
        assertEquals(QuestScreenText.EQUIPMENT_LIMIT_REACHED,
                QuestEquipmentReissuePolicy.messageKeyFor(QuestEquipmentReissuePolicy.Outcome.LIMIT_REACHED));
    }

    @Test
    void everyOtherRefusalReadsAsUnavailableRatherThanAsARuleThePlayerBroke() {
        for (String code : new String[] {
                "not_reissuable", "quest_not_active", "delivery_refused", "rate_limited",
                "transport_error", "unauthorized", "player_not_found", "wat"
        }) {
            assertEquals(QuestEquipmentReissuePolicy.Outcome.UNAVAILABLE,
                    QuestEquipmentReissuePolicy.outcomeFor(false, code), code);
        }
        assertEquals(QuestEquipmentReissuePolicy.Outcome.UNAVAILABLE,
                QuestEquipmentReissuePolicy.outcomeFor(false, null));
    }

    @Test
    void findingNothingMissingSaysNothingAtAll() {
        assertNull(QuestEquipmentReissuePolicy.messageKeyFor(
                QuestEquipmentReissuePolicy.Outcome.NOTHING_MISSING));
    }

    // ------------------------------------------------------------------ reading Rails' payload

    @Test
    void aGrantWithNoDeliveryIsRefusedRatherThanPromisedToThePlayer() {
        QuestEquipmentReissueService.Answer answer =
                QuestEquipmentReissueService.parse(200, "{\"granted\":true}");
        assertFalse(answer.granted());
        assertEquals("missing_delivery", answer.error());
        assertNull(answer.delivery());
    }

    @Test
    void aRefusalCarriesItsCodeThrough() {
        QuestEquipmentReissueService.Answer answer =
                QuestEquipmentReissueService.parse(200, "{\"granted\":false,\"error\":\"limit_reached\"}");
        assertFalse(answer.granted());
        assertEquals("limit_reached", answer.error());
        assertEquals(QuestEquipmentReissuePolicy.Outcome.LIMIT_REACHED,
                QuestEquipmentReissuePolicy.outcomeFor(answer.granted(), answer.error()));
    }

    @Test
    void anUnreadableOrFailedAnswerNeverReadsAsAGrant() {
        assertFalse(QuestEquipmentReissueService.parse(200, "not json").granted());
        assertEquals("invalid_service_response",
                QuestEquipmentReissueService.parse(200, "not json").error());
        assertFalse(QuestEquipmentReissueService.parse(503, "{\"error\":\"unavailable\"}").granted());
        assertFalse(QuestEquipmentReissueService.parse(401, "{\"error\":\"unauthorized\"}").granted());
        assertEquals("unauthorized",
                QuestEquipmentReissueService.parse(401, "{\"error\":\"unauthorized\"}").error());
    }

    @Test
    void anItemIdBecomesItsOwnDisplayNameKey() {
        assertEquals("item.britannia_mod.farming_hoe",
                QuestEquipmentReissueService.itemTranslationKey("britannia_mod:farming_hoe"));
        assertEquals("item.minecraft.bucket",
                QuestEquipmentReissueService.itemTranslationKey("minecraft:bucket"));
        assertEquals(QuestScreenText.ITEM_UNKNOWN,
                QuestEquipmentReissueService.itemTranslationKey("no_namespace"));
        assertEquals(QuestScreenText.ITEM_UNKNOWN,
                QuestEquipmentReissueService.itemTranslationKey(null));
    }
}
