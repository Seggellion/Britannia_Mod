# Rowan farming questline — Rails/NeoForge integration protocol

Status: **frozen at M0 (2026-09-06), version 1 of each contract.** This document is the
authority for every payload, result code, identity, and recovery rule that the Rails and NeoForge
sides implement in milestones M2–M5. A change to any field, code, or ownership rule below is a
contract change: it goes back to the project integrator, bumps the affected `protocol_version`,
and updates the mirrored fixtures in both repositories before either side implements it.

Mirrored fixtures: `src/test/resources/quest_contract/v1/` (NeoForge) and
`test/fixtures/quest_contract/v1/` (Rails), byte-identical after LF normalisation, listed with
SHA-256 digests in `MANIFEST.sha256`. Each repository's contract test verifies its copies against
the manifest, so the two sides cannot drift silently.

Repository bases: NeoForge `claude/rowan-farming-questline-mod` from `patch-18` @ `421e2785`;
Rails `claude/rowan-farming-questline-rails` from `release/public` @ `a9425ca`.

## 0. Vocabulary and conventions

* **Player identity**: `player_uuid` is the Minecraft UUID; Rails resolves it with the single
  `resolve_player` path and journal-row candidate list already used by every quest action
  (`quest_states_controller.rb#resolve_player`). Scope is always `(shard, player)`.
* **Quest state identity**: `quest_state_id` = `player_quest_states.id` (string on the wire, as
  today). One row per `(player_uuid, quest_id)`; a restart reuses the row.
* **Run token**: `player_quest_states.run_uuid` — an opaque, database-generated uuid, unique by
  index, minted fresh by a restart. It discriminates successive runs of the same row
  (abandon → restart). It was originally `accepted_at` in integer epoch seconds; whole seconds are
  not an identity, and a player who abandoned and re-accepted inside one second produced the same
  token, so a hand-in prepared in the dead run satisfied the live one. NeoForge treats the token as
  opaque — it appears only inside `transition_key`, which the mod stores and echoes and never
  parses.
* **Authentication tiers** (existing):
  * *Legacy quest tier*: `Shard-Name` + `Shard-Secret`, optional HMAC signature
    (`Api::ShardServerAuthentication`, `REQUIRE_SIGNATURE=false`). Used by the existing quest
    endpoints, which stay where they are.
  * *v2 tier*: shard headers + **mandatory** signature (`REQUIRE_SIGNATURE = true` on the
    controller) + `Minecraft-Server-Key` (`Api::MinecraftServerAuthentication`, the active
    `minecraft_servers.public_id` of the calling server). Every new endpoint below is v2 tier,
    matching `Api::V2::BlessedItemMaterializationsController`.
* **Correlation**: `request_uuid` is the per-attempt trace id the mod already generates and Rails
  already logs (opaque, sanitised, authorises nothing). Stable identities (`delivery_uuid`,
  `event_uuid`) are distinct from it.
* **Timestamps**: ISO-8601 UTC strings.
* **Item ids**: full registry ids on the wire (`britannia_mod:gold_coin`, `minecraft:bucket`).
  Rails accepts bare ids in authored effects and namespaces them to `britannia_mod:` when
  publishing, so NeoForge never guesses.
* **Error envelope** (all new endpoints): `{ "error": "<code>", "field": "...", "detail": "..." }`
  with HTTP status per §6; success envelopes carry `protocol_version`.

## 1. Reward deliveries (contract `quest_reward_delivery`, version 1)

### 1.1 Problem being solved

Today a grant exists only inside one HTTP response and is stamped as a temporary item
(discovery D1, D2). A delivery makes the grant a durable Rails record with a stable identity that
the Minecraft server applies at most once and acknowledges after its own durable record exists.

### 1.2 Rails record: `quest_reward_deliveries`

| Column | Type | Rule |
| --- | --- | --- |
| `delivery_uuid` | uuid, not null, unique, default `gen_random_uuid()` | The stable delivery identity |
| `shard_id` | bigint FK `shards` (`on_delete: :restrict`), not null | Scope |
| `user_id` | bigint FK `users` (`on_delete: :restrict`), not null | Resolved player |
| `player_uuid` | string, not null | Normalised Minecraft UUID at creation |
| `quest_id` | bigint FK `quests` (`on_delete: :nullify`) | Informational |
| `player_quest_state_id` | bigint FK `player_quest_states` (`on_delete: :nullify`) | The transition's row |
| `transition_key` | string(200), not null | `"<run_token>:<source_node_id>:<kind>:<key>"`; `kind` ∈ `choice`, `trigger`, `action`; `key` = choice id, trigger key, or trigger key of the matched `action_trigger` |
| `request_uuid` | string(36), nullable | The attempt that created it |
| `items` | jsonb array, not null | `[{"id":"britannia_mod:britannia_shovel","count":1,"temporary":false}, …]`, 1–32 entries, count 1–1024 |
| `response` | jsonb object, not null | The exact success response returned when the delivery was created; replayed verbatim |
| `state` | string, not null, default `pending` | `pending` → `acknowledged` |
| `acknowledged_outcome` | string, nullable | `applied` or `queued` once acknowledged |
| `acknowledged_at`, `applied_at` | datetime, nullable | Timestamps agree with state (check constraint) |
| `acknowledged_by_minecraft_server_id` | bigint FK `minecraft_servers` (`on_delete: :nullify`) | Which server acknowledged |
| timestamps | | |

