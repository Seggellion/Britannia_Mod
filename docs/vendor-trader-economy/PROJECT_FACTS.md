# Vendor/Trader Economy — PROJECT_FACTS

Status: Milestones 0–1 (repository discovery). Verified facts only; every claim cites source.
Date: 2026-08-10.

## 1. Repositories

### Minecraft (NeoForge)
- Root: `C:\projects\britannia\mod\Britannia_Mod` (main checkout, branch `patch-18`), plus worktrees.
- Remote: `https://github.com/Seggellion/Britannia_Mod.git`.
- Stack: NeoForge 1.21.1, Java 21, Gradle (configuration cache ON — see `docs/known_environment_baseline.md` §1).
- **Approved integration tip: `patch-18` @ `2accdbd1`** ("Merge Guildmaster Service NPC into patch-18"). Evidence: `patch-18-troubleshooting` (`50061f07`) is an ancestor of `patch-18` (`git merge-base --is-ancestor` verified); the most recent completed feature (Guildmaster Service NPC) merged into `patch-18`; the main checkout sits on `patch-18`. The playbook header's "historically `patch-18-troubleshooting`" is now stale — `patch-18` contains everything in it plus Guildmaster.
- Worktrees at Milestone 0 start: `Britannia_Mod` [patch-18 @2accdbd1], `Britannia_Mod-house-farm-plot` [feature/house-farm-plot-farming], `Britannia_Mod-integration` [patch-18-network-integration @50061f07], `Britannia_Mod-troubleshooting` [patch-18-troubleshooting @50061f07, **dirty** — staged bank/farming docs, modified network/registry sources, untracked Guildmaster/RunUO/vendor-playbook files], `britannia_mod_guildmaster` [feature/guildmaster-service-npc].
- Feature worktree created this milestone: `Britannia_Mod-vendor-trader-economy`, branch `feature/vendor-trader-economy` from `patch-18` @ `2accdbd1`.
- No `AGENTS.md`/`CLAUDE.md` exists in the Minecraft repo. Repository instruction docs: `docs/known_environment_baseline.md` (Gradle rerun/config-cache gotchas, WSL/CRLF warning for Rails files, known standing issues incl. `ServerEconomyService.reserveItems` having no idempotency guard).
- `gradle/wrapper/gradle-wrapper.jar` is a **Git LFS pointer** (oid `498495…`, 43504 bytes). LFS smudge does not run on fresh worktree checkout in this environment; the jar must be restored manually (byte-identical copy verified by sha256 against the pointer oid). The main and troubleshooting checkouts carry the expanded jar; troubleshooting shows it as perpetually "modified". Never stage this file.

### Rails
- Root (WSL): `/home/dusti/ultimacraft-website`. Native-LF Linux repo — access via `wsl.exe`; do not edit through Windows-side tools (CRLF risk, per known_environment_baseline §1.2).
- Stack: Rails 8, PostgreSQL 18 (WSL, Unix socket, peer auth), Ruby 3.2.2 via rbenv.
- **Integration branch: `banking`** @ `35653f5` ("Add a local setup task for Guildmaster in-game validation"). All recent features (banking, Service NPC, Guildmaster) landed here.
- Working tree at Milestone 0 start: **dirty** — in-flight spawn-point-retirement work (modified `app/controllers/admin/service_npc_spawn_points_controller.rb`, routes, sidebar; untracked `app/services/service_npc_spawn_points/retire.rb` + admin controller + test). Untouched by this project.
- Feature worktree created this milestone: `/home/dusti/ultimacraft-website-vendor-trader-economy`, branch `feature/vendor-trader-economy` from `banking` @ `35653f5`. (Worktree checkout loses the exec bits on `bin/codex_test`/`bin/setup_test_database` — tracked mode is 100644 and `core.filemode=false`; run via `bash bin/…` or chmod locally.)
- `AGENTS.md` (repo root): canonical test command is `bin/codex_test` (bootstraps via `bin/setup_test_database`); never point tests at production; begin milestones by running relevant existing tests; do not proceed while DB setup is broken.
- Stale parked branches documented in Rails `docs/known_environment_baseline.md` §3.2 (service-npc m3/m4) — not touched.

