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
