package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.quest.equipment.QuestEquipmentReissuePolicy;
import com.seggellion.britannia_mod.quest.equipment.QuestEquipmentReissueService;
import org.junit.jupiter.api.Test;

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
