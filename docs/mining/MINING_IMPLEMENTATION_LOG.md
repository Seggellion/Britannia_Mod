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
