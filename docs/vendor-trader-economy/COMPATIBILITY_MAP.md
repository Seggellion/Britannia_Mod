# Vendor/Trader Economy — COMPATIBILITY_MAP

Forensic comparison of the legacy Trader system, legacy Merchant system, and Service NPC system,
with the target boundary for the new economic NPC architecture. All paths are relative to the
Minecraft repo (`src/main/java/com/seggellion/britannia_mod/…`) or the Rails repo
(`ultimacraft-website/…`). Verified against `patch-18` @ `2accdbd1` and `banking` @ `35653f5`.

## 1. How each system works today

### 1.1 Legacy Trader (and its Merchant clone)

Spawn: `block/TraderSpawnBlock.java` → `block/entity/TraderSpawnBlockEntity.java`.
The block entity generates `sourceId = UUID.randomUUID()` **in field initialization at construction**
and persists it to NBT (`SourceId`). Every 200 ticks (after a 40-tick load grace) `maintainTrader`
ensures one live entity: look up by `traderNpcId`, else adopt any nearby mob carrying the
`trader_source_<sourceId>` tag, else spawn. On spawn it generates `traderNpcId = UUID.randomUUID()`
**and forces it onto the entity** (`trader.setUUID(traderNpcId)`), restores appearance/name from a
saved NBT snapshot (`SavedTraderData`) because trader entities set `shouldBeSaved() == false`
(`entity/AbstractTraderEntity.java:99`), then calls `CityDataSync.upsertLiveNpc(...)` with the
entity, type, free-text `cityName`, `sourceId`, and block pos. Every 600 ticks it heartbeats Rails
(`city/CityDataSync.heartbeatLiveNpc` → `NPC_HEARTBEAT`). Death/config-change/block-removal send
`markLiveNpcInactive` (status endpoints with delete fallback). The same block also spawns
`townPersonAmount` townspeople. `MerchantSpawnBlockEntity` is a structural clone
(`merchant_source_*` tags, `merchantNpcId`, `MerchantTypes` instead of `TraderTypes`).

Configuration: `client/gui/TraderSpawnScreen.java` sends `network/TraderSpawnConfigC2SPayload`
(type string, city string, townsperson count) straight to the block entity's `applyAndResync` — no
menu session, no permission gate, no revision, no city/type validation beyond
`TraderTypes.normalize` silently defaulting unknown types to `wood_trader` (`trader/TraderTypes.java:82-90`).

Interaction/sell flow (`economy/ServerEconomyService.java`):
1. Entity interaction opens the sell screen; the client-side role handler
   (`npc/TraderRoleHandlers.create` → substring dispatch on the role string;
   `npc/TraderRoleHandler.collectSellableInventory` chooses a collector by `role.contains("wood"|"fish"|…)`)
   builds the sellable list from typed items (`WeightedWoodItem`, `WeightedFishItem`,
   `PurityOreItem`, `GradeStoneItem`, `CommodityMappings`).
2. `SellItemsC2SPayload` → `sellRequestedItems`: server re-validates entity aliveness, ≤12-block
   distance, and overrides client-supplied city/role with the entity's own values when present.
3. Preflight `ensureTraderNpcSynced` upserts the live NPC to Rails once per entity UUID (cached in
   `SYNCED_NPCS`).
4. `reserveItems` copies+shrinks the selected stacks — **plain check-then-shrink, no idempotency
   guard** (standing issue, `docs/known_environment_baseline.md` §2.1) — then POSTs
   `trader_transactions` with idempotency key
   `sale:<player>:<traderUuid>:<gameTime>:<sig>:<uuid>` and signed server auth
   (`server/auth/RailsRequestAuthenticator`). Fallback to legacy `transactions` endpoint on 404/405.
5. On success: applies Rails' commodity/supply response to the **local** `city/CityManager` mirror,
   grants physical coins from the Rails `currency_grant`, records the key in
   `economy/EconomySyncData`. On failure/replay: refunds the reserved items.

