# Mining Skill Progression — Implementation Log

Project: UltimaCraft Mining Skill Progression
Governing documents: `MINING_SKILL_DESIGN.md`, `MINING_SKILL_MILESTONE_PLAYBOOK.md` (repository root)

---

## Milestone 0 — Preflight baseline (2026-08-14)

### Repository baseline

| Item | Value |
|---|---|
| Repository root | `C:\projects\britannia\mod\Britannia_Mod` |
| Branch | `patch-18` |
| HEAD | `40fa27d27d4b29f37e9cd7d4a981ef4160a6a518` (`feat(grabby): make the crate family grabbable`) |
| Remote | `origin` → `https://github.com/Seggellion/Britannia_Mod.git` (fetch/push) |
| Worktrees | Single worktree (this checkout, `patch-18`) |
| Working tree at start | Clean except 3 untracked files (below) |

### Pre-existing dirty/untracked files (preserved, not created by this project)

```text
?? MINING_SKILL_DESIGN.md
?? MINING_SKILL_KICKOFF_PROMPT.md
?? MINING_SKILL_MILESTONE_PLAYBOOK.md
```

These are the owner-supplied project documents for this feature. No reset/clean/rebase/merge/push performed.

### Toolchain (from `gradle.properties`, `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`)

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.72 (`neo_version_range=[21.1.72]`) |
| NeoGradle userdev plugin | 7.0.165 |
| Gradle wrapper | 8.9 |
| Java toolchain | 21 |
| GeckoLib | 4.6.6 |
| Parchment mappings | 2024.07.28 (MC 1.21) |
| JUnit | 5.11.4 (junit-bom), junit-platform-launcher |
| Mixin / MixinExtras | 0.8.5 (processor) / 0.3.5 |
| nanohttpd | 2.2.0 (jarJar-ed) |
| Mod id / version / license | `britannia_mod` / 0.1.8 / **All Rights Reserved** |

License note: the mod is All Rights Reserved; RunUO is GPL. RunUO is used strictly as a
behavioral reference. No RunUO source may be copied into this repository.

### Repository guidance reviewed

- No `CLAUDE.md` or `AGENTS.md` exists at the repository root.
- `.claude/settings.local.json` exists (permission allowlist only; no behavioral guidance).
- `docs/known_environment_baseline.md` — build/test gotchas (see Test infrastructure).
- Root design docs for prior projects (Grabby Hands, Bank, Guildmaster, Vendor/Trader,
  Farming, New Assets, Wild Reagents, Managed Vegetation) reviewed for conventions.
- `docs/vendor-trader-economy/*` reviewed for economy identity rules.

### Test infrastructure (verified from `build.gradle` and `docs/known_environment_baseline.md`)

| Suite | Command |
|---|---|
| JUnit unit tests (`src/test`) | `./gradlew.bat test` |
| Compile | `./gradlew.bat compileJava --no-configuration-cache` |
| Full build | `./gradlew.bat build` |
| GameTests (`src/main/.../gametest`, namespace `britannia_mod`) | `./gradlew.bat runGameTestServer --no-configuration-cache` |

Standing gotchas (from `docs/known_environment_baseline.md`, verified 2026-07-26):

1. `:test` reports UP-TO-DATE on unchanged inputs — pass `--rerun-tasks` when a genuinely fresh
   result matters.
2. `org.gradle.configuration-cache=true` is on; pair `--rerun-tasks --no-configuration-cache`
   for `runGameTestServer`/`test`/`build` when verifying changes.
3. Rails repo is WSL-native (`/home/dusti/ultimacraft-website`, branch `banking`); use
   WSL-side tooling for any Rails file access to avoid CRLF corruption.

### RunUO / UOGuide references

Reviewed as behavioral references (mining ladder, `HarvestResource` required/min/max separation,
respawning resource banks). The approved UltimaCraft ladder (Tin replacing Dull Copper at 65.0,
Silver inserted at 55.0) is recorded in the design document and is authoritative.

### Source-authority order (recorded)

1. Owner decisions in `MINING_SKILL_DESIGN.md`
2. Current repository behavior/data
3. Current project documentation
4. RunUO/UOGuide behavioral reference
5. Implementation inference

### Milestone 0 result

- No production code changed. No files modified; only `docs/mining/*` created (M0/M1 deliverables).
- Status: **PASS**

---

## Milestone 1 — Discovery (2026-08-14)

Discovery executed read-only. Full findings in:

- [MINING_SKILL_DISCOVERY_REPORT.md](MINING_SKILL_DISCOVERY_REPORT.md) — system traces (break flow,
  restoration, skill engine, economy, assets, Rails).
- [MINING_MINEABLE_MASTER_CATALOG.md](MINING_MINEABLE_MASTER_CATALOG.md) — complete mineable inventory.
- [MINING_PROGRESSION_GAP_ANALYSIS.md](MINING_PROGRESSION_GAP_ANALYSIS.md) — per-resource classification.

Headline findings (details and evidence in the reports above):

1. Mining break flow lives in `CustomBlockBreakHandler` (NeoForge `BlockEvent.BreakEvent`,
   server-side, cancel-and-replace). **No Mining skill participation exists anywhere today** —
   no gate, no award, no durability consumption.
2. Restoration is `BrokenBlockTracker`/`BrokenBlockDataStorage` (per-level `SavedData`) +
   `BlockRestoreHandler` (server tick, 6 real-time hours, Overworld only, no occupancy check,
   no provenance model).
3. The skill engine is `SkillManager` (Rails-authoritative, 0.1-point gains,
   per-skill `skill_difficulty_modifier` from Rails skill config). Rails already has a
   **Mining** skill row (UoSkillRoster) and a **Miner guildmaster** that teaches it.
4. All nine approved metals already have ore blocks registered; Silver additionally has
   `high_purity_silver_ore`. Custom `gold_ore` and `high_purity_silver_ore` have **no assets**
   (missing blockstate/model/texture).
5. Bronze: Rails seeds a `metal/ingots/bronze` commodity but **no bronze ore commodity**, and the
   mod has no bronze ore block, no bronze ingot item, and no alloy recipe. Model = refined-only
   identity; production path absent.
6. Worldgen is the Rails-driven `/populateores` admin command (`OreVeinFetcher` + six vein
   generator classes). Rails seeds contain only tin (47), iron (133), copper (1) veins — no
   silver veins seeded.
7. Economy identity mismatches were found between mod sale payloads and Rails-seeded
   commodity identities (ore `"Silver ore"` vs `silver`; stone subcategory `blocks` vs
   seeded families; unsellable stone types incl. `Blackrock`, `Deepslate`, custom rocks).

