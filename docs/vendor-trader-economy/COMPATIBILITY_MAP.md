# Vendor/Trader Economy — COMPATIBILITY_MAP

Forensic comparison of the legacy Trader system, legacy Merchant system, and Service NPC system,
plus the owner-approved target architecture for economic NPCs. Verified against Minecraft
`patch-18` @ `2accdbd1` and Rails `banking` @ `35653f5`. Paths are relative to
`src/main/java/com/seggellion/britannia_mod/…` (Minecraft) or `ultimacraft-website/…` (Rails).

Every statement in this document is classified as one of:

```text
FACT                  verified from repository source
OWNER DECISION        required target architecture (see OPEN_QUESTIONS.md and the two
                      corrective context documents in this directory)
IMPLEMENTATION DETAIL exact class/table/API shape chosen during the relevant milestone
OPEN QUESTION         actually unresolved
```

Corrective context incorporated: `UltimaCraft_Compatibility_Map_Review_Suggestions.md` and
`UltimaCraft_Currency_Denomination_and_NPC_Settlement_Context.md` (both in this directory).

Transaction directions are written `Player -> Trader` (NPC acquisition) and `Vendor -> Player`
(NPC retail) throughout; "buy"/"sell" alone are ambiguous and are not used for direction.

---

# 1. Verified Current-State Architecture (FACT throughout)

## 1.1 Legacy Trader (and its Merchant clone)

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
`townPersonAmount` ambient townspeople tied to the same `sourceId` tag.
`MerchantSpawnBlockEntity` is a structural clone (`merchant_source_*` tags, `merchantNpcId`,
`MerchantTypes` instead of `TraderTypes`).

Configuration: `client/gui/TraderSpawnScreen.java` sends `network/TraderSpawnConfigC2SPayload`
(type string, city string, townsperson count) straight to the block entity's `applyAndResync` — no
menu session, no permission gate, no revision, no city/type validation beyond
`TraderTypes.normalize` silently defaulting unknown types to `wood_trader`
(`trader/TraderTypes.java:82-90`).

Player -> Trader flow (`economy/ServerEconomyService.java`):
1. Entity interaction opens the sell screen; the client-side role handler
   (`npc/TraderRoleHandlers.create` → substring dispatch on the role string;
   `npc/TraderRoleHandler.collectSellableInventory` chooses a collector by
   `role.contains("wood"|"fish"|…)`) builds the sellable list from typed items
   (`WeightedWoodItem`, `WeightedFishItem`, `PurityOreItem`, `GradeStoneItem`,
   `CommodityMappings`).
2. `SellItemsC2SPayload` → `sellRequestedItems`: server re-validates entity aliveness, ≤12-block
   distance, and overrides client-supplied city/role with the entity's own values when present.
3. Preflight `ensureTraderNpcSynced` upserts the live NPC to Rails once per entity UUID (cached in
   `SYNCED_NPCS`).
4. `reserveItems` copies+shrinks the selected stacks — **plain check-then-shrink, no idempotency
   guard** (standing issue, `docs/known_environment_baseline.md` §2.1) — then POSTs
   `trader_transactions` with idempotency key
   `sale:<player>:<traderUuid>:<gameTime>:<sig>:<uuid>` and signed server auth
   (`server/auth/RailsRequestAuthenticator`). Fallback to legacy `transactions` endpoint on
   404/405.
5. On success: applies Rails' commodity/supply response to the **local** `city/CityManager`
   mirror, grants **mixed physical coins** (gold+silver+copper) from the Rails `currency_grant`,
   records the key in `economy/EconomySyncData`. On failure/replay: refunds the reserved items.

Rails side (`app/controllers/api/trader_transactions_controller.rb` +
`app/services/economy/sale_transaction_processor.rb`): signed shard auth + rate limit; non
alcohol/wine/salvage roles go through `SaleTransactionProcessor` — idempotency replay by
`shard.transactions.find_by(idempotency_key:)` plus `RecordNotUnique` rescue; requires an active
`Npc` row (upserting from payload if needed); `city.lock!`; per-item `commodity.lock!`;
supply-cap check; **unit price = `commodity.current_price` (Rails-authoritative, metadata
`price_authority: "rails"`)**; commodity quantity/weight increased; the payout is computed as one
copper-equivalent total then greedily split across denominations by `Currency.base_value`
(`currency_breakdown`) and credited to the `shard_user.currency` JSON ledger — **in addition to**
the physical coins Minecraft grants from the same response (duplicate payout representation).
Alcohol/wine/salvage still use a legacy in-controller branch with its own replay guard.
**No treasury row is ever debited by any Player -> Trader flow.**