Unique indexes: `delivery_uuid`; `(player_quest_state_id, transition_key)` where
`player_quest_state_id IS NOT NULL`. Index: `(shard_id, player_uuid, state)`;
`(shard_id, player_uuid, request_uuid)` where `request_uuid IS NOT NULL`.

Check constraints: `state IN ('pending','acknowledged')`; `jsonb_typeof(items) = 'array'`;
`(state='pending' AND acknowledged_at IS NULL) OR (state='acknowledged' AND acknowledged_at IS NOT NULL AND acknowledged_outcome IN ('applied','queued'))`.

### 1.3 Creation (Rails, inside the locked transition)

`QuestEngine::Processor#advance` and `QuestStatesController#trigger_node` (and the M4 action-event
path) create or find the delivery **inside the same transaction and row lock** as the node
advance, whenever the applied effects produced a non-empty `granted_items`. The response embeds:

```json
"granted_items": [{"id": "britannia_mod:britannia_shovel", "count": 1}],
"reward_delivery": {
  "protocol_version": 1,
  "delivery_uuid": "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01",
  "quest_id": 41,
  "quest_state_id": "9001",
  "transition_key": "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01:1201:choice:accept",
  "items": [{"id": "britannia_mod:britannia_shovel", "count": 1, "temporary": false}],
  "state": "pending",
  "created_at": "2026-09-06T21:10:00Z"
}
```

`granted_items` is kept for legacy clients (they apply immediately, as today). A client that
understands `reward_delivery` **must ignore `granted_items` when `reward_delivery` is present**.

### 1.4 Replay (Rails)

* `request_uuid` matching the delivery's `request_uuid` (any transition, ending or not) returns
  the stored `response` verbatim, with the same `delivery_uuid`. This generalises today's
  ending-only `completion_result` replay; endings keep writing `completion_result` too.
* A **different** `request_uuid` for the same `(state, transition_key)` finds the existing row by
  the unique index and returns the same `delivery_uuid` and items with `"replayed": true`
  (the transition itself is not applied twice because the node already advanced; a second
  claim of an ending stays refused as today).
* Concurrent attempts serialise on the quest-state row lock; the unique index is the backstop.

### 1.5 Temporary versus permanent items

`items[].temporary` is decided by Rails:

* `true` when the effect is `give_temporary_item` (new, same shapes as `give_item`), or when the
  destination node carries a `pickup_trigger`/`destroy_trigger` whose `item_tag` equals the item
  id (bare or namespaced). This reproduces the only legitimate use of the stamp today (the magic
  ring).
* `false` otherwise. Coins, tools, seeds, bowls, buckets, produce are never temporary.

NeoForge stamps `quest_item`/`quest_owner_uuid`/`quest_id`/`quest_state_id` **only** on
`temporary: true` stacks. When the field is absent (old Rails), NeoForge applies the same
destination-node heuristic locally and stamps nothing else. Cleanup keeps deleting stamped items
whose quest left the journal; unstamped items are never touched. Legacy stacks already carrying a
stamp but no `quest_trigger_key` are treated as permanent: their stamp is stripped at the next
cleanup instead of the item being deleted (M1 conservative migration).

### 1.6 Pending listing

`GET /api/v2/quest_reward_deliveries/pending?player_uuid=<uuid>` (v2 tier)

```json
{
  "protocol_version": 1,
  "player_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "deliveries": [
    {"delivery_uuid": "…", "quest_id": 41, "quest_state_id": "9001",
     "transition_key": "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01:1201:choice:accept",
     "items": [{"id": "britannia_mod:britannia_shovel", "count": 1, "temporary": false}],
     "created_at": "2026-09-06T21:10:00Z"}
  ]
}
```

Only `state = pending`, ordered by `created_at, id`, at most 50. An unknown player returns
`deliveries: []` (a listing is not a mutation). The world bootstrap (`GET /api/world_bootstrap/:shard`,
legacy tier) also carries the same array as `pending_reward_deliveries` for the resolved player so
login recovery needs no extra round trip; the v2 endpoint serves on-demand reconciliation.

### 1.7 Acknowledgement

`POST /api/v2/quest_reward_deliveries/:delivery_uuid/result` (v2 tier), body:

```json
{"protocol_version": 1, "delivery_uuid": "…", "player_uuid": "…",
 "outcome": "applied", "recorded_at": "2026-09-06T21:10:04Z", "request_uuid": "…"}
```

* `outcome` ∈ `applied` (items are in the inventory and the local applied record is durable) or
  `queued` (items are in the durable local pending queue because the inventory was full). Both
  mean "the server owns it now": Rails moves the row to `acknowledged`.
* Response: `{"protocol_version": 1, "delivery_uuid": "…", "state": "acknowledged",
  "outcome": "applied", "duplicate": false}`.
* Idempotency: the same outcome again → `duplicate: true`, nothing changes. `queued` followed by
  `applied` is the normal upgrade (records `applied_at`, `duplicate: false`). `applied` followed
  by `queued` → `409 conflicting_delivery_result`.