### Files created this milestone

```text
docs/mining/MINING_IMPLEMENTATION_LOG.md
docs/mining/MINING_SKILL_DISCOVERY_REPORT.md
docs/mining/MINING_MINEABLE_MASTER_CATALOG.md
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
```

### Production code changes

None.

### Milestone 1 result

- Status: **PASS** (discovery only; hard stop before Milestone 2)

---

## Milestone 2 — Progression definition and Mining policy layer (2026-08-14)

Goal: encode the approved progression as repository-native definitions **without changing any
block-breaking behavior**.

### Pattern choice

The smallest existing pattern that expresses Mining policy is the authoritative-catalogue
pattern already used by Blacksmithing (`CraftableRegistry`): a classpath JSON file parsed and
validated fail-fast at mod construction, with static lookups. Chosen over block tags (tags cannot
carry per-resource requirement/challenge/identity data) and over a Java-coded registry (the
catalogue must be machine-reviewable and extensible without code changes, design §19). The model
layer is deliberately Minecraft-free so plain JUnit contract tests validate the real shipped data
— the same trick the repository's existing `*ContractTest`s use.

### Files added

```text
src/main/resources/data/britannia_mod/mining/mineables.json   (schema 1; 27 definitions)
src/main/java/com/seggellion/britannia_mod/mining/MineableDefinition.java
src/main/java/com/seggellion/britannia_mod/mining/MineableCatalog.java
src/main/java/com/seggellion/britannia_mod/mining/Mineables.java
src/test/java/com/seggellion/britannia_mod/mining/MineableCatalogContractTest.java   (11 tests)
src/test/java/com/seggellion/britannia_mod/mining/MineableCatalogValidationTest.java (12 tests)
```

### Files modified

```text
src/main/java/com/seggellion/britannia_mod/BritanniaMod.java
  + Mineables.init() beside CraftableRegistry.init()  (parse+validate fail-fast; no behavior)
  + Mineables.validateBlockIdsResolve() in onServerStarting (post-registration reference check)
src/main/resources/assets/britannia_mod/lang/en_us.json
  + message.britannia_mod.mining.{insufficient,skill_unavailable,automation_blocked,unresolved}
docs/mining/MINING_MINEABLE_MASTER_CATALOG.md   (M2 encoding note)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md  (definition layer → COMPLETE; localization → PARTIAL)
```

### What was encoded

- Approved metal ladder **exactly**: Iron 0.0, Silver 55.0, Tin 65.0 (Dull Copper analogue),
  Shadow Iron 70.0, Copper 75.0, Gold 85.0, Agapite 90.0, Verite 95.0, Valorite 99.0.
  No Dull Copper, no Bronze (contract-tested as absent).