Rails side (`app/controllers/api/trader_transactions_controller.rb` +
`app/services/economy/sale_transaction_processor.rb`): signed shard auth + rate limit; non
alcohol/wine/salvage roles go through `SaleTransactionProcessor` — idempotency replay by
`shard.transactions.find_by(idempotency_key:)` plus `RecordNotUnique` rescue; requires an active
`Npc` row (upserting from payload if needed); `city.lock!`; per-item `commodity.lock!`; supply-cap
check; **unit price = `commodity.current_price` (Rails-authoritative, metadata
`price_authority: "rails"`)**; commodity quantity/weight increased; currency granted to
`shard_user.currency` JSON ledger. Alcohol/wine/salvage still use a legacy in-controller branch with
its own replay guard. **No treasury row is ever debited.**

Trader buy-side catalogs (some roles): `economy/ServerCatalogService` GETs `catalog` or POSTs
`trader_catalog` (`app/controllers/api/products_controller.rb`) which price from
`commodity.current_price` with ad-hoc conversion/rounding.

### 1.2 Legacy Merchant (Baker/Tavernkeeper/Costermonger + Architect)

Spawn/identity: identical clone of the Trader block (§1.1), `block/entity/MerchantSpawnBlockEntity.java`.

Catalog & buy flow (`economy/MerchantEconomyService.buyRequestedItems`):
1. Entity interaction (`entity/AbstractEconomyMerchantEntity.interactAt`) opens the NPC screen via
   `ClientboundOpenNpcScreenPayload` with role title + city string + entity id.
   **`entity/ArchitectEntity` extends `CitizenEntity` directly** but opens the same MERCHANT screen
   — merchants are not all under `AbstractEconomyMerchantEntity`.
2. Server rebuilds the catalog at purchase time: `MerchantCatalogBuilder.build(CityCommodityApi.fetchServer(level, city), MerchantRecipes.forRole(role))`
   — a **live synchronous GET of `city_commodities` per catalog build**, recipes **hard-coded in
   Java** (`economy/MerchantRecipes.java`: baker/tavernkeeper/costermonger lists), price =
   `max(1, ceil(inputCost × 1.70))` where inputCost = Σ(commodity.current_price × ingredient.amount),
   stock = floor(min available/required) per ingredient; products rejected when a commodity is
   missing, empty, or unpriced (good fail-closed precedent, `MerchantCatalogBuilder.java:44-102`).
3. Coins: physical items counted/removed/change-given via `CoinConversion` (1g=100s; 1s=100c).
4. POST `merchant_transactions` with computed unit prices and consumed-commodity list; Rails
   (`app/services/economy/merchant_purchase_processor.rb`) decrements input commodities and records
   the transaction, but **accepts the client-submitted line price**
   (`submitted_line_price(...) || default_line_price(...)`, line ~140) and **credits no treasury**.
5. Products granted with per-item weight rolled from **hard-coded Java ranges keyed by recipe id**
   (`MerchantEconomyService.outputWeightRange`). Role derived from the **entity class name**
   (`merchantRole()`: className.contains("baker") → "Baker" …).

### 1.3 Service NPC (current, proven — the target model)

Physical post: `block/ServiceNpcSpawnBlock.java` → `block/entity/ServiceNpcSpawnBlockEntity.java`.
Identity: `spawnPointId` generated server-side on placement, guarded by a level-saved claim store
(`service/spawn/ServiceNpcSpawnClaimData` + `ServiceNpcSpawnIdentityResolver`) that detects cloned
NBT (structure paste, `/clone`, restored backups) and **rekeys copies** with a fresh UUID; block
items never carry identity NBT (`saveToItem` intentionally empty; `removeComponentsFromTag`
sanitizes). Configuration state: `cityPublicId` (UUID from the Rails bootstrap city registry),
`serviceNpcTypeKey`, `enabled`, `configurationRevision`, `registrationState` machine
(UNCONFIGURED/PENDING_REGISTRATION/PENDING_UPDATE/REGISTERED/ERROR), `lastErrorCode`.

Registration: configuration changes append durable `ServiceNpcSpawnPendingRecord`s (UPSERT/REMOVE,
with shard, location, revision) to a level-saved outbox (`ServiceNpcSpawnPendingData`);
`ServiceNpcSpawnDeliveryProcessor` + `ServiceNpcSpawnRegistrationClient` deliver them to Rails
`service_npc_spawn_operations` (signed auth), producing acknowledgement receipts reconciled back
into the block (`ServiceNpcSpawnReceiptReconciler`), with collision repair
(`ServiceNpcSpawnCollisionRepairCoordinator`) for Rails-reported UUID collisions and tombstone
resubmission for restored blocks. Rails applies operations idempotently by `operation_id` +
`source_revision` compare (`app/services/service_npc_spawn_points/apply_operation.rb`:
applied/already_applied/stale/revision_conflict).