## 2. Baseline test state (Milestone 0)

### Minecraft
- Command: `./gradlew build --rerun-tasks --no-configuration-cache` in the feature worktree (fresh, patch-18 tip).
- Result: compileJava OK; `:test` **FAILED** — 1788 tests, **13 failed** (12 × `BannerScaffoldToolTest` with `BannerScaffoldTool$ScaffoldException`, 1 × `ParallelMediumIntegrationTest.fiveApprovedGeometryGroupsPreserveTheFullCanvasUvBasisAndMatchRuntime`), 17 skipped. 6m33s.
- Classification: **pre-existing at `patch-18` @ 2accdbd1** — the worktree contains only the untouched commit tree (plus the byte-identical LFS jar restore). All failures are banner-scaffold tooling, unrelated to Vendor/Trader/Service NPC/economy.
- GameTest suite: `./gradlew runGameTestServer --rerun-tasks --no-configuration-cache` — result recorded in IMPLEMENTATION_LOG.

### Rails
- Command attempted: `bash bin/codex_test` (feature worktree, then reproduced identically from the main checkout).
- Result: **BLOCKED — environment breakage, pre-existing and repo-wide.** `db:prepare` fails with `PG::InsufficientPrivilege: permission denied for table schema_migrations`. Root cause verified with psql: **every `ultimacraft_test*` database (incl. all 16 parallel-worker DBs) is owned by role `ultimacraft`, not the canonical `ultimacraft_codex_test`** documented in `docs/local_test_database.md`. Even `SELECT` on `schema_migrations` is denied for the peer role, so no Rails test can run from any checkout. The codex role cannot repair it (does not own the databases; no passwordless sudo; no trust auth for `ultimacraft`).
- Required user/admin remediation (not performed — needs Postgres admin rights): as a superuser or the `ultimacraft` role, drop the `ultimacraft_test*` databases (or `ALTER DATABASE … OWNER TO ultimacraft_codex_test` + reassign table ownership), then run `bin/setup_test_database`. The development DB (`ultimacraft_development`, used by in-game validation) is unaffected and must not be touched.

## 3. RunUO source inputs
- `runuo_vendor_reconstruction_design.md` + `runuo_vendor_catalog.json` located as **untracked files in the troubleshooting worktree**; copied into the feature branch (repo precedent: playbooks/design docs are committed at repo root, e.g. the Guildmaster set).
- Pinned source: `runuo/runuo` @ `71b2794f12eb6f948b1c5598ae8b350401a22d4d`, subsystem `Scripts/Mobiles/Vendors` (`BaseVendor`/`SBInfo`).
- **The JSON is explicitly non-exhaustive**: `coverage` metadata declares `product_export: safety_filtered`, 11 verified vendor definitions, 12 catalogs (carpenter *safe subset*, animal trainer, architect/house deeds, baker, beekeeper, cobbler, farmer, fisherman, fur trader, hair stylist, jeweler), `restricted_product_rows_omitted: true`, `restricted_catalog_paths_omitted: true`. 141 products in the normalized index. The engine-model documentation is exact; the catalog data is a verified sample. **Milestone 2 must audit the pinned RunUO source directly and build the complete project-owned vendor/buyback matrix.**

## 4. Key verified architecture facts (details + citations in COMPATIBILITY_MAP.md)