- Rock baseline adjusted per discovery: Stone/Cobblestone 0.0, Calcite 5.0 (limestone alias —
  the design's "Limestone 5.0" row, since no limestone block exists), Diorite 10.0,
  Andesite 15.0, Granite 20.0, Tuff 25.0, Deepslate + Cobbled Deepslate 30.0, Basalt/Smooth
  Basalt 40.0, Blackstone 45.0. Quartz-bearing rock omitted (no Overworld mineable form exists —
  catalog C6). Dripstone 35.0 and Obsidian 60.0 encoded as **DEFERRED** (approved values carried,
  but they are not in today's managed set; activation is an explicit Milestone-6 act).
- Custom rocks (managed today, no approved tier existed) got provisional owner-review tiers:
  Igneous 40.0, Metamorphic 30.0, Volcanic 45.0, Glacial 35.0.
- `high_purity_silver` held at Silver's 55.0 with `owner_review` (gap analysis §1 decision open);
  custom `britannia_mod:gold_ore` kept inside the `gold` definition so the duplicate cannot
  drift to its own tier.
- Every definition carries: category (stone/ore), status (active/deferred), `required_mining`,
  `challenge` (seeded = requirement; Milestone 4 calibrates), block ids, drop identity,
  current economy commodity (or null where discovery proved unsellable), restoration
  relationship, owner-review flag, notes.

### Validation implemented

Parse-time (fail-fast, `MineableCatalog`): schema version; duplicate ids; one-definition-per-block;
required/challenge in [0,100]; blank id/display/drop; empty block lists; malformed block ids;
unknown category/status; active-but-unrestorable (impossible combination — would silently change
restoration scope). Server-start (`Mineables.validateBlockIdsResolve`): every catalogued block id
must exist in the block registry (the unresolved-reference check; runs after deferred
registration completes). Unresolved *tags*: not applicable — the chosen pattern uses explicit
block ids, no tags.

### Tests/commands run (all fresh: `--rerun-tasks --no-configuration-cache`)

```text
gradlew test --tests "com.seggellion.britannia_mod.mining.*"   → 23/23 passed
gradlew test (full unit suite, fresh)                          → 2244 tests, 22 failures, 0 errors
```

The 22 failures are **pre-existing and unrelated to Mining**: 21 banner-dyeing geometry-sha256
mismatches (BannerScaffoldToolTest ×12 and six banner integration/closeout suites — approved-hash
manifests vs banner model JSONs, e.g. `tournament_curtain`, `road_guard`,
`silver_and_gold_pennon`) plus 1 `DisplayCaseContractTest` case. Every input those tests read is
a committed file this milestone did not touch (`git status`: only `BritanniaMod.java` and
`en_us.json` modified, +10 lines total), so the failures are properties of HEAD `40fa27d2` —
plausibly fallout of the patch-18 "rescued work" merges. Both mining suites are green inside the
same fresh run. Not fixed here: unrelated feature area, out of milestone scope; flagged to the
owner as standing breakage.

### Explicitly NOT done (by playbook design)

- No break denial wired; no event handler touched; `PickaxeMiningRules`/`BlockBreakUtils` remain
  the live break-flow drivers until Milestone 3.
- No skill award; no gain-rate change; no worldgen/economy/asset changes.

### Milestone 2 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 3 — Server-authoritative Mining break gate (2026-08-14)

Goal: prevent under-skilled players from breaking gated rocks and ores, with side-effect-free
denial, without regressing any allowed path.

### Integration point and ordering

The gate is `MiningGateHandler` on `BlockEvent.BreakEvent` at `EventPriority.HIGH` — the earliest
cancelable server-side point (discovery §1.5). HIGH is load-bearing: `CustomBlockBreakHandler`
mutates the world *inside* its NORMAL-priority listener, so denial must land before NORMAL runs.
Cancellation suppresses both the custom mutation path and the vanilla break (neither receives
cancelled events), which also closes the under-skilled half of the vanilla-tool destruction hole
from discovery §1.3. Existing relative ordering of NORMAL listeners (CustomBlockBreakHandler →
StructureProtectionHandler) is untouched. Commands/worldgen/restoration/explosions/pistons cannot
traverse the gate: none fire `BreakEvent`.

### Policy (mirrors `FarmingCultivationGate`)

Order: catalogue resolution (`Mineables.resolve`, ACTIVE definitions only; empty →
NOT_APPLICABLE, gate abstains) → actor typing (non-`ServerPlayer` / `FakePlayer` → deny,
`automation_blocked`) → admin bypass (`FlowerProtectionService.isAdministrator`; creative
detected from the authoritative **server game mode**, see below; operator = permission ≥ 2;
bypass grants no skill — no award path exists in M3 at all) → skill-data availability
(non-AVAILABLE → deny, `skill_unavailable`) → inclusive threshold `current >= required`
(`mining.insufficient` with current/required/material display name, Farming's action-bar +
formatting conventions). Denial also runs Farming's client-resync convention.

**Creative-detection decision:** the gate reads
`serverPlayer.gameMode.getGameModeForPlayer() == CREATIVE` rather than `Player#isCreative()`.
The server game mode is the same state the vanilla break pipeline consults and matches
`StructureProtectionHandler`'s existing break-path convention; `isCreative()` is an overridable
derived view — `GameTestHelper` mock players hard-code it `true` regardless of game mode, which
both poisoned this milestone's first GameTest run and is the root cause of a **pre-existing**
training-dummy GameTest failure (below). Production behavior is identical for real players.

### Files added

```text
src/main/java/com/seggellion/britannia_mod/mining/MiningBreakGate.java
src/main/java/com/seggellion/britannia_mod/mining/MiningGateHandler.java
src/test/java/com/seggellion/britannia_mod/mining/MiningBreakGateTest.java      (9 tests)
src/main/java/com/seggellion/britannia_mod/gametest/MiningGateGameTests.java    (7 GameTests)
```

### Files modified

```text
src/main/java/com/seggellion/britannia_mod/BritanniaMod.java  (+1 registration, HIGH-priority gate)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md                (gate → COMPLETE; feedback → PARTIAL;
                                                               vanilla-tool risk narrowed)
```

### Tests/commands run (all fresh: `--rerun-tasks --no-configuration-cache`)

```text
gradlew test --tests "com.seggellion.britannia_mod.mining.*"  → 32/32 passed
  (9 gate-policy tests incl. requirement−0.1/exact/+0.1 for every ACTIVE definition and every
   approved metal tier, actor policy, bypass, unavailable-data denial)
gradlew runGameTestServer                                     → 463 GameTests, 1 failure
  Mining's 7 GameTests all passed:
    underSkilledBreakIsCompletelyInert          (block/drops/durability/skill/restoration all zero-delta)
    twoPlayersWithDifferentSkillsSeeDifferentOutcomes (54.9 denied / 55.0 exact-threshold mines;
                                                 one drop, one restore record, no skill movement)
    stoneTrainsAtZeroAndRunsTheManagedFlow
    unavailableSkillDataDeniesEvenWithTheRightTool
    creativeBypassesTheThresholdWithoutGain
    fakePlayersAreDeniedByDefault
    vanillaToolCanNoLongerDestroyGatedOreDroplessly
```

The 1 failing GameTest, `validhitsareperplayerratelimitedanddonotconsumedurability`
(`NewAssetsTrainingDummyGameTests`), is **pre-existing and unrelated**: `attemptStrike` rejects
`isCreative()` players and every GameTest mock player hard-codes `isCreative()==true`, so its
"adventure-mode" strike is rejected deterministically — a property of committed code this
milestone did not touch (training dummy uses `LeftClickBlock`, not `BreakEvent`). Flagged to the
owner as a separate task with the root cause and the fix precedent.

### Explicitly NOT done (by playbook design)

- No Mining award on success (milestone 4); success-path messages remain the existing literals
  (milestone 9); no BreakSpeed pre-gate UX (milestone 9 candidate); no change to what happens
  after an *allowed* break.

### Milestone 3 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 4 — Mining awards and 18,000-activation calibration (2026-08-14)

Goal: connect an authorized Mining action to the existing skill engine exactly once, feed
per-material difficulty, and calibrate field progression to ~18,000 activations for 0.0 → 100.0.

### Award path

`CustomBlockBreakHandler` calls `MiningSkill.awardForBreak(player, state, pos)` at the managed
flow's success boundary — after the resource was extracted and its restoration scheduled, which is
the only point where a real Mining action has demonstrably completed. The award re-evaluates the
break gate and proceeds only on `ELIGIBLE`, so Creative/operator bypass, automation, unavailable
skill data and unmanaged blocks are excluded **by policy rather than by call-site placement**.
Invalid-tool breaks never reach the managed flow at all (design §9.1 excludes them). A
`(player, position, tick)` guard makes duplicate callbacks award nothing.

Award itself is `SkillManager.awardSkillGain(player, "mining", 0.1f)` — the same canonical API
`FarmingSkill` uses, so persistence to Rails, client sync, the cap and the increase message are
all the existing engine's. Nothing accrues between activations, so there is no second Mining
progression ledger.

### Gain model and calibration

The roll lives in `MiningSkill` rather than `SkillManager.trySkillGain` because that path exposes
only a per-skill Rails modifier and has **no per-material input**, while the project requires
RunUO's separation of access requirement from progression difficulty (design §4). Keeping it
mod-side also meant calibration changed no Rails data and no other skill.

```text
chance = BASE_CHANCE × (100 − current)/100 × materialFactor(current, challenge)
materialFactor = 1.0 if challenge ≥ current, else max(0.25, 1 − (current − challenge)/100)
BASE_CHANCE = 0.4252
```

Because `BASE_CHANCE` is a pure multiplier, expected activations scale exactly as
`1/BASE_CHANCE`; the target was hit in one algebraic step from a single measured run
(0.4155 → 18,420 measured → 0.4252 → **18,000**), not by search.

Results (deterministic expected values from the production formula):

| Route | Expected activations 0 → 100 |
|---|---:|
| Recommended (best available tier) | **18,000** (target 17,100–18,900, deviation −0.00 %) |
| Stone only | 64,429 (3.6× worse, but never impossible) |

Full band table and the Guildmaster-training analysis:
[MINING_SKILL_CALIBRATION_REPORT.md](MINING_SKILL_CALIBRATION_REPORT.md).

### Files added

```text
src/main/java/com/seggellion/britannia_mod/mining/MiningSkill.java
src/main/java/com/seggellion/britannia_mod/gametest/MiningSkillGameTests.java      (7 GameTests)
src/test/java/com/seggellion/britannia_mod/mining/MiningProgressionCalculator.java (test-side tool)
src/test/java/com/seggellion/britannia_mod/mining/MiningCalibrationTest.java       (5 tests)
src/test/java/com/seggellion/britannia_mod/mining/MiningSkillTest.java             (3 tests)
docs/mining/MINING_SKILL_CALIBRATION_REPORT.md
```

### Files modified

```text
src/main/java/com/seggellion/britannia_mod/event/CustomBlockBreakHandler.java (+1 award call, +import)
src/main/java/com/seggellion/britannia_mod/gametest/MiningGateGameTests.java
  (one M3 assertion, "milestone 3 must not award Mining on success", deliberately superseded:
   an authorized break may now sit at the threshold or exactly one 0.1 above it)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md (award → COMPLETE; Guildmaster row quantified)
```

The calculator lives in test sources on purpose: it consumes the production `gainChance`, so the
published numbers cannot drift from shipped behaviour, while no unused balance tool ships in the jar.

### Tests/commands run (fresh)

```text
gradlew test --tests "com.seggellion.britannia_mod.mining.*"   → 40/40 passed
  (catalogue 11, validation 12, gate policy 9, calibration 5, activation guard 3)
gradlew runGameTestServer                                      → 470 GameTests
```

Calibration assertions are real acceptance gates: the build fails if the recommended route leaves
the 17,100–18,900 window, if Stone ever stops granting Mining, or if stone-only stops being the
worse path.

### Balance observation surfaced (not a defect)

90 → 100 costs 12,385 of the 18,000 activations (69 %), the last point alone 6,934. That follows
from the `(100 − current)/100` ramp the existing engine already applies to every skill, and matches
UO's punishing final point. Reshaping was not requested; the report documents the one-line
alternative if the owner ever wants a flatter late game.

### Explicitly NOT done

- No global skill-gain change; no Rails data change; no change to what an *allowed* break does
  besides the award; success-path break messages still the existing literals (milestone 9).

### Milestone 4 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 5 — Silver vertical slice (2026-08-14)

Goal: make Silver a real, findable Mining resource connected to its **existing** economy identity,
adding only what discovery proved missing.

### What already existed (re-verified, not re-created)

Block, BlockItem, blockstate/model/texture, block lang, the 55.0 catalogue tier and gate, the
managed break → `PurityOreItem("Silver ore")` drop, `SmallForge`/`LargeForge` refining, the
`silver_ingot` item, `UOMetalToolMaterial.SILVER`, blacksmithing consumption, restoration
eligibility, the `VerticalLayeredVein` generator already routed for `silver` in
`/populateores`, and the Rails `ore/raw/silver` + `metal/ingots/silver` commodities. **None of
this was duplicated.**

### Gap 1 — Silver existed nowhere in the world (worldgen data)

`/populateores silver` had nothing to place: the Rails vein catalogue seeds only tin (47), iron
(133) and copper (1). Added **24 Silver veins** — 18 Britain, 6 Minoc — at radius 30–40 as
vertical sheets (`ZW`, some `XZ`), every coordinate inside the established mining belts and within
~150 blocks of a proven iron/tin vein, `y` following the existing 60/100 convention. Rarer and
narrower than iron (133 @ r60) and tin (47), which is exactly the design's "rarer than common
Iron, more available than the endgame metals". No generator or framework was added.

Delivered as a **separate additive seed**, `db/seeds/silver_ore_veins.rb` + a `db:seed:silver_ore_veins`
task, following the `uo_skills.rb` precedent, because `db/seeds/ore_veins.rb` uses `OreVein.create!`
— appending Silver there would have made re-seeding duplicate all 181 existing veins. The new seed
is idempotent (`find_or_initialize_by` on shard + ore type + position).

### Gap 2 — mined ore could not resolve to its commodity (economy)

The sale payload posted the *display* name (`"Silver ore"`), but Rails seeds `ore/raw` under
`silver`, and because the payload carries category+subcategory Rails performs an **exact** lookup
with no legacy fallback — so mined Silver could never resolve. Added
`CommodityMappings.oreCommodityKey`, mirroring the existing `stoneCommodityKey`, mapping all nine
approved metals to their seeded identities. High-Purity Silver deliberately resolves to plain
`silver`: design §16 forbids a second Silver identity, and the premium already travels as the
`purity` field.

Wiring it also exposed a **real second defect**: `matchesRequest` had no ore branch, so every mined
ore shared one item id and a sale request for Silver could reserve a Valorite stack from the same
inventory. Added the ore branch mirroring the stone one.

### Gap 3 — localization

`item.britannia_mod.silver_ingot` and `item.britannia_mod.purity_ore_item` (the item Silver mining
actually produces) were missing, so both rendered as raw translation keys. Added. The six other
custom ingots and `grade_stone_item` have the same gap and are left to M6 (their own resources).

### Files added

```text
[Rails] db/seeds/silver_ore_veins.rb              (24 idempotent Silver veins)
[Rails] lib/tasks/seed_silver_ore_veins.rake      (db:seed:silver_ore_veins)
src/main/java/com/seggellion/britannia_mod/gametest/SilverMiningGameTests.java   (5 GameTests)
src/test/java/com/seggellion/britannia_mod/mining/SilverVerticalSliceTest.java   (8 tests)
```

### Files modified

```text
src/main/java/com/seggellion/britannia_mod/economy/CommodityMappings.java
  (+SUPPORTED_ORE_COMMODITIES, +oreCommodityKey)
src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java
  (ore sale payload posts the seeded identity; sale matching now discriminates by metal)
src/main/resources/assets/britannia_mod/lang/en_us.json  (+2 keys)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md           (Silver → COMPLETE; ore identity → RESOLVED vs seeds)
```

### Tests/commands run

```text
gradlew compileJava compileTestJava                            → SUCCESS
gradlew test --tests "com.seggellion.britannia_mod.mining.*"   → 48/48 passed
gradlew runGameTestServer (fresh)                              → see closeout
ruby -c on both new Rails files                                → Syntax OK, zero CR bytes (clean LF)
```

Coverage against the playbook's M5 list: generation/placement (vein generator GameTest), 54.9
denial, 55.0 success, correct drop identity + purity range, correct refining (GameTest, where
registries are live), correct material identity, restoration + save/load round trip, economy
mapping (unit).

One test-placement correction was needed mid-milestone: `UOMetalToolMaterial` cannot initialize
without the registry bootstrap, so the refining-chain assertions moved from the unit test into
`SilverMiningGameTests` rather than being weakened.

### Not done / deferred (explicit)

- **Silver ingot art** — still the vanilla `minecraft:item/iron_ingot` reference shared by all seven
  custom ingots. Authoring original art is an owner/asset task; copying or recolouring Mojang's
  texture into an All-Rights-Reserved mod is not appropriate. Pinned by a test so it cannot be
  mistaken for finished.
- **High-Purity Silver** — still asset-less, unrefinable and ungenerated; owner decision (premium
  tier vs retire) unchanged. Its economy identity now at least reuses `silver` rather than dangling.
- **Loot tables for custom ore blocks** — a vanilla-tool break of Silver still destroys it with no
  drop; cross-cutting for all nine ores (M6/M7).
- **Rails commit** — the two Rails files are written but **uncommitted**; that repository also holds
  unrelated in-progress work, and its dev database was unreachable, so Rails tests could not run and
  live vein/commodity state could not be verified.

### Milestone 5 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 6 — Ore and rock gap completion (2026-08-14)

Goal: fill the content gaps discovery found, and finish the data-driven mandate so a future rock
is data rather than code.

### 6.1 One source of truth for "what is mineable" (the architectural gap)

The catalogue drove the **gate**, but the **managed flow** still had its own hard-coded idea of
the managed set (`PickaxeMiningRules`) and of what each block yields (`BlockBreakUtils`). Two
sources of truth that could drift, and three Java edits to add one rock — exactly what design §19
forbids. Both now delegate to the catalogue:

- `PickaxeMiningRules.isAllowedMineableBlock/Stone/Ore` → `Mineables.resolve` + category.
- `BlockBreakUtils.deduceOreType/deduceStoneType` → the definition's `drop` field.
- Removed the dead `ORE_TYPES` map, which was unreferenced and carried a misleading
  `dull_copper` entry contradicting an owner decision.

Behaviour is unchanged: the contract test already pinned the catalogue's ACTIVE block set to
exactly the list those chains carried, and a new test pins all 29 drop names byte-identical to the
chain they replaced (they are the identity refining and the economy both key on).

`Mineables.resolve` now uses an identity-keyed `Map<Block, MineableDefinition>` built once after
registration — the `FarmingSkillRequirementResolver` pattern — because it moved onto the mining hot
path (`getDestroySpeed`/`isCorrectToolForDrops` run every tick while digging) where per-call
registry-key string allocation would have been a real regression.

### 6.2 Dripstone activated — as data only

With one source of truth, activating an approved rock is a JSON edit. Dripstone is now ACTIVE at
its approved **35.0**, with no Java change anywhere; the contract test's expected managed set was
updated deliberately (it is the only intentional widening so far).

### 6.3 Obsidian deliberately still deferred

Obsidian is the one baseline rock requiring a **diamond-tier** pickaxe in vanilla, and the managed
flow removes the block itself without consulting tool tier — so activating it would let the
iron-tier Britannia pickaxe harvest Obsidian, bypassing a vanilla tool rule design §11 explicitly
protects. Activation needs a tool-tier check in the managed flow first: a gameplay change, not a
data edit. Recorded in the catalogue entry and pinned by test.

### 6.4 Vein data for the rest of the ladder

Five approved metals still had **zero** veins, so the entire upper ladder was unreachable in a real
world; Copper had exactly one. Added `db/seeds/colored_metal_ore_veins.rb` (+ rake task), idempotent
like the Silver seed, with scarcity falling as the requirement rises:

| Metal | Req | Veins added | Radius |
|---|---:|---:|---|
| Shadow Iron | 70.0 | 12 | 30–35 |
| Copper | 75.0 | 14 (+1 existing) | 30–40 |
| Gold | 85.0 | 10 | 25–30 |
| Agapite | 90.0 | 7 | 20–25 |
| Verite | 95.0 | 5 | 18–22 |
| Valorite | 99.0 | 3 | 15–18 |

51 veins total, against Iron 133 / Tin 47 / Silver 24. Every type routes to the generator
`PopulateOresCommand` already assigns it; none were added or changed.

### 6.5 Asset and localization gaps

- `britannia_mod:gold_ore` and `high_purity_silver_ore` were registered, reachable from the creative
  tab, and had **no blockstate/model** — they would render as missing-model blocks. Both now ship
  assets. Gold references the vanilla gold-ore texture (a reference, the same approach the existing
  ingot models already take — Mojang art is not copied into this All-Rights-Reserved mod);
  High-Purity Silver reuses the mod's own silver ore texture.
- Added the six remaining custom ingot lang keys plus `grade_stone_item`, which all rendered as raw
  translation keys. Every mineable-derived item a player can hold now has a display name.

### Files added

```text
[Rails] db/seeds/colored_metal_ore_veins.rb        (51 idempotent veins)
[Rails] lib/tasks/seed_colored_metal_ore_veins.rake
assets/britannia_mod/blockstates/{gold_ore,high_purity_silver_ore}.json
assets/britannia_mod/models/block/{gold_ore,high_purity_silver_ore}.json
assets/britannia_mod/models/item/{gold_ore,high_purity_silver_ore}.json
```

### Files modified

```text
util/PickaxeMiningRules.java   (delegates to the catalogue)
util/BlockBreakUtils.java      (drop names from the catalogue; dead ORE_TYPES removed)
mining/Mineables.java          (identity-keyed lookup for the hot path)
data/britannia_mod/mining/mineables.json  (Dripstone → active; Obsidian deferral rationale)
assets/britannia_mod/lang/en_us.json      (+7 keys)
test .../MineableCatalogContractTest.java (drop-name pinning, Dripstone activation)
test .../MiningBreakGateTest.java         (Dripstone gating at 34.9/35.0)
test .../SilverVerticalSliceTest.java     (asset + ingot localization coverage for every ore)
```

### Tests/commands run

```text
gradlew test --tests "com.seggellion.britannia_mod.mining.*"   → 52/52 passed
gradlew runGameTestServer (fresh)                              → All 475 required tests passed
gradlew test (full suite, fresh)                               → 2273 tests, 0 failures, 0 errors
ruby -c on both new Rails files                                → Syntax OK, 0 CR bytes
```

#### An unrelated flake investigated and cleared

Two GameTest runs during this milestone failed on `spinningAndWeavingAreExactAndRejectSpidersSilk`
(textiles). It was chased to a conclusion rather than assumed unrelated:

| Run | Tree | Result |
|---|---|---|
| 1–2 | full M6 set | FAIL |
| 3 | clean HEAD (M6 stashed) | PASS |
| 4 | data/assets only, Java reverted | PASS |
| 5 | + delegation trio restored | PASS |
| 6 | **full M6 set again (identical to runs 1–2)** | **PASS** |

Run 6 is byte-identical to the runs that failed, so the test is **nondeterministic**, not broken by
Mining. Root cause found: the test counts every `BALL_OF_YARN` item entity within 2 blocks of its
mock player, but `makeMockServerPlayerInLevel()` spawns mock players at the **world spawn** shared
by every concurrent test, and `run/gametest/world` **persists between runs** — so the yarn that
`TextileProcessing` drops when the inventory is full accumulates across runs until the sum exceeds
one, then ages out again. Filed as a separate task with the evidence; no Mining code was changed to
accommodate it, and no test was weakened.

### Not done / deferred (explicit)

- **Obsidian** — see §6.3; needs a tool-tier gate in the managed flow.
- **Custom-ore loot tables** — a vanilla-tool break of a custom ore still destroys it with no drop.
  Unchanged and still cross-cutting; belongs with the restoration/provenance work in M7.
- **Unsellable rocks (E4)** — Deepslate, Cobbled Deepslate, Blackrock, Dripstone and the four custom
  rocks still have no stone commodity. Economy identity work is M8.
- **Custom rock worldgen** — igneous/metamorphic/volcanic/glacial still have no placement path;
  they remain creative/structure-only. Not required by the approved progression.
- **Silver ingot art** — unchanged owner/asset task.

### Milestone 6 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 7 — Renewable restoration integration (2026-08-14)

Goal: prove every skill-managed node cooperates with the existing restoration system, and close the
place-break loop — which milestone 4 had turned from an item exploit into a *skill* exploit.

### 7.1 Provenance — the smallest model that works

Design §13 requires the place-break loop not to be farmable and warns against inventing "a large new
global block-provenance database". `GrabbyProvenance` was examined first, as the design instructs,
but it rides on a **block entity** and mineables are plain blocks, so it could not be reused
directly. Its principle was kept and inverted:

| | Grabby | Mining |
|---|---|---|
| Default when nothing is stored | `WORLD` (protected) | **natural** (mineable) |
| Why | scenery must not be movable | natural blocks are the overwhelming majority and must cost nothing |

`MiningProvenance` is a per-level `SavedData` holding player-placed positions as a packed
`long[]`. Consequences that fall out of "absence = natural": **existing saves need no migration**,
world generation and structures are natural for free, and the set only ever holds *standing player
construction* because entries are removed the moment such a block is broken.

`MiningProvenanceHandler` marks on `BlockEvent.EntityPlaceEvent` — only for real players (fake
players excluded, as everywhere else in this project) and only for catalogued mineables, so placing
dirt or a chest costs nothing. Placement is judged by *who placed it*, not game mode: a
Creative-built granite wall is construction, not a deposit.

A player-placed mineable resolves **NOT_APPLICABLE**, which yields the whole behaviour in one
stroke: no gate, no managed drop, no restoration, no award — and, importantly, **a player can always
dismantle their own construction** regardless of skill. That last point was a real latent problem
introduced in M3: the gate is block-type based, so before this a 0-skill player could place granite
(20.0) or deepslate (30.0) and then be unable to break it.

**Ordering is load-bearing.** The marker is cleared in a separate `EventPriority.LOWEST` listener,
never in the HIGH-priority gate: every reader — gate, managed flow, award — must still see it while
deciding. Clearing it early would hand the exploit straight back. This was caught during
implementation and is now pinned by a source contract as well as by GameTests.

### 7.2 Restoration corrections

Two ways the existing scheduler did not yet cooperate, both fixed inside the **one existing timer**
(no second scheduler, no force-loading — both pinned by test):

- **Every dimension.** Records are stored per level, but the loop read only `Level.OVERWORLD`, so a
  node mined anywhere else was scheduled and never restored. Harmless before Mining; load-bearing
  once Basalt and Blackstone — Nether-native blocks — became managed resources.
- **Never overwrite what is standing there.** Restoration wrote the block back unconditionally,
  which could delete a player's construction or materialise stone inside a player or their animals.
  It now restores only into a genuinely free, unoccupied cell and otherwise waits — the "wait when
  the target is occupied" policy design §12.2 asks for. Unloaded cells are still retried later
  rather than force-loaded.

### Files added

```text
src/main/java/com/seggellion/britannia_mod/mining/MiningProvenance.java
src/main/java/com/seggellion/britannia_mod/mining/MiningProvenanceHandler.java
src/main/java/com/seggellion/britannia_mod/gametest/MiningRestorationGameTests.java   (9 GameTests)
src/test/java/com/seggellion/britannia_mod/mining/MiningRestorationPolicyTest.java    (7 tests)
```

### Files modified

```text
block/blockrestore/BlockRestoreHandler.java  (all dimensions; occupancy guard; per-level dirty flag)
mining/MiningBreakGate.java                  (position-aware evaluate)
mining/MiningGateHandler.java                (position-aware gate + LOWEST-priority marker clearing)
mining/MiningSkill.java                      (award uses the position-aware gate)
event/CustomBlockBreakHandler.java           (player-placed blocks fall through to vanilla behaviour)
BritanniaMod.java                            (+1 handler registration)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
```

### Tests/commands run (fresh)

```text
gradlew compileJava                                            → SUCCESS
gradlew test --tests "com.seggellion.britannia_mod.mining.*"   → 59/59 passed
gradlew runGameTestServer                                      → All 484 required tests passed
gradlew test (full suite)                                      → see closeout
```

Playbook §M7 coverage: insufficient-skill denial schedules nothing ✓; one restore per successful
break ✓; original state returns correctly ✓; save/restart ✓; chunk unload (retry, no force-load) ✓;
occupied target ✓; target replaced with another block ✓; duplicate schedule ✓; neighbouring
simultaneous mines ✓; two players ✓; old-save compatibility ✓ (absence = natural, no migration);
Stone/rock restoration scope ✓; place-break exploit ✓ (through the real placement path).

### Known limitations

- A cell a player builds on permanently keeps its pending record waiting forever. Deliberate: the
  alternative is discarding the node, and waiting is the non-destructive choice. Worth an eventual
  expiry policy if ledgers grow.
- Provenance entries for blocks destroyed by explosions or other non-`BreakEvent` routes are not
  cleared, so a stale marker can linger. It only ever errs toward "treat as construction", i.e.
  denying a gain, never granting one.

### Milestone 7 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 8 — Economy, refining and crafting compatibility (2026-08-14)

Goal: make Mining's outputs actually land in the existing economy, without duplicating commodities
or inventing prices.

### 8.1 Every stone sale was failing (E2)

Rails performs an **exact** lookup on `category + subcategory + item_name` whenever a payload
carries a category and a subcategory — with no legacy fallback. The mod posted the literal
subcategory `"blocks"`, and `CommoditySeeder` seeds stone by **material family**:

```text
rubble/cobblestone · common/stone · igneous/{andesite,diorite,granite}
volcanic/{tuff,basalt,blackstone} · sedimentary/limestone · mineral/quartz
```

No seeded row has ever used `blocks` (verified against the seeder: zero occurrences), so every
stone sale raised `MissingCommodity`. Fixed at all three sites that posted it — the sale payload,
the trader listing, and the commodity table `forStack`/city-inventory path both read — from one new
`STONE_COMMODITY_FAMILIES` map that is now also the source of the supported-stone set, so the two
cannot drift apart again. The identity key changed from `stone|blocks|basalt` to
`stone|volcanic|basalt`, which is what Rails parses and looks up.

### 8.2 Blackrock reached no commodity (E4, partly)

Blackstone's managed drop is named `"Blackrock"`, which matched no commodity even though Rails
seeds `volcanic/blackstone`. Mapped the name rather than renaming the drop, so stacks players
already mined stay valid. The catalogue's `blackstone` entry now declares its commodity — a change
the new contract test forced, having caught the data saying "no commodity" while the code resolved
one.

### 8.3 The rest of the loop, verified

`MiningEconomyGameTests` proves the full chain for **all nine** approved metals, not just Silver:
each mined drop name refines through the forge's exact resolution into its **own** ingot (no two
metals share one), and each of those ingots is resolved back by the blacksmith's own
`getMaterialByIngot`, so mine → refine → craft closes. The metal roster is pinned at nine, and the
retired High-Purity variant is asserted to resolve to no metal of its own.

### Deliberately not done

- **No new commodities, no invented prices.** Deepslate, Cobbled Deepslate, Dripstone and the four
  custom rocks have no commodity in Rails at all. Creating one means choosing a price, which design
  §16 reserves for the owner; the convention-derived proposal is recorded below rather than applied.
- **Stone still yields cobblestone (E3).** Mining Stone produces rubble, so the seeded `stone`
  commodity receives no mined supply. That is long-standing behaviour, not a bug introduced here,
  and changing the drop name would change refining and economy identity together — an owner call.
- **Live Rails DB still unverified.** Its credentials remained unavailable all session, so these
  fixes are proven against the seeded identities, which are the version-controlled truth.

### Owner decisions available (proposals, not applied)

| Question | Convention-derived proposal |
|---|---|
| Should Deepslate / Cobbled Deepslate sell? | `stone/volcanic/deepslate`, base 2.0–2.5 (beside tuff 2.0 and basalt 2.5) |
| Should Dripstone sell? | `stone/sedimentary/dripstone`, base ~3.0 |
| Should the four custom rocks sell? | one `stone/*` row each; they also still have no worldgen path, so they may be scenery rather than resources |
| Should mining Stone yield Stone rather than Cobblestone? | would give the seeded `stone` commodity (2.0) a supply source; changes drop identity |

### Files added

```text
src/main/java/com/seggellion/britannia_mod/gametest/MiningEconomyGameTests.java  (3 GameTests)
src/test/java/com/seggellion/britannia_mod/mining/MiningEconomyIdentityTest.java (8 tests)
```

### Files modified

```text
economy/CommodityMappings.java     (stone families as one source of truth; Blackrock alias;
                                    identity key carries the real subcategory)
economy/ServerEconomyService.java  (stone sale posts the seeded family)
npc/TraderRoleHandler.java         (trader listing posts the seeded family)
data/britannia_mod/mining/mineables.json  (blackstone declares its commodity)
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
```

### Milestone 8 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 9 — Feedback, UI synchronization and administration (2026-08-14)

Goal: make Mining understandable without moving any authority to the client.

### 9.1 Held-click denial no longer repeats itself

A denied dig re-completes every few ticks while the button is held, so the same action-bar line was
being rewritten continuously. Denials are now throttled per player: an identical message waits out
a 40-tick (2 s) cooldown, while a **different** message — a tougher ore, a changed requirement —
appears immediately, because that is new information rather than noise. A rewound clock cannot mute
feedback permanently.

The throttle state rides on the player's own persistent data, the same place
`TrainingDummyService` keeps its cooldown, so it cannot leak when players log out. The decision
itself is a pure function (`shouldSendDenial`) and is unit-tested without a player or a world.

Feedback remains on the **action bar** and never in chat — asserted, so it cannot regress into spam.

### 9.2 Operator diagnostics: `/mining` (read-only)

`/mining debug` answers, for the block being looked at, exactly the questions an operator has when
someone reports "I can't mine this":

```text
block=…, player_placed=…                     (is this construction rather than a deposit?)
resource=…, category=…, required_mining=…    (what is it, and what does it demand?)
drop="…", economy_commodity=…, restorable=…  (what does it yield, and can it be sold?)
gate=… (would break / would be denied), your_mining=…, required=…
restoration: owes=…, broken_by=…, due_in=…, cell_free=…
```

Plus `/mining skill [player]` (value **and** data state, so "denied because Rails has not answered
yet" is visible) and `/mining restorations` (pending, due-now, and how many are waiting on an
occupied cell, per dimension, plus the provenance count).

It is operator-gated at permission 2 and **mutates nothing** — no skill write, no restoration
trigger, no provenance write — so running it can never change the situation it describes. A test
enumerates the mutators it must never call. Skill changes remain `/setskill`'s job.

### 9.3 Verified, not assumed

- **Threshold crossing is immediate.** A GameTest denies at 54.9, raises the skill mid-session, and
  the very next attempt succeeds — no reconnect, no relog. The gate re-reads the authoritative
  snapshot on every attempt, so there is nothing to invalidate.
- **No eligibility on ItemStacks.** Asserted across the Mining sources: nothing writes
  `DataComponents`/`CustomData`. Caching eligibility onto a stack would both leak a skill oracle to
  the client and delay threshold crossings until the stack changed.
- **Client sync is the existing one.** Mining adds no payload; gains reach the client through
  `SkillManager`'s existing `SkillSyncPayload`, which already fires on every gain.
- **Creative/operator behaviour** is unchanged from M3 and still covered by its GameTests.
- **Messages carry the numbers.** The insufficient-skill string is asserted to interpolate current,
  required and material, per design §2.4.

### Files added

```text
src/main/java/com/seggellion/britannia_mod/commands/MiningDebugCommand.java
src/test/java/com/seggellion/britannia_mod/mining/MiningFeedbackPolicyTest.java   (8 tests)
```

### Files modified

```text
mining/MiningBreakGate.java                  (denial throttle + pure shouldSendDenial)
block/blockrestore/BlockRestoreHandler.java  (RESTORE_DELAY exposed so tooling reports the real value)
registry/CommandRegistry.java                (+1 command registration)
gametest/MiningGateGameTests.java            (+1 immediate-threshold GameTest)
```

### Milestone 9 result

- Status: **PASS** — see closeout in the milestone report.

---

## Milestone 10 — Automated regression, runtime QA and closeout (2026-08-15)

Goal: prove the feature as an integrated system.

### 10.1 Calibration re-verified against final production values

The catalogue changed after milestone 4 (Dripstone activated, High-Purity Silver retired), so the
progression target was recomputed from the shipped data rather than assumed to still hold:

```text
TOTAL 0-100   recommended 18,000   stone-only 64,429
target window 17,100 - 18,900; deviation -0.00%
```

Unchanged, because the recommended route follows the hardest available tier and neither change
moved the ladder's maxima. `MiningCalibrationTest` fails the build outside the window, so this
cannot drift silently.

### 10.2 Integration coverage added

- `MiningLadderGameTests.everyTierDeniesJustBelowAndMinesAtItsRequirement` — the entire approved
  ladder in a running world: for each of Stone, Iron, Silver, Tin, Shadow Iron, Copper, Gold,
  Agapite, Verite and Valorite, one tenth below the requirement is denied and inert, and the
  requirement exactly mines and schedules its restoration. The unit suite already pinned the
  policy; this proves the same thing end to end through a real break.
- `stoneRemainsTrainableAtEverySkillLevel` — Stone works at 0.0, 50.0 and 99.9.
- `playerPlacedProvenanceIsPersisted` — the provenance marker is written to disk, so the
  place-break loop cannot reopen on the next boot.
- Asset integrity now also resolves the **textures** each block model names, not just the model
  files, so a model pointing at art nobody shipped fails the build instead of rendering as the
  missing-texture checkerboard in game.

### 10.3 Documentation completed

Added [MINING_SKILL_IMPLEMENTATION_STATUS.md](MINING_SKILL_IMPLEMENTATION_STATUS.md) (closeout
summary, acceptance criteria, carried risks, and the owner-run live acceptance runbook) and
[MINING_SKILL_TEST_MATRIX.md](MINING_SKILL_TEST_MATRIX.md) (every requirement mapped to the test
that enforces it, including what is deliberately owner-run).

### 10.4 The one failure seen, and what it was

A full GameTest run reported `spinningAndWeavingAreExactAndRejectSpidersSilk` failing — the
textile flake root-caused during milestone 6 and filed as a separate task. It is unrelated to
Mining: the test counts every yarn item entity near its mock player, mock players spawn at the
**shared world spawn**, and `run/gametest/world` persists between runs, so dropped yarn accumulates
until the count exceeds one. Resetting that disposable world clears it -- confirmed: the same suite that
reported the failure passed **491/491** immediately after `run/gametest/world` was deleted, which
is the accumulation hypothesis verified rather than assumed. No Mining code was changed
on its account and no assertion was weakened.

### Milestone 10 result

- Status: **PASS** for everything automatable here. Live two-client acceptance and live Rails
  verification remain owner-run and are documented rather than claimed.

---

## Owner decisions — 2026-08-14 (answers to the open M10 questions)

Both questions that had been carried as "open for M10 sign-off" are now answered by the owner.
They are recorded here as project decisions and were applied the same day.

### Decision 1 — High-Purity Silver is retired: there is exactly one Silver metal/ore

> *"I'm not certain about high-purity silver, that could be removed — there should only be one
> silver metal/ore."*

The principle is settled (one Silver metal/ore); the removal itself was hedged, so it was applied
as a **soft retirement** rather than a registration deletion:

| Change | Rationale |
|---|---|
| Removed from the Mining catalogue | It can no longer be gated, mined, awarded, or restored — it is not a Mining resource |
| Removed from the Ores creative tab | Not offered to players, so no new ones enter a world |
| Removed from `/populateores` | It never had a generator case, so the command could only ever place zero anyway |
| **Block/item registration kept** | Deleting a registration breaks worlds that already contain the block. Retirement is fully reversible |
| **Assets kept** | The block still exists, so it must still render rather than become a missing-model block |
| **Economy mapping kept** (`High-Purity Silver ore` → `silver`) | Players may hold mined stacks from before; they keep selling as ordinary Silver instead of becoming unsellable — which is also what "one Silver identity" means economically |

Hard removal of the registration remains available on request; it is the only step not taken.

### Decision 2 — Obsidian is not a Mining resource

> *"There is also no use for Obsidian tools. (not a part of Ultima Online)."*

Obsidian was carried as a DEFERRED 60.0 entry from the design's proposed rock baseline. It is not
an Ultima Online material and yields no tool material, so it has been **removed from the catalogue
entirely**. This also retires the blocker recorded in M6 §6.3: no tool-tier check needs to be built
in the managed flow for Obsidian's sake, and vanilla Obsidian behaviour is left completely
untouched.

### Effect on the catalogue

25 definitions, **all ACTIVE** — nothing is deferred and nothing is pending an owner decision. The
`deferred` status remains supported by the schema for future use. Both retirements are pinned by
tests (`retiredResourcesAreAbsentFromTheCatalogue`, `retiredResourcesNeverReachTheGate`, and a
GameTest asserting exactly one Silver metal exists), so neither can silently return.

Files touched: `mineables.json`, `CreativeTabRegistry`, `PopulateOresCommand`,
`MineableCatalogContractTest`, `MiningBreakGateTest`, `SilverVerticalSliceTest`,
`SilverMiningGameTests`, plus the gap analysis.