Assignment/materialization: Rails owns `WorldNpc` (persistent identity) + `NpcSpawnAssignment`
(world_npc ↔ spawn point, active/closed, revision), published through `world_bootstrap/:shard` and
the `world_state_changes/:shard` versioned change log; `worldstate/WorldStateSyncPoller` polls every
~5min+jitter with full-bootstrap fallback. `service/spawn/ServiceNpcAssignmentReconciler` (a
serverTick companion, cache-only) creates exactly one `entity/ServiceNpcEntity` per active
assignment, discards duplicates deterministically (highest assignmentRevision, then lowest entity
UUID), overwrites entity NBT with cache truth (name/gender/worldNpcPublicId/type/revisions), and
removes entities whose assignment closed. Entity UUID is never forced; post UUID ≠ WorldNpc
public_id ≠ assignment public_id ≠ entity UUID.

Configuration security: menu-backed (`menu/ServiceNpcSpawnMenu`), and
`network/payload/ServiceNpcSpawnPayloadHandler.validateSession` checks: permission level 2, correct
menu + owner + container id, dimension, distance, submitted pos == menu pos, block type, block
entity present, submitted spawnPointId matches menu AND block, `stillValid`, expected revision;
then `ServiceNpcSpawnConfigurationValidator` validates city/type against the bootstrap registry
caches (`city/BootstrapCityRegistryCache`, `service/ServiceNpcRegistryCache`).

Population/eligibility (Rails): `CityStaffing::EconomicEligibility` evaluates per-type,
admin-editable `service_npc_types.minimum_city_supplies` against city supply columns (precious
metals → treasury amounts); `DesiredStaffing` (baseline + residents/increment, capped by
max_per_city and real spawn capacity; zero when economically ineligible — un-staffs existing NPCs);
`Reconcile` applies plans under `city.lock!` with an `AdminActionAudit` row, **admin-triggered
only** (`CityStaffingReconciliationJob`).

## 2. Comparison table

