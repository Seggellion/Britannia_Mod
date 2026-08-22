# Milestone 2 - Canonical Data-Driven Resource Definitions

**Project:** UltimaCraft OreVein Remediation
**Date:** 2026-08-20
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**M1 commit:** `61a359d016dc9636f8ef5a546a6125f8e09c773c`
**Status:** complete, uncommitted, awaiting review

---

## Objective

Establish one canonical, validated, data-driven resource-definition seam capable of carrying the
existing resources, clay, silica, and eventually 25-50 managed geological resources — without
duplicating any authority `MineableCatalog` already holds, and without adding a third catalogue
beside the two that existed.

---

## Findings

### 1. `ManagedDeposit` evolved cleanly. It was the right seam.

Nothing obstructed it. That record already carried the two things the Mining half could not express
— an extraction **tag** and a configured yield — and everything M2 added (depleted state,
regeneration duration, generation config, a Mining reference) fits the same value-type shape. It has
become `ResourceDefinition`, `ManagedDeposits` is now a **view** (`family == SEDIMENT`) over the one
catalogue, and `ManagedDepositExtraction` was touched in exactly two lines. Its ordering — resolve,
house rights, tool, record, mutate, yield — is byte-for-byte the same.

The migration is genuinely an evolution and not a parallel system: `ManagedDeposit.java` is deleted,
not deprecated, and there is no code path that reads deposit configuration from anywhere but
`resources.json`.

### 2. The completeness check had to be split out of `parse()`

The strongest anti-fallback rule — *every ACTIVE mineable must be claimed by exactly one resource* —
made `ResourceCatalog.parse()` unusable for test fixtures, because a one-entry fixture is not a
broken catalogue. It is now `validateCoversEveryActiveMineable()`, called by `instance()` (so the
running game still cannot boot with an unconfigured mineable) and driven directly by tests. Without
that split, the milestone's central claim — that a new resource is describable in data alone — could
not have been demonstrated.

### 3. Two guards make "two resources sharing one Mining requirement" unrepresentable

Attempting it fails either as a duplicate block claim or as a block-set mismatch against the
mineable, depending on how it is spelled. There is no third arrangement. The
`validateNoMineableIsClaimedTwice` guard is therefore defence in depth rather than the load-bearing
one; the load-bearing rule is that a resource must cover **exactly** the blocks its mineable claims.
Pinned in both spellings.

### 4. Tag naming: kept the repository convention rather than the playbook's sketch

The kickoff suggested `britannia_mod:tools/extracts/clay`. The established convention, documented in
`ModTags` itself, is flat and named for the job — `clay_shovels`, `silica_shovels`,
`root_crop_shovels`, `grain_harvest_blades`, `skinning_knives` — and the two silica/clay tags already
exist and are already resource-specific. Migrating them to a nested path would rename two live tag
files, contradict the stated naming rationale, and gain nothing functionally.

So the existing tags are kept and one is added on the Mining side: **`britannia_mod:mining_pickaxes`**.
The requested property is fully satisfied — each definition names its tag per resource, so a future
resource-specific or upgraded pickaxe is a data change. This was the instruction to "inspect existing
tag conventions and choose the actual paths accordingly"; flagging it because it differs from the
example given.

### 5. The Mining tag migration changed no behaviour, provably

`britannia_mod:pickaxe` is the **only** registered `QualityToolItem` and the only registered
`BritanniaPickaxeItem` — `BritanniaPickaxeItem` is never registered at all. So the M1 predicate
`instanceof QualityToolItem || instanceof BritanniaPickaxeItem` admitted exactly one item, and the
tag holding exactly that item admits exactly the same set. The migration is a change of mechanism,
not of coverage.

### 6. Per-resource regeneration activated without touching the M4 scheduler

`BlockRestoreHandler` resolves the delay from `data.originalState` — the state already stored in
every restoration record — rather than from a new field. The saved-data format is untouched, debts
already on disk keep working, and no migration is needed. Silica's 24 hours is live; everything else
resolves six. The scheduler is still the single existing pre-tick sweep.

### 7. One cosmetic resource-name switch remains, out of scope