* Ownership: the delivery must belong to the authenticated shard and the reported
  `player_uuid`'s resolved user; otherwise `404 delivery_not_found` (one answer for "unknown" and
  "not yours"). A `player_uuid` that resolves to a different user than the delivery's →
  `409 player_mismatch`.

### 1.8 NeoForge durable application (M3)

Two durable stores, chosen so that the save boundaries are provable:

1. **Delivery ledger**: `SavedData` `britannia_quest_reward_deliveries` on the overworld storage,
   schema-versioned, quarantining corrupt entries, force-flushed (`fsync` via the same
   `DimensionDataStorage#save()` path `BlessedDeliveryReceipts` uses). Entries:
   `{delivery_uuid, player_uuid, quest_id, quest_state_id, items, local_state, attempts,
   recorded_at, applied_at, acknowledged_at}` with `local_state` ∈ `pending_local` → `applied`
   → `acknowledged`, or `queued` → `applied`. Bounded per player (256 newest; older acknowledged
   entries are pruned).
2. **Player marker**: `PlayerData.applied_delivery_uuids` (bounded list, 256) in the player's
   persistent NBT. It is written in the same in-memory player state as the inserted items, so the
   vanilla player-file write (temp file + atomic replace) persists both or neither.

Apply sequence per delivery:

1. If the ledger already holds the uuid as `applied`/`acknowledged`, or the player marker holds
   it: do not insert; ensure acknowledgement (step 6).
2. Record `pending_local` in the ledger and flush.
3. Insert items on the server thread. If they do not all fit, insert none of them, record
   `queued` (flush) and keep the items in the ledger entry; acknowledge with `queued`.
4. Append the uuid to the player marker, then force a player-data save
   (`PlayerList#saveAll` or the per-player `PlayerDataStorage#save`; M3 records which).
5. Mark `applied` in the ledger and flush.
6. POST the result (`applied` or `queued`); on success mark `acknowledged`; on failure retry on
   the reconciliation schedule (login, journal refresh, and every 60 s while pending, capped at
   10 minutes between attempts).

Restart reconciliation for a ledger entry still `pending_local`: if the player marker contains
the uuid, the items were persisted → mark `applied` without inserting; if not, the insertion was
lost with the unsaved player file → insert again. This is the documented failure model:

| Failure point | Outcome |
| --- | --- |
| Crash after step 2, before 3 | Re-applied on restart; nothing was inserted |
| Crash after step 3, before the player save completes | Player file lacks items and marker → re-applied once |
| Crash after step 4, before 5 | Marker present → ledger repaired to `applied`, no second insert |
| Crash after 5, before 6 | Acknowledgement retried; Rails still `pending`, so bootstrap lists it again; ledger says applied → ack only |
| Rails lost the ack response | Second ack → `duplicate: true` |
| Full inventory | `queued`: a reconciler re-tries insertion on inventory change and every 30 s; the marker/ack rules above then apply |
| Two Rowans, same delivery | Same `delivery_uuid` → ledger refuses a second application |
| Power loss defeating `fsync` on the storage device | Not covered; identical limitation to the blessed-item and banking receipts |

Legacy responses (no `reward_delivery`) keep the immediate-apply path with no ledger; they are
compatibility only and are logged as `delivery_mode=legacy`.

### 1.9 Observability (non-secret)

Rails: `event=quest_reward_delivery_created|replayed|acknowledged|duplicate|rejected` with
`delivery_uuid`, `quest_state_id`, `transition_key`, `outcome`, `request_uuid`.
NeoForge: `event=quest_delivery_recorded|applied|queued|acknowledged|reconciled|rejected` with the
same identifiers plus `local_state`. Never log item NBT or player names beyond the UUID.

## 1.5 Strict item hand-ins (contract `quest_item_handin`, version 1)

**Rails is implemented. The NeoForge half is not.** Nothing below reaches a player until a mod
removes items and confirms it, and the release order in 1.5.7 exists to keep that true.

### 1.5.1 The rule

When a quest giver asks for an item, delivery is **permanent**. Successful turn-in removes exactly
the configured item and quantity from the player's carried inventory and nothing else. A quest
without an authored hand-in removes nothing at all: an action, location, conversation, harvest or
escort objective is not a delivery.

The engine must never remove every item a quest touched, tools it granted, unrelated inventory,
currency that was not itself the requested hand-in, or more than the configured quantity — and it
must never infer consumption from an action objective. Only carried inventory counts; no ender
chest, container, house or dropped entity is searched.

### 1.5.2 Authoring

A hand-in hangs off a CHOICE, beside the effects that choice applies:

```json
"handin": {
  "requires": [{"item": "britannia_mod:dung", "count": 1}],
  "missing_message": "Rowan needs 1 dung in your hands before he will take it."
}
```

`requires` carries 1–8 entries, each a namespaced item id with a positive integer count, or a typed
dynamic requirement:

```json
{"resolver": "awarded_crop_harvest_item", "flag": "awarded_crop", "count": 1}
```

Rails deliberately never resolves that to an item id. The crop-to-produce mapping lives in the
mod's `CropRegistry`, and treating a seed as its produce is precisely the mistake this shape
avoids. Rails carries the question — resolver, flag, and the flag's value **stamped at prepare
time** — and the mod answers it.