### Minecraft
- Trader entities: `AbstractTraderEntity extends CitizenEntity implements ITrader`; subclasses are thin type-pointers into the static `TraderTypes` registry (`trader/TraderTypes.java`): wood (class `EntityWoodMerchant` — a Trader despite the name), fish, salvage, alcohol, ore, stone, meat, grain, produce, fur_leather (+ aliases). `shouldBeSaved() == false` — traders are never persisted as entities; the spawn block recreates them from an NBT snapshot.
- Merchant entities: `AbstractEconomyMerchantEntity extends CitizenEntity`; subclasses Baker/Tavernkeeper/Costermonger (registry `merchant/MerchantTypes.java` — exactly those three). **`ArchitectEntity` extends `CitizenEntity` directly** (not the abstract merchant) yet opens the MERCHANT screen — a documented hierarchy exception. Legacy Villager-based `EntityMerchant` hierarchy (Horse/Metal/Stone) still exists (`entity/EntityMerchant.java` extends `net.minecraft.world.entity.npc.Villager`).
- Legacy spawn blocks (`block/entity/TraderSpawnBlockEntity.java`, `MerchantSpawnBlockEntity.java` — structural clones): `sourceId = UUID.randomUUID()` at construction, persisted in NBT, reused as Rails sync source id, entity tag (`trader_source_*`/`merchant_source_*`), and heartbeat identity; `traderNpcId`/`merchantNpcId` is a second random UUID that is **forced onto the loaded entity via `setUUID`** and sent to Rails as `npc_id`. Per-block 600-tick Rails heartbeat (`CityDataSync.heartbeatLiveNpc`) and 200-tick maintain loop. City identity: free-text string name. No clone-repair, no revision, no registration state, no menu-backed config validation.
- Service NPC posts (`block/entity/ServiceNpcSpawnBlockEntity.java` + `service/spawn/*`): separate `spawnPointId` (post UUID), `cityPublicId` (UUID), `serviceNpcTypeKey`, enabled flag, `configurationRevision`, registration state machine, claim store with clone/collision rekey, durable pending-operation outbox with receipts, assignment projection (`assignedNpcPublicId` ≠ post UUID ≠ entity UUID). Entities are recreated per-chunk-tick by `ServiceNpcAssignmentReconciler` from `ServiceNpcAssignmentsCache` (never a live Rails fetch), with deterministic duplicate discard.
- Config security: `ServiceNpcSpawnPayloadHandler.validateSession` — menu-backed (`ServiceNpcSpawnMenu`), permission level 2, owner, container id, dimension, distance, submitted pos, block type, spawn-point UUID match, `stillValid`, revision check, plus registry validation of city/type against bootstrap caches. Legacy Trader/Merchant config uses direct screen payloads with none of this (`network/TraderSpawnConfigC2SPayload.java` etc.).
- Sync: `WorldStateSyncPoller` polls `world_state_changes/:shard?from_version=` every ~5 min + jitter, `WorldStateFullBootstrapFallback` on gaps; Service NPC ops POST `service_npc_spawn_operations` from a durable outbox. No per-tick Rails traffic in the Service NPC path.
- Economy flows: Trader sell = `economy/ServerEconomyService` (server-side re-validation, reserve-then-refund of real items, idempotency key, POST `trader_transactions`; **known standing issue: `reserveItems` check-then-shrink has no idempotency/dedup guard** — known_environment_baseline §2.1). Merchant buy = `economy/MerchantEconomyService` + `MerchantCatalogBuilder` (price = `ceil(inputCost × 1.70)` computed in Java from Rails commodity prices; recipes hard-coded in `economy/MerchantRecipes.java`; coins are physical items, `CoinConversion` 1g = 100s = 10,000c).
- Item metadata: canonical carrier is vanilla `minecraft:custom_data` (`DataComponents.CUSTOM_DATA`). `item/BlacksmithItemData.java` (`BritanniaBlacksmithing` compound) already stores material, int quality, **`region_id`/`region_name` rendered as "Origin: …"**, `maker_uuid`/`maker_name` ("Crafted by …"), `recipe_id`, per-instance `instance_id` UUID. Typed DataComponents exist too (`registry/DataComponentRegistry`: WINE_DATA, BANK_CHEQUE_DATA, shrine/banner/dye-tub state).
- Quality scales in force: blacksmithing rolls `quality = exceptional ? 2 : 1` (`skill/BlacksmithCrafting.java:111`); `QualitySwordItem.getQualityName` names 1–4 Crude/Basic/Fine/Exceptional; farming `CropQualityCalculator` uses 0–100. There is **no single project-wide quality enum** — see OPEN_QUESTIONS.
- Metal tiers: `item/UOMetalToolMaterial` enum — iron, gold, shadow_iron, copper, tin, silver, agapite, verite, valorite (RunUO ladder).