Trader NPC-retail catalogs (some roles): `economy/ServerCatalogService` GETs `catalog` or POSTs
`trader_catalog` (`app/controllers/api/products_controller.rb`) which price from
`commodity.current_price` with ad-hoc conversion/rounding.

## 1.2 Legacy Merchant (Baker/Tavernkeeper/Costermonger + Architect)

Spawn/identity: identical clone of the Trader block (§1.1),
`block/entity/MerchantSpawnBlockEntity.java`.

Vendor -> Player flow (`economy/MerchantEconomyService.buyRequestedItems`):
1. Entity interaction (`entity/AbstractEconomyMerchantEntity.interactAt`) opens the NPC screen via
   `ClientboundOpenNpcScreenPayload` with role title + city string + entity id.
   **`entity/ArchitectEntity` extends `CitizenEntity` directly** but opens the same MERCHANT
   screen — merchants are not all under `AbstractEconomyMerchantEntity`.
2. Server rebuilds the catalog at purchase time:
   `MerchantCatalogBuilder.build(CityCommodityApi.fetchServer(level, city), MerchantRecipes.forRole(role))`
   — a **live synchronous GET of `city_commodities` per catalog build**, recipes **hard-coded in
   Java** (`economy/MerchantRecipes.java`: baker/tavernkeeper/costermonger lists), price =
   `max(1, ceil(inputCost × 1.70))` where inputCost = Σ(commodity.current_price ×
   ingredient.amount), stock = floor(min available/required) per ingredient; products rejected
   when a commodity is missing, empty, or unpriced (fail-closed precedent,
   `MerchantCatalogBuilder.java:44-102`). Every catalog `Product` is hard-coded currency
   `"copper"`.
3. Payment: **mixed-basket copper-equivalent checkout** — `reserveCoins` counts ALL of the
   player's gold/silver/copper into one copper total via `CoinConversion`, removes every coin,
   and gives change (`MerchantEconomyService.countCoins/removeAllCoins/giveChange`).
4. POST `merchant_transactions` with computed unit prices and consumed-commodity list; Rails
   (`app/services/economy/merchant_purchase_processor.rb`) decrements input commodities and
   records the transaction, but **accepts the client-submitted line price**
   (`submitted_line_price(...) || default_line_price(...)`, line ~140) and **credits no
   treasury**.
5. Products granted with per-item weight rolled from **hard-coded Java ranges keyed by recipe id**
   (`MerchantEconomyService.outputWeightRange`). Role derived from the **entity class name**
   (`merchantRole()`: className.contains("baker") → "Baker" …).

## 1.3 Service NPC (current, proven)

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
the `world_state_changes/:shard` versioned change log; `worldstate/WorldStateSyncPoller` polls
every ~5min+jitter with full-bootstrap fallback. `service/spawn/ServiceNpcAssignmentReconciler`
(a serverTick companion, cache-only) creates exactly one `entity/ServiceNpcEntity` per active
assignment, discards duplicates deterministically (highest assignmentRevision, then lowest entity
UUID), overwrites entity NBT with cache truth (name/gender/worldNpcPublicId/type/revisions), and
removes entities whose assignment closed. Entity UUID is never forced; post UUID ≠ WorldNpc
public_id ≠ assignment public_id ≠ entity UUID.

Configuration security: menu-backed (`menu/ServiceNpcSpawnMenu`), and
`network/payload/ServiceNpcSpawnPayloadHandler.validateSession` checks: permission level 2,
correct menu + owner + container id, dimension, distance, submitted pos == menu pos, block type,
block entity present, submitted spawnPointId matches menu AND block, `stillValid`, expected
revision; then `ServiceNpcSpawnConfigurationValidator` validates city/type against the bootstrap
registry caches (`city/BootstrapCityRegistryCache`, `service/ServiceNpcRegistryCache`).