`ClientEventHandler:140-144` switches on metal name for a client-side particle scale, and
`ClientModSetup` does the same for tint colours. These are rendering lookups, not resource
definition, generation, extraction or regeneration. No resource-name branching remains anywhere in
`commands`, `features`, `mining`, `deposit`, `resource`, `event` (server) or `block`.

---

## Canonical ownership model

| Concern | Canonical authority | Notes |
|---|---|---|
| Resource identity | `resources.json` → `ResourceDefinition.id` | 28 definitions. Deposit ids kept as `britannia_mod:clay_deposit` / `…silica_sand_deposit` because housing material data references them as feedstock. Ore paths kept as the legacy Rails ore-type names. |
| Mining requirement / challenge | **`mineables.json` alone** | `ResourceDefinition` carries no Mining number and no field that could hold one — pinned reflectively by `noResourceDefinitionCarriesAMiningNumber`. Definitions reference a mineable id; the reference is validated for existence, ACTIVE status, category agreement, and **exact block-set equality**. |
| Which blocks a resource governs | Both, and they must agree exactly | Neither catalogue can gain or lose a block for a resource without the other; mismatch fails the load. |
| Extraction tag | `resources.json` → `extraction_tool` | `britannia_mod:mining_pickaxes` for stone and ore; `britannia_mod:clay_shovels` / `britannia_mod:silica_shovels` for the beds. Resolved through `Resources.isAuthorizedTool` by both families. |
| Output / yield | `resources.json` → `yield` | Three modes: `purity_ore` (ORE), `graded_stone` (STONE), `item` (SEDIMENT). Mode is validated against family, so a family cannot pair with a yield nothing knows how to make. The `purity_ore` / `graded_stone` **producers** still live in `CustomBlockBreakHandler` — see technical debt. |
| Regeneration policy | `resources.json` → `regeneration.hours` | Live: `BlockRestoreHandler` resolves per record. Silica 24 h, everything else 6 h. |
| Shape / generation config | `resources.json` → `generation` | Shape id, block placed, configured min/max radius, for the 9 placeable ores. |
| Shape crash boundary | `ResourceShape.hardFloorRadius()` | A property of the algorithm, not tuning. Data may narrow the safe range, never widen it below the floor. |
| Depleted state | `resources.json` → `depleted` | One mode, `fluid_aware_air`, reproducing existing behaviour. Consumed by both extraction paths via `Resources.depletedState`. |
| Managed-resource block resolution | `Resources.resolve(BlockState)` | Identity map, built once after registration. |
| Sediment-vs-Mining routing | `ResourceDefinition.Family` | `ManagedDeposits` = the SEDIMENT view; Mining = ORE + STONE. |

---

## Changes

