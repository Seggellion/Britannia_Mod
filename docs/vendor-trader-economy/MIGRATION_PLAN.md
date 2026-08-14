# Vendor/Trader Economy — Migration Plan

How an existing shard moves from the legacy economy to the authoritative one,
in the order the steps must actually happen, and what each step is safe
against. Every step is reversible or inert until the next one runs.

## Ordering (Rails before Minecraft, always)

1. **Rails migrations** — `bin/rails db:migrate`. Adds: economic NPC types and
   shard policies, spawn-point economic FK (`num_nonnulls` contract), currency
   canonical ladder correction, commodity policy flags + `form`,
   `products.price_denomination`, material definitions, staffing hysteresis
   columns + `cities.economic_staffing_dirty_at`, canonical stock units
   (`stock_unit`/`unit_weight`).
   *Data corrections applied in-place*: the obsolete 1/10/100 currency ladder
   becomes 1/100/10,000; bulk commodities become weight-canonical and their
   mirror columns are derived once (a switching row with no tracked weight
   seeds weight from its legacy count).
2. **Seeds** (idempotent; existing rows are never overwritten):
   ```bash
   bin/rails db:seed:commodities
   bin/rails db:seed:economic_trader_types
   bin/rails db:seed:economic_products_baker
   bin/rails db:seed:economic_vendor_types_legacy_merchants
   bin/rails db:seed:economic_vendor_rollout
   ```
   The last one must run **before** any world migrates legacy spawn blocks, so
   every legacy merchant/trader role has a type to migrate onto.
3. **Shard configuration** (Shard Admin, no deploy): enable the types this
   shard wants, set per-shard requirements, payout multipliers, and hysteresis
   dwell. Types ship inactive where their items/commodity families do not yet
   exist — activating one is a data edit.
4. **Minecraft deploy**. On first load the mod registers the economic registry
   from the world bootstrap and begins reconciling.
5. **Legacy spawn-block migration** — automatic, per block, on its own tick
   cadence. See `LEGACY_SPAWN_BLOCK_MIGRATION.md` for the full runbook and
   rollback procedure.

## What is safe if a step is skipped or fails

| Situation | Behavior |
|---|---|
| Minecraft deployed before seeds | Legacy blocks keep running unchanged; migration waits for a mapped type. Nothing is lost. |
| Rails unreachable at boot | Bootstrap registry unavailable ⇒ no migration, no economic catalog; legacy merchants keep working. |
| Rails unreachable mid-session | Purchases/sales fail closed with refunds in kind; post registration retries from the durable outbox; the world-state poller re-arms with jitter. |
| A city has no treasury | Trader payouts are rejected (`treasury_insufficient`); Vendor sales create balances on demand. |
| A type is inactive or shard-disabled | Assignments close immediately (administrative gates bypass hysteresis); World NPC identity survives. |

## Irreversible vs reversible

**Reversible**: everything in steps 3–5. Type activation, requirements, and
dwell are data. Legacy block migration keeps a full-fidelity rollback receipt
per position (legacy block id + complete NBT) in the world's migration ledger.

**Effectively one-way** (plan a database backup before step 1):
- the currency ladder correction (old values are replaced),
- the canonical stock-unit backfill (parallel weight/quantity truths collapse
  into one; the mirror is derived thereafter),
- ledger rows, which are never edited or deleted (admin surfaces are read-only
  by construction).

## Legacy systems and their retirement

| Legacy | Status | Retires when |
|---|---|---|
| `MerchantSpawnBlock` / `TraderSpawnBlock` | Migrating in place (M16) | Each configured block converts on load; registry ids stay registered so old chunks always deserialize. |
| Legacy `sourceId`/heartbeat NPC sync | Bypassed per block at conversion | The last legacy block converts. |
| `townPersonAmount` side-spawning | Stops at conversion; existing TownPersons preserved | The Rails-driven regional population system (Milestone 20) reaches parity. |
| `MerchantRecipes` / `CraftableRegistry` as economy authority | Not used by the economic path | Already inert for economic NPCs; Rails `Product.requirements` is the sole authority. |
| `shard_user.currency` credits | Not written by new flows | Legacy reads remain only while an unmigrated flow needs them. |
| Legacy admin transaction edit/delete | Removed (owner decision) | Done — the ledger is read-only. |

## Verification after migration

1. `/economy post` at a migrated post: expect a post UUID, the decoded
   economic type, an assigned World NPC, `pending operations: 0`, and no
   duplicate projections.
2. Admin → city **economic diagnostics**: eligibility satisfied, catalog rows
   available, treasury balances present, staffing not stuck "pending sweep".
3. Buy one item and sell one commodity; confirm in **economic transactions**
   that each committed once with the expected denomination and metadata.
4. The migration ledger shows `configured` for every converted position.

## Rollback

Per-position legacy restoration is documented in
`LEGACY_SPAWN_BLOCK_MIGRATION.md`. A full program rollback means restoring the
pre-migration database backup and deploying the previous mod build; the world
itself remains loadable by older builds because no registry ids were removed
or renamed.