Population/eligibility (Rails): `CityStaffing::EconomicEligibility` evaluates per-type,
admin-editable `service_npc_types.minimum_city_supplies` against city supply columns (precious
metals → treasury amounts); `DesiredStaffing` (baseline + residents/increment, capped by
max_per_city and real spawn capacity; zero when economically ineligible — un-staffs below-minimum
cities); `Reconcile` applies plans under `city.lock!` with an `AdminActionAudit` row,
**admin-triggered only** (`CityStaffingReconciliationJob`).

## 1.4 Current Rails Economy

- `CityCommodity` — unique per (city_id, category, subcategory, item_name); columns quantity,
  weight, base_price, current_price, max_supply_cap (default 1000), scarcity_markup (0.5),
  elasticity (1.0). `current_price = max(base + base×scarcity_markup×(1−fill)^elasticity, 1.0)`
  recalculated in a model callback with price-history rows. Raw/processed is only a
  subcategory-string convention (`raw`, `logs`, `whole`/`milled`, `ingot`). **No
  saleability/permission columns exist.** Weight-vs-quantity inventory is a hard-coded category
  list (`weight_based_inventory?`).
- Treasury — `City has_one :treasury`; `TreasuryBalance` per `Currency` (reserve,
  coins_outstanding, strength, buy/sell rates). `City#gold_amount` reads `coins_outstanding` of
  the gold balance. The only modern mutation is `GuildTraining::Purchase#credit_treasury`
  (`balance.increment!(:coins_outstanding, gold)` inside the purchase transaction). The treasury
  model is **already denomination-specific** — per-currency rows.
- `Product` — **exists today**: `products` table with `item_id` (unique), `requirements` JSONB
  (default `{"wood"=>0,"metal"=>0,"stone"=>0}`), `price` decimal (NO denomination column),
  plus CMS columns (title/slug/meta/published/template), Shopify columns
  (`shopify_product_id`/`shopify_variant_id`), rich text, FriendlyId, image attachments.
  `Product#requirement_met?(city_commodities)` already implements a primitive
  "all required commodities exist" gate. `product_listings` also exists:
  (product, npc_type, city, shard) unique rows with `enabled` and `override_price` (also no
  denomination). `products#catalog` / `products#trader_catalog` price from `requirements` ×
  `commodity.current_price` with ad-hoc conversion.
- Transactions — `transactions.idempotency_key` unique per shard; replay payloads;
  `transaction_items`; `currency_grant` JSON supports mixed {gold, silver, copper}.
- Auth — modern endpoints use signed `Api::ShardServerAuthentication` + rate limits; legacy
  `Api::BaseTransactionsController` descendants still use a static Bearer token
  (`Setting.get("britannia_api_token")`).

## 1.5 Current Currency Facts (both repos)

- Minecraft canonical ratios: `economy/CoinConversion.java` — `COPPER_PER_SILVER = 100`,
  `SILVER_PER_GOLD = 100`, `COPPER_PER_GOLD = 10_000`. All Minecraft coin math (merchant
  checkout, banking, guild training) uses this ladder.
- Rails old-ladder survivals (the **1/10/100 discrepancy**):
  - `db/migrate/20251006205944_treasuries.rb:15` — `base_value` integer, default 1, comment
    "relative value units; copper=1, silver=10, gold=100".
  - `app/services/economy/sale_transaction_processor.rb:483-485` — fallbacks `gold ||= 100`,
    `silver ||= 10`, `copper ||= 1` in `currency_breakdown`.
  - `app/controllers/api/trader_transactions_controller.rb:151-152` — fallbacks
    `gold ||= 100.0`, `silver ||= 10.0` in the legacy wine/salvage branch.
  - `test/controllers/api/world_bootstrap_profile_test.rb:174` — fixture sets `base_value = 1`
    for every currency.
  - No seed creates `Currency` rows; `Admin::TreasuriesController` lets admins set `base_value`
    by hand. **Live `Currency.base_value` rows are unverifiable from this environment** (peer
    role reaches only the broken test DBs; dev DB is off-limits) — the migration default is NOT
    proof of live values.
  - Consumers of `base_value` that are ladder-agnostic once data is corrected:
    `Treasury#total_reserve_value`, admin treasury ordering, admin city form ordering.
- `TreasuryBalance` constants (`PAR_COINS_PER_INGOT = 10.0`, strength/rate math) concern
  reserve-vs-coins banking strength, not the inter-denomination ladder.

---

# 2. Compatibility Comparison Table