| Concern | Legacy Trader | Legacy Merchant | Service NPC | Target Economic NPC |
|---|---|---|---|---|
| Physical post identity | `sourceId` random-at-construction, NBT-persisted, clone-unsafe (`TraderSpawnBlockEntity.java:45`) | Same clone (`MerchantSpawnBlockEntity.java:45`) | `spawnPointId` + claim store + clone rekey + item-NBT sanitizer (`ServiceNpcSpawnBlockEntity`, `ServiceNpcSpawnClaimData`) | Service NPC model, reused or generalized |
| Persistent NPC identity | None — `traderNpcId` regenerated when absent; NBT snapshot fakes continuity | Same (`merchantNpcId`) | Rails `WorldNpc.public_id`, immutable identity fields (`app/models/world_npc.rb`) | `WorldNpc` (possibly with economic subtype/profession) |
| Loaded entity identity | == `traderNpcId` (forced via `setUUID`, `TraderSpawnBlockEntity.java:143`) | Same | Free entity UUID; cache-driven dedupe (`ServiceNpcAssignmentReconciler.pickCanonical`) | Free entity UUID |
| Rails registration | Live-NPC upsert of the *entity* (`CityDataSync.upsertLiveNpc` → `Npc` row keyed by entity UUID) | Same | Durable post registration: outbox → `service_npc_spawn_operations` → `ServiceNpcSpawnPoint` (public_id, revision, status) | Spawn-point registration protocol, extended for economic types |
| Rails assignment | None — block decides locally | None | `NpcSpawnAssignment` (active/closed, revision), projected via bootstrap/state-sync | Same mechanism |
| City identity | Free-text string name, client-editable (`cityName`), `City.find_by(name:)` on Rails | Same | `cityPublicId` UUID validated against bootstrap registry | City public_id everywhere; name only for display |
| Profession/type registry | Static Java `TraderTypes` (unknown → silently `wood_trader`) | Static Java `MerchantTypes` (3 types) | Rails `ServiceNpcType` (key, profession, entity type key, active/spawnable, revision) projected to `ServiceNpcRegistryCache` | Rails-owned economic NPC type registry (vendor\|trader kind) |
| Client configuration | Direct screen payload, free-text city, no session (`TraderSpawnConfigC2SPayload`) | Same | Menu-backed session + validated selections (`ServiceNpcSpawnMenu`, `ServiceNpcSpawnScreen`) | Service NPC pattern |
| Server-side validation | Distance + entity aliveness at sale only; config unvalidated | Same | Full session/permission/revision/registry validation (`ServiceNpcSpawnPayloadHandler.validateSession`) | Service NPC pattern |
| UUID collision handling | None (clone duplicates sourceId and NPC) | None | Claim-store rekey + Rails collision repair coordinator | Service NPC pattern |
| Chunk unload/reload | Block re-adopts by UUID or source tag scan; NBT snapshot restore | Same | serverTick reconciler recreates from assignments cache; entities `shouldBeSaved()==false`? (ServiceNpcEntity persists spawnPointId/assignment ids and is overwritten by cache truth) | Reconciler-driven projection |
| Duplicate entity detection | First-match adoption by tag; no dedupe of extras | Same | Deterministic canonical pick + discard (`pickCanonical`) | Service NPC pattern |
| Bootstrap/synchronization | Per-block 600-tick heartbeat + ad-hoc GETs (`CITY_COMMODITIES` per catalog build) | Same | Versioned `world_bootstrap` + `world_state_changes` polling (~5min), durable op outbox — no per-tick traffic | Versioned snapshot/state sync; no heartbeats |
| Market authority | Rails `CityCommodity.current_price` for sales; Java for some catalog math | **Java computes retail price; Rails accepts client price** (`merchant_purchase_processor.rb:140`) | n/a | Rails computes/authorizes all prices |
| Treasury authority | **None — sales mint value** (no treasury debit in `SaleTransactionProcessor`) | **None — purchases credit no treasury** | Guildmaster precedent: `GuildTraining::Purchase#credit_treasury` (coins_outstanding, in-transaction) | All flows treasury-coupled via the Guildmaster pattern |
| Commodity mutation | Rails increments quantity/weight under `commodity.lock!` with supply-cap guard | Rails decrements inputs (`process_input!`) | n/a | Reuse locked mutation pattern in one authoritative service |
| Buy transaction (player buys) | Some roles via `products#catalog`/`trader_catalog` | `MerchantEconomyService` → `merchant_transactions` | n/a (Guildmaster training = closest modern flow) | Rails-quoted catalog + Rails-committed purchase |
| Sell transaction (player sells) | `ServerEconomyService` → `trader_transactions` (Rails-priced) | Forbidden by design (and no path exists) | n/a | Generalized Trader sale keeping Rails price authority, adding treasury funding |
| Idempotency | Client key + Rails unique index + replay; **local reservation unguarded** | Same server pattern; coin reservation local-only | `operation_id` + revision compare; receipts; reconcilers | Existing transaction idempotency + a guarded local reservation |
| Admin configuration | None (all Java constants) | None (recipes/prices/markup in Java) | Rails Admin: types, minimum_city_supplies, staffing knobs, dialogue sets; audited reconciliation | Shard Admin-managed economic types, catalogs, requirements |
| Shard-specific rules | None | None | `minimum_city_supplies` per type (global per type, not per shard — see OPEN_QUESTIONS) | Typed, shard-scoped requirement rows |

## 3. Required architecture conclusions (Milestone 1 answers)

1. **Which Service NPC components should govern Merchant/Trader posts?** The whole post lifecycle:
   `ServiceNpcSpawnBlockEntity`-style identity (claim store, rekey, sanitized item NBT), the
   pending-operation outbox + delivery + receipts, Rails `ServiceNpcSpawnPoint`/`ApplyOperation`,
   `NpcSpawnAssignment` + `WorldNpc`, the assignments cache + `ServiceNpcAssignmentReconciler`
   projection, and the menu-backed validated configuration flow.
2. **Legacy behaviors safe to preserve:** Rails-authoritative sale pricing from
   `CityCommodity.current_price`; commodity mutation under row locks with supply-cap fail-closed;
   transaction idempotency (unique key + replay payload); fail-closed catalog building (skip
   product when commodity missing/empty/unpriced — `MerchantCatalogBuilder`); server-side re-validation
   of entity/distance/city at transaction time; reserve-then-refund item handling (with a proper
   guard added); Citizen entity rendering/appearance stack (GeckoLib, outfits, names).
