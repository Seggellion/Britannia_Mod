# Legacy Spawn-Block Migration — Operations and Rollback (Milestone 16)

## What happens

When a world containing legacy `britannia_mod:merchant_spawn_block` /
`britannia_mod:trader_spawn_block` posts loads on a server whose Rails city
bootstrap has arrived, each configured legacy block converts IN PLACE onto the
authoritative Service NPC post architecture, at its own maintenance cadence
(once per ~10s while its chunk is loaded):

1. The legacy-MANAGED merchant/trader mob is despawned (legacy Rails NPC sync
   is notified with reason `migrated_to_authoritative_post`). **TownPersons
   are never touched** — owner decision #12 preserves them until the regional
   population system reaches parity. The deprecated `townPersonAmount`
   side-spawning ends with the legacy block; the amount is preserved in the
   migration receipt for the future population system.
2. A full-fidelity rollback receipt is written to the migration ledger
   (`data/britannia_legacy_spawn_block_migrations.dat` on the overworld):
   dimension, position, legacy block id, the COMPLETE legacy block-entity NBT,
   the mapped economic type key, city, `townPersonAmount`, timestamp, outcome,
   and the replacing post's UUID.
3. The block becomes a `britannia_mod:service_npc_spawn_block`; its fresh
   entity mints a stable UUID through the claim store exactly like a
   player-placed post, and the migrated configuration (city public id +
   `economic:<type>`) is applied — staging the durable pending UPSERT the
   delivery pipeline registers with Rails.
4. Rails takes over: registration marks the city for the staffing sweep
   (Milestone 15), `EconomicStaffing::Reconcile` assigns a persistent World
   NPC, and the assignment projection materializes it at the post.

Type mapping: legacy trader keys are already the Rails economic type keys
(Milestone 11 seed); legacy merchant types map `baker`/`tavernkeeper`/
`costermonger` (Milestones 7 + 16 seeds — run
`bin/rails db:seed:economic_vendor_types_legacy_merchants` before migrating a
production world).

## What does NOT happen

- **No registry ids change.** The legacy blocks, items, and block-entity
  types remain registered; old chunks and inventories keep loading. Only
  in-world instances convert.
- **Unresolvable blocks never convert.** A blank city name, an unmapped type
  key, an unavailable city bootstrap, or a city name Rails does not know all
  leave the legacy block running its OLD behavior unchanged (merchant
  maintenance, heartbeat, townsperson top-up). A world with no backend keeps
  working exactly as before this milestone.
- **No receipts, no changes** are written for blocks that did not convert.

## Verifying a migration

- Server log: `Legacy merchant spawn block at ... migrated to authoritative
  post <uuid> (economic:<key>, city <name>)`.
- The Shard Admin spawn-post views show the new post once registered; the
  city's staffing sweep assigns an NPC within its 5-minute cadence.
- The ledger (NBT, openable with any NBT tool) lists every receipt with
  outcome `configured`. Outcomes `configuration_failed:*` / `replace_failed`
  mean the block was replaced but needs admin attention (the post is visible
  in the normal admin menu; the receipt holds the intended configuration).

## Rollback

Per-position, using the receipt (no automated bulk rollback is provided —
each rollback is an operator decision):

1. Break the migrated `service_npc_spawn_block` (as an operator). Its
   destruction records the normal Rails REMOVE tombstone; the persistent
   World NPC identity survives on Rails (owner rule — identity is never
   deleted by post removal).
2. Restore the legacy block: place the block named by the receipt's
   `LegacyBlockId`, then restore its NBT from the receipt's
   `LegacyBlockEntityNbt` compound (e.g. via an NBT editor on the saved
   region, or a data-merge command with the compound's fields — the receipt
   preserves `SourceId`, `CityName`, `MerchantType`/`TraderType`,
   `TownPersonAmount`, `SpawnRadius`, and the tracked TownPerson ids. The
   despawned merchant's own entity snapshot is deliberately NOT in the
   receipt: that despawn was Rails-synced, and a restored legacy block simply
   spawns a fresh merchant the way it always did).
3. The restored legacy block resumes legacy behavior — and will migrate
   again on a later load unless the mod is downgraded or the block left
   unconfigured; roll back the mod version too if the intent is to stay
   legacy.

The receipts are never deleted by code: the ledger is the world's own
migration history.

## Known limits (recorded, not hidden)

- If configuration fails AFTER block replacement (e.g. a UUID-collision
  repair window), the post remains unconfigured and the receipt records the
  error; the legacy block does not come back automatically — restore from
  the receipt if desired.
- TownPerson top-up stops at migration (deliberate, decision #12). Existing
  TownPersons persist; population management returns with the Rails-driven
  regional population system.