There is **no authorable `returns`**. An earlier draft had one, so an NPC could inspect an item and
hand it back; authoring it is now a hard validation error. Anything a quest gives a player is
already a reward effect, where the preview and the delivery ledger know how to describe it.
Rejecting the key rather than ignoring it means a definition written against the old draft fails
loudly instead of silently keeping what it promised to return.

The validator refuses invalid or un-namespaced ids, non-positive or non-integer counts, malformed
arrays and objects, unknown keys, unsupported resolvers, a resolver naming a flag the quest never
sets, an entry carrying both an item and a resolver, duplicate identities, and a hand-in placed on
a presentation choice, on a choice with no destination, or on a node rather than a choice. The same
validation runs at seed time and in the admin form, so editing a quest cannot corrupt a definition.

### 1.5.3 The two-phase transition

A claim on a node carrying a hand-in **does not finish the quest**.

1. **Prepare.** `POST /api/quests/:id/choose` — the player's own claim, the existing endpoint. Under
   the quest-state row lock Rails creates or adopts a `quest_item_handins` row and answers
   `handin_required`. The node does not move, no effect runs, no reward is published and the quest
   is not complete.
2. **Confirm.** `POST /api/v2/quest_item_handins/:handin_uuid/result` — signed, per-server key. The
   shard reports whether it removed the full quantity, all or nothing, **and what it actually
   took**. On success Rails finalizes through the same shared transition every other choice runs.
3. **Reconcile.** `POST /api/v2/quest_item_handins/reconcile` — read-only. A shard that removed
   items but never saw a final answer asks what happened; it can never cause a second removal.

Preparation deliberately has no endpoint of its own. A hand-in is minted by the claim the player
clicks, so a shard cannot mint transactions nobody asked for. Preparation also **fails closed**: a
hand-in that cannot name the shard and user its compensation would be issued to is refused before
any shard is told to take anything.

#### Removal proof

`removed: true` must be accompanied by `removed_items`: one concrete entry per requirement, joined
to it by `requirement_index`.

```json
"removed_items": [
  {"item": "britannia_mod:carrot", "count": 1, "requirement_index": 0,
   "resolver": "awarded_crop_harvest_item", "flag_value": "carrot"}
]
```

This is what makes a resolver requirement refundable. Rails cannot name stage five's produce and
must not try — the crop-to-item mapping is the mod's `CropRegistry`, and the authenticated shard is
authoritative for it — so the shard reports the concrete item and Rails keeps the snapshot for
replay and compensation. Rails does not duplicate `CropRegistry`; it checks that the report
describes *this* hand-in's requirements as they were persisted.

Validation, all of it refusing rather than guessing:

* exactly one entry per requirement — none added, omitted or repeated;
* a literal requirement is answered by the item and count it named, exactly (an inflated count is
  refused, not clamped), and never dressed up as a resolver;
* a resolver requirement is answered at the right index, echoing the resolver and the `flag_value`
  **pinned at prepare time**, so a flag rewritten since cannot move the goalposts;
* a resolver requirement whose flag was never resolved is **refused at preparation**. Rails cannot
  describe what to take, so no shard is asked to take it — publishing that demand left a hand-in
  with no reachable outcome at all, neither the transition nor a refund;
* item identifiers are namespaced resource locations, and the list is bounded by the same 1–8 the
  requirements are;
* `removed: false` may not carry removal proof at all;
* a repeated confirmation must present the **same** normalized proof. A report that contradicts the
  stored one is refused and logged — it is the only way to catch a shard changing its story about
  what a resolver resolved to, since Rails cannot check that item against anything else.

A report that does not describe the requirements answers `evidence_rejected`
(`reason: "evidence_mismatch"`); one that contradicts a stored report answers `evidence_rejected`
(`reason: "evidence_conflict"`). Both are HTTP 409, both leave the hand-in exactly as it was —
nothing consumed, cancelled or paid — so a shard that garbled a retry can send the true report and
still be answered properly. Neither is collapsed into the 404 that hides unknown/wrong-shard/
wrong-player, because a caller reaching them has already proved it owns the transaction.

### 1.5.4 States

| State | Meaning |
| --- | --- |
| `pending` | prepared; no shard has reported a removal. The quest is deliberately unfinished. |
| `consumed` | a shard removed the items and Rails finalized, in one transaction. `response` holds the exact success payload, replayed verbatim on a duplicate confirmation. |
| `cancelled` | Rails has determined this hand-in can never finalize — abandoned, restarted onto a fresh run token, completed by another path, the state went invalid, or the transition itself was refused at confirm time (`finalization_refused`, which is how an item already removed reaches the refund when a choice or its destination was renamed underneath it). |

`removed_items` records what the shard reported taking; it is written once, at the confirmation
that reports a removal, and is thereafter immutable.

`confirmed_at` is orthogonal to the state rather than equal to it: it records that a shard reported
a removal, true of every `consumed` row and of a `cancelled` row that was refunded.
`cancelled_at IS NOT NULL AND confirmed_at IS NOT NULL` is the audit trail of an item taken for a
quest that could not be finished, and given back.

Two unique indexes carry the guarantees: `handin_uuid` is the transaction a shard applies at most
once, and `(player_quest_state_id, transition_key)` is the backstop under the row lock, so a second
claim click — or a second quest giver — adopts the first row instead of minting another.