3. **Legacy behaviors that must NOT be copied:** `sourceId` conflation and construction-time UUID
   generation; forcing entity UUIDs (`setUUID`); NBT snapshot as pseudo-persistent identity;
   free-text city strings as authoritative identity; per-block Rails heartbeats; substring role
   dispatch (`role.contains("fish")`); class-name-derived roles; Java-hard-coded recipes, prices,
   markups, and output-weight tables; Rails trusting client-submitted merchant prices; treasury-less
   value flows; silent type fallback to `wood_trader`; direct unvalidated config payloads; the
   Villager-based `EntityMerchant` hierarchy.
4. **Can Merchant/Trader posts use the Service NPC spawn registration/assignment infrastructure
   directly?** Yes at the protocol level — the registration envelope (post UUID, shard, location,
   city_public_id, type key, enabled, revision) and `ApplyOperation` semantics carry nothing
   service-specific. The binding constraint is that `ServiceNpcSpawnPoint.service_npc_type` and the
   registry cache validate against `ServiceNpcType`; economic NPCs need either (a) economic types
   represented as `ServiceNpcType` rows (kind: vendor|trader added), or (b) a generalized post/type
   abstraction. Resolving a/b is a Milestone 3 design decision (OPEN_QUESTIONS #1).
5. **Same post model or a generalized abstraction?** Recommendation: extend the existing model
   (add an NPC-kind dimension to `ServiceNpcType` or a sibling economic type table sharing the
   registration tables) rather than cloning the spawn-point/assignment tables. The playbook forbids
   a second persistent NPC identity system; `WorldNpc`/`NpcSpawnAssignment`/`ServiceNpcSpawnPoint`
   are already NPC-kind-agnostic (`profession_key`, optional `service_npc_type`).
6. **How are current Trader prices calculated?** Player-sell: Rails,
   `unit_price = CityCommodity.current_price` × inventory delta (weight or quantity), greedy
   denomination breakdown into a currency grant (`SaleTransactionProcessor#currency_breakdown`).
   No raw-vs-processed multiplier exists anywhere. Trader buy-side catalogs
   (`products#trader_catalog`) price ad hoc from `current_price` with unit-weight scaling.
7. **How are current Merchant prices calculated?** Minecraft Java:
   `ceil(Σ(current_price × ingredient.amount) × 1.70)` (`MerchantCatalogBuilder.MARKUP`); Rails
   then *accepts the submitted price*. The 1.70 markup is the only retail modifier.
8. **Where are city commodities authoritative?** Rails `CityCommodity` (unique per city ×
   category × subcategory × item_name; price formula in the model; history rows). Minecraft's
   `city/CityManager`/`inventory/CityInventory` is a display mirror updated from Rails responses —
   it must never become authoritative.
9. **Where is treasury authority implemented?** Rails `Treasury`/`TreasuryBalance` (per-currency
   reserve + coins_outstanding; `City#gold_amount` reads coins_outstanding). The only modern
   mutation is `GuildTraining::Purchase#credit_treasury`. **No trader/merchant flow touches it
   today — wiring both directions through the treasury is new work, not a port.**
10. **What transaction/idempotency framework exists?** (a) Economy: `transactions.idempotency_key`
    unique per shard + replay + `RecordNotUnique` rescue, `city.lock!`/`commodity.lock!` row locks;
    (b) banking: prepare/confirm/cancel `BankTransferOperation` with client receipts and startup
    reconciliation (`BankTransferReconciliationService`); (c) spawn ops: operation_id + revision.
    A Trader purchase (treasury-funded, item-removing) should reuse (a)'s server shape but adopt
    (b)-style local receipts for the item-removal leg, replacing today's unguarded `reserveItems`.
11. **Where should RunUO Vendor definitions eventually live?** Rails (economic NPC type/catalog
    tables, seeded from the Milestone 2 matrix; shard-scoped enablement) — projected to Minecraft
    via the existing bootstrap/state sync. Not in Java constants; `runuo_vendor_catalog.json`
    remains the source artifact for seeding, never a runtime file.
12. **Where should RunUO buyback→Trader mappings live?** Rails, as data keyed by
    (runuo type / item id or tag / commodity identity) → trader type + valuation strategy;
    Minecraft only needs the projected accept-list per trader type.
13. **How can product recipes be valued without a second recipe authority?** Today Minecraft is the
    only recipe authority (Java `MerchantRecipes` for merchants; `skill/crafting/CraftableRegistry`
    + vanilla data recipes for crafting). Rails knows nothing of recipes but owns all ingredient
    prices. Options (unresolved, OPEN_QUESTIONS #4): export recipe compositions to Rails as seeded
    economic input definitions (recommended — Rails must price without trusting the client), or
    have Minecraft submit compositions per quote and Rails price them (keeps one authority but
    trusts the client's composition claim).
14. **How should city provenance fit the ItemStack data model?** Follow `BlacksmithItemData`
    exactly: a named compound inside vanilla `minecraft:custom_data` already carrying
    `region_id`/`region_name` (tooltip "Origin: …"). Provenance should be
    `origin city public_id + display name`, written server-side at product construction. Stacking:
    custom_data participates in stack equality, so differing origins naturally refuse to merge —
    matching the playbook's requirement (verify per item type in implementation).
15. **Which quality tier is one below maximum?** Ambiguous today — three scales coexist
    (blacksmithing 1/2 = Normal/Exceptional; `QualitySwordItem` names 1–4 with 4 = Exceptional;
    crops 0–100). On the only metal-goods scale actually written by crafting (1/2), "one below max"
    = Normal(1), which contradicts the playbook's "high quality" intent. Needs an owner decision —
    OPEN_QUESTIONS #6. Do not invent a new enum until then.
16. **How can shard-specific spawn rules use existing patterns?** Extend the
    `minimum_city_supplies` pattern: typed requirement rows validated against a closed key set,
    admin-edited, evaluated by an `EconomicEligibility`-style pure service. Two gaps: today's rules
    are per-type but **not per-shard**, and only threshold-≥ comparisons over supply/treasury
    columns exist (no commodity-exists / saleability predicates). The magery example
    (alcohol > 100 AND treasury gold ≥ 20) is already expressible in the current shape except for
    shard scoping.
17. **What currently makes an NPC appear/disappear on economic change?** Rails-side:
    `CityStaffing::Reconcile` (admin-triggered only) closes/creates assignments using
    `EconomicEligibility`; `CommodityConsumptionJob` draws supplies down, so a below-minimum city
    un-staffs on the next reconciliation; the change reaches Minecraft via the world-state change
    log, and `ServiceNpcAssignmentReconciler` discards or spawns entities. Legacy Trader/Merchant
    NPCs never react to economy state — the block respawns them unconditionally.
18. **Migration path for existing MerchantSpawnBlocks/TraderSpawnBlocks?** Keep legacy blocks
    functioning untouched until parity (playbook Milestone 16). Then: on load, a migration
    reconciler reads legacy NBT (cityName string, type, sourceId), resolves the city name to a
    city public_id via the bootstrap registry, issues a **new** spawn-point UUID through the claim
    store (never reusing `sourceId`, which may be cloned), registers through the standard outbox,
    and lets Rails staffing create the assignment; the legacy maintain/heartbeat path is disabled
    only after the new registration is acknowledged. Legacy `Npc` rows are superseded by
    `WorldNpc`+assignment; block/item IDs preserved.

## 4. Proposed Rails/Minecraft boundary (target)

Rails: economic NPC types (vendor|trader kind, profession, catalogs, buyback mappings, valuation
strategies, shard-scoped requirements), spawn-point registration + assignments + WorldNpc identity,
commodity state/prices, treasury movements, transaction authorization/ledger/idempotency,
eligibility evaluation with machine-readable reasons, versioned projection (bootstrap + change log).

Minecraft: physical posts (Service NPC lifecycle), entity projection/rendering/AI, screens,
server-side inspection of real inventory items, item construction (material/quality/provenance from
authoritative data), reserve-then-confirm item mutation with durable local receipts, cached
catalog display refreshed by revision.

Hard rules verified against current weaknesses: no client-supplied price ever accepted (fixes
merchant flow); no city identified by mutable string (fixes both legacy flows); no per-tick or
per-block-heartbeat Rails traffic (fixes legacy sync); every value transfer treasury-coupled
(fixes both legacy flows); no silent type fallback.
