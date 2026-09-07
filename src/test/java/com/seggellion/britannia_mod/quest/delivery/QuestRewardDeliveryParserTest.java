package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M3: the delivery parser against the frozen M0 fixtures (accept), the
 * malformed shapes the protocol names (reject, and never fall back to {@code granted_items}), and
 * a legacy response without any delivery (the immediate path).
 */
class QuestRewardDeliveryParserTest {

    @Test
    void theTransitionFixtureParsesToTheFrozenDelivery() {
        QuestRewardDeliveryParser.TransitionResult result =
            QuestRewardDeliveryParser.parseTransition(QuestContractFixtures.json("transition_response_with_delivery.json"));

        QuestRewardDeliveryParser.Present present = assertInstanceOf(QuestRewardDeliveryParser.Present.class, result);
        assertFalse(present.replayed());
        QuestRewardDelivery delivery = present.delivery();
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID), delivery.deliveryUuid());
        assertEquals(41L, delivery.questId());
        assertEquals("9001", delivery.questStateId());
        assertEquals("1757200000:1201:choice:accept", delivery.transitionKey());
        assertEquals("pending", delivery.state());
        assertEquals("2026-09-06T21:10:00Z", delivery.createdAt());
        assertEquals(List.of(new QuestRewardDeliveryItem("britannia_mod:britannia_shovel", 1, false)), delivery.items());
    }

    @Test
    void theReplayedFlagIsReadOffTheResponseRoot() {
        JsonObject root = QuestContractFixtures.json("transition_response_with_delivery.json");
        root.addProperty("replayed", true);

        QuestRewardDeliveryParser.Present present = assertInstanceOf(QuestRewardDeliveryParser.Present.class,
            QuestRewardDeliveryParser.parseTransition(root));
        assertTrue(present.replayed());
    }

    @Test
    void thePendingListingFixtureParsesToTheSameDelivery() {
        QuestRewardDeliveryParser.PendingListing listing =
            QuestRewardDeliveryParser.parsePendingListing(QuestContractFixtures.json("pending_deliveries_response.json"));

        assertEquals(UUID.fromString(QuestContractFixtures.PLAYER_UUID), listing.playerUuid());
        assertEquals(1, listing.deliveries().size());
        QuestRewardDelivery delivery = listing.deliveries().get(0);
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID), delivery.deliveryUuid());
        assertEquals("9001", delivery.questStateId());
        assertEquals("1757200000:1201:choice:accept", delivery.transitionKey());
        assertEquals("pending", delivery.state(), "a listing element carries no state; pending is implied");
        assertEquals(List.of(new QuestRewardDeliveryItem("britannia_mod:britannia_shovel", 1, false)), delivery.items());
    }

    @Test
    void theBootstrapArrayUsesTheListingElementShape() {
        JsonObject listing = QuestContractFixtures.json("pending_deliveries_response.json");
        JsonObject bootstrap = new JsonObject();
        bootstrap.add("pending_reward_deliveries", listing.getAsJsonArray("deliveries"));

        List<QuestRewardDelivery> parsed = QuestRewardDeliveryParser.parseBootstrapPending(bootstrap);
        assertEquals(1, parsed.size());
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID), parsed.get(0).deliveryUuid());
    }

    @Test
    void anOldRailsBootstrapWithoutTheArrayYieldsNothing() {
        assertTrue(QuestRewardDeliveryParser.parseBootstrapPending(new JsonObject()).isEmpty());
        assertTrue(QuestRewardDeliveryParser.parseBootstrapPending(null).isEmpty());
        JsonObject notAnArray = new JsonObject();
        notAnArray.addProperty("pending_reward_deliveries", "nope");
        assertTrue(QuestRewardDeliveryParser.parseBootstrapPending(notAnArray).isEmpty());
    }

    @Test
    void aBadBootstrapElementIsSkippedWithoutRejectingTheGoodOnes() {
        JsonArray array = new JsonArray();
        array.add(QuestContractFixtures.json("pending_deliveries_response.json").getAsJsonArray("deliveries").get(0));
        JsonObject broken = QuestContractFixtures.json("pending_deliveries_response.json")
            .getAsJsonArray("deliveries").get(0).getAsJsonObject().deepCopy();
        broken.addProperty("delivery_uuid", "not-a-uuid");
        array.add(broken);
        array.add("just a string");
        JsonObject bootstrap = new JsonObject();
        bootstrap.add("pending_reward_deliveries", array);

        List<QuestRewardDelivery> parsed = QuestRewardDeliveryParser.parseBootstrapPending(bootstrap);
        assertEquals(1, parsed.size());
    }

    @Test
    void aLegacyResponseWithoutADeliveryIsAbsentNotMalformed() {
        JsonObject legacy = JsonParser.parseString(
            "{\"success\":true,\"quest_id\":41,\"granted_items\":[{\"id\":\"gold_coin\",\"count\":5}]}").getAsJsonObject();
        assertInstanceOf(QuestRewardDeliveryParser.Absent.class, QuestRewardDeliveryParser.parseTransition(legacy));

        // M2 Rails publishes reward_delivery: null on every success response without a grant.
        JsonObject explicitNull = legacy.deepCopy();
        explicitNull.add("reward_delivery", null);
        assertInstanceOf(QuestRewardDeliveryParser.Absent.class, QuestRewardDeliveryParser.parseTransition(explicitNull));
        assertInstanceOf(QuestRewardDeliveryParser.Absent.class, QuestRewardDeliveryParser.parseTransition(null));
    }

    /**
     * The trigger path reaches {@code QuestRewardService} only as a Gson-bound
     * {@link QuestModels.QuestResponse}, so {@code reward_delivery} must BIND for every legal wire
     * value. A {@code JsonObject} field cannot hold JSON {@code null} -- Gson throws while reading
     * a response that is perfectly legal -- which is why the field is a {@code JsonElement}.
     */
    @Test
    void aNullRewardDeliveryBindsOntoTheResponseInsteadOfThrowing() {
        Gson gson = new Gson();
        QuestModels.QuestResponse withoutDelivery = gson.fromJson(
            "{\"success\":true,\"quest_id\":41,\"quest_state_id\":\"9001\","
                + "\"granted_items\":[{\"id\":\"gold_coin\",\"count\":5}],\"reward_delivery\":null}",
            QuestModels.QuestResponse.class);
        assertTrue(withoutDelivery.reward_delivery == null || withoutDelivery.reward_delivery.isJsonNull(),
            "a null delivery must bind as no delivery, never as an object");

        QuestModels.QuestResponse withDelivery = gson.fromJson(
            QuestContractFixtures.text("transition_response_with_delivery.json"), QuestModels.QuestResponse.class);
        JsonObject rebuilt = new JsonObject();
        rebuilt.add("reward_delivery", withDelivery.reward_delivery.deepCopy());
        QuestRewardDeliveryParser.TransitionResult parsed = QuestRewardDeliveryParser.parseTransition(rebuilt);
        assertInstanceOf(QuestRewardDeliveryParser.Present.class, parsed,
            "and a real delivery still survives the same binding");
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID),
            ((QuestRewardDeliveryParser.Present) parsed).delivery().deliveryUuid());
    }

    @TestFactory
    Stream<DynamicTest> malformedDeliveriesAreRejectedWithTheirReason() {
        Map<String, Consumer<JsonObject>> cases = new java.util.LinkedHashMap<>();
        cases.put("delivery_uuid_invalid", delivery -> delivery.addProperty("delivery_uuid", "6f1d0c8e-3c2f-4d0a-9a9b"));
        cases.put("delivery_uuid_invalid:missing", delivery -> delivery.remove("delivery_uuid"));
        cases.put("delivery_uuid_invalid:number", delivery -> delivery.addProperty("delivery_uuid", 12));
        cases.put("items_empty", delivery -> delivery.add("items", new JsonArray()));
        cases.put("items_not_an_array", delivery -> delivery.addProperty("items", "shovel"));
        cases.put("items_not_an_array:missing", delivery -> delivery.remove("items"));
        cases.put("item_count_out_of_range:zero", delivery -> item(delivery).addProperty("count", 0));
        cases.put("item_count_out_of_range:1025", delivery -> item(delivery).addProperty("count", 1025));
        cases.put("item_count_out_of_range:negative", delivery -> item(delivery).addProperty("count", -1));
        cases.put("item_count_invalid", delivery -> item(delivery).addProperty("count", "one"));
        cases.put("item_count_invalid:fraction", delivery -> item(delivery).addProperty("count", 1.5));
        cases.put("item_id_invalid:blank", delivery -> item(delivery).addProperty("id", "  "));
        cases.put("item_id_invalid:control", delivery -> item(delivery).addProperty("id", "britannia_mod:sh\novel"));
        cases.put("item_id_invalid:long", delivery -> item(delivery).addProperty("id", "x".repeat(129)));
        cases.put("item_temporary_invalid", delivery -> item(delivery).addProperty("temporary", "yes"));
        cases.put("item_not_an_object", delivery -> {
            JsonArray items = new JsonArray();
            items.add("shovel");
            delivery.add("items", items);
        });
        cases.put("items_too_many", delivery -> {
            JsonArray items = new JsonArray();
            for (int i = 0; i < 33; i++) items.add(item(delivery).deepCopy());
            delivery.add("items", items);
        });
        cases.put("protocol_version_unsupported:2", delivery -> delivery.addProperty("protocol_version", 2));
        cases.put("protocol_version_unsupported:string", delivery -> delivery.addProperty("protocol_version", "1"));
        cases.put("protocol_version_missing", delivery -> delivery.remove("protocol_version"));
        cases.put("quest_id_invalid", delivery -> delivery.addProperty("quest_id", "forty-one"));
        cases.put("transition_key_invalid", delivery -> delivery.addProperty("transition_key", "k".repeat(201)));
        cases.put("quest_state_id_invalid", delivery -> delivery.addProperty("quest_state_id", true));

        return cases.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            JsonObject root = QuestContractFixtures.json("transition_response_with_delivery.json");
            entry.getValue().accept(root.getAsJsonObject("reward_delivery"));
            QuestRewardDeliveryParser.TransitionResult result = QuestRewardDeliveryParser.parseTransition(root);
            QuestRewardDeliveryParser.Malformed malformed = assertInstanceOf(QuestRewardDeliveryParser.Malformed.class, result);
            assertEquals(entry.getKey().split(":")[0], malformed.reason());
        }));
    }

    @Test
    void aDeliveryThatIsNotAnObjectIsMalformedNotLegacy() {
        JsonObject root = QuestContractFixtures.json("transition_response_with_delivery.json");
        root.addProperty("reward_delivery", "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01");
        QuestRewardDeliveryParser.Malformed malformed = assertInstanceOf(QuestRewardDeliveryParser.Malformed.class,
            QuestRewardDeliveryParser.parseTransition(root));
        assertEquals("reward_delivery_not_an_object", malformed.reason());
    }

    @Test
    void aListingElementMayOmitTheProtocolVersionButMayNotContradictIt() {
        JsonObject element = QuestContractFixtures.json("pending_deliveries_response.json")
            .getAsJsonArray("deliveries").get(0).getAsJsonObject();
        assertFalse(element.has("protocol_version"));
        assertEquals(UUID.fromString(QuestContractFixtures.DELIVERY_UUID),
            QuestRewardDeliveryParser.parseDelivery(element, false).deliveryUuid());

        JsonObject contradicting = element.deepCopy();
        contradicting.addProperty("protocol_version", 7);
        QuestRewardDeliveryParser.MalformedDeliveryException rejected = assertThrows(
            QuestRewardDeliveryParser.MalformedDeliveryException.class,
            () -> QuestRewardDeliveryParser.parseDelivery(contradicting, false));
        assertEquals("protocol_version_unsupported", rejected.reason());
    }

    @Test
    void theListingEnvelopeIsStrict() {
        JsonObject listing = QuestContractFixtures.json("pending_deliveries_response.json");
        listing.addProperty("protocol_version", 2);
        assertThrows(QuestRewardDeliveryParser.MalformedDeliveryException.class,
            () -> QuestRewardDeliveryParser.parsePendingListing(listing));

        JsonObject noPlayer = QuestContractFixtures.json("pending_deliveries_response.json");
        noPlayer.remove("player_uuid");
        assertThrows(QuestRewardDeliveryParser.MalformedDeliveryException.class,
            () -> QuestRewardDeliveryParser.parsePendingListing(noPlayer));

        JsonObject tooMany = QuestContractFixtures.json("pending_deliveries_response.json");
        JsonArray deliveries = new JsonArray();
        for (int i = 0; i < 51; i++) deliveries.add(tooMany.getAsJsonArray("deliveries").get(0).deepCopy());
        tooMany.add("deliveries", deliveries);
        assertThrows(QuestRewardDeliveryParser.MalformedDeliveryException.class,
            () -> QuestRewardDeliveryParser.parsePendingListing(tooMany));
    }

    @Test
    void anAbsentTemporaryFlagIsNoVerdictNeverTrue() {
        JsonObject root = QuestContractFixtures.json("transition_response_with_delivery.json");
        item(root.getAsJsonObject("reward_delivery")).remove("temporary");
        QuestRewardDeliveryParser.Present present = assertInstanceOf(QuestRewardDeliveryParser.Present.class,
            QuestRewardDeliveryParser.parseTransition(root));
        assertNull(present.delivery().items().get(0).temporary());
    }

    @Test
    void theWireShapeRoundTripsThroughToJson() {
        QuestRewardDelivery delivery = assertInstanceOf(QuestRewardDeliveryParser.Present.class,
            QuestRewardDeliveryParser.parseTransition(QuestContractFixtures.json("transition_response_with_delivery.json")))
            .delivery();
        QuestRewardDelivery again = assertInstanceOf(QuestRewardDeliveryParser.Present.class,
            QuestRewardDeliveryParser.parseTransition(delivery.asTransitionRoot())).delivery();
        assertEquals(delivery, again);
    }

    private static JsonObject item(JsonObject delivery) {
        return delivery.getAsJsonArray("items").get(0).getAsJsonObject();
    }
}