### 1.5.5 Refunds are compensation, not content

A late confirmation against a `cancelled` row does not finalize the quest. It returns the removed
items through the durable reward-delivery ledger, which already holds a grant pending when the pack
is full. This is the system making good on a transaction it could not complete — it is not an
authored give-back, and content cannot request it.

**The invariant.** Once a mod has durably removed a hand-in item, the protocol reaches exactly one
of two ends: the transition completes exactly once, or the exact item and quantity are durably
refunded exactly once. There is deliberately **no** result meaning "removed, and cannot be
returned".

Two paths used to mean exactly that, and both are closed:

* *A requirement Rails could not name.* Closed by the removal proof above — the shard reports the
  concrete item, so a resolver hand-in refunds the produce it actually took.
* *A journal row that had been deleted.* `Quest` carries `dependent: :destroy` on
  `player_quest_states`, so deleting a quest left a compensation keyed on the journal row with
  nothing to attach to. The refund is anchored to `quest_reward_deliveries.quest_item_handin_id`
  instead. The hand-in survives that deletion — its `player_quest_state_id` is `ON DELETE SET NULL`
  while `user_id` and `shard_id` are `ON DELETE RESTRICT` — so the anchor is always there when the
  refund is needed, and a partial unique index on it makes "exactly one refund" a database
  guarantee rather than a convention.

The compensation is an ordinary pending reward delivery, so it survives reconnects, a full pack,
repeated reconciliation and a lost response by exactly the machinery every other grant uses. It is
keyed `handin_refund:<handin_uuid>` and found through its anchor. The key deliberately contains the
hand-in's identity rather than deriving from its transition key: derived keys collided with an
authored sibling choice named `<choice>:handin_refund`, and with any two hand-in keys sharing a
186-character prefix, and the refund then adopted a delivery that was not a refund.

**The one thing Rails takes on trust.** For a resolver requirement Rails checks the resolver, the
pinned flag value, the index and the count — but it cannot check the concrete item, because not
knowing that mapping is the entire reason the shape exists. An authenticated shard can therefore
name any item as the produce it removed, and a cancellation will refund that item. This is the
owner's decision that the mod is authoritative for `CropRegistry`, and it is bounded rather than
open: the count must match the requirement exactly, the requirement must exist and be unresolved
only once per run, and every resolver refund is logged with its concrete item and `handin_uuid` so
the grant is auditable. It is not a way for a *player* to mint items; it is the shard credential
being the trust root it already is everywhere else in this protocol.

`confirmed_at` means "a shard reported a removal" and not "the player got the items back", so
reconciliation answers `cancelled_refunded` from the delivery ledger rather than from the flag.

A confirmed removal that reaches the refund with no record of what was taken raises
`QuestItemHandins::Confirm::MissingRemovalProof`. That is an internal diagnostic for a corrupt row
— unreachable through the protocol, since the contract refuses `removed: true` without evidence —
and never an answer a shard is given.

### 1.5.6 Response states

`handin_required`, `items_missing` (with what the player is short of), `consumed`, `duplicate` (a
replay of the stored completion), `cancelled` (nothing was taken), `cancelled_refunded` (it was
taken and given back), `evidence_rejected`, and `rejected`.

For a hand-in whose removal was confirmed, the terminal results reduce to `consumed`/`duplicate`
with the completed quest response, or `cancelled_refunded` with a durable refund delivery. `pending`
may persist while reconciliation is outstanding, but it converges on one of those.

Every rejection reason — unknown transaction, wrong shard, wrong player — answers identically, so a
shard cannot probe another shard's transaction ids by the shape of its own errors. The reason is in
the log.

Inventory mutation never travels in `client_actions`. Removal is server-authoritative; clients
render the resulting state and nothing more.

### 1.5.7 Release order

The order matters, and step 2 is the barrier:

1. Deploy the Rails code and migration.
2. **Do not apply the updated Rowan seed yet.**
3. Deploy the hand-in-capable mod.
4. Apply the updated Rowan seed.
5. Begin live acceptance.

Between steps 1 and 4 no node carries hand-in metadata, so every quest behaves exactly as before.

An old mod meeting a hand-in-enabled node **fails closed**: it receives `handin_required` with
`completed: false` and no granted items, the node does not move, no delivery row is created and the
quest does not complete. It cannot obtain a free reward because none is ever created. This is
asserted, not assumed — see `rowan_farming_questline_play_test.rb`, "an old client that cannot hand
in gets no reward and no completion".

### 1.5.8 Compatibility

Additive within contract version 1, per the owner's decision. The `handin` key is new on choice
metadata and the `handin` block is new on a journal entry; an unknown additive field must not change
existing behaviour, and a node without hand-in metadata takes the identical path it always has,
pinned by characterization tests over all 1,481 authored choices carrying a destination.

The honest cost of extending v1 in place rather than minting v2: the frozen `quest_contract/v1`
fixtures no longer describe only what v1 originally shipped, so "v1" alone no longer distinguishes a
shard that understands hand-ins from one that does not. The release order above is what covers that
gap, and it is the reason step 2 exists.

## 2. Action events and objectives (contract `quest_action_event`, version 1)

### 2.1 Node metadata (authoring)

Two additive keys on a node's `metadata`, alongside the existing observers:

