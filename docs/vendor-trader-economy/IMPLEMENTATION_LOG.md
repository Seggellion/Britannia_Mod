# Vendor/Trader Economy — IMPLEMENTATION_LOG

## 2026-08-10 — Milestone 0: repository safety and baseline

Governing playbook: `UltimaCraft_Vendor_Trader_Economy_Claude_Playbook.md` (read in full; copied
into this branch together with `runuo_vendor_reconstruction_design.md` and
`runuo_vendor_catalog.json`, which existed only as untracked files in the troubleshooting worktree).

### Repository state recorded
- Minecraft: repo `C:\projects\britannia\mod\Britannia_Mod` (+5 worktrees). Approved integration
  tip determined to be **`patch-18` @ `2accdbd1`** (contains `patch-18-troubleshooting` @
  `50061f07` as an ancestor plus the Guildmaster Service NPC merge; main checkout sits on it).
- Rails: `/home/dusti/ultimacraft-website` (WSL), integration branch **`banking` @ `35653f5`**.
- Both primary checkouts are dirty with other in-flight work (bank/farming docs + network sources
  on the Minecraft side; service-NPC spawn-point retirement work on the Rails side). Untouched.
- No `vendor-trader-economy` branches or worktrees existed in either repo.

### Worktrees created (no resets, no cleans, no user files touched)
- `C:\projects\britannia\mod\Britannia_Mod-vendor-trader-economy` — branch
  `feature/vendor-trader-economy` from `patch-18` @ `2accdbd1`.
- `/home/dusti/ultimacraft-website-vendor-trader-economy` — branch
  `feature/vendor-trader-economy` from `banking` @ `35653f5`.
- Environment fixes confined to the new worktrees: restored `gradle/wrapper/gradle-wrapper.jar`
  from the LFS pointer (sha256 of the copied jar equals the pointer oid `498495…17`; smudge does
  not run on fresh checkouts here — never staged), and `chmod +x` on `bin/codex_test` /
  `bin/setup_test_database` / `bin/rails` (tracked as 100644 with `core.filemode=false`, so
  invisible to git).

### Baseline validation actually run
1. `./gradlew build --rerun-tasks --no-configuration-cache` (Minecraft feature worktree)
   → **automated validation failed / pre-existing failure**: compile OK; `:test` 1788 tests,
   13 failed (12 × `BannerScaffoldToolTest`, 1 × `ParallelMediumIntegrationTest`), 17 skipped,
   6m33s. Failures are banner-scaffold tooling on the untouched `patch-18` tree — recorded as
   pre-existing; not repaired (out of scope per Milestone 0 rules).
2. `./gradlew runGameTestServer --rerun-tasks --no-configuration-cache` (same worktree)
   → **automated validation passed**: BUILD SUCCESSFUL, 3m30s. This is the suite containing the
   Service NPC / Guildmaster / banking / world-state GameTests.
3. `bash bin/codex_test` (Rails feature worktree, and reproduced identically from
   `~/ultimacraft-website`) → **blocked, pre-existing environment failure**: `db:prepare` fails
   with `PG::InsufficientPrivilege: permission denied for table schema_migrations`. Verified via
   psql: every `ultimacraft_test*` database (including all parallel-worker DBs) is owned by role
   `ultimacraft` instead of the canonical `ultimacraft_codex_test` (docs/local_test_database.md
   defines the canonical state). The peer role cannot even SELECT, and cannot drop/re-own the
   databases; no passwordless sudo exists. **No Rails tests could run from any checkout — needs
   local Postgres admin action** (drop or `ALTER DATABASE … OWNER` the test DBs, then
   `bin/setup_test_database`). Development DB untouched and unaffected.

No production behavior changed in Milestone 0.

## 2026-08-10 — Milestone 1: forensic compatibility map

### Method
Direct source reading in the two feature worktrees (no behavior changes). Call paths traced rather
than inferred from names; historical findings from earlier projects re-verified against
`patch-18` @ `2accdbd1` / `banking` @ `35653f5`.

### Files inspected (principal)
Minecraft: `entity/CitizenEntity`, `entity/AbstractTraderEntity`, all Trader subclasses via
`trader/TraderTypes` (incl. `EntityWoodMerchant`, `FishTraderEntity`, `SalvageTraderEntity`),
`entity/AbstractEconomyMerchantEntity` + Baker/Tavernkeeper/Costermonger + `ArchitectEntity`
(hierarchy exception) + Villager-based `EntityMerchant`; `block/TraderSpawnBlock(.Entity)`,
`block/MerchantSpawnBlock(.Entity)`, `block/QuestGiverSpawnBlock(.Entity)`,
`block/ServiceNpcSpawnBlock(.Entity)`; `client/gui/TraderSpawnScreen`, trader/merchant C2S/S2C
payloads, `network/payload/ServiceNpcSpawnPayloadHandler`, `menu/ServiceNpcSpawnMenu`;
`service/spawn/*` (claim data, identity resolver, pending outbox, delivery, receipts, collision
repair, assignment reconciler), `service/ServiceNpc*Cache/Parser`, `entity/ServiceNpcEntity`;
`economy/ServerEconomyService`, `economy/MerchantEconomyService`, `economy/MerchantCatalogBuilder`,
`economy/MerchantRecipes`, `economy/CityCommodityApi`, `economy/ServerCatalogService`,
`economy/CoinConversion`, `npc/*RoleHandler*`, `city/CityDataSync`, `city/CityManager` usage,
`worldstate/WorldStateSyncPoller` (+ package), `server/http/RailsApiUrlResolver.Endpoint`;
items: `item/QualitySwordItem`, `item/BlacksmithItemData`, `item/UOMetalToolMaterial`,
`item/MaterialQualityJewelryItem`, weighted commodity items, `registry/DataComponentRegistry`,
`skill/BlacksmithCrafting`, `skill/crafting/*`, `farming/CropQualityCalculator`.
Rails: models `City`, `CityCommodity`, `Treasury`, `TreasuryBalance`, `Transaction`,
`TransactionItem` (via schema), `Npc`, `WorldNpc`, `ServiceNpcType`, `ServiceNpcSpawnPoint`,
`NpcSpawnAssignment`, `Shard`; `db/schema.rb` (cities, city_commodities, service_npc_types,
treasuries, transactions); controllers `api/trader_transactions`, `api/merchant_transactions`,
`api/base_transactions` (legacy bearer auth), `api/products` (catalog/trader_catalog), routes;
services `economy/sale_transaction_processor`, `economy/merchant_purchase_processor`,
`city_staffing/*` (economic_eligibility, desired_staffing, reconcile),
`service_npc_spawn_points/apply_operation`, `guild_training/purchase` (treasury credit precedent),
`api/world_bootstrap_city_serializer`; `AGENTS.md`, `docs/local_test_database.md`,
`docs/known_environment_baseline.md` (both repos).
RunUO inputs: `runuo_vendor_reconstruction_design.md` (full), `runuo_vendor_catalog.json`
(structure + coverage metadata).

