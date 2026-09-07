package com.seggellion.britannia_mod.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M3 (protocol section 1.6): the bootstrap's additive
 * {@code pending_reward_deliveries} array is parsed as part of the core section, tolerantly per
 * entry, and is simply empty on an older Rails that does not publish it.
 */
class WorldBootstrapAPIPendingRewardDeliveriesTest {
    private static final String DELIVERY_UUID = "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01";

    @Test
    void thePendingArrayRidesAlongWithTheCoreSection() {
        JsonObject root = JsonParser.parseString("""
            {
              "accepted_quests": [{"quest_state_id": "9001", "quest_id": "41", "name": "Dung Duty"}],
              "pending_reward_deliveries": [
                {"delivery_uuid": "%s", "quest_id": 41, "quest_state_id": "9001",
                 "transition_key": "1757200000:1201:choice:accept",
                 "items": [{"id": "britannia_mod:britannia_shovel", "count": 1, "temporary": false}],
                 "created_at": "2026-09-06T21:10:00Z"},
                {"delivery_uuid": "not-a-uuid", "items": []}
              ]
            }
            """.formatted(DELIVERY_UUID)).getAsJsonObject();

        WorldBootstrapAPI.CoreBootstrapData core = WorldBootstrapAPI.parseCore(root);

        assertEquals(1, core.acceptedQuests().size());
        assertEquals(1, core.pendingRewardDeliveries().size(), "the malformed element is skipped, the good one kept");
        assertEquals(UUID.fromString(DELIVERY_UUID), core.pendingRewardDeliveries().get(0).deliveryUuid());
        assertEquals("9001", core.pendingRewardDeliveries().get(0).questStateId());
    }

    @Test
    void anOlderRailsWithoutTheArrayStillBootstraps() {
        JsonObject root = new JsonObject();
        root.add("accepted_quests", new JsonArray());
        WorldBootstrapAPI.CoreBootstrapData core = WorldBootstrapAPI.parseCore(root);
        assertTrue(core.pendingRewardDeliveries().isEmpty());
    }

    @Test
    void aFailedBootstrapCarriesNoPendingDeliveries() {
        assertTrue(WorldBootstrapAPI.WorldBootstrapData.failed("fetch_failed").pendingRewardDeliveries().isEmpty());
        assertTrue(WorldBootstrapAPI.WorldBootstrapData.empty().pendingRewardDeliveries().isEmpty());
    }
}