| Concern | Legacy Trader | Legacy Merchant | Service NPC | Target Economic NPC (OWNER DECISION) |
|---|---|---|---|---|
| Physical post identity | `sourceId` random-at-construction, NBT-persisted, clone-unsafe (`TraderSpawnBlockEntity.java:45`) | Same clone (`MerchantSpawnBlockEntity.java:45`) | `spawnPointId` + claim store + clone rekey + item-NBT sanitizer | Service NPC post model |
| Persistent NPC identity | None — `traderNpcId` regenerated when absent; NBT snapshot fakes continuity | Same (`merchantNpcId`) | Rails `WorldNpc.public_id`, immutable identity | Shared `WorldNpc` |
| Loaded entity identity | == `traderNpcId` (forced via `setUUID`) | Same | Free entity UUID; cache-driven dedupe | Free entity UUID |
| Rails registration | Live-NPC upsert of the *entity* (`Npc` row keyed by entity UUID) + heartbeats | Same | Durable post registration via op outbox → `ServiceNpcSpawnPoint` | Spawn-point registration protocol |
| Rails assignment | None — block decides locally | None | `NpcSpawnAssignment` (active/closed, revision) | Same mechanism |
| City identity | Free-text string name; `City.find_by(name:)` | Same | `cityPublicId` UUID validated against bootstrap registry | City public_id everywhere |
| Profession/type registry | Static Java `TraderTypes` (unknown → silently `wood_trader`) | Static Java `MerchantTypes` (3 types) | Rails `ServiceNpcType` (revisioned) | Generalized NPC type foundation, Economic specialization (kind: vendor\|trader) |
| Client configuration | Direct screen payload, free-text city, no session | Same | Menu-backed session + validated selections | Service NPC pattern |
| Server-side validation | Distance + aliveness at sale only; config unvalidated | Same | Full session/permission/revision/registry validation | Service NPC pattern |
| UUID collision handling | None | None | Claim-store rekey + Rails collision repair | Service NPC pattern |
| Chunk unload/reload | Block re-adopts by UUID or tag scan; NBT snapshot restore | Same | Reconciler recreates from assignments cache | Reconciler-driven projection |
| Duplicate entity detection | First-match adoption; no dedupe | Same | Deterministic canonical pick + discard | Service NPC pattern |
| Bootstrap/synchronization | Per-block 600-tick heartbeat + ad-hoc GETs | Same | Versioned bootstrap + change log + durable outbox | Versioned sync; no heartbeats |
| Market authority | Rails `current_price` for Player -> Trader; Java for some catalogs | **Java computes retail; Rails accepts client price** | n/a | Rails computes/authorizes everything |
| Price/settlement denomination | One copper-equivalent total, greedily split (mixed grant) | Mixed-basket copper-equivalent checkout; catalog hard-codes "copper" | n/a (guild training is gold-specific) | **amount + denomination contract; exact-denomination settlement; no conversion** |
| Treasury authority | **None — payouts mint value** | **None — revenue uncredited** | `GuildTraining::Purchase#credit_treasury` precedent | Every flow treasury-coupled, denomination-preserving |
| Commodity mutation | Rails increments under `commodity.lock!` + cap guard | Rails decrements inputs | n/a | Reuse locked mutation in one authoritative service |
| Vendor -> Player transaction | Some roles via `products#catalog`/`trader_catalog` | `MerchantEconomyService` → `merchant_transactions` | n/a | Rails-quoted catalog + Rails-committed purchase, Gold-denominated retail |
| Player -> Trader transaction | `ServerEconomyService` → `trader_transactions` | Forbidden by design (no path exists) | n/a | Generalized denomination-aware Trader acquisition, treasury-funded |
| Idempotency | Client key + Rails unique index + replay; **local reservation unguarded** | Same server pattern; coin reservation local-only | operation_id + revision; receipts; reconcilers | Existing transaction idempotency + guarded local reservation |
| Admin configuration | None (Java constants) | None (recipes/prices in Java) | Rails Admin: types, minimum supplies, staffing, audited reconciliation | Shard Admin-managed economic types/catalogs/requirements |
| Shard-specific rules | None | None | `minimum_city_supplies` per type (global, not per shard) | Global defaults + shard overrides, both NPC families |
| Ambient TownPersons | Spawned by each block from `townPersonAmount`, coupled to legacy `sourceId` | Same | None | Separate Rails-driven city regional population subsystem |