```json
"action_trigger": {
  "trigger_key": "crop_harvested",
  "label": "Harvest your crop",
  "action": "crop_harvest",
  "match": {"crop_id": "$flag:awarded_crop", "require_planter": true},
  "require_bound": ["plot_key", "crop_cycle_uuid"]
},
"action_steps": [
  {"key": "plot_hoed",       "label": "Prepare a public plot",  "action": "plot_hoe",       "match": {"community_plot": true}, "bind": ["plot_key"]},
  {"key": "plot_fertilized", "label": "Fertilize it",           "action": "plot_fertilize", "require_bound": ["plot_key"]},
  {"key": "crop_planted",    "label": "Plant your seed",        "action": "crop_plant",     "match": {"crop_id": "$flag:awarded_crop"}, "require_bound": ["plot_key"], "bind": ["crop_cycle_uuid"]},
  {"key": "crop_watered",    "label": "Water it",               "action": "crop_water",     "require_bound": ["plot_key", "crop_cycle_uuid"]}
]
```

* An `action_trigger` **advances** the node exactly like a hidden choice with
  `conditions: {"trigger_key": …}`; authoring still requires that hidden choice so routing and
  effects stay where every other trigger keeps them.
* `action_steps` are ordered, **non-advancing** progress steps: an applied step records
  `state_variables.flags.progress.<key> = true` (and `bind` values into
  `state_variables.flags.bound.<name>`), then the response reports `result: "applied"` with the
  unchanged node. Steps must be applied in order; skipping ahead is `rejected` with
  `reason: "step_out_of_order"`. A step that is already recorded returns `applied` without any
  change. A step **earlier** than the current progress restarts the sequence from that step:
  its bindings are rebound, every later `progress`/`bound` entry is cleared, and the response is
  `applied` with `"restarted": true` (this is how a reclaimed plot recovers: the player hoes
  again and the journal follows).
* A step may carry `"deadline": {"after": "<step key>", "seconds": 600}`. When the step arrives
  later than `seconds` after the named step was recorded, Rails answers `rejected` with
  `reason: "window_expired"` and `"reset_to": "<after step key>"`, and clears the progress from
  `reset_to` onward so the next `plot_hoe` restarts cleanly. The provisional windows are 600 s
  hoe→fertilize and 300 s fertilize→plant; M9 may tune the numbers only.
* `match` compares each listed key with the event's `subject` (§2.3): equality for strings and
  booleans; `$flag:<name>` resolves from `state_variables.flags.<name>` at evaluation time;
  `require_planter: true` requires `subject.planter_uuid == player_uuid`.
* `bind` copies the named subject fields into bound flags when the step applies;
  `require_bound` requires the event's subject fields to equal the previously bound values.
* The serializer publishes `triggers.action` and `triggers.steps` in the journal entry with
  placeholders already resolved, so the NeoForge watcher subscribes to concrete values. They are
  server-only, exactly like the three existing observers. In that published form `require_bound`
  is an **object of the currently bound values** (omitted while nothing is bound), each step
  carries `done`, and `action_trigger.label` feeds the last `progress` entry. The watcher uses
  these only to decide *whether to send* an event; Rails re-evaluates everything.

Supported `action` values (version 1) and the subject fields each must carry:

| `action` | Authoritative success point (NeoForge) | Required subject fields | Optional |
| --- | --- | --- | --- |
| `wild_resource_harvest` | `WildResourceHarvestService.harvestOne` (existing event) | `resource_id` | `tool_item_id`, `position` |
| `dirt_gather` | `DirtGatheringService.attempt` → `GATHERED` | `item_id` (`britannia_mod:dirt`) | `tool_item_id`, `position` |
| `water_container_fill` | `WaterSourceInteraction.fillFromSource` after the exchange | `container_item_id` (result item, e.g. `minecraft:water_bucket`), `source` (`well` or `source_block`) | `position` |
| `bowl_prepare` | `BowlPreparationService.apply` → `APPLIED` | `output_item_id` (`britannia_mod:bowl_of_dirt` or `bowl_of_fertile_dirt`) | |
| `bowl_water_fill` | `BowlWaterFillingService.apply` → `APPLIED` | `output_item_id` (`britannia_mod:bowl_of_water`) | `source` |
| `fertile_dirt_mix` | `FertileDirtMixingService.apply` → `APPLIED` | `output_item_id` (`britannia_mod:fertilized_dirt`) | |
| `plot_hoe` | `FarmingHoeItem.prepareCommunityPlot` after `setBlock` | `plot_key`, `community_plot` | `position` |
| `plot_fertilize` | `CommunityHoedFarmBlock.fertilizeCommunityPlot` after `setBlock` | `plot_key`, `community_plot` | |
| `crop_plant` | `FarmingBlock.tryPlantSeed` after `plant()` and the seed shrink | `plot_key`, `crop_id`, `crop_cycle_uuid`, `planter_uuid` | `community_plot` |
| `crop_water` | `WateringCanItem.waterFarmingBlock` after `water(1)` | `plot_key`, `crop_id`, `crop_cycle_uuid`, `hydration` (0–5) | `care_state` (`dry`, `ok`, `ideal`, `over`) |
| `crop_harvest` | `FarmingBlock.tryHarvestCrop` after `popResource` | `plot_key`, `crop_id`, `crop_cycle_uuid`, `planter_uuid` | `yield` |

