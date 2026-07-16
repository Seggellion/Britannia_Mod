# Service NPC registry bootstrap contract

Milestone 3 adds a single optional `service_npc_registry` member to the authenticated world bootstrap response. It is additive: existing root members and name-based city handling remain unchanged. Each city may also contain its durable `public_id`; existing consumers may continue using `name`.

The logical server owns authentication. It loads `ULTIMACRAFT_SHARD_NAME` plus `ULTIMACRAFT_SHARD_SECRET`, or the complete ignored server-only properties fallback, and sends `Shard-Name` and `Shard-Secret` directly to Rails. The requested shard is resolved before its secret is compared. No credential is placed in the bootstrap body, client packet, client state, or world SavedData. See `docs/server_authentication.md`.

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

A missing member becomes the immutable empty snapshot. A malformed member or unsupported future schema fails closed to that same empty snapshot without rejecting fish, regions, cities, grapes, shard-user data, or accepted quests. A complete snapshot is parsed and cross-validated on a bounded worker before the cache is replaced. `WorldBootstrapHandler` applies the complete result through `MinecraftServer.execute`; generation guards discard a completion after disconnect, a newer login, or server stop. Only sanitized city UUID/name and active, spawnable type key/name choices are sent to the client.

Older clients ignore the new root member and city `public_id`. Current city synchronization continues to look up cities by `name`.


## Dedicated-server compact profile and transport

`api_base_url` is the Rails service origin, not an endpoint prefix. The centralized resolver accepts origins ending with no slash, `/`, `/api`, or `/api/`, normalizes them to the origin, and constructs the bootstrap endpoint exactly once as `/api/world_bootstrap/:shard`. Plain HTTP is accepted only for explicit loopback hosts; non-loopback origins require HTTPS.

The dedicated server requests `profile=minecraft_server`. Rails retains cities, UUIDs, supplies, treasury, market weights/quantities, NPCs, accepted quests, Service NPC registry, fish, regions, grapes, and shard-user data while omitting only each city's redundant `commodities` array. The default profile remains the full backward-compatible document.

Profile values are single, bounded, and closed. Unknown, blank, repeated, array-shaped, malformed, or oversized profiles return `invalid_bootstrap_profile` before player mutation. Full and compact responses use profile-isolated ETags. Rails preloads city associations so compact query growth remains bounded, and the representative 23-city compact contract remains below 2.5 MiB with headroom under the 4 MiB client limit.

The server limits bootstrap bodies to 4 MiB, uses 5-second connect, 10-second read, and 15-second overall timeouts, and cancels in-flight requests on disconnect, a newer login, or server stop. Completions are generation-checked before applying on the logical server thread.

`shard_user.inventory` and `shard_user.stats` accept an object, JSON `null`, or absence; null/absence becomes an empty object. Wrong non-null shapes fail the core bootstrap atomically rather than publishing partial player/city/quest/registry state.
## Dialogue and dispatch boundary

`QuestDialogueAdapter` converts quest responses into the shared presentation model without moving quest transitions or state. `ServiceDialogueController` resolves an option identifier against the immutable current node; callers do not provide an action type or service key. The fixed dispatcher recognizes only `bank.open` and `bank.create_check`. Both return `service_not_available` and `Banking services are not available yet.` No handler receives inventory, currency, quest, world, Rails, or networking mutation objects.

`ServiceDialogueScreen` is a presentation-only test path. Milestone 3 does not register a Service NPC entity, spawn block, command, direct HTTP call, or production packet. Production interaction remains deferred until the server can validate entity identity, proximity, persistent World NPC identity, Service NPC type, current node, selected option, session ownership, and expiry.