---

# 3. Verified Current-State Conclusions (FACT)

1. Trader/Merchant `sourceId` conflation is real and current (post identity + Rails source id +
   entity tag + heartbeat identity; entity UUID forced from block state).
2. Rails is price-authoritative for the generic Player -> Trader path
   (`unit_price = CityCommodity.current_price`); Java is price-authoritative for merchant retail
   (`ceil(inputCost × 1.70)`), and Rails **accepts the client-submitted merchant price**.
3. No NPC flow debits or credits any treasury today; Player -> Trader payouts are minted and
   **double-represented** (`shard_user.currency` credit AND physical coins).
4. Settlement today is copper-equivalent-normalized everywhere (greedy split on trader payouts;
   mixed-basket checkout on merchant purchases) — the exact behavior the owner has prohibited for
   the target.
5. Minecraft is the only recipe authority today (`MerchantRecipes` hard-coded;
   `CraftableRegistry`/data recipes for player crafting). Rails `Product.requirements` exists but
   currently holds only the generic `{"wood","metal","stone"}` shape.
6. Rails `products` + `product_listings` already provide item_id-unique products, requirements
   JSONB, price, per-(npc_type, city, shard) listing rows with `enabled` + `override_price` — a
   working head start for the owner-approved Product authority, entangled with CMS/Shopify
   columns.
7. Item provenance precedent exists: `item/BlacksmithItemData` writes material, int quality,
   `region_id`/`region_name` ("Origin: …"), `maker_uuid`/`maker_name`, `recipe_id`, and an
   `instance_id` UUID into `minecraft:custom_data` (participates in stack equality).
8. Three quality scales coexist: blacksmithing writes 1|2 (`BlacksmithCrafting.java:111`);
   `QualitySwordItem` names 1–4 Crude/Basic/Fine/Exceptional; crops use 0–100.
9. `UOMetalToolMaterial` has 9 metals (iron→valorite) with no explicit economic rank; enum order
   is not a usable ranking (gold sits between iron and shadow_iron).
10. The Service NPC registration envelope (post UUID, shard, location, city_public_id, type key,
    enabled, revision) carries nothing service-specific; reuse is technically feasible.
11. `CityStaffing` provides the working requirement/eligibility/staffing precedent
    (per-type `minimum_city_supplies`, closed key set, treasury-mapped precious metals,
    admin-triggered reconcile under city lock, no hysteresis).
12. The Rails 1/10/100 currency ladder survives in the migration default/comment, two fallback
    constant sets, and one test fixture (§1.5); Minecraft's canonical ladder is 1/100/10,000.
    Live `Currency.base_value` rows are admin-edited and currently unverifiable.
13. The wine/alcohol/salvage Player -> Trader path bypasses `SaleTransactionProcessor` via a
    legacy controller branch with its own replay guard and its own valuation math (including
    `CityCommodity#calculate_wine_value` and salvage material/quality pricing with old-ladder
    fallbacks).
14. Legacy blocks also own ambient TownPerson side-spawning (`townPersonAmount`), tagged with the
    same `sourceId`, upserted to Rails as `townsperson` `Npc` rows.

---

# 4. Owner-Approved Target Architecture (OWNER DECISION)

Authoritative sources: regenerated `OPEN_QUESTIONS.md`, `UltimaCraft_Compatibility_Map_Review_Suggestions.md`,
`UltimaCraft_Currency_Denomination_and_NPC_Settlement_Context.md`, and the project playbook.

## 4.1 NPC type architecture

Generalized NPC role/type foundation with Service and Economic specializations:

```text
general NPC type / role
    +-- Service specialization: dialogue, service actions, guildmaster/skills
    +-- Economic specialization: kind vendor|trader, product/catalog policy,
        valuation strategy, economic eligibility
```

`WorldNpc`, persistent spawn-post identity, `NpcSpawnAssignment`, `City`, `Shard` remain shared.
No second persistent NPC identity system. Vendors/Traders are not forced through Service-specific
dialogue/guildmaster validation. (Exact Rails table/class migration: IMPLEMENTATION DETAIL,
Milestone 3.)

## 4.2 Currency and settlement