| File | Change | Reason |
|---|---|---|
| `resource/ResourceDefinition.java` **(new, 171)** | The canonical record: id, display name, family, blocks, mineable reference, extraction tag, yield, depleted state, regeneration, generation, revision. Pure Java. | `ManagedDeposit` grown up. Registry ids as strings so JUnit validates the real data without booting Minecraft, exactly as `MineableDefinition` does. |
| `resource/ResourceCatalog.java` **(new, 414)** | Classpath JSON loader, fail-fast, with duplicate/reference/consistency validation and a separable completeness check. | The single authority. Same shape as `MineableCatalog` per the M0 decision; nothing in M2 argued against classpath JSON. |
| `resource/ResourceShape.java` **(new, 60)** | The six shapes and each one's hard floor radius. | Moved out of `VeinPlacementValidation` so the resource package owns shape identity and `features` depends on it, not the reverse. |
| `resource/Resources.java` **(new, 185)** | Minecraft adapter: block resolution, tag resolution, authorization, yield stacks, depleted state, regeneration lookup, and two server-start validators. | Mirrors `Mineables`. Keeps every Minecraft type out of the catalogue. |
| `data/…/resources/resources.json` **(new, 28 defs)** | 9 ore, 17 stone, 2 sediment. Generated from `mineables.json` plus the M1 shape table, so the migration cannot drift by transcription. | The data. |
| `data/…/tags/item/mining_pickaxes.json` **(new)** | `["britannia_mod:pickaxe"]` | The Mining side's extraction tag — the pickaxe's half of the arrangement clay and silica already had. |
| `util/ModTags.java` | `MINING_PICKAXES`. | Java handle for the new tag, following the flat job-named convention. |
| `deposit/ManagedDeposit.java` | **Deleted.** | It became `ResourceDefinition`. Deleting rather than deprecating is what makes this an evolution and not a parallel system. |
| `deposit/ManagedDeposits.java` | Now a view over the catalogue: `all()`, `resolve()`, `byName()` operate on SEDIMENT definitions; MC-typed helpers for block/yield/tag. `CLAY` and `SILICA_SAND` resolve from data. | Preserves the healthy seam and its API shape while moving every fact into data. |
| `deposit/ManagedDepositExtraction.java` | Two lines: yield from the catalogue, depleted state from the definition. | Validate-before-mutate ordering deliberately untouched. |
| `commands/ManagedDepositCommands.java` | Uses `ResourceDefinition`; `types` now also reports regeneration hours. | Regeneration is per resource now, so the operator listing should say so. |
| `mining/MiningExtractionTool.java` | Predicate replaced by a per-resource tag lookup. | The M1 class check was temporary by design; authorization is now data. |
| `mining/MiningBreakGate.java` | `subject(actor, state)` — the tool is checked against the tag *that resource* names. | Authorization became per resource, so the snapshot needs the block. |
| `event/CustomBlockBreakHandler.java` | Asks `MiningExtractionTool.isAuthorized(state, held)`; private predicate removed; both depletions route through the declared depleted state. | Removes the last copy of the tool rule; makes the depleted-state concept real on the Mining path too. |
| `event/ManagedResourceExplosionHandler.java` | One catalogue lookup on `family != STONE` instead of two subsystem lookups. | Same policy, expressed once. Vanilla iron/gold protection retained per the M2 decision. |
| `features/VeinPlacementValidation.java` | Ore→shape table and per-ore bounds removed; reads them from the definition. Kept: rotation whitelist, build-height and world-limit checks. | The M1 parallel source is gone. Ownership transition to M3 stated in the class docs. |
| `commands/PopulateOresCommand.java` | `ORE_TYPES` deleted; placeability and the placed block come from the catalogue; dispatch switches on `ResourceShape`, not on the ore name. | The milestone's central claim, in one method. |
| `block/blockrestore/BlockRestoreHandler.java` | `restoreDelayFor(record)` resolves per resource from `originalState`. | Activates per-resource timing with no saved-data change and no scheduler redesign. |
| `BritanniaMod.java` | `Resources.init()` after `Mineables.init()`; two validators at server start. | Fail fast at load, not at a player's break. |
| `gametest/ResourceExtractionMatrixGameTests.java` **(new, 11 tests)** | The full cross-family tool matrix plus behaviour-preservation checks. | The negatives are the point. |
| `test/…/resource/ResourceCatalogTest.java` **(new, 15)** | Shipped-catalogue contract: coverage, ownership, unchanged levels and yields, timing, generation config. | |
| `test/…/resource/ResourceCatalogValidationTest.java` **(new, 27)** | Every validation rule driven against real fixtures, plus the new-resource-needs-no-Java proof. | |
| `gametest/ManagedClayDepositGameTests.java`, `test/…/ManagedClayDepositTest.java`, `test/…/HousingMaterialCommodityTest.java` | Call-site updates for the new record shape (`id()` is a String; yield/tag via helpers). | Mechanical; assertions unchanged in meaning. |

---

## Tests

| Test / command | Result |
|---|---|
| `./gradlew test --tests "…resource.*"` (focused) | **42 run, 0 failed** |
| `./gradlew test --tests "…mining.* …deposit.* …features.* HousingMaterialCommodityTest"` (focused) | **BUILD SUCCESSFUL**, 0 failed |
| `./gradlew test` (full) | **2819 run, 5 failed, 17 skipped** |
| `./gradlew runGameTestServer --no-configuration-cache` (full) | **647 run, 645 passed, 2 failed** |

### Regression accounting

| | M1 baseline | After M2 | Delta |
|---|---|---|---|
| JUnit run | 2777 | 2819 | **+42 new** |
| JUnit failed | 5 | 5 | **0 new** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 636 | 647 | **+11 new** |
| GameTests failed | 2 | 2 | **0 new** |