### Rails
- `CityCommodity`: unique key `(city_id, category, subcategory, item_name)`; columns quantity, weight, base_price, current_price, max_supply_cap (default 1000), scarcity_markup (0.5), elasticity (1.0). Price formula (model callback `recalculate_price`): `current_price = max(base + base×scarcity_markup×(1−fill_ratio)^elasticity, 1.0)`; price history recorded on change. `commodity_key == item_name` in API JSON. Weight-vs-quantity inventory decided by hard-coded category list (`weight_based_inventory?`). **No saleability/eligibility flag exists.**
- Raw/processed distinction is by subcategory convention (e.g. `grain/whole` vs `grain/milled`, `fish/raw`, `ore/raw` vs `metal/ingot|salvage`) — no dedicated column.
- Treasury: `City has_one :treasury`, `TreasuryBalance` per currency (reserve, coins_outstanding, strength, buy/sell rates). `City#gold_amount` reads `coins_outstanding` of the gold balance. Modern mutation precedent: `GuildTraining::Purchase#credit_treasury` (`balance.increment!(:coins_outstanding, gold)` inside the purchase transaction).
- **Trader sales do NOT debit any treasury** (`Economy::SaleTransactionProcessor`: commodity supply increases, `shard_user.currency` ledger credited, transaction+items recorded — no treasury row touched). **Merchant purchases do NOT credit any treasury**, and Rails accepts the *client-submitted* line price (`submitted_line_price(item, quantity) || default_line_price(...)`, `merchant_purchase_processor.rb:140`). Trader-sale pricing IS Rails-authoritative (`unit_price = commodity.current_price`, metadata `price_authority: "rails"`).
- Idempotency: `transactions.idempotency_key` unique per shard; `SaleTransactionProcessor`/`MerchantPurchaseProcessor` replay by lookup + `ActiveRecord::RecordNotUnique` rescue; wine/salvage sales use a legacy controller branch with its own replay. Spawn ops use `operation_id` + `source_revision` compare (`ServiceNpcSpawnPoints::ApplyOperation`: stale / already_applied / revision_conflict).
- Auth: modern endpoints use `Api::ShardServerAuthentication` (request signatures, nonce store, rate limits — trader/merchant transactions, spawn ops, world state). Legacy `Api::BaseTransactionsController` still uses a static Bearer token (`Setting.get("britannia_api_token")`).
- NPC identity duality: `Npc` (legacy live projection: `npc_id` = loaded entity UUID, `city_name` string, status lifecycle, upserted by `CityDataSync`) vs `WorldNpc` (persistent identity: `public_id`, city/shard FK, immutable name/gender/profession, active/retired) + `NpcSpawnAssignment` (world_npc ↔ `ServiceNpcSpawnPoint`, active/closed, revision) + `ServiceNpcType` (key, profession_key, minecraft_entity_type_key, active, spawnable, definition_revision, `minimum_city_supplies` jsonb, staffing_baseline/increment/max columns).
- Spawn requirements engine (current): `CityStaffing::EconomicEligibility` evaluates `service_npc_types.minimum_city_supplies` (admin-editable, per type; closed key set → city supply columns, with gold/silver/copper mapped to **treasury** amounts). `DesiredStaffing` formula + `SpawnCapacity` + `Plan`/`ApplyPlan`; `Reconcile` runs under `city.lock!`, is **admin-triggered only** (`CityStaffingReconciliationJob` from Admin), and un-staffs below-minimum cities (no hysteresis, owner decision).
- Bootstrap/sync: `world_bootstrap/:shard` (cities with `public_id`, treasury {gold,silver,copper}, market weights/quantities, commodities, service NPC registry + assignments; ETag from deterministic serialization) + `world_state_changes/:shard?from_version=` change log.
- Cities: resolved **by name** in all legacy economy APIs (`City.find_by(name:)`); `cities.public_id` UUID exists and is used by the Service NPC path.
- Legacy Rails catalog endpoints for NPC screens: `products#catalog` (GET, merchant roles — dynamic price from `requirements` × `current_price`) and `products#trader_catalog` (POST — trader buy-side lists with currency conversion); Baker/Tavernkeeper/Costermonger catalogs are built Minecraft-side instead.

