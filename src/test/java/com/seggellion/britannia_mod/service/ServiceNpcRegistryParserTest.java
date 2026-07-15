package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNpcRegistryParserTest {
    @AfterEach
    void clearCache() {
        ServiceNpcRegistryCache.clear();
    }

    @Test
    void missingRegistryProducesAnEmptySnapshot() {
        JsonObject root = JsonParser.parseString("{\"fish\":[],\"cities\":[]}").getAsJsonObject();

        ServiceNpcRegistryParser.ParseResult result = ServiceNpcRegistryParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcRegistryParser.ParseStatus.MISSING, result.status());
        assertTrue(result.snapshot().isEmpty());
        assertEquals(1, result.snapshot().schemaVersion());
    }

    @Test
    void parsesACompleteImmutableSnapshotAndIgnoresUnknownFields() {
        JsonObject root = validBootstrapRoot();

        ServiceNpcRegistryParser.ParseResult result = ServiceNpcRegistryParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcRegistryParser.ParseStatus.ACCEPTED, result.status(), result.error());
        ServiceNpcRegistrySnapshot snapshot = result.snapshot();
        assertEquals(501424005180508762L, snapshot.revision());
        assertEquals(List.of("bank.open", "bank.create_check"), snapshot.serviceActions().keySet().stream().toList());

        ServiceNpcTypeDefinition npcType = snapshot.serviceNpcTypes().get("bank_teller");
        assertEquals("Bank Teller", npcType.displayName());
        assertEquals("banker", npcType.professionKey());
        assertEquals("britannia_mod:service_npc", npcType.minecraftEntityTypeKey());
        assertEquals("bank_teller_default", npcType.defaultDialogueKey());
        assertEquals(List.of("bank.open", "bank.create_check"), npcType.allowedServiceKeys());
        assertTrue(npcType.active());
        assertTrue(npcType.spawnable());

        ServiceDialogueSetDefinition dialogue = snapshot.dialogueSets().get("bank_teller_default");
        assertEquals("greeting", dialogue.entryNodeKey());
        assertEquals(List.of("open_bank_box", "create_bank_check", "goodbye"),
                dialogue.nodes().getFirst().options().stream().map(ServiceDialogueOptionDefinition::id).toList());
        assertEquals(DialogueActionType.INVOKE_SERVICE,
                dialogue.nodes().getFirst().options().getFirst().actionType());
        assertEquals(DialogueActionType.CLOSE,
                dialogue.nodes().getFirst().options().getLast().actionType());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.serviceNpcTypes().put("other", npcType));
        assertThrows(UnsupportedOperationException.class,
                () -> npcType.allowedServiceKeys().add("other.action"));
        assertThrows(UnsupportedOperationException.class,
                () -> dialogue.nodes().getFirst().options().clear());
    }

    @Test
    void rejectsUnsupportedSchemasWithoutTouchingUnrelatedBootstrapMembers() {
        JsonObject root = validBootstrapRoot();
        root.getAsJsonObject(ServiceNpcRegistryParser.ROOT_KEY).addProperty("schema_version", 2);

        ServiceNpcRegistryParser.ParseResult result = ServiceNpcRegistryParser.parseBootstrapRoot(root);

        assertEquals(ServiceNpcRegistryParser.ParseStatus.REJECTED, result.status());
        assertTrue(result.snapshot().isEmpty());
        assertTrue(result.error().contains("unsupported schema_version"));
        assertEquals("unrelated-data", root.get("unrelated").getAsString());
    }

    @Test
    void rejectsTheWholeRegistryForUnknownActionsOrUnsafeInterpolation() {
        JsonObject unknownAction = validBootstrapRoot();
        unknownAction.getAsJsonObject(ServiceNpcRegistryParser.ROOT_KEY)
                .getAsJsonArray("service_actions")
                .get(0).getAsJsonObject()
                .addProperty("key", "bank.withdraw");

        ServiceNpcRegistryParser.ParseResult actionResult =
                ServiceNpcRegistryParser.parseBootstrapRoot(unknownAction);
        assertEquals(ServiceNpcRegistryParser.ParseStatus.REJECTED, actionResult.status());
        assertTrue(actionResult.snapshot().isEmpty());

        JsonObject unsafeTemplate = validBootstrapRoot();
        unsafeTemplate.getAsJsonObject(ServiceNpcRegistryParser.ROOT_KEY)
                .getAsJsonArray("dialogue_sets")
                .get(0).getAsJsonObject()
                .getAsJsonArray("nodes")
                .get(0).getAsJsonObject()
                .addProperty("body", "Welcome %{player_class}.");

        ServiceNpcRegistryParser.ParseResult templateResult =
                ServiceNpcRegistryParser.parseBootstrapRoot(unsafeTemplate);
        assertEquals(ServiceNpcRegistryParser.ParseStatus.REJECTED, templateResult.status());
        assertTrue(templateResult.snapshot().isEmpty());
    }

    @Test
    void cacheReplacementIsWholeSnapshotOrWholeEmptyFallback() {
        ServiceNpcRegistrySnapshot accepted =
                ServiceNpcRegistryParser.parseBootstrapRoot(validBootstrapRoot()).snapshot();
        ServiceNpcRegistryCache.replace(accepted);

        assertEquals(accepted, ServiceNpcRegistryCache.snapshot());
        assertFalse(ServiceNpcRegistryCache.snapshot().isEmpty());

        JsonObject invalid = validBootstrapRoot();
        invalid.getAsJsonObject(ServiceNpcRegistryParser.ROOT_KEY).addProperty("revision", 1.5);
        ServiceNpcRegistryParser.ParseResult rejected = ServiceNpcRegistryParser.parseBootstrapRoot(invalid);
        assertEquals(ServiceNpcRegistryParser.ParseStatus.REJECTED, rejected.status());

        ServiceNpcRegistryCache.replace(rejected.snapshot());
        assertTrue(ServiceNpcRegistryCache.snapshot().isEmpty());
        assertEquals(0L, ServiceNpcRegistryCache.snapshot().revision());
    }

    private static JsonObject validBootstrapRoot() {
        return JsonParser.parseString("""
                {
                  "unrelated": "unrelated-data",
                  "service_npc_registry": {
                    "schema_version": 1,
                    "revision": 501424005180508762,
                    "future_registry_field": {"ignored": true},
                    "service_actions": [
                      {
                        "key": "bank.open",
                        "display_name": "Open bank account",
                        "description": "Open the player's bank account.",
                        "future_metadata": "ignored"
                      },
                      {
                        "key": "bank.create_check",
                        "display_name": "Create bank check",
                        "description": "Begin bank-check creation."
                      }
                    ],
                    "service_npc_types": [
                      {
                        "key": "bank_teller",
                        "display_name": "Bank Teller",
                        "profession_key": "banker",
                        "minecraft_entity_type_key": "britannia_mod:service_npc",
                        "default_dialogue_key": "bank_teller_default",
                        "allowed_service_keys": ["bank.open", "bank.create_check"],
                        "active": true,
                        "spawnable": true,
                        "definition_revision": 1,
                        "future_type_field": "ignored"
                      }
                    ],
                    "dialogue_sets": [
                      {
                        "key": "bank_teller_default",
                        "entry_node_key": "greeting",
                        "definition_revision": 1,
                        "nodes": [
                          {
                            "key": "greeting",
                            "body": "Welcome to the bank of %{city_name}. How may I assist you?",
                            "presentation_hint": "ignored",
                            "options": [
                              {
                                "id": "open_bank_box",
                                "label": "Open my bank box.",
                                "action_type": "invoke_service",
                                "service_key": "bank.open",
                                "client_hint": "ignored"
                              },
                              {
                                "id": "create_bank_check",
                                "label": "I would like to create a bank check.",
                                "action_type": "invoke_service",
                                "service_key": "bank.create_check"
                              },
                              {
                                "id": "goodbye",
                                "label": "Goodbye.",
                                "action_type": "close"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """).getAsJsonObject();
    }
}
