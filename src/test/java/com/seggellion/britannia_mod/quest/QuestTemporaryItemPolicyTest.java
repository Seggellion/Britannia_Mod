package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M1 (discovery D1, protocol section 1.5): which granted rewards are
 * TEMPORARY -- and therefore the only ones that may carry the cleanup stamp.
 *
 * <p>Pure decision logic over the parsed response and the raw body; no registry bootstrap is
 * needed because the policy only compares ids as strings.
 */
class QuestTemporaryItemPolicyTest {
    private static final ResourceLocation RING_REGISTRY_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "one_ring");
    private static final ResourceLocation SHOVEL_REGISTRY_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "britannia_shovel");

    /** Every item the Rowan questline hands out to keep; none may ever be stamped. */
    private static final List<String> PERMANENT_KIT = List.of(
        "britannia_shovel", "empty_bowl", "minecraft:bucket", "watering_can", "carrot_seeds",
        "farming_hoe", "gold_coin", "silver_coin", "copper_coin", "britannia_mod:carrot", "fertilized_dirt");

    // --- Legacy responses: the destination-node heuristic -------------------------------------

    @Test
    void aDestroyObjectiveNamingTheItemBareMakesItTemporaryWithItsTriggerKeyAndVolume() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, null);

        assertTrue(decision.temporary());
        assertEquals("ring_destroyed", decision.triggerKey());
        assertEquals("magic_ring", decision.itemTag());
        assertEquals(QuestTemporaryItemPolicy.Source.DESTINATION_NODE, decision.source());
        assertEquals(new QuestTemporaryItemPolicy.Volume(10, 60, -20, 20, 70, -10), decision.volume());
    }

    @Test
    void aDestroyObjectiveNamingTheItemNamespacedAlsoMatches() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("britannia_mod:magic_ring", "ring_destroyed"));

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, null);

        assertTrue(decision.temporary());
        assertEquals("ring_destroyed", decision.triggerKey());
        assertEquals("britannia_mod:magic_ring", decision.itemTag(), "the stamp carries the objective's own spelling");
    }

    @Test
    void aNamespacedGrantMatchesABareObjectiveTag() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));

        assertTrue(QuestTemporaryItemPolicy.decide("britannia_mod:magic_ring", RING_REGISTRY_ID, response, null).temporary());
        assertTrue(QuestTemporaryItemPolicy.decide("  Magic_Ring ", RING_REGISTRY_ID, response, null).temporary(),
            "ids are compared trimmed and case-insensitively");
    }

    @Test
    void anObjectiveNamingTheResolvedRegistryIdAlsoMatches() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("britannia_mod:one_ring", "ring_destroyed"));

        assertTrue(QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, null).temporary());
    }

    @Test
    void aPickupObjectiveNamingTheItemMakesItTemporaryWithoutAVolume() {
        JsonObject metadata = new JsonObject();
        JsonObject pickup = new JsonObject();
        pickup.addProperty("trigger_key", "relic_recovered");
        pickup.addProperty("item_tag", "ancient_relic");
        metadata.add("pickup_trigger", pickup);

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("ancient_relic", null, responseWithNode(metadata), null);

        assertTrue(decision.temporary());
        assertEquals("relic_recovered", decision.triggerKey());
        assertEquals("ancient_relic", decision.itemTag());
        assertNull(decision.volume());
        assertEquals(QuestTemporaryItemPolicy.Source.DESTINATION_NODE, decision.source());
    }

    @Test
    void anObjectiveNamingAnotherItemLeavesTheGrantPermanent() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));

        for (String kitItem : PERMANENT_KIT) {
            QuestTemporaryItemPolicy.Decision decision =
                QuestTemporaryItemPolicy.decide(kitItem, SHOVEL_REGISTRY_ID, response, null);
            assertFalse(decision.temporary(), kitItem + " must never be stamped");
            assertEquals("", decision.triggerKey(), kitItem + " must carry no trigger key");
        }
    }

    @Test
    void withoutAnObjectiveEveryGrantIsPermanent() {
        QuestModels.QuestResponse bare = new QuestModels.QuestResponse();
        QuestModels.QuestResponse nodeWithoutMetadata = new QuestModels.QuestResponse();
        nodeWithoutMetadata.currentNode = new QuestModels.QuestNode();
        QuestModels.QuestResponse locationOnly = responseWithNode(locationTrigger());

        for (String kitItem : PERMANENT_KIT) {
            assertFalse(QuestTemporaryItemPolicy.decide(kitItem, null, bare, null).temporary(), kitItem);
            assertFalse(QuestTemporaryItemPolicy.decide(kitItem, null, nodeWithoutMetadata, null).temporary(), kitItem);
            assertFalse(QuestTemporaryItemPolicy.decide(kitItem, null, locationOnly, null).temporary(), kitItem);
            assertFalse(QuestTemporaryItemPolicy.decide(kitItem, null, null, null).temporary(), kitItem);
        }
        assertFalse(QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, bare, null).temporary(),
            "even the ring is permanent when nothing says it must be destroyed");
    }

    @Test
    void anObjectiveWithoutATriggerKeyCannotMakeAnItemTemporary() {
        JsonObject metadata = new JsonObject();
        JsonObject destroy = new JsonObject();
        destroy.addProperty("item_tag", "magic_ring");
        metadata.add("destroy_trigger", destroy);

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, responseWithNode(metadata), null);

        assertFalse(decision.temporary(), "a stamp without a trigger key could never be cleaned up, so it is not written");
    }

    @Test
    void blankOrNullGrantIdsArePermanent() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));
        assertFalse(QuestTemporaryItemPolicy.decide(null, null, response, null).temporary());
        assertFalse(QuestTemporaryItemPolicy.decide("   ", null, response, null).temporary());
    }

    // --- Rails' verdict: reward_delivery.items[].temporary ------------------------------------

    @Test
    void railsSayingPermanentOverridesAMatchingObjective() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));
        JsonObject raw = delivery("{\"id\":\"britannia_mod:magic_ring\",\"count\":1,\"temporary\":false}");

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, raw);

        assertFalse(decision.temporary());
        assertEquals(QuestTemporaryItemPolicy.Source.DELIVERY, decision.source());
    }

    @Test
    void railsSayingTemporaryUsesTheObjectiveKeyWhenOneNamesTheItem() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));
        JsonObject raw = delivery("{\"id\":\"britannia_mod:magic_ring\",\"count\":1,\"temporary\":true}");

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, raw);

        assertTrue(decision.temporary());
        assertEquals("ring_destroyed", decision.triggerKey());
        assertEquals(QuestTemporaryItemPolicy.Source.DELIVERY, decision.source());
        assertEquals(new QuestTemporaryItemPolicy.Volume(10, 60, -20, 20, 70, -10), decision.volume());
    }

    @Test
    void railsSayingTemporaryWithoutAnObjectiveStillStampsWithTheUnnamedTriggerKey() {
        JsonObject raw = delivery("{\"id\":\"britannia_mod:sealed_letter\",\"count\":1,\"temporary\":true}");

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("sealed_letter", null, responseWithNode(locationTrigger()), raw);

        assertTrue(decision.temporary());
        assertEquals(QuestTemporaryItemPolicy.UNNAMED_TEMPORARY_TRIGGER_KEY, decision.triggerKey(),
            "cleanup keys on the trigger key, so a temporary item always carries one");
        assertEquals("sealed_letter", decision.itemTag());
        assertNull(decision.volume());
    }

    @Test
    void railsSayingPermanentKeepsTheKitPermanent() {
        JsonObject raw = delivery(
            "{\"id\":\"britannia_mod:britannia_shovel\",\"count\":1,\"temporary\":false},"
                + "{\"id\":\"britannia_mod:gold_coin\",\"count\":5,\"temporary\":false},"
                + "{\"id\":\"minecraft:bucket\",\"count\":1,\"temporary\":false}");
        QuestModels.QuestResponse response = responseWithNode(locationTrigger());

        assertFalse(QuestTemporaryItemPolicy.decide("britannia_shovel", SHOVEL_REGISTRY_ID, response, raw).temporary());
        assertFalse(QuestTemporaryItemPolicy.decide("gold_coin", null, response, raw).temporary());
        assertFalse(QuestTemporaryItemPolicy.decide("minecraft:bucket", null, response, raw).temporary());
    }

    @Test
    void aDeliveryThatDoesNotListTheItemFallsBackToTheHeuristic() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));
        JsonObject raw = delivery("{\"id\":\"britannia_mod:gold_coin\",\"count\":5,\"temporary\":false}");

        assertTrue(QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, raw).temporary());
        assertFalse(QuestTemporaryItemPolicy.decide("gold_coin", null, response, raw).temporary());
    }

    @Test
    void aMalformedTemporaryFlagIsNoVerdict() {
        QuestModels.QuestResponse response = responseWithNode(destroyTrigger("magic_ring", "ring_destroyed"));

        JsonObject stringFlag = delivery("{\"id\":\"britannia_mod:magic_ring\",\"count\":1,\"temporary\":\"yes\"}");
        assertTrue(QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, stringFlag).temporary(),
            "a non-boolean flag must fall back to the heuristic, never read as a verdict");

        JsonObject missingFlag = delivery("{\"id\":\"britannia_mod:magic_ring\",\"count\":1}");
        assertTrue(QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, response, missingFlag).temporary());

        JsonObject stringFlagOnKit = delivery("{\"id\":\"britannia_mod:gold_coin\",\"count\":1,\"temporary\":\"true\"}");
        assertFalse(QuestTemporaryItemPolicy.decide("gold_coin", null, response, stringFlagOnKit).temporary(),
            "a string 'true' must never stamp a coin");

        JsonObject notAnObject = JsonParser.parseString("{\"reward_delivery\":\"broken\"}").getAsJsonObject();
        assertFalse(QuestTemporaryItemPolicy.decide("gold_coin", null, response, notAnObject).temporary());
        JsonObject itemsNotAnArray = JsonParser.parseString("{\"reward_delivery\":{\"items\":{}}}").getAsJsonObject();
        assertFalse(QuestTemporaryItemPolicy.decide("gold_coin", null, response, itemsNotAnArray).temporary());
    }

    // --- Id normalisation ---------------------------------------------------------------------

    @Test
    void idsNormaliseToTheModNamespaceWhenBare() {
        assertEquals("britannia_mod:gold_coin", QuestTemporaryItemPolicy.normalize("gold_coin"));
        assertEquals("britannia_mod:gold_coin", QuestTemporaryItemPolicy.normalize(" Gold_Coin "));
        assertEquals("minecraft:bucket", QuestTemporaryItemPolicy.normalize("minecraft:bucket"));
        assertEquals("", QuestTemporaryItemPolicy.normalize(null));
        assertEquals("", QuestTemporaryItemPolicy.normalize("   "));
    }

    @Test
    void matchingIsExactAfterNormalisation() {
        assertTrue(QuestTemporaryItemPolicy.matches("magic_ring", "britannia_mod:magic_ring", null));
        assertTrue(QuestTemporaryItemPolicy.matches("britannia_mod:one_ring", "magic_ring", RING_REGISTRY_ID));
        assertFalse(QuestTemporaryItemPolicy.matches("magic_rings", "magic_ring", RING_REGISTRY_ID));
        assertFalse(QuestTemporaryItemPolicy.matches("ring", "magic_ring", RING_REGISTRY_ID),
            "a substring is not a match");
        assertFalse(QuestTemporaryItemPolicy.matches("", "magic_ring", RING_REGISTRY_ID));
        assertFalse(QuestTemporaryItemPolicy.matches(null, "magic_ring", RING_REGISTRY_ID));
    }

    @Test
    void volumesAcceptNumbersAndNumericStrings() {
        JsonObject metadata = new JsonObject();
        JsonObject destroy = new JsonObject();
        destroy.addProperty("trigger_key", "ring_destroyed");
        destroy.addProperty("item_tag", "magic_ring");
        destroy.addProperty("min_x", "-5");
        destroy.addProperty("min_y", 3);
        destroy.addProperty("min_z", "7");
        destroy.addProperty("max_x", "not a number");
        metadata.add("destroy_trigger", destroy);

        QuestTemporaryItemPolicy.Decision decision =
            QuestTemporaryItemPolicy.decide("magic_ring", RING_REGISTRY_ID, responseWithNode(metadata), null);

        assertEquals(new QuestTemporaryItemPolicy.Volume(-5, 3, 7, 0, 0, 0), decision.volume());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static QuestModels.QuestResponse responseWithNode(JsonObject metadata) {
        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = true;
        response.quest_id = 41L;
        response.questStateId = "9001";
        QuestModels.QuestNode node = new QuestModels.QuestNode();
        node.id = 1201L;
        node.title = "Cast it into the fire";
        node.metadata = metadata;
        response.currentNode = node;
        return response;
    }

    private static JsonObject destroyTrigger(String itemTag, String triggerKey) {
        JsonObject metadata = new JsonObject();
        JsonObject destroy = new JsonObject();
        destroy.addProperty("trigger_key", triggerKey);
        destroy.addProperty("item_tag", itemTag);
        destroy.addProperty("min_x", 10);
        destroy.addProperty("min_y", 60);
        destroy.addProperty("min_z", -20);
        destroy.addProperty("max_x", 20);
        destroy.addProperty("max_y", 70);
        destroy.addProperty("max_z", -10);
        metadata.add("destroy_trigger", destroy);
        return metadata;
    }

    private static JsonObject locationTrigger() {
        JsonObject metadata = new JsonObject();
        JsonObject location = new JsonObject();
        location.addProperty("trigger_key", "arrived_at_well");
        location.addProperty("min_x", 0);
        location.addProperty("max_x", 5);
        metadata.add("location_trigger", location);
        return metadata;
    }

    private static JsonObject delivery(String itemsJson) {
        return JsonParser.parseString("{\"success\":true,\"reward_delivery\":{\"protocol_version\":1,"
            + "\"delivery_uuid\":\"6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01\",\"items\":[" + itemsJson + "]}}")
            .getAsJsonObject();
    }
}