Known baseline failures, unchanged and unrelated: `RoutineSkillGainPresentationTest`,
`MilestoneEightContentReportTest`, `CorrectiveMilestoneNineARenderAlignmentTest`, two
`MonolithMilestoneSevenRenderingTest` asset-hash pins (JUnit); `aprivateplotacceptsitsownerandrefuseseveryoneelse`
and `alegacyvineconvertsonceandkeepsitsvarietyandmaturity` in `GrapeArborGameTests` (GameTest), left
untouched per the M2 decision. **No environmental failures. No new regressions.**

New test counts verified independently: `grep -c "@GameTest("` on the new class returns 11, repo
total 647; `ResourceCatalogTest` 15 + `ResourceCatalogValidationTest` 27 = 42.

### Required proof, mapped

| Required | Where |
|---|---|
| all migrated definitions load | `theShippedCatalogueLoadsAndCoversEveryFamily`, `everyCataloguedBlockAndYieldItemResolves` |
| malformed definition fails cleanly | `missingRequiredFieldsFailWithTheFieldNamed`, `anUnknownDepletedStateFails`, `anUnknownShapeFails`, `anUnsupportedSchemaFails`, `anEmptyCatalogueFails`, `anUnnamespacedIdFails` |
| duplicate resource fails | `duplicateResourceIdsFail`, `twoResourcesCannotShareOneMiningRequirement` |
| unresolved Mineable reference fails | `anUnresolvedMineableReferenceFails`, `aMiningResourceWithNoMineableReferenceFails`, `aFamilyThatDisagreesWithItsMineableCategoryFails`, `blocksThatDisagreeWithTheMineableFail` |
| extraction tags resolve | `everyExtractionTagAuthorisesSomething` (drives the live server-start validator) |
| Britannia pickaxe succeeds for Mining resources | `theProjectPickaxeIsAuthorisedForStoneAndOre` (stone, verite, vanilla iron, sandstone deposit) |
| Britannia shovel rejected for ore/stone | `theProjectShovelIsRefusedByStoneAndOre` |
| `TwoHandedAxeItem` rejected for ore/stone | `everyUnauthorisedToolIsRefusedByStoneAndOre` |
| Britannia shovel succeeds for clay | `theProjectShovelWorksClayAndSilica` |
| Britannia shovel succeeds for silica | same |
| Britannia pickaxe rejected for clay/silica | `theProjectPickaxeIsRefusedByClayAndSilica` |
| vanilla shovel rejected for clay/silica | `everyUnauthorisedToolIsRefusedByClayAndSilica` |
| unrelated tools rejected | both `everyUnauthorisedTool…` tests (axe, vanilla pickaxe, vanilla shovel, stick, bare hand) |
| Mining levels unchanged | `existingMiningLevelsAreUnchanged` (20 values pinned) |
| yields / economic identities unchanged | `existingYieldIdentitiesAreUnchanged`, `theBedsStillYieldTheIdentitiesTheEconomyPrices` |
| clay behaviour unchanged | all pre-existing `ManagedClayDepositGameTests` still green |
| silica behaviour unchanged | `ManagedDepositHouseProtectionGameTests`, `HousingMaterialSupplyGameTests` still green |
| silica resolves 24 h | `silicaResolvesTwentyFourHoursAndEverythingElseKeepsSix`, `regenerationDelayIsResolvedPerResource` |
| others resolve 6 h | same two |
| new resource needs no Java `switch` | `aNewResourceNeedsNoJavaAtAll` |

No new source-text policy tests were introduced. The pre-existing ones in surrounding systems were
left alone.

---

## Invariants now established

1. **Mining progression has exactly one source.** `ResourceDefinition` has no field that could hold
   a Mining number; two files cannot state different requirements for one resource.
2. **The two catalogues cannot drift apart.** A resource covers exactly the blocks its mineable
   claims, or the load fails.
3. **No managed resource can exist without a configured extraction tag.** Every ACTIVE mineable must
   be claimed; an unclaimed one fails the load rather than reaching a permissive default.
4. **No extraction tag can authorise nobody.** Checked once datapacks load; an empty tag fails start.
5. **Authorization is data, in both directions.** Pickaxe → stone/ore only; shovel → clay/silica
   only; axe, vanilla pickaxe, vanilla shovel, unrelated items and bare hands → nothing. Neither
   family's tool has authority over the other, and no class membership grants any.