`plot_key` = `"<dimension_key>:<x>:<y>:<z>"`. `crop_cycle_uuid` is minted by the farming block
entity when a crop is planted and cleared or rotated when the crop cycle ends (harvest reset,
clear, replant, block removal); it lives in the block entity NBT next to `PlanterUUID` and
survives chunk unload and restart.

### 2.2 NeoForge outbox

Every event is created on the server with a fresh `event_uuid`, written to the `SavedData`
`britannia_quest_action_outbox` (schema-versioned, fsync-flushed on enqueue, overworld anchored)
**before** the first HTTP attempt, and removed only on a terminal result. Retry schedule: attempt
immediately, then 10 s, 30 s, 60 s, 2 min, 5 min, then every 5 min; on player login the player's
entries are flushed first; on server start the whole outbox is scheduled. Per-player cap 64
entries: when exceeded the oldest terminal-less entry is dropped and logged
(`event=quest_action_outbox_overflow`). The mod only enqueues events that match a subscribed
`triggers.action`/`triggers.steps` in the server journal (as the watcher does today), so the
outbox never carries every farming action of every player.

### 2.3 Endpoint

`POST /api/v2/quest_action_events` (v2 tier), body:

```json
{
  "protocol_version": 1,
  "event_uuid": "3d2a0f8b-6b6c-4d2c-9d5a-0e7b8f1c2d3e",
  "request_uuid": "8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b",
  "player_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "action": "crop_harvest",
  "occurred_at": "2026-09-06T21:40:00Z",
  "dimension_key": "minecraft:overworld",
  "position": {"x": 1203, "y": 64, "z": -488},
  "subject": {
    "plot_key": "minecraft:overworld:1203:64:-488",
    "crop_id": "carrot",
    "crop_cycle_uuid": "b1b3d4a0-1f7e-4c0d-8f4c-6a2e9c1f0a11",
    "planter_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
    "community_plot": true,
    "yield": 3
  },
  "target": {"quest_state_id": "9005", "node_id": 1305, "trigger_key": "crop_harvested"}
}
```

`target` is optional and names what the mod matched locally; Rails uses it only to answer
`stale` when that state's current node is no longer `node_id`.

Response (always HTTP 200 once authenticated and well-formed):

```json
{
  "protocol_version": 1,
  "event_uuid": "3d2a0f8b-6b6c-4d2c-9d5a-0e7b8f1c2d3e",
  "result": "applied",
  "quest_id": 45,
  "quest_state_id": "9005",
  "completed": false,
  "node": {"id": 1306, "title": "Done", "text": "…", "type": "decision", "metadata": {…client-safe…}},
  "choices": [{"id": "claim", "text": "Claim reward", "is_locked": false}],
  "granted_items": [],
  "reward_delivery": null,
  "client_actions": [],
  "accepted_quest": {…journal entry…},
  "progress": [{"key": "plot_hoed", "label": "Prepare a public plot", "done": true}, …]
}
```

`result` values:

| `result` | Meaning | Mod outbox action |
| --- | --- | --- |
| `applied` | The event advanced a node or recorded a step; response carries the new state | Remove; apply response (journal, rewards via delivery, client notice) |
| `duplicate` | `event_uuid` already processed; the stored response is returned with `result` set to `duplicate` and `original_result` naming the stored result | Remove; apply the stored response idempotently |
| `irrelevant` | No active quest of this player subscribes to this action/subject | Remove |
| `stale` | `target` no longer matches the state's current node | Remove; refresh journal |
| `rejected` | Well-formed but failed validation: `reason` ∈ `wrong_player`, `wrong_shard`, `mismatch` (a `match`/`require_bound` failed), `step_out_of_order`, `window_expired` (with `reset_to`), `not_planter`, `wrong_crop`, `wrong_plot`, `wrong_cycle` | Remove; log `reason` |

Non-200: `400 invalid_action_event` (`field`, `detail`), `401 unauthorized`,
`403 server_not_authorized`, `404 player_not_found`, `429 rate_limited`, `503`/transport → retry.

Idempotency: `quest_action_events` stores `(shard_id, event_uuid)` unique with `result`,
`player_quest_state_id`, `response` jsonb; the row is written in the same transaction as the
state change.

### 2.4 Client authority

`QuestActionC2SPayload.TRIGGER` stays refused (`client_trigger_not_authoritative`). No new
client-to-server payload carries an objective. The client learns results only through
`QuestTriggerResultS2CPayload` (existing) and journal syncs.

## 3. Journal and dialogue additions (additive, no version bump)

### 3.1 Journal entry (`accepted_quests[]`, `accepted_quest`)

```json
{
  "id": "9005", "quest_state_id": "9005", "quest_id": "45", "quest_key": "rowan_farming_5",
  "name": "From Soil to Supper (5 of 5): Plant and Harvest",
  "brief_description": "Hoe a public plot, fertilize it, plant your seed, water it, harvest your crop.",
  "accepted_at": "…", "status": "accepted",
  "stage": {"questline_key": "rowan_farming", "index": 5, "count": 5, "label": "Quest 5 of 5"},
  "quest_giver": {"name": "Rowan", "profession": "Farmer"},
  "objective": "Hoe a public plot with your Farming Hoe.",
  "progress": [{"key": "plot_hoed", "label": "Prepare a public plot", "done": false}, …],
  "rewards_preview": {"on_accept": [], "on_complete": [{"id": "britannia_mod:gold_coin", "count": 5}]},
  "keep_items": [{"id": "britannia_mod:fertilized_dirt", "count": 1}],
  "claim_pending": false,
  "triggers": {"action": {…}, "steps": [ … ]}
}
```

