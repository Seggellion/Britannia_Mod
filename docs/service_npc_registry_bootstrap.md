# Service NPC registry bootstrap contract

Milestone 3 adds a single optional `service_npc_registry` member to the authenticated world bootstrap response. It is additive: existing root members and name-based city handling remain unchanged. Each city may also contain its durable `public_id`; existing consumers may continue using `name`.

## Schema 1

```json
{
  "service_npc_registry": {
    "schema_version": 1,
    "revision": 501424005180508762,
    "service_actions": [
      {
        "key": "bank.open",
        "display_name": "Open bank account",
        "description": "Open the player's bank account."
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
        "definition_revision": 1
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
            "options": [
              {
                "id": "open_bank_box",
                "label": "Open my bank box.",
                "action_type": "invoke_service",
                "service_key": "bank.open"
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
```

`schema_version` selects the parser contract. Schema 1 accepts only `navigate`, `invoke_service`, and `close` actions. It accepts only `%{city_name}`, `%{npc_name}`, and `%{profession_name}` interpolation. Unknown JSON members are ignored, but unknown action types, services, references, required-field shapes, and interpolation directives reject the entire Service NPC registry.

`revision` is a deterministic Rails-generated digest integer over the complete published registry. Any serialized definition change changes it. `definition_revision` is the explicit revision owned by each type or dialogue definition. The world-bootstrap ETag includes the top-level registry revision.

## Consumer behavior

A missing member becomes the immutable empty snapshot. A malformed member or unsupported future schema fails closed to that same empty snapshot without rejecting fish, regions, cities, grapes, shard-user data, or accepted quests. A complete snapshot is parsed and cross-validated before the cache is replaced. Replacement occurs in `WorldBootstrapHandler` inside its existing `thenAcceptAsync(..., player.server)` server-thread continuation.

Older clients ignore the new root member and city `public_id`. Current city synchronization continues to look up cities by `name`.

## Dialogue and dispatch boundary

`QuestDialogueAdapter` converts quest responses into the shared presentation model without moving quest transitions or state. `ServiceDialogueController` resolves an option identifier against the immutable current node; callers do not provide an action type or service key. The fixed dispatcher recognizes only `bank.open` and `bank.create_check`. Both return `service_not_available` and `Banking services are not available yet.` No handler receives inventory, currency, quest, world, Rails, or networking mutation objects.

`ServiceDialogueScreen` is a presentation-only test path. Milestone 3 does not register a Service NPC entity, spawn block, command, direct HTTP call, or production packet. Production interaction remains deferred until the server can validate entity identity, proximity, persistent World NPC identity, Service NPC type, current node, selected option, session ownership, and expiry.