- Canonical comparative ratios: **Copper = 1, Silver = 100, Gold = 10,000**. The Rails 1/10/100
  ladder is obsolete and must not be used by the new economy; correcting `Currency.base_value`
  data and the fallback constants is a required, narrowly-scoped correction (scheduled Milestone 3
  — see IMPLEMENTATION_LOG).
- Every Vendor/Trader quote is `amount + denomination` with the closed set
  {copper, silver, gold}. The NPC settles **only** in that denomination. No automatic conversion,
  no equivalent-copper acceptance, no mixed-denomination checkout, no payout substitution.
- Comparative value ≠ settlement: normalized comparative values may be used internally for
  analytics/calibration/arbitrage tests, never as the settlement unit.
- Catalogs display the actual settlement denomination ("25 Gold", never "250,000 Copper").
- Minter NPCs (explicit denomination exchange) are future work, entirely out of scope for this
  project; Vendor/Trader behavior must be complete and correct with no Minter anywhere. A player
  lacking the required denomination simply cannot buy — intentional.
- RunUO GP calibration: **1 RunUO GP = 1 UltimaCraft Gold**. RunUO-imported Vendor products
  default to gold denomination; do not reduce prices because copper-equivalents look large.
  Vendor retail is intentionally expensive (luxury/convenience path and player-wealth drain).

## 4.3 Product and recipe authority

Rails `Product` is authoritative for NPC product definitions and production requirements
(`item_id`, evolved `requirements`, baseline price, **price denomination**, material policy,
quality policy, Vendor associations, other economic policy). Prohibited as NPC production/valuation
authority: vanilla recipes, legacy Minecraft recipes, `MerchantRecipes`, `CraftableRegistry`,
datapack recipes, client-submitted compositions. Player crafting and NPC production are separate
systems. Defaults are seeded from the project-owned RunUO mapping; shards can override product
requirements in Rails without Java changes. (`Product.requirements` JSON schema, denomination
column shape, and the CMS/Shopify entanglement split: IMPLEMENTATION DETAIL, Milestone 3.)

## 4.4 Pricing

- Rails owns every final price and its denomination. Minecraft never decides price, denomination,
  substitution, conversion, or affordability.
- RunUO retail price is the **Iron-baseline finished-product anchor** (it embeds craftsmanship/
  labor/margin/balance value). Target retail formula (calibrated in `ECONOMY_RULES.md`):

```text
final_vendor_price = RunUO Iron baseline
                   + (selected_material_cost − iron_material_cost)
                   + approved production/economy adjustment
```

- The legacy `input cost × 1.70` is a forensic fact only, never the target rule. No fixed Java
  prices. Material adjustment stays in the Product's denomination; fractional results resolve by
  an explicit rounding policy within that denomination (documented + tested in the pricing
  milestone) — never by splitting into mixed denominations.
- Trader valuation uses a closed Rails-owned strategy registry with
  `CityCommodity.current_price` and other Rails data as inputs — conceptually
  CURRENT/RAW/PROCESSED commodity value, RECIPE/PRODUCT-derived value, WINE_QUALITY_VALUE,
  SALVAGE_MATERIAL_QUALITY_VALUE. Trader quotes are denomination-aware; denomination policy per
  economic class is Rails-owned data, not Java class-name dispatch.
- Same-city arbitrage (Vendor -> Player -> Trader profit loops) must be tested and prevented.

## 4.5 Treasury

Every economic transaction is treasury-coupled and **denomination-preserving**:

```text
Player -> Trader:  city treasury [denomination] coins_outstanding decreases;
                   commodity/economic supply increases; player receives that denomination
Vendor -> Player:  player pays [denomination]; city treasury [denomination]
                   coins_outstanding increases; product requirements consumed; player
                   receives product
```

Debits are atomic, never negative, and reject the whole transaction when unfundable in the exact
denomination (no partial purchases, no reserve fallback, no cross-denomination netting).
Denomination-specific city liquidity is intentional (a city can be copper-rich and gold-poor).
Vendors accepting payment need no pre-existing destination-denomination supply — that is how gold
enters city treasuries. Implemented via a locked/idempotent Rails service in the
`GuildTraining::Purchase#credit_treasury` pattern.

## 4.6 Player payout representation

One authoritative payout per transaction: physical denomination-specific coins and/or the
established banking/account system. New flows stop writing `shard_user.currency`; the legacy
ledger stays temporarily readable during migration only.