6. **Yield mode cannot mismatch its family**, and a derived yield cannot also name an item.
7. **A configured radius cannot dip below the radius at which its algorithm throws.**
8. **A resource cannot generate a block it does not govern**, and a sediment bed cannot configure
   vein generation at all.
9. **Regeneration is per resource and validated to a sane range**, live at runtime.
10. **Every resource declares its depleted state**, and both extraction paths read it rather than
    assuming air.
11. **Adding a resource that reuses an existing Mining requirement, extraction tag and shape
    requires no Java.** No resource-name branching remains in any server-side OreVein-scope package.

All M1 invariants remain in force and re-verified by the unchanged M1 test suite.

---

## Temporary adapters / technical debt

| Item | Why it remains | Removal milestone |
|---|---|---|
| `VeinPlacementValidation` still exists as a validator (rotation whitelist, build height, world limits) | The shape numbers moved to data; what is left is placement-input containment that the pure planners will absorb. | **M3** — folded into the shape codecs; the file goes away. |
| `PopulateOresCommand.generateOreVein` switches on `ResourceShape` | A shape-id → implementation lookup, not resource-name branching. Needed until planners are registered. | **M3** — becomes a planner registry lookup. |
| `purity_ore` / `graded_stone` **producers** still live in `CustomBlockBreakHandler` | The definition names the mode and validates it; restructuring where the item is built is a yield-pipeline change beyond M2's scope. | **M3/M4** with the extraction transaction work. |
| `ResourceShape.hardFloorRadius()` documents crash boundaries of the six imperative algorithms | The floors exist because the algorithms throw. | **M3** — disappears with the algorithms. |
| Only one `DepletedState` mode | A second mode with nothing consuming it would be the speculative field M2 was told not to add. | **M4/M6** — the mode arrives with the behaviour that reads it. |
| `MineableDefinition.Category` and `ResourceDefinition.Family` both express ore-vs-stone | Category is Mining's; Family also covers SEDIMENT, which Mining has no concept of. Validated to agree. | Not scheduled; harmless while validated. |
| `ClientEventHandler` / `ClientModSetup` switch on metal name for tints and particle scale | Client rendering, not resource definition. | Not scheduled; out of project scope. |
| `MiningExtractionTool` retained as a Mining-side seam | Two thin methods onto `Resources`; keeps the gate asking one place. | Not scheduled; it is the seam, not a duplicate. |

---

## Playbook impact

### M6 — effective scope, updated

**The Britannia shovel is the approved extraction tool for both clay and silica. No silica-specific
shovel exists or will be created.** This supersedes the M0/M6 recommendation to add one.

Of the four gaps the M1 report listed for M6, **three are now done**:

| M6 gap | Status |
|---|---|
| Dedicated silica shovel | **Cancelled** by owner decision. Existing `britannia_mod:britannia_shovel` is the tool, referenced through `britannia_mod:silica_shovels` — a tag, not item equality, so a future upgraded shovel is a data change. |
| Migrate resource definitions to the canonical architecture | **Done.** Silica and clay are catalogue entries. |
| Per-resource regeneration timing | **Done and live.** Silica resolves 24 h; everything else 6 h. |
| Fortune / Silk Touch / fake-player / automation policy | **Outstanding.** Still M6. |

M6 is now essentially one item: pin the enchantment and automation policies explicitly. Everything
else it was scoped for either already existed or landed here.

### M3 — narrowed and better specified

`ResourceShape` is the planner registry's key, already carrying every shape's identity and crash
boundary. The per-resource min/max radius is already data. M3 implements the planners, deletes
`VeinPlacementValidation`, and replaces the shape switch with a registry lookup.

### M4 — unchanged in scope, one dependency clarified

`Resources.depletedState` is the seam a solid depleted block will arrive through, which is the
answer to the submerged-restoration problem M1 exposed. `BlockRestoreHandler.restoreDelayFor` reads
the definition rather than a record field, so the scheduler redesign inherits per-resource timing for
free and needs no saved-data migration for it.

### M5 — unchanged

### The `deposit/` discovery, final assessment