`quest_key` comes from a new `quests.quest_key` string (unique per shard, nullable for legacy
rows; the serializer keeps the `quest_<id>` fallback). `stage`, `quest_giver`, `objective`,
`progress`, `rewards_preview`, `keep_items` come from quest and node metadata. `triggers` stays
server-only in NeoForge (`QuestEntryCodecs` does not carry it); the other new fields are added to
`ClientQuestEntry`/`QuestEntryCodecs` in M8 together, and old clients ignore them.

### 3.2 Node metadata visible to the client

`journal_objective`, `progress_steps` (derived from `action_steps` labels), `rewards_preview`,
`keep_items`, `help` (`{"title", "body", "guide": [{"main_hand","off_hand","gesture","result","returned"}]}`),
`stage`. Choices may carry `"presentation": "help"`; such a choice is rendered client-side and
**never sent** as a `CHOOSE`, so the node that owns the observers stays current. The published
choice list (`{id, text, is_locked}`) passes `presentation` through, and Rails refuses a
`CHOOSE`/`transition` that names a presentation choice with `422 presentation_only`, so a
legacy client cannot move the state by clicking it. Rails filters
`location_trigger`, `pickup_trigger`, `destroy_trigger`, `action_trigger`, `action_steps` from
client-facing `node.metadata` when `Quest::CLIENT_METADATA_FILTER` is enabled (default off during
the compatibility window, on after the mod that reads `accepted_quest.triggers` is deployed).

### 3.3 Random selection and inheritance

Effect `give_random_item`: `{"pool": [{"id": "britannia_mod:carrot_seeds", "count": 1, "crop_id": "carrot"}], "flag": "awarded_seed"}`.
Rails rolls once inside the transition transaction, appends the chosen entry to the granted items
and the delivery, and stores `flags.awarded_seed` (item id) and `flags.awarded_crop` (crop id).
Because the response is stored on the delivery, a replay returns the same roll.
`start_conditions.inherit_flags: ["awarded_seed", "awarded_crop"]` copies those flags from the
prerequisite quest's state when `interact`/`start` creates the next stage's state. Placeholders
`$flag:awarded_crop` in `match` resolve against the current state's flags.

## 4. Compatibility matrix

| Client ↔ server | Behaviour |
| --- | --- |
| New Rails, old NeoForge | Old mod applies `granted_items` immediately (stamping only via its own heuristic after M1); deliveries stay `pending` in Rails forever and are listed by bootstrap, which the old mod ignores. Acceptable during rollout; an operator report lists unacknowledged deliveries older than a day. |
| New NeoForge, old Rails | No `reward_delivery` → legacy immediate apply; no v2 endpoints → 404 handled as "unsupported", logged once per boot; action events 404 → outbox entries are dropped after the endpoint is confirmed absent (`endpoint_unsupported`, logged), objectives fall back to the three legacy observers only. |
| Both new | Full contract. |

Old journal consumers keep every existing field. Existing location, pickup, destroy, escort
(`fireDirect`), kill, and legacy payload behaviour is unchanged.

## 5. Security boundaries

* v2 endpoints refuse unsigned requests, unknown server keys, and cross-shard identities.
* Acknowledgements and events never move another player's state (`player_mismatch`,
  `wrong_player`).
* Quest-giver spawn configuration is applied only for Creative or permission-level-2 players
  standing within reach of the block (M1).
* Nothing in a client packet can name an objective, a delivery, or an item to grant.

## 6. HTTP status summary

| Status | Codes |
| --- | --- |
| 200 | success envelopes, including `result: rejected` for action events |
| 400 | `invalid_delivery_result`, `invalid_action_event` |
| 401 | `unauthorized` (shard secret or signature) |
| 403 | `server_not_authorized` (server key) |
| 404 | `delivery_not_found`, `player_not_found` |
| 409 | `player_mismatch`, `conflicting_delivery_result` |
| 429 | `rate_limited` |
| 5xx | retry with backoff |

## 7. Fixture index (`quest_contract/v1/`)

| File | Purpose |
| --- | --- |
| `transition_response_with_delivery.json` | `choose` response for the stage-1 acceptance with `reward_delivery` |
| `pending_deliveries_response.json` | v2 pending listing |
| `delivery_result_request.json` / `delivery_result_response.json` | acknowledgement round trip |
| `action_event_request_crop_harvest.json` | v2 event for the stage-5 harvest |
| `action_event_response_applied.json`, `_duplicate.json`, `_irrelevant.json`, `_stale.json`, `_rejected.json` | the five results |
| `journal_entry_stage5.json` | journal entry with the additive fields |
| `node_metadata_stage5.json` | authoring example with `action_steps` and `action_trigger` |
| `MANIFEST.sha256` | LF-normalised digests, verified by both repositories' contract tests |