## 4.7 Quality and materials

- NPC-manufactured equipment ladder: Crude / Basic / **Fine (NPC product)** / Exceptional (max).
  The blacksmithing 1|2 representation is a migration concern, bridged without corrupting existing
  items. Crop quality stays separate.
- Material progression is explicit Rails data (key, family, economic rank, commodity mapping,
  enabled) — never Java enum order/durability/alphabetical. Iron is the baseline. Per base
  product slot, catalogs display the **highest-value currently producible** material variant,
  falling back down the rank as city supply changes.

## 4.8 Commodity policy

- Explicit per-direction permissions (equivalent of `npc_buy_enabled`, `npc_sell_enabled`,
  `production_enabled`) as Rails data, shard-overridable — never derived permanently from
  category/subcategory strings.
- Explicit form classification (raw/processed/finished/other) backfilled from current
  conventions; shard multipliers (raw vs processed payout policy) bind to that data.

## 4.9 Shard scoping

Global NPC-type defaults + shard-specific policy overrides, managed in Shard Admin, applying to
**both** Service and Economic NPC types (one staffing-rule system, not two).

## 4.10 Reconciliation and population

- Economic eligibility is Rails-authoritative and reconciles asynchronously: economic changes
  enqueue/mark reconciliation, a periodic safety job provides eventual consistency, and
  activation uses hysteresis / minimum-duration stability so visible NPCs do not flicker.
  Never run the full staffing engine inside model `after_commit` callbacks. Minecraft never
  decides economic spawning.
- TownPersons: remain an economic sink and prosperity display, removed from spawn-block
  ownership. Rails computes desired population (bounded nonlinear prosperity curve: min/max,
  food/alcohol/wealth influences, growth parameters, rate limits); Minecraft gradually
  reconciles at validated random positions throughout the authoritative city Region (loaded
  chunks only, no force-loading, density/distance checks). Not implemented in Milestone 2.

## 4.11 Transactions and catalogs

- Catalog snapshots are display-only and may go stale; **purchase-time Rails validation is
  authoritative** for money, availability, material, stock, price, and treasury (stale-quote
  purchases are rejected/requoted; the catalog refreshes to the next eligible variant).
- Wine/alcohol/salvage valuation is absorbed into the strategy registry; legacy branches may
  delegate during migration and are retired after differential parity tests.