### Deliverables produced
- `docs/vendor-trader-economy/PROJECT_FACTS.md` — verified repository facts.
- `docs/vendor-trader-economy/COMPATIBILITY_MAP.md` — system narratives, comparison table with
  citations, 18 required architecture conclusions, target boundary.
- `docs/vendor-trader-economy/OPEN_QUESTIONS.md` — 15 unresolved items.
- This log.
- Playbook + RunUO artifacts committed at repo root (repo precedent: Guildmaster docs).

### Key findings (summary; full detail in COMPATIBILITY_MAP)
- Legacy `sourceId` conflation confirmed still present in both Trader and Merchant blocks;
  entity UUIDs are forced from block state; per-block 600-tick Rails heartbeats.
- Service NPC post/assignment architecture is strictly stronger on every compared concern and its
  registration protocol carries nothing service-specific — reuse is feasible pending the type-model
  decision (OPEN_QUESTIONS #1).
- Rails is price-authoritative for trader sales but **accepts client prices for merchant
  purchases**; **no NPC flow touches any treasury today** (Guildmaster training is the only
  treasury-coupled precedent).
- Provenance precedent exists: `BlacksmithItemData` (region/maker/material/quality/recipe inside
  `minecraft:custom_data`).
- `runuo_vendor_catalog.json` is explicitly safety-filtered → Milestone 2 must audit pinned RunUO
  source and build the complete matrix.

### Validation for the documentation change itself
- Docs-only commit; `git status` confirmed only milestone-owned files staged (no `git add .`).
- Automated validation: not applicable beyond the baseline runs above (no production code changed).
- Manual validation performed: none beyond source reading; no game client launched.

### Commits
- Minecraft `feature/vendor-trader-economy`: Milestone 0/1 docs + playbook + RunUO artifacts
  (hash recorded in the final report).
- Rails `feature/vendor-trader-economy`: no commit — no Rails-owned files changed this milestone
  (all four deliverables live in the Minecraft repo, matching the precedent of
  `docs/ultimacraft_banking_service_npc_compatibility_map.md`).

### Stopped
Milestone 1 complete. Milestone 2 (RunUO coverage audit) not started, per playbook stop condition.

## 2026-08-10 — Milestone 1 finalization: owner decisions + currency correction pass

Owner review landed three corrective inputs: the regenerated `OPEN_QUESTIONS.md` (14 binding
decisions, committed `7734ab6d`), `UltimaCraft_Compatibility_Map_Review_Suggestions.md`, and
`UltimaCraft_Currency_Denomination_and_NPC_Settlement_Context.md` (both now committed in this
directory).

Work performed:
- `COMPATIBILITY_MAP.md` refactored into the required FACT / OWNER DECISION / IMPLEMENTATION
  DETAIL / OPEN QUESTION structure. Forensic current-state sections preserved; stale target
  conclusions replaced per the review document (§3–§23) and the currency context (§25):
  generalized NPC type foundation, Rails Product recipe authority (Minecraft recipes prohibited),
  RunUO→Product seeding flow, Fine quality, explicit material rank + highest-producible display
  rule, RunUO Iron-baseline pricing, Gold-denominated RunUO retail, amount+denomination
  transaction contracts, denomination-preserving treasury, canonical 1/100/10,000 ratios,
  commodity permission/form data, shard overrides for both NPC families,
  reconciliation-with-hysteresis, TownPerson regional population (+ new comparison-table row),
  single payout representation, wine/salvage strategy absorption, catalog-snapshot vs
  purchase-time authority, Minter explicitly out of scope, consolidated invariant list.
- Currency-ladder audit completed per instruction (migrations, model, seeds, fallbacks, tests,
  conversion code): all four Rails 1/10/100 survivals enumerated in PROJECT_FACTS §4b. Live
  `Currency.base_value` rows remain unverifiable (test DBs still owned by `ultimacraft` —
  re-verified today; dev DB out of bounds). The data/code correction is **scheduled for
  Milestone 3** behind the OQ-1 repair; no historical migration will be edited; regression tests
  will accompany the correction when the test environment permits.
- Pinned RunUO reference checkout created and verified (PROJECT_FACTS §4c; OQ-2 resolved):
  `C:\projects\runuo-reference` @ `71b2794f…`, remote `https://github.com/runuo/runuo.git`,
  read-only.

Validation: documentation-only change; no automated suites apply. Rails tests still blocked
(pre-existing OQ-1 breakage, re-verified via psql).

## 2026-08-10 — Milestone 2: RunUO coverage audit and Vendor/Trader matrix

### Source authority established
- No local RunUO checkout existed anywhere searched; created a dedicated read-only reference
  clone at `C:\projects\runuo-reference` from `https://github.com/runuo/runuo.git` and checked
  out exactly `71b2794f12eb6f948b1c5598ae8b350401a22d4d` (verified via `git rev-parse HEAD`).
  Never modified; kept outside the implementation worktrees. (OQ-2 resolved.)

### Audit performed
- Wrote `tools/parse_runuo_vendors.py`: balanced-paren C# scanner over the entire pinned
  `Scripts/` tree — every `class X : SBInfo` catalog (InternalBuyInfo/InternalSellInfo bodies,
  all four buy-info classes, if/else condition tracking, commented-row capture) and every
  `InitSBInfo` implementor (base class, title, SB composition with conditions). The scan
  covers vendors outside `Mobiles/Vendors/` (healers, Banker, faction vendors, ML-quest
  trainers, Guildmasters).
- Generated the project-owned machine-readable mapping
  `runuo_ultimacraft_mapping.json` (schema 1.0.0): 74 vendors, 84 catalogs, 1,015 buy rows,
  915 sell rows, all rows status-classified with owner rules embedded (1 GP = 1 Gold, retail
  denomination gold, canonical 1/100/10,000, vendors never buy from players).
- Wrote `tools/validate_mapping.py` (duplicate keys, statusless rows, invalid denominations,
  unknown trader targets, unknown item ids, missing catalogs). Run result: **OK — all
  invariants hold** (exact output recorded below).
- Produced `RUNUO_VENDOR_MATRIX.md` (coverage, status distributions, five proposed new trader
  types, commodity/material/quality gap audits, currency-correction tracking) and
  `ECONOMY_RULES.md` (denomination rules, retail pricing formula + open calibration items,
  valuation strategy registry, availability gating, material/quality rules, arbitrage
  invariant, treasury flow).
- PROJECT_FACTS §4d and OPEN_QUESTIONS OQ-3…OQ-9 updated with audit facts and owner-review
  queues.

### Commands and results (automated validation)
```text
python tools/parse_runuo_vendors.py
  vendors=74 catalogs=84 buy_rows=1015 sell_rows=915 conditional_rows=119 inactive_rows=10
python tools/validate_mapping.py
  OK: 74 vendors, 84 catalogs, 1015 buy rows, 915 sell rows — all invariants hold
```
Rails test suite: **not run — still blocked** by the OQ-1 database-ownership breakage
(re-verified via psql this session). No Rails code was changed in this milestone; the
1/10/100 currency correction is explicitly scheduled for Milestone 3 behind the OQ-1 repair.
Gradle suites: not rerun — no Minecraft production code changed (docs/tools only).

### Explicitly NOT implemented (per milestone scope)
Generalized economic NPC schema, spawn-post convergence, runtime vendor/trader rollout,
TownPerson population, Minter/denomination exchange, Product transaction APIs, Shard Admin
rules, automatic staffing, legacy block migration, commodity/Product seed creation.

### Stopped
Milestone 2 complete pending owner review of OQ-3…OQ-9. Milestone 3 not started.

## 2026-08-10 — Open-question cleanup pass (post-Milestone 2, pre-Milestone 3)

- **OQ-1 diagnosed, nothing repaired**: connected as the `ultimacraft` role (owner-supplied
  credentials, used only in-session; not recorded in documentation) and enumerated owners,
  roles, and privileges. Root cause: all `ultimacraft_test*` databases and every table inside
  were created/owned by the superuser role `ultimacraft`; the codex role has no table grants.
  Two repair options written up in OPEN_QUESTIONS for owner approval. Bonus finding from
  read-only inspection: dev `currencies` rows confirm the obsolete 1/10/100 ladder LIVE
  (copper 1 / silver 10 / gold 100); test `currencies` empty — folded into the Milestone 3
  correction scope (data migration required, not just defaults).
- **OQ-6 reclassified**: expanded the matcher's item universe with the 202 dynamically
  registered blacksmithing craftables (+ ItemRegistry re-extraction: 397 ids) and re-bucketed
  the 1,015 retail rows (DIRECT_MATCH 66 / LIKELY_MATCH 102 / MISSING_ITEM 473 /
  SERVICE_OR_MOBILE 29 / UNSUPPORTED 26 / OWNER_REVIEW 93 / VARIABLE_MATERIAL 226). The 93
  OWNER_REVIEW rows reduce to three decisions (magic consumables, deed economy, Hammer
  disambiguation).
- **OQ-4 resolved to policy**: class-based payout denominations applied as data
  (copper 83 / silver 787 / gold 11); 34 exceptions remain, all tied to OQ-8 vendors.
- **OQ-3/OQ-5/OQ-8 tables** and the **OQ-7 mobile-fulfillment proposal** written into
  RUNUO_VENDOR_MATRIX.md §4a/§5/§8/§7b.
- **OQ-9 settled as carrier gaps only** (Fine policy untouched): melee/metal armor/shields
  and jewelry have carriers; ranged and leather/cloth families have neither items nor
  carriers.
- Regenerated + revalidated the mapping (`parse_runuo_vendors.py` → 74/84/1015/915;
  `validate_mapping.py` → OK, all invariants hold). Milestone 3 still not started.

## 2026-08-10 — OQ-1 repair (owner-approved Option A) + Milestone 3

### OQ-1 repair executed
Dropped the 17 `ultimacraft_test*` databases as the `ultimacraft` role, recreated via
`bash bin/setup_test_database` (now owned by `ultimacraft_codex_test`), verified ownership by
query. Full record in OPEN_QUESTIONS "OQ-1 RESOLVED". Development DB untouched.
Baseline re-established: **1340 runs, 1 pre-existing failure** (CityFoodSupplyRecalculator,
deterministic — reran twice in isolation, fails both times; not repaired, unrelated).

### Milestone 3 — Rails economic NPC domain contract + canonical currency correction
Commit `15bf968` on Rails `feature/vendor-trader-economy` (27 files, +1333/−161). Contents:
- Currency: `CANONICAL_BASE_VALUES` (1/100/10,000), data migration correcting existing
  copper/silver/gold rows (dev rows were live at 1/10/100), canonical fallbacks replacing the
  obsolete literals in `SaleTransactionProcessor#currency_breakdown` and the legacy
  wine/salvage controller branch.
- `EconomicNpcType` (kind vendor|trader; mirrors ServiceNpcType constraints; typed
  `default_requirements`), `ShardNpcTypePolicy` (polymorphic over BOTH specializations —
  owner decision #2; replace-not-merge requirements; raw/processed multipliers),
  `NpcRequirements::RuleSet`/`Evaluate` (closed typed rules, fail-closed against the shared
  supply map, machine-readable reasons), `EconomicNpcs::EffectivePolicy`/`Eligibility`
  (admin-neutral domain API; revision = max(type, policy)).
- `city_commodities`: `npc_buy_enabled`/`npc_sell_enabled`/`production_enabled` (default true,
  inert until the eligibility engine consumes them) + `form` raw/processed/finished/other with
  convention backfill (owner decisions #8/#9).
- `MaterialDefinition` + nine-metal seed (`db:seed:material_definitions`, iron rank 1 …
  valorite rank 9 — DEFAULT ladder awaiting owner confirmation, admin-editable).
- 29 new tests incl. the canonical magery example evaluated per shard.

### Validation actually run
```text
bash bin/setup_test_database                    → Test database is ready (codex-owned)
bin/rails db:schema:dump (test env)             → schema.rb updated (test env disables
                                                  dump_schema_after_migration; worker DBs
                                                  rebuild from schema.rb — 58 UndefinedTable
                                                  errors before the dump, 0 after)
bash bin/codex_test <6 new files>               → 29 runs, 87 assertions, 0 failures, 0 errors
bash bin/codex_test (full, post-change)         → 1369 runs, 6435 assertions, 0 errors,
                                                  1 failure (pre-existing recalculator);
                                                  one order-dependent cheque flake observed
                                                  once, passes isolated and on rerun
```

### Scope discipline
Not started at Milestone 3 close: Shard Admin UI (Milestone 4), spawn-post convergence (M5+),
catalogs/Products evolution, transaction APIs, staffing automation.

## 2026-08-10 — Milestone 4: Shard Admin management for economic NPC rules

Owner approved resuming ("i approve"); the seeded nine-metal rank ladder is thereby confirmed
as the working default. Rails commit `37277cc` (19 files, +1046/−73):

- `Admin::EconomicNpcTypesController` — index/show/new/create/edit/update over the economic
  type registry. Typed fields only; key immutable after creation; `definition_revision`
  auto-bumps on change; create/update audited via `AdminActionAudit.record!`.
- `Admin::ShardNpcTypePoliciesController` (nested under types) — per-shard enable/disable,
  raw/processed payout multipliers, and requirement overrides with an explicit
  inherit-vs-override checkbox (nil = inherit defaults; override = replace, possibly empty).
  Policy `revision` bumps on change; create/update/destroy audited.
- `Admin::EconomicNpcEligibilitiesController#show` — read-only per-city eligibility preview
  for one type × shard, calling the SAME `EconomicNpcs::Eligibility` service used outside
  Admin (playbook acceptance criterion), rendering machine-readable reasons and the
  requirements in force.
- `NpcRequirements::FormParams` — maps structured admin requirement rows onto the closed
  typed rule vocabulary (no free-form expressions; malformed numerics surface as model
  validation errors). One fix during testing: ActionController::Parameters handled via
  `to_unsafe_h` — safe because rows are read field-by-field against the closed vocabulary.
- Views under `app/views/admin/economic_npc_types|shard_npc_type_policies|
  economic_npc_eligibilities`, a sidebar link, and nested admin routes.

Guardrails honored: typed fields, no code evaluation, admin session/role gating from
`Admin::ApplicationController`, validation against real shard/supply/currency keys (closed
maps), revision history + immutable audit rows, safe defaults (new types inactive).

### Validation actually run
```text
bash bin/codex_test <3 admin test files>   → 13 runs, 50 assertions, 0 failures, 0 errors
  (includes the acceptance test: magery gate configured entirely via admin HTTP actions,
   evaluated ineligible→eligible by the shared domain service)
bash bin/codex_test (full)                 → 1382 runs, 6481–6483 assertions, 0 errors;
  failures per run: the deterministic pre-existing CityFoodSupplyRecalculator baseline,
  plus occasional members of the known order/timing flake class
  (ServiceNpcSpawnPointsConcurrencyTest race — passes 3/3 isolated;
   Admin::BankChequesController leak — previously verified isolated-pass)
```

### Stopped
Milestone 4 complete.

## 2026-08-10 — Milestone 5: Merchant/Trader spawn posts converge on Service NPC identity rules

Owner approved resuming ("i approve"). Design (per owner decision #1): economic posts ARE
Service NPC spawn posts configured with an Economic type — one shared block, claim store,
outbox, registration protocol, receipts, collision repair, and assignment model. Legacy
Trader/Merchant blocks remain untouched until Milestone 16.

### Rails half (commit `4ad5711`, 13 files)
- Operation envelope accepts exactly one of `service_npc_type_key` / `economic_npc_type_key`
  per UPSERT (`AMBIGUOUS_NPC_TYPE` when both, `MALFORMED_REQUEST` when neither); economic
  gating outcomes added to the closed vocabulary (INVALID/INACTIVE/NOT_SPAWNABLE).
- `ServiceNpcSpawnPoint` gains an exclusive `economic_npc_type` FK; DB status-contract check
  redefined to require exactly one type FK when registered (`num_nonnulls(...) = 1`).
- `ApplyOperation` resolves/stores either kind through one `npc_type_attributes` writer
  (switching kinds nils the other side); world-state payloads + assignments serializer
  publish `economic_npc_type_key`; new `economic_npc_registry` bootstrap section
  (`EconomicNpcRegistrySerializer`, deterministic digest in the ETag).
- `NpcSpawnAssignment` compatibility generalized to the post's NPC type; a service-typed
  World NPC cannot staff an economic post.
- Tests: registration/replay/type-switch acceptance for a merchant + trader post, gating
  outcomes, assignment via the shared model, serializer coverage; two existing contract
  tests updated for the extended shape.

### Minecraft half (commit `6ee2ef00`, 17 files)
- Transport convention `EconomicNpcTypeKeys`: economic keys travel the existing pipeline in
  the shared type-key slot with an `economic:` prefix (colon illegal in real keys), decoded
  once in the wire serializer → emits `economic_npc_type_key`. No outbox schema change.
- New `EconomicNpcRegistry{Cache,Snapshot,Parser,TypeDefinition}` parsing the bootstrap
  section accept-or-empty wholesale; wired in `WorldBootstrapAPI.parseCore` and cleared on
  server stop.
- `ServiceNpcSpawnPayloadHandler.resolveTypeSelection`: service registry first, economic
  fallback on TYPE/REGISTRY_UNAVAILABLE, full gating (bounded key, city, active, spawnable);
  spawn screen lists economic types alongside service ones.
- Spawn-point definitions carry an optional economic key through the bootstrap parser,
  world-state candidate apply, and the durable assignments-cache NBT.
- `ServiceNpcAssignmentReconciler.reconcileEconomic`: materializes economic assignments as
  the registry-named entity type (never hardcoded), dedupes by World NPC public id
  (lowest-UUID canonical), overwrites entity state with cache truth (name/gender/city),
  restricts to the post, fails closed when registry/entity key missing; stale projections
  discarded via the block's last-reconciled World NPC id when the assignment closes.

### Validation actually run
```text
Rails: bash bin/codex_test test/services/service_npc_spawn_points_economic_posts_test.rb
         → 6 runs, 32 assertions, 0 failures, 0 errors, 1 intentional skip
       bash bin/codex_test (full) → 1388 runs, 6516 assertions, 0 errors;
         failures = pre-existing recalculator + known isolated-pass concurrency flake
Minecraft: ./gradlew compileJava --no-configuration-cache → BUILD SUCCESSFUL
           ./gradlew runGameTestServer --rerun-tasks --no-configuration-cache
             → "All 352 required tests passed", BUILD SUCCESSFUL in 7m39s
             (includes the 3 new EconomicNpcSpawnPostGameTests: registry parse/wholesale
              rejection; prefixed pipeline emitting the economic wire field; acceptance —
              BakerEntity materialized, no duplication, recreation with fresh entity UUID,
              withdrawal on assignment close)
           Unit :test suite not rerun (its 13 pre-existing banner failures are unrelated;
           no unit-test inputs changed)
```

### Acceptance criteria
Merchant post + Trader post register (Rails test), retain stable identity across replay,
receive assignment/state (shared NpcSpawnAssignment + reconciler), survive restart (durable
cache recreation test), and recreate the correct registry-named entity without identity
duplication (post UUID ≠ WorldNpc id ≠ entity UUID asserted). Legacy blocks untouched.

### Stopped
Milestone 5 complete.

## 2026-08-11 — Milestone 6: Economic NPC bootstrap and assignment projection

Owner approved resuming ("i approve"). Rails-only milestone (commit `5d425ed`) — the
Milestone 5 architecture already projects assignments into Minecraft, which is the point:
activation is decided entirely in Rails and reaches the mod through existing plumbing.

- `EconomicStaffing::Reconcile` (city row lock + in-transaction audit, mirroring
  `CityStaffing::Reconcile`): per economic type, evaluates `EconomicNpcs::Eligibility`;
  eligible → fills every enabled, registered economic post, preferring an existing active,
  unassigned World NPC of the profession (service-typed World NPCs excluded) before minting
  via `WorldNpcs::Create`; ineligible → closes assignments with the machine-readable
  eligibility reasons in the audit change rows, never deleting/retiring World NPC identity.
  Milestone 6 population policy is one-NPC-per-eligible-post; formula/hysteresis/scheduling
  stay in Milestone 15 as planned.
- `EconomicStaffingReconciliationJob` + `Admin::EconomicStaffingReconciliationsController`
  (perform_later-from-admin-action, the app's established shape); per-city "Reconcile
  staffing" button on the eligibility preview page.
- `EconomicNpcRegistrySerializer` gains an optional shard scope: the per-shard bootstrap now
  publishes `shard_enabled`, `policy_revision`, and `effective_revision` per type (playbook's
  "economic eligibility revision" metadata). The NeoForge parser reads a closed member set,
  so the additions are wire-compatible with the Milestone 5 client unchanged.

### Validation actually run
```text
bash bin/codex_test test/services/economic_staffing_reconcile_test.rb
  → 6 runs, 37 assertions, 0 failures, 0 errors
  (acceptance: a shard policy edit alone deactivates and reactivates the NPC assignment
   through authoritative state, publishing world-state changes; the SAME World NPC is
   reused on reactivation; audit rows carry the applied changes)
bash bin/codex_test (full) → 1394 runs, 6551 assertions, 0 errors, 1 failure
  (the deterministic pre-existing CityFoodSupplyRecalculator baseline only)
Minecraft: no changes this milestone — the M5 GameTest suite (352/352 passed) already
  covers assignment projection/withdrawal, which is the Java half of this acceptance.
```

### Stopped
Milestone 6 complete.

## 2026-08-11 — Milestone 7: Generic Vendor definition and sell-to-player catalog (Baker slice)

Owner approved resuming ("i approve"). Rails `db0d036`, Minecraft `656cf812`.

Rails: `EconomicCatalog::ForVendor` — the authoritative Vendor catalog from Product rows
(ProductListing keyed by economic type key; global/shard/city listing scoping; owner decision
#4 honored — Minecraft recipes never consulted). Per-row availability decisions with
machine-readable reasons; stock floored from the limiting commodity; pricing precedence
listing-override > positive baseline > legacy dynamic input-markup computed in Rails
(price 0 = dynamic; strategy named per row — the calibrated RunUO formula binds at the
pricing milestone). `products.price_denomination` added (closed set, nullable);
`Product#commodity_requirements` normalizes typed + legacy shapes with save-time validation.
Served by `GET economic_catalog` (signed shard auth, city by public id only, rate-limited,
catalog revision digest). Baker slice seeded via `db:seed:economic_products_baker` (economic
type `baker` + four products mirroring the legacy recipe outputs).

Minecraft: the reconciler stamps economic projections with economic type key + city public
id (NBT-persisted); `ServerCatalogService` resolves stamps on-thread and routes stamped
entities to the economic_catalog endpoint (available rows only → Product list with
denomination + max_quantity/catalog-revision custom data). Unstamped legacy-block merchants
keep the local path; purchases stay on the legacy merchant flow until Milestone 14 (seed
mirrors legacy outputs so both agree in the slice); Vendors expose no player-sell path.

Validation:
```text
Rails: bash bin/codex_test test/services/economic_catalog_for_vendor_test.rb
         → 7 runs, 26 assertions, 0 failures, 0 errors
       bash bin/codex_test (full) → 1401 runs, 6567 assertions, 0 errors,
         1 failure (pre-existing recalculator), 1 intentional skip
Minecraft: compileJava BUILD SUCCESSFUL; runGameTestServer --rerun-tasks
       --no-configuration-cache → "All 352 required tests passed" (8m54s),
       incl. the extended stamp assertions on materialized projections
```

### Stopped
Milestone 7 complete.

## 2026-08-11 — Milestone 8: Commodity and recipe eligibility engine

Owner approved resuming ("i approve"). Rails-only, commit `9583763`.

`EconomicCatalog::ProductAvailability` is now THE single explainable listing/stock/price
decision (playbook §9): the vendor catalog delegates every row to it (batch plumbing: one
eligibility evaluation + one commodity index), and the Milestone 14 retail transaction will
re-validate purchases through the same service. New over Milestone 7: the NPC activation
decision is an input (ineligible Vendor lists nothing, nested reasons preserved under
`npc_not_eligible`), and available units are capped at the RunUO 999 maximum-stock tier
(reconstruction design §15/§23 — the project's documented stock-cap precedent). Mandatory
listing rule stays fail-closed; the acceptance round-trip (commodity absent → delisted;
restored → relisted) is covered, including catalog/decision parity for purchase-time callers.

Validation:
```text
bash bin/codex_test <availability + catalog suites> → 11 runs, 47 assertions, 0 failures
bash bin/codex_test <all four M5–M8 economy suites> → 23 runs, 116 assertions, 0 failures
bash bin/codex_test (full) → 1405 runs, 0 errors; failure count varies 1–4 per run,
  ALL from the pre-existing set: the deterministic CityFoodSupplyRecalculator baseline plus
  the known non-transactional cheque-leak flake family (bank_cheques file passes 13/13 in
  isolation). Correction to the 9583763 commit message, which cited the single-failure run:
  the accurate statement is 0 errors with only pre-existing/flaky failures.
```

### Stopped
Milestone 8 complete.

## 2026-08-11 — Milestone 9: City provenance on Vendor/Trader products

Owner approved resuming ("i approve"). Minecraft-only, commit `92c6f1f6`.

`item/CityProvenanceItemData` — BlacksmithItemData-style named compound in
`minecraft:custom_data` carrying `origin_city_public_id` + `origin_city_name`. Stack
equality prevents cross-origin merging; vanilla serialization preserves it everywhere;
written server-side only from the reconciler-stamped authoritative assignment city
(`applyFromVendor`; M14 will stamp from the Rails transaction payload). Merchant purchases
stamp products for economic projections (legacy-block merchants stamp nothing); the global
tooltip handler renders "Origin: <city>".

Validation:
```text
compileJava BUILD SUCCESSFUL; runGameTestServer --rerun-tasks --no-configuration-cache
  → "All 354 required tests passed" (5m19s) incl. the 2 new CityProvenanceGameTests:
  Britain/Minoc stacks never merge, both fields survive NBT round-trip, identical
  provenance stays stackable, vendor stamp uses the assignment city, legacy merchants
  add nothing. Rails untouched this milestone.
```

### Stopped
Milestone 9 complete.

## 2026-08-11 — Milestone 10: Trader player-sell transaction foundation

Owner approved resuming. Rails `8c1ec95`, Minecraft payload patch committed alongside.

`Economy::EconomicTraderSale`: Player -> Trader sales for economic projections (payload
names the World NPC public id; legacy path untouched). Identity/city from the ACTIVE
ASSIGNMENT, never the client; Rails-priced with shard raw/processed multipliers over
explicit form data; npc_buy_enabled fail-closed; supply caps. Quote = amount + ONE
denomination (ratified tiers raw→copper, processed/finished→silver; mixed baskets rejected
whole; nearest-whole rounding documented as provisional). Treasury debited atomically in
that denomination (locked coins_outstanding, never negative, whole-sale rejection when
unfundable) — and every failure RAISES so the transaction rolls back partial mutations
(tests caught that a plain return committed them). One payout representation
(currency_grant only; shard_user.currency never written). Idempotent replay.

Validation: focused 6 runs/22 assertions/0 failures. Full suite 1411 runs, 0 errors;
failures are the pre-existing recalculator plus a known order-dependent leak into the
staffing suite (passes 3/3 isolated). compileJava BUILD SUCCESSFUL; the GameTest suite was
not rerun for the payload-field addition (covered by Rails routing tests; 354/354 as of
Milestone 9). Known remaining hardening, tracked for Milestone 19: the Minecraft-side
reserveItems reservation still lacks banking-style local receipts (pre-existing standing
issue; Rails-side idempotency plus refund-on-replay bound the exposure).

### Stopped
Milestone 10 complete.

## 2026-08-11 — Milestone 11: Full RunUO buyback-to-Trader coverage

Owner approved resuming. Rails `50abc0a`; Minecraft coverage-test commit + this log.

- Machine-readable completeness test IN THE BUILD: `RunuoBuybackCoverageTest` (JUnit, runs
  in `gradlew test`) walks all 915+ sell rows of `runuo_ultimacraft_mapping.json` — every
  row must target a known Trader (10 legacy + 5 approved new types) with a valuation
  strategy, or carry an explicit unresolved status with denomination bookkeeping; also
  asserts every vendor keeps `vendor_can_buy_from_player=false`. Coverage cannot regress
  silently. Result: BUILD SUCCESSFUL — the matrix reports 100% accounted-for buyback rows
  (638 mapped to existing traders, 224 to the approved new types, 53 explicitly unresolved
  pending the open owner decisions — magic consumables, deed economy, Thief/VarietyDealer).
- Rails: `db:seed:economic_trader_types` seeds the fifteen Trader EconomicNpcTypes every
  mapped row targets (legacy categories on their existing entity types; new five inactive
  until their Milestone 17 families). Negative tests prove the specialty-Vendor rule at the
  transaction layer (Vendor assignment → `trader_not_assigned`, nothing mutated) and
  registry coverage/idempotence.

Validation: Rails focused 2 runs/35 assertions/0 failures; full 1413 runs/0 errors
(pre-existing recalculator only this run). Minecraft coverage test BUILD SUCCESSFUL.
Per-row runtime accept-lists land with the Milestone 17 profession families.

### Stopped
Milestone 11 complete.

## 2026-08-11 — Milestone 12: Raw and processed material Trader pricing

Owner approved resuming. Rails `134a983` (test-only — the policy machinery itself landed in
Milestone 10; this milestone proves it against the playbook fixtures).

- Acceptance: raw wheat and processed flour trade under DIFFERENT shard-configured
  multipliers while sharing the same authoritative CityCommodity market (equal starting
  prices asserted), payouts in the ratified tiers (discounted copper vs full-value silver).
- A shard policy row alone changes the payout for the same category (shard-configurable).
- All eight legacy trader categories (wood, fish, ore, stone, fur/leather, grain, produce,
  meat) price a representative commodity through the shared engine with the raw discount
  and a real city-supply increase.

Validation: focused 3 runs/31 assertions/0 failures; full suite 1416 runs/0 errors —
failures this run were the pre-existing recalculator, the known spawn-point concurrency
race, and the order-dependent leak into EconomicStaffingReconcileTest (passes 2/2 with the
new suite in isolation). Robustness note for Milestone 19: EconomicStaffing::Reconcile
iterates every EconomicNpcType row, which makes its suite sensitive to type rows leaked by
the app's documented non-transactional test classes; scoping or cleanup support would
de-flake it.

### Stopped
Milestone 12 complete.

## 2026-08-11 — Milestone 13: Recipe-valued weapons and metal goods

Owner approved resuming. Rails `9ff24cb`; Minecraft passthrough commit.

Products declare variable-material requirements ({material_family, quantity});
`ProductAvailability` selects the HIGHEST enabled material from MaterialDefinition rank
data whose metal/ingot commodity exists, is production-enabled, priced, and supplied --
walking down the ladder on shortfall and failing closed (no_eligible_material /
missing_baseline_material / no_material_rank_data). Retail price = Product baseline (RunUO
Iron anchor) + material delta converted into the Product denomination (provisional nearest
rounding, ECONOMY_RULES section 2); quality = Fine (owner tier); rows carry
selected_material/quality, passed through the Minecraft catalog custom data for the
Milestone 17 item construction. Guard fix caught by tests: material-only products are not
"without production requirements".

Acceptance proven: price tracks the city material market with no Java constants; supply
fallback walks the rank ladder to iron and fails closed past it; disabled rank rows are
skipped; recipe-quantity changes change the valuation.

Validation: focused 15 runs/67 assertions/0 failures (M13 + M7/M8 regression); full suite
1420 runs/0 errors (pre-existing recalculator + one known flake this run); compileJava
BUILD SUCCESSFUL.

### Stopped
Milestone 13 complete.

## 2026-08-11 — Milestone 14: Vendor retail transactions and city production consumption

Owner approved resuming. Rails `6557a32`; Minecraft commit in this revision.

Rails `Economy::EconomicVendorPurchase` (POST /api/economic_purchase, signed shard auth)
is the authoritative retail commit: identity from the active assignment (vendor kind
required), every line re-validated through the SAME `ProductAvailability` the catalog
displays, stale quotes rejected (`quote_changed`, 409) rather than honored. Atomic
effects under row locks: production inputs consumed (fixed commodity requirements plus
the selected material ingot, re-checked, floored at zero -- reject, never oversell);
treasury credited in the paid denomination with Currency/TreasuryBalance created on
demand; one idempotent ledger row (replay returns the recorded outcome, no duplicate
production or credit). Response carries construction data (material, quality, origin
city).

Minecraft `EconomicVendorPurchaseService`: stamped CitizenEntity projections route out
of the legacy MerchantRecipes/copper path entirely. Buy re-quotes the Rails catalog
server-side, enforces single-denomination checkout, reserves EXACTLY the quoted
denomination (a player rich in gold but short one silver cannot pay a silver price --
no automatic conversion), posts with expected prices, refunds in kind on rejection or
outage, and constructs granted items from the response construction data
(economic_material/economic_quality custom data + Milestone 9 city provenance). New
`Endpoint.ECONOMIC_PURCHASE`. Weight is NOT invented from MerchantRecipes tables --
Rails Product.requirements stays the sole production authority.

Acceptance proven (with Milestone 10 as the Trader half of the pair): the full
treasury/economy cycle -- Trader buys from players debiting coins_outstanding (M10),
Vendor sells to players crediting coins_outstanding with production inputs consumed
(this milestone), all in exact denominations against one treasury ledger.

Validation: focused 4 runs/20 assertions/0 failures; regression band (M7/M8/M10/M12/M13
+ M14) 28 runs/140 assertions/0 failures; full Rails suite 1424 runs/0 errors (failures
only in the three documented pre-existing flaky families, each re-verified isolated);
route + controller boot verified via rails runner/routes; GameTests 359/359 (354
existing + 5 new exact-denomination settlement tests); compileJava BUILD SUCCESSFUL.

### Stopped
Milestone 14 complete.

## 2026-08-11 — Owner decision pass (pre-Milestone-15)

All remaining owner decisions resolved and applied (MC `c9057709`): magic/alchemy
consumables DEFERRED (60 rows UNSUPPORTED_PENDING_MAGIC); deed retail stays with the
existing house-deed service (30 rows SERVICE_EXISTING_SYSTEM); per-profession hammer
items (3 rows MISSING_ITEM with planned carpenter/stonecrafter/tinker_hammer ids,
landing with their Milestone 17 families); all five new trader types kept. The 53
unresolved buyback rows fell to zero (49 OWNER_MAPPED twin-consistent, 4
OWNER_UNSUPPORTED); validator + RunuoBuybackCoverageTest enforce the owner_decision
records. Zero owner decisions remain open before Milestones 15-19.

## 2026-08-11 — Milestone 15: Rails-driven activation and population

Owner approved resuming. Rails `0d6320a`; NO Java changes (activation flows through the
existing Milestone 5/6 world-state assignment projection — the acceptance criterion "no
Java deployment required to change economic spawn requirements" holds by construction).

Owner decision #10 lands in full. Hysteresis = minimum active/inactive dwell durations
(EffectivePolicy-resolved: type defaults 300s/300s, shard policy overrides nullable =
inherit, Shard-Admin-editable in both places): economically ineligible assignments
younger than min_active_seconds are HELD open; posts whose last ECONOMIC closure is
younger than min_inactive_seconds are HELD empty — no oscillation around thresholds.
Administrative gates (type_inactive/shard_disabled/city_not_on_shard) bypass dwell in
both directions with their own closure reason. Async reconciliation: economic commits
(commodities, treasury balances, economic policies, type definitions) MARK hosting
cities dirty (cheap update_all, never the engine inline); EconomicStaffingSweepJob
drains marks every 5 minutes via Solid Queue with a mid-reconcile-safe conditional
clear; system runs log structured events instead of AdminActionAudit rows. Reconcile is
now scoped to types with posts in the reconciled city (retires the Milestone 12
leak-sensitivity note).

Acceptance proven (playbook example verbatim: alcohol > 100 AND treasury gold >= 20):
below threshold ineligible with machine-readable reasons; crossing assigns; falling
below is dwell-held then closes; recovery is dwell-held then reassigns the SAME
persistent World NPC; a second shard's own thresholds and dwell overrides act
independently; requirement changes propagate with zero Java.

Validation: hysteresis suite 5 runs/55 assertions/0 failures; staffing suites 11/73/0;
recurring-schedule test 3/11/0; full suite 1432 runs/0 errors (only the two documented
pre-existing failures, both pass isolated). Environment note: a schema.rb dumped
without RAILS_ENV=test poisoned the main test DB via maintain_test_schema! (phantom-
applied migration); repaired by un-record + re-migrate + re-dump + worker-DB reload —
recorded in the baseline docs conventions.

### Stopped
Milestone 15 complete.

(Record correction: the approval that resumed Milestone 15 was a misunderstanding --
the owner intended it for the owner-decision application only. The owner accepted the
delivered milestone after the fact.)

## 2026-08-11 — Owner architecture directive: canonical supply units (corrective)

Owner directive applied (Rails commit in this revision; OPEN_QUESTIONS + ECONOMY_RULES
section 9 record it verbatim). One authoritative supply representation per commodity:
`city_commodities.stock_unit` ("weight" | "count") names the canonical column and
`unit_weight` derives the mirror on every save -- a direct write to the mirror can
never survive as a second truth (model-enforced, invariant-tested). Bulk/fungible
families are weight-canonical: fish, wood, stone, ore, meat, GRAIN (flour explicitly,
per the directive's example), metal, textile, and every food-supply category. Discrete
goods (wine bottles today; weapons/tools/potions/animals when their families arrive)
stay count-canonical. New rows default from the category; explicit stock_unit wins.

Findings from the mandated inspection, all healed: the classification lived as a
hardcoded category|subcategory list in CityCommodity#weight_based_inventory? AND as a
second, diverged copy in SaleTransactionProcessor#weighted_commodity? (both retired --
stock_unit is the single authority); the plural `metal|ingots` rows missed the
singular-`ingot` check and were silently count-based; the grain family was
count-based purely historically; the legacy processor's count branch let payload
weight leak into count-denominated supply (now: count moves by count). Writers
(EconomicVendorPurchase, EconomicTraderSale, SaleTransactionProcessor) now write the
canonical column only. Trader-sale deltas for weight goods without payload weight
derive through unit_weight instead of the raw count.

Verified already compliant, unchanged: pricing normalizes inventory_level against
max_supply_cap (unit-agnostic); ProductAvailability divides canonical supply by the
per-unit requirement -- exactly the derived-ItemStack-count rule the directive asks
for, so no Minecraft changes are needed.

Migration backfills stock_unit for existing rows, seeds weight from the legacy count
where a switching row had no tracked weight, and derives all mirrors once; check
constraints close the unit set and force unit_weight > 0.

Follow-ups (ECONOMY_RULES section 9): unit_weight calibration for count rows whose
derived weight feeds weight-summed city supply columns (wine bottles); reagent-family
unit decision at Milestone 17.

Validation: new invariant suite 6 runs/23 assertions/0 failures; M14 purchase suite
re-asserts flour consumption BY WEIGHT (4/16/0); economy regression band 35 runs/173
assertions with only the documented pre-existing recalculator failure; full suite
tally recorded in the corrective commit message.

### Stopped
Canonical supply units corrective complete.

## 2026-08-11 — Milestone 16: Migrate legacy MerchantSpawnBlock and TraderSpawnBlock

Owner approved resuming. Rails `36db9e6`; Minecraft `854641f5` (+ log commit).

Legacy posts converge in place onto the authoritative architecture. Minecraft
`LegacySpawnBlockMigrator`, invoked from each legacy block entity's own tick cadence:
despawn the legacy-MANAGED merchant/trader mob (Rails-synced,
migrated_to_authoritative_post -- the assignment pipeline staffs the post, so leaving
it would duplicate); write a full-fidelity rollback receipt to
`LegacySpawnBlockMigrationLedger` (overworld SavedData: legacy block id, complete NBT,
mapped type/city, townPersonAmount, outcome, replacing post UUID; never deleted by
code); replace the block with a real ServiceNpcSpawnBlock whose fresh entity mints its
stable UUID through the claim store like any player-placed post; apply the migrated
configuration (city public id + economic:<type>), staging the durable pending UPSERT.
Milestones 5/6/15 finish it: registration marks the city (new Rails rule in this
milestone: economic post commits mark the staffing sweep), EconomicStaffing assigns,
the projection materializes.

Availability-safe: until a conversion SUCCEEDS (bootstrap city resolvable, type key
mapped), legacy behavior runs untouched -- a backendless world keeps its merchants;
the deprecated sourceId/heartbeat path is bypassed only when the new path takes over
(playbook deprecation rule; parity proven by the new GameTests). No registry ids
change; old chunks keep deserializing. TownPersons are never touched (decision #12 --
preserved until the regional population system; townPersonAmount preserved in the
receipt for it; only the deprecated side-spawning ends). Type mapping: the ten legacy
trader keys ARE the Milestone 11 Rails keys; merchants map baker/tavernkeeper/
costermonger -- Rails seeds the two new vendor types ACTIVE
(db:seed:economic_vendor_types_legacy_merchants) because these NPCs already exist in
live worlds; their catalogs arrive with Milestone 17.

Rollback runbook: docs/vendor-trader-economy/LEGACY_SPAWN_BLOCK_MIGRATION.md
(per-position restore from the receipt; known limits recorded).

Acceptance proven: a world with configured legacy posts loads and converges -- config
preserved (city/type on the new post + pending Rails registration), legacy merchant
despawned, TownPerson alive, receipt complete; trader keys carry over; unconfigured/
unresolvable blocks stay fully legacy with no receipts; an unavailable city registry
blocks migration entirely.

Validation: Rails 4 runs/49 assertions focused; full suite 1442 runs/0 errors (only
the documented deterministic recalculator failure); compileJava BUILD SUCCESSFUL;
GameTest server all 363 required tests passed (359 existing + 4 new).

### Stopped
Milestone 16 complete.

## 2026-08-11 — Milestone 17: Full RunUO vendor rollout

Owner approved resuming. Rails `57a74ce`; Minecraft commit in this revision.

Acceptance: the matrix reports EVERY RunUO Vendor and retail row as implemented or
explicitly excluded, machine-enforced by the new `RunuoRetailRolloutCoverageTest`
(retail twin of Milestone 11's buyback coverage test). Generated data-driven by
`tools/generate_vendor_rollout.py` into `economic_vendor_rollout.json` (mirrored to
Rails `db/seeds/data/`, seeded by `db:seed:economic_vendor_rollout`; conservative:
never overwrites existing types/products, always ensures listings).

Vendors: 33 vendor economic NPC types (19 ACTIVE with catalogs; 14 registered
INACTIVE awaiting items/commodity families -- explicit, admin-activatable). The other
41 vendor classes: OQ-8 merges, trader aliases, service, guildmaster territory, or
unsupported by design. Presentation: dedicated entities where they exist; every other
profession shares the new `britannia_mod:vendor` GenericVendorEntity whose role title
humanizes the stamped type key (VendorRoleTitles, JUnit-covered) -- per-profession
visuals become an admin `minecraft_entity_type_key` swap, never a deploy.

Retail rows (1,015): 267 seeded -- 116 fixed-input + 151 metal-family
material-machinery rows across 98 unique Products; RunUO GP as GOLD (ratified 1:1);
requirements are PROVISIONAL family conventions over canonical CommoditySeeder keys
only, admin-editable, Milestone 19 section-2a owns calibration. 748 explicitly
excluded: 548 pending item creation, 60 magic-deferred, 36 pending commodity families
(glass/reagent/textile/scribe, OQ-5), 30 deed/service, 29 mobile fulfillment (OQ-7),
26 unsupported, 19 without a retail home in merged/service vendors.

The three owner-decided profession hammers (carpenter/stonecrafter/tinker) are REAL
registered items (interim visuals parent the blacksmith hammer model) and their rows
are seeded products. Corrective folded in because the metal family made it live: the
Milestone 13 material lookup's singular `metal|ingot` identity would never have
matched the canonical plural `metal|ingots` CommoditySeeder rows -- fixed in both the
availability and purchase paths (M19 note: centralize the identity); tests updated.

Family flows proven end-to-end in the rollout suite: a generated weapon values
through material selection and sells with gold treasury credit + provenance data; a
tavern product consumes canonical weight supply on purchase. Hysteresis suite
assertions city-scoped (leak-proof against non-transactional neighbors).

Validation: rollout + purchase + metal + eligibility + hysteresis focused suites all
green; full Rails suite 1447 runs/0 errors (only the four documented pre-existing
flaky failures, re-verified isolated); mapping validator all invariants hold; both
JUnit coverage tests green; compileJava BUILD SUCCESSFUL; GameTest server all 363
required tests passed on the final build.

### Stopped
Milestone 17 complete. Milestone 18 (observability, admin diagnostics, hardening) not
started, per stop rule.
