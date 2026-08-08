package com.seggellion.britannia_mod.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBootstrapAPINullHandlingTest {
    @AfterEach
    void clearCaches() {
        BootstrapCityRegistryCache.clear();
        ServiceNpcRegistryCache.clear();
    }

    @Test
    void inventoryObjectParsesNormallyAndRemainsUnchanged() {
        JsonObject inventory = JsonParser.parseString("{\"bank_check\":2}").getAsJsonObject();
        WorldBootstrapAPI.ShardUserData parsed =
                WorldBootstrapAPI.parseShardUser(rootWithInventory(inventory));

        assertEquals(inventory, parsed.inventory());
        assertEquals(2, parsed.inventory().get("bank_check").getAsInt());
    }

    @Test
    void explicitNullInventoryUsesAnEmptyObject() {
        WorldBootstrapAPI.ShardUserData parsed =
                WorldBootstrapAPI.parseShardUser(rootWithInventory(JsonNull.INSTANCE));

        assertTrue(parsed.inventory().isEmpty());
    }

    @Test
    void missingInventoryUsesAnEmptyObject() {
        JsonObject root = rootWithInventory(new JsonObject());
        root.getAsJsonObject("shard_user").remove("inventory");

        WorldBootstrapAPI.ShardUserData parsed = WorldBootstrapAPI.parseShardUser(root);

        assertTrue(parsed.inventory().isEmpty());
    }

    @Test
    void emptyInventoryObjectParsesSafely() {
        WorldBootstrapAPI.ShardUserData parsed =
                WorldBootstrapAPI.parseShardUser(rootWithInventory(new JsonObject()));

        assertTrue(parsed.inventory().isEmpty());
    }

    @Test
    void invalidInventoryPrimitiveAndArrayTypesAreRejectedWithTypedMetadata() {
        List<JsonElement> invalidValues = List.of(
                JsonParser.parseString("\"RAW_BODY_SENTINEL\""),
                JsonParser.parseString("[]"),
                JsonParser.parseString("42"),
                JsonParser.parseString("true")
        );

        for (JsonElement invalidValue : invalidValues) {
            WorldBootstrapAPI.BootstrapParseException failure = assertThrows(
                    WorldBootstrapAPI.BootstrapParseException.class,
                    () -> WorldBootstrapAPI.parseShardUser(rootWithInventory(invalidValue))
            );
            assertEquals("shard_user.inventory", failure.field());
            assertEquals("object_or_null", failure.expected());
            assertFalse(failure.getMessage().contains("RAW_BODY_SENTINEL"));
        }
    }

    @Test
    void nullableOrMissingStatsUsesTheSameEstablishedEmptyObjectDefault() {
        JsonObject explicitNull = rootWithInventory(new JsonObject());
        explicitNull.getAsJsonObject("shard_user").add("stats", JsonNull.INSTANCE);
        assertTrue(WorldBootstrapAPI.parseShardUser(explicitNull).stats().isEmpty());

        JsonObject missing = rootWithInventory(new JsonObject());
        missing.getAsJsonObject("shard_user").remove("stats");
        assertTrue(WorldBootstrapAPI.parseShardUser(missing).stats().isEmpty());
    }

    @Test
    void absentOrNullShardUserIsOptionalButWrongTypeIsRejected() {
        assertNull(WorldBootstrapAPI.parseShardUser(new JsonObject()));

        JsonObject explicitNull = new JsonObject();
        explicitNull.add("shard_user", JsonNull.INSTANCE);
        assertNull(WorldBootstrapAPI.parseShardUser(explicitNull));

        JsonObject wrongType = new JsonObject();
        wrongType.addProperty("shard_user", "not-an-object");
        WorldBootstrapAPI.BootstrapParseException failure = assertThrows(
                WorldBootstrapAPI.BootstrapParseException.class,
                () -> WorldBootstrapAPI.parseShardUser(wrongType)
        );
        assertEquals("shard_user", failure.field());
        assertEquals("object_or_null", failure.expected());
    }

    @Test
    void nullInventoryRetainsCityMarketQuestAndBankTellerRegistryData() {
        JsonObject root = completeCoreRoot(JsonNull.INSTANCE);

        WorldBootstrapAPI.CoreBootstrapData core = WorldBootstrapAPI.parseCore(root);

        assertTrue(core.shardUser().inventory().isEmpty());
        assertEquals(1, core.cities().size());
        WorldBootstrapAPI.CityBootstrapData city = core.cities().getFirst();
        assertEquals(7.5, city.food());
        assertEquals(41, city.gold());
        assertEquals(12.5, city.weights().get("food").get("grain").get("wheat"));
        assertEquals(7, city.quantities().get("food").get("grain").get("wheat"));
        assertEquals("quest_state_1", core.acceptedQuests().getFirst().questStateId());
        assertTrue(core.serviceNpcRegistry().serviceNpcTypes().get("bank_teller").active());
        assertTrue(core.serviceNpcRegistry().serviceNpcTypes().get("bank_teller").spawnable());

        ServiceNpcRegistryCache.replace(core.serviceNpcRegistry());
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(
                core.cities().stream()
                        .map(value -> new BootstrapCityDefinition(UUID.fromString(value.publicId()), value.name()))
                        .toList()
        ));
        assertTrue(ServiceNpcRegistryCache.snapshot().serviceNpcTypes().containsKey("bank_teller"));
        assertEquals("Britain",
                BootstrapCityRegistryCache.snapshot().cities().values().iterator().next().displayName());
    }

    @Test
    void malformedInventoryProducesNoPartialCacheOrPlayerDataResult() {
        WorldBootstrapAPI.CoreBootstrapData newer =
                WorldBootstrapAPI.parseCore(completeCoreRoot(new JsonObject()));
        PlayerData existingPlayerData = new PlayerData(UUID.randomUUID());
        JsonObject existingInventory = JsonParser.parseString("{\"existing_item\":3}").getAsJsonObject();
        existingPlayerData.syncFromShardUser("female", 1, 2, 0, existingInventory, new JsonObject());
        ServiceNpcRegistryCache.replace(newer.serviceNpcRegistry());
        BootstrapCityRegistrySnapshot newerCities = BootstrapCityRegistrySnapshot.available(
                List.of(new BootstrapCityDefinition(
                        UUID.fromString(newer.cities().getFirst().publicId()),
                        newer.cities().getFirst().name()
                ))
        );
        BootstrapCityRegistryCache.replace(newerCities);

        assertThrows(
                WorldBootstrapAPI.BootstrapParseException.class,
                () -> WorldBootstrapAPI.parseCore(completeCoreRoot(JsonParser.parseString("[]")))
        );

        assertSame(newer.serviceNpcRegistry(), ServiceNpcRegistryCache.snapshot());
        assertSame(newerCities, BootstrapCityRegistryCache.snapshot());
        assertEquals(existingInventory, existingPlayerData.getInventory());
    }

    private static JsonObject rootWithInventory(JsonElement inventory) {
        JsonObject root = new JsonObject();
        JsonObject shardUser = new JsonObject();
        shardUser.addProperty("gender", "female");
        shardUser.addProperty("fame", 2);
        shardUser.addProperty("karma", 3);
        shardUser.addProperty("murder_count", 0);
        shardUser.add("inventory", inventory);
        shardUser.add("stats", JsonParser.parseString("{\"strength\":10}").getAsJsonObject());
        root.add("shard_user", shardUser);
        return root;
    }

    private static JsonObject completeCoreRoot(JsonElement inventory) {
        JsonObject root = rootWithInventory(inventory);
        JsonObject retained = JsonParser.parseString("""
                {
                  "cities": [{
                    "public_id": "0de41982-7807-47ed-9802-4921e17f0d97",
                    "name": "Britain",
                    "supplies": {
                      "food": 7.5, "wood": 2, "metal": 3, "stone": 4,
                      "textile": 5, "alcohol": 6, "technology": 7
                    },
                    "treasury": { "gold": 41, "silver": 23, "copper": 9 },
                    "market_weights": { "food": { "grain": { "wheat": 12.5 } } },
                    "market_quantities": { "food": { "grain": { "wheat": 7 } } },
                    "npcs": []
                  }],
                  "accepted_quests": [{
                    "quest_state_id": "quest_state_1",
                    "quest_id": "quest_1",
                    "quest_key": "first_quest",
                    "name": "First Quest",
                    "status": "accepted"
                  }],
                  "service_npc_registry": {
                    "schema_version": 1,
                    "revision": 1,
                    "service_actions": [],
                    "dialogue_sets": [{
                      "key": "bank_teller_default",
                      "entry_node_key": "greeting",
                      "definition_revision": 1,
                      "nodes": [{
                        "key": "greeting",
                        "body": "Welcome.",
                        "options": [{
                          "id": "goodbye",
                          "label": "Goodbye.",
                          "action_type": "close"
                        }]
                      }]
                    }],
                    "service_npc_types": [{
                      "key": "bank_teller",
                      "display_name": "Bank Teller",
                      "profession_key": "banker",
                      "minecraft_entity_type_key": "britannia_mod:service_npc",
                      "default_dialogue_key": "bank_teller_default",
                      "allowed_service_keys": [],
                      "active": true,
                      "spawnable": true,
                      "definition_revision": 1
                    }]
                  }
                }
                """).getAsJsonObject();
        for (String key : retained.keySet()) root.add(key, retained.get(key));
        return root;
    }
}