M0 predicted it would reshape M6; M1 showed the deposit half needed only piston and explosion cover;
M2 confirms the rest. `ManagedDeposit` absorbed every concern the Mining half had scattered, with no
friction, and the Mining half is now the one that had to change to match it. The M0 decision to
generalise it rather than replace it was correct, and this milestone is the evidence.

---

## Git state

| Item | Value |
|---|---|
| Repository | `C:\projects\britannia\mod\Britannia_Mod` |
| Worktree | `.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| **M1 commit** | **`61a359d016dc9636f8ef5a546a6125f8e09c773c`** — `fix(mining): contain managed resource mutation paths` (17 files; worktree verified clean immediately after) |
| Current HEAD | `61a359d016dc9636f8ef5a546a6125f8e09c773c` — unchanged |
| **M2 committed?** | **No.** All M2 work is uncommitted in the working tree. |

`git status --short`:

```
 M src/main/java/com/seggellion/britannia_mod/BritanniaMod.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BlockRestoreHandler.java
 M src/main/java/com/seggellion/britannia_mod/commands/ManagedDepositCommands.java
 M src/main/java/com/seggellion/britannia_mod/commands/PopulateOresCommand.java
D  src/main/java/com/seggellion/britannia_mod/deposit/ManagedDeposit.java
 M src/main/java/com/seggellion/britannia_mod/deposit/ManagedDepositExtraction.java
 M src/main/java/com/seggellion/britannia_mod/deposit/ManagedDeposits.java
 M src/main/java/com/seggellion/britannia_mod/event/CustomBlockBreakHandler.java
 M src/main/java/com/seggellion/britannia_mod/event/ManagedResourceExplosionHandler.java
 M src/main/java/com/seggellion/britannia_mod/features/VeinPlacementValidation.java
 M src/main/java/com/seggellion/britannia_mod/gametest/ManagedClayDepositGameTests.java
 M src/main/java/com/seggellion/britannia_mod/mining/MiningBreakGate.java
 M src/main/java/com/seggellion/britannia_mod/mining/MiningExtractionTool.java
 M src/main/java/com/seggellion/britannia_mod/util/ModTags.java
 M src/test/java/com/seggellion/britannia_mod/deposit/ManagedClayDepositTest.java
 M src/test/java/com/seggellion/britannia_mod/economy/HousingMaterialCommodityTest.java
?? src/main/java/com/seggellion/britannia_mod/gametest/ResourceExtractionMatrixGameTests.java
?? src/main/java/com/seggellion/britannia_mod/resource/
?? src/main/resources/data/britannia_mod/resources/
?? src/main/resources/data/britannia_mod/tags/item/mining_pickaxes.json
?? src/test/java/com/seggellion/britannia_mod/resource/
```

16 tracked files changed (+322 / −350, including the 46-line `ManagedDeposit.java` deletion), plus 8
new files: 4 source, 1 data catalogue (28 definitions), 1 tag, 1 GameTest class, 2 unit test classes.

Pre-existing unrelated work, untouched: `stash@{0}` (`On shrines-monoliths`, 2026-08-02), and the
untracked `docs/` project files in the main checkout, which were deliberately excluded from the M1
commit.

---

## Recommendation

**Safe to proceed to M3.**

The canonical seam exists, is validated, and is consumed at runtime by every part of the system that
previously held its own copy of the knowledge: the break gate, both yield handlers, the explosion
policy, the placement command, the placement validator, and the restoration sweep. Mining progression
remains solely `MineableCatalog`'s, and the two catalogues are now structurally unable to disagree.
The pickaxe and the shovel are parallel, tag-authorised, and cross-family refusals are tested in both
directions for every tool family.

Two things worth your attention before M3, neither blocking:

1. **Tag naming differs from the kickoff's example** (`britannia_mod:mining_pickaxes` and the
   existing `*_shovels`, rather than `tools/extracts/*`). The requested property — per-resource,
   data-driven, no item equality — is fully met; say the word if you would prefer the nested paths
   and I will migrate all three tags in M3.
2. **M6 is now nearly empty** — enchantment and automation policy only. It may be worth folding into
   another milestone rather than running as its own.

**Stopping after M2 as instructed. Milestone 3 has not been started, and M2 remains uncommitted.**