## 4b. Currency ladder facts (verified 2026-08-10)

- Minecraft canonical ratios (`economy/CoinConversion.java`, used by merchant checkout, banking,
  and guild training): 1 silver = 100 copper, 1 gold = 100 silver, therefore 1 gold = 10,000
  copper. **Owner-confirmed canonical: Copper 1 / Silver 100 / Gold 10,000.**
- Rails still carries an obsolete 1/10/100 ladder in exactly these places:
  - `db/migrate/20251006205944_treasuries.rb:15` — `base_value` default 1 with comment
    "copper=1, silver=10, gold=100";
  - `app/services/economy/sale_transaction_processor.rb:483-485` — `currency_breakdown`
    fallbacks gold=100 / silver=10 / copper=1;
  - `app/controllers/api/trader_transactions_controller.rb:151-152` — legacy wine/salvage
    branch fallbacks gold=100.0 / silver=10.0;
  - `test/controllers/api/world_bootstrap_profile_test.rb:174` — fixture `base_value = 1` for
    all currencies.
- No seed creates `Currency` rows; `Admin::TreasuriesController` allows admins to edit
  `base_value` by hand, so **live rows cannot be inferred from the migration default**. Live
  values are unverifiable from this environment (peer role reaches only the broken
  `ultimacraft_test*` DBs; the development DB is out of bounds). Verification + a safe data
  migration to 1/100/10,000 + fallback-constant removal + regression tests are scheduled for
  Milestone 3 (blocked on the OQ-1 test-DB repair). Historical migrations are not to be edited.
- Ladder-agnostic consumers that become correct once the data is fixed:
  `Treasury#total_reserve_value`, admin treasury ordering, admin city-form ordering.
  `TreasuryBalance`'s `PAR_COINS_PER_INGOT`/strength math is reserve-vs-coins policy, not the
  inter-denomination ladder.

## 4c. Pinned RunUO reference checkout (Milestone 2 source authority)

- Absolute path: `C:\projects\runuo-reference` (Windows), created 2026-08-10.
- Remote: `https://github.com/runuo/runuo.git` (origin).
- HEAD verified: `71b2794f12eb6f948b1c5598ae8b350401a22d4d` (exactly the pinned commit;
  detached checkout). Read-only reference — never modified, kept separate from the
  implementation worktrees.

## 5. Test inventory relevant to this project (for when the test DB is repaired)
- Rails: `test/controllers/api/trader_transactions_controller_test.rb`, `merchant_transactions_controller_test.rb`, `transactions_legacy_purchase_idempotency_test.rb`, `city_commodities_controller_test.rb`, `npcs_controller_test.rb`, `service_npc_spawn_operations_controller_test.rb`, `world_bootstrap_*`, `world_state_changes_controller_test.rb`, `test/services/city_staffing/*`, plus banking auth/concurrency suites.
- Minecraft unit: `src/test/java/...` (bannerdyeing, bank, tools). Minecraft GameTests: `gametest/ServiceNpc*`, `GuildmasterServiceNpcGameTests`, `WorldStateSync*`, `Banking*` (run via `runGameTestServer`). No Trader/Merchant-specific automated tests exist on the Minecraft side.