- The Minecraft item-removal/coin-removal leg follows the banking receipt/reconciliation pattern
  (replacing today's unguarded `reserveItems`).

## 4.12 Legacy migration

Keep legacy blocks working until parity. Migration reads legacy NBT, resolves city name → city
public_id, issues a NEW post UUID through the claim store (never reusing `sourceId`), registers
through the standard outbox, receives an authoritative assignment, and only then disables legacy
maintenance. TownPerson side-spawning is NOT migrated into economic posts — it waits for the
regional population system (operational + adoption/reconciliation of existing citizens) before
legacy side-spawning is disabled. Legacy `Npc` townsperson rows are accounted for in that
migration. Block/item IDs preserved.

## 4.13 RunUO data flow

```text
pinned RunUO source (completeness authority)
  -> Milestone 2 normalized project-owned mapping
       -> economic NPC / Vendor definitions
       -> Rails Product definitions (+ associations)
       -> Trader buyback mappings
  -> Rails seeds/default configuration -> shard overrides -> city runtime evaluation
```

`runuo_vendor_catalog.json` is reference input, not coverage proof and not a runtime source.
Once seeded, Rails is the UltimaCraft configuration authority.

RunUO row mapping:

```text
RunUO vendor class            -> economic NPC / Vendor definition
RunUO BuyInfo (player buys)   -> Rails Product + Vendor/Product association
RunUO SellInfo (buyback)      -> Trader buyback mapping + valuation strategy
```

## 4.14 Target invariants (consolidated)

```text
Rails Product.requirements is authoritative for NPC production.
Vanilla/legacy Minecraft recipes are never authoritative for NPC production.
RunUO prices anchor default/Iron finished-product retail value; 1 RunUO GP = 1 Gold.
Current city material costs dynamically alter higher-material product prices.
Rails determines material eligibility and ranking.
UI shows the highest-value currently producible material variant per base product slot.
NPC equipment is Fine quality, not Exceptional.
Vendor finished products default to Gold denomination.
Rails owns all final economic prices and denominations.
CityCommodity.current_price is a market input, not necessarily the final formula.
Canonical comparative values: Copper 1, Silver 100, Gold 10,000.
Every NPC price/payout carries an explicit denomination; settlement is exact-denomination.
No Vendor/Trader performs denomination conversion; no mixed-denomination checkout.
Every Player -> Trader payout debits the city treasury in the payout denomination.
Every Vendor -> Player purchase credits the city treasury in the paid denomination.
Trader treasury debits cannot go negative; unfundable transactions are rejected whole.
New transactions do not double-credit shard_user.currency and physical currency.
Commodity buy/sell/production permissions are explicit Rails policy.
Commodity raw/processed form is explicit Rails data.
Shard overrides apply to Service and Economic NPC policy.
Economic NPC reconciliation is asynchronous, Rails-authoritative, and stabilized.
TownPerson population is Rails economy-driven and Minecraft region-spawned.
Spawn-post UUID != WorldNpc UUID != assignment UUID != loaded entity UUID.
Catalog snapshots may be stale; purchase-time Rails validation is authoritative.
Minter/denomination exchange is out of scope; nothing may depend on or preempt it.
```

---

# 5. Implementation Details to Resolve During Milestones

- Exact Rails table/class shape for the generalized NPC type foundation and the
  `ServiceNpcSpawnPoint.service_npc_type` FK generalization (Milestone 3).
- `Product` schema evolution: requirements JSON schema, `price_denomination` column/enum,
  material/quality policy representation, and how to disentangle (or safely coexist with) the
  CMS/Shopify columns (Milestone 3).
- The `Currency.base_value` data correction to 1/100/10,000 plus removal of the 1/10/100
  fallback constants, via a safe data migration + regression tests once the test DB is repaired
  (scheduled Milestone 3; blocked on OQ-1).
- Denomination rounding policy per denomination (pricing milestone; documented in
  ECONOMY_RULES.md).
- Requirement-rule shape for shard overrides (typed rows extending the
  `minimum_city_supplies` pattern).
- Commodity policy columns/table and form-classification backfill list.
- Material rank table/import shape.
- Reconciliation trigger mechanics (enqueue points, safety-job cadence, hysteresis parameters).
- Local receipt shape for the Minecraft reservation leg (banking-pattern adaptation).
- TownPerson population curve parameters and region-spawn validation reuse.

---

# 6. Remaining Operational Open Questions

See `OPEN_QUESTIONS.md` (regenerated): **OQ-1** Rails test-database ownership repair (admin
action; blocks all Rails test execution and therefore Milestone 3+ Rails work);
**OQ-2** resolved 2026-08-10 — pinned RunUO reference checkout exists at
`C:\projects\runuo-reference` @ `71b2794f12eb6f948b1c5598ae8b350401a22d4d`
(remote `https://github.com/runuo/runuo.git`, read-only; recorded in PROJECT_FACTS).

## Final state (Milestone 19, 2026-08-11)

**OQ-1 is resolved** (test-database ownership repaired 2026-08-10); the blocker
note above is historical.

Legacy paths and their delivered disposition:

| Legacy mechanism | Final state |
|---|---|
| `MerchantSpawnBlock` / `TraderSpawnBlock` | Migrate in place onto authoritative posts (Milestone 16); registry ids retained so old chunks always load. |
| Legacy `sourceId`/heartbeat NPC sync | Bypassed per block at conversion; retires with the last legacy block. |
| `MerchantRecipes` / `CraftableRegistry` valuation | Never consulted by the economic path; Rails `Product.requirements` is the sole authority. |
| Legacy copper-valued `reserveCoins` settlement | Economic purchases use exact-denomination reservation instead; legacy merchants keep the old path until migrated. |
| `shard_user.currency` credits | Not written by any new flow. |
| Parallel weight+quantity commodity truths | Collapsed into one canonical supply amount with a derived mirror. |
| Admin ledger edit/delete | Removed — economy transactions are read-only in admin. |
| `townPersonAmount` side-spawning | Stops at conversion; existing TownPersons preserved for Milestone 20. |

The compatibility risks catalogued earlier in this document are therefore
either eliminated or carry an explicit, tested boundary. Remaining bounded
limits are enumerated in `OPEN_QUESTIONS.md` §"Final status".
