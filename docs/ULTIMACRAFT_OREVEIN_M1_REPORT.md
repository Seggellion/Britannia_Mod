# Milestone 1 - Correctness Containment

**Project:** UltimaCraft OreVein Remediation
**Date:** 2026-08-20
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**Status:** complete, uncommitted, awaiting review

---

## Objective

Close every mutation path that could permanently destroy or corrupt a managed resource outside its
extraction transaction, before any new generation infrastructure is built on top. Containment only:
no canonical definition migration (M2), no pure planners (M3), no deposit ledger or scheduler
redesign (M4), no vanilla feature suppression (M5).

---

## Baselines taken before any code change

Two separate baselines, kept separate from the M1 regression count throughout.

### GameTest baseline (the one M0 was missing)

Command: `./gradlew runGameTestServer --no-configuration-cache`

The first attempt failed in Gradle configuration, not in a test:

```
Could not load the value of field `provider` of `ProviderBackedFileCollectionSpec` ...
> null array
```

That is a Gradle configuration-cache defect in the `runGameTestServer` task wiring.
`--no-configuration-cache` is required for this task and is used for every run reported here.

| Metric | Value |
|---|---|
| Discovered and run | **617** |
| Passed | **615** |
| Failed | **2** |
| Skipped | 0 (the framework reports none) |
| Wall clock | 13.14 s |

Failing tests, both in `GrapeArborGameTests` — grape arbor / farm plots, no relationship to Mining,
deposits or OreVein:

| Test | Assertion | Classification |
|---|---|---|
| `aprivateplotacceptsitsownerandrefuseseveryoneelse` | "a private plot accepted a stranger" — `FarmingBlockEntity.mayPlant` returned true for a random-UUID owner | **Pre-existing; environmental cause plausible but unproven.** The run had no Rails backend (`WorldBootstrapHandler ... code=fetch_failed`, `WorldStateSyncPoller ... code=transport_error` throughout), and plot ownership resolution may fall open when region data is unavailable. Not confirmed — it was not investigated, being outside this project. |
| `alegacyvineconvertsonceandkeepsitsvarietyandmaturity` | "the legacy vine survived its own conversion" — the log shows `LegacyGrapeVineMigration` did convert the vine, but the legacy block remained | **Pre-existing.** No Rails dependency is visible in the failure; this looks like a genuine defect or ordering issue in the grape migration, not an environment problem. |

Both were produced on a completely unmodified tree (`git status --short` empty).

### JUnit baseline (carried forward from M0, unchanged)

| Metric | Value |
|---|---|
| Run | 2759 |
| Failed | 5 |
| Skipped | 17 |
| Mining/deposit tests | 82, all passing |

Known failures: `RoutineSkillGainPresentationTest.gainsStillMutatePersistAndSync`,
`MilestoneEightContentReportTest.committedReportExactlyMatchesDeterministicCatalogueAndResourceReconstruction`,
`CorrectiveMilestoneNineARenderAlignmentTest.ownerSuppliedAssetsAndUnchangedProtectedAssetsHaveExpectedHashes`,
and two `MonolithMilestoneSevenRenderingTest` asset-hash pins.

---

## Findings

Discoveries during implementation that change later-milestone assumptions.

### 1. The wrong-tool defect is narrower than the audit implied, but real — and the audit's example cases would not have proved it

Two unrelated handlers were incidentally covering most of the space:

- `CityGameModeHandler.onPlayerTick` forces ADVENTURE outside cities unless the player holds a
  `QualityToolItem` or a `TwoHandedAxeItem`, and adventure mode cannot break blocks.
- `StructureProtectionHandler.onBlockBreak` refuses a survival break outside a house unless the
  held item is a `QualityToolItem` or a `TwoHandedAxeItem`.

So a bare hand and a vanilla pickaxe were already blocked in the common case — **by coincidence,
not by policy**. A regression test using either would have passed against the unfixed code. The
genuinely reachable routes were:

- **`TwoHandedAxeItem`**, which both handlers wave through and `CustomBlockBreakHandler` ignores.
  Against a Britannia ore (no loot table) this deleted the vein with no drop, no yield, no
  restoration debt and no skill award.
- **any vanilla pickaxe inside a house the player owns**, where `HouseBuildRights` returns
  `ALLOWED` and `StructureProtectionHandler` therefore does not refuse. Against catalogued
  `minecraft:iron_ore` / `minecraft:gold_ore` this paid out the **vanilla loot table**, leaking raw
  iron and gold outside the economy entirely.

Every wrong-tool GameTest added here asserts the **Mining gate itself** returns `WRONG_TOOL`, not
merely that the break failed, so none of them can pass for the incidental reason.

**Impact on later milestones:** M2's extraction tags must explicitly exclude `TwoHandedAxeItem`.
The audit's suggestion to generalise `instanceof QualityToolItem` in `CityGameModeHandler` and
`SurvivalZoneHandler` must be done carefully — those checks are load-bearing for game-mode
switching, not just for mining, and widening them would widen who may break blocks in the world.

### 2. Tool policy had three definitions; it now has one

`PickaxeMiningRules`, `QualityToolItem.isCorrectToolForDrops` and
`CustomBlockBreakHandler.isBritanniaPickaxe` each carried part of the answer, and the gate carried
none. `MiningExtractionTool` is now the single authority, consulted by both the gate and the yield
handler. **M2's tag migration therefore has exactly one predicate to replace** rather than three
call sites to keep in step.

### 3. Explosion protection for vanilla iron/gold is a visible world-behaviour change

`minecraft:iron_ore`, `minecraft:deepslate_iron_ore`, `minecraft:gold_ore` and
`minecraft:deepslate_gold_ore` are ACTIVE catalogued ORE resources, so the required invariant
("explosions do not permanently delete managed resources") makes **naturally generated vanilla iron
and gold ore blast-proof in existing chunks**. TNT-mining them stops working; a creeper crater will
leave them standing.

This is a direct consequence of those blocks being managed today, and it resolves once the approved
iron/gold migration gives them UltimaCraft block identities and legacy vanilla ore becomes
decorative. **It is a one-line dial** — restricting the ORE branch of
`ManagedResourceExplosionHandler.isProtectedFromExplosions` to the `britannia_mod` namespace would
scope it to the mod's own blocks. Flagged for an owner decision rather than decided here, because
either answer is defensible and the invariant as written requires the current one.

### 4. Restoration now blocks on fluid, which creates permanently-pending debts underwater

Both extraction paths empty a cell with `level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), …)`.
The block being removed is solid, so its fluid state is empty and the cell becomes **air** — and
water then flows in from neighbours. Under the new occupancy rule that cell can never restore.

Before M1 it did restore, by deleting the water. That is precisely the behaviour the milestone was
asked to stop, so this is correct — but the consequence is that a node mined in a shallow keeps its
debt forever rather than returning. **M4 must ship the blocked-entry diagnostics and backoff it
already plans, and should decide whether a submerged deposit is meant to be recoverable at all.**
No existing data is lost: the record is retained, never dropped.

### 5. Source-text tests will keep obstructing correct fixes

`MiningRestorationPolicyTest.restorationRefusesOccupiedCells` asserted that
`BlockRestoreHandler.java` *contained the string* `canBeReplaced` — the exact call that produced the
water/lava defect. Pinning the spelling of a bug made it look protected. It has been rewritten to
drive the policy function across every combination.

`MiningFeedbackPolicyTest` and `SilverVerticalSliceTest` are the same shape and sit directly in
M3/M4's path. **Budget for converting them rather than editing them to keep passing.**

### 6. Vanilla blocks cannot be piston-protected without a mixin

`PushReaction.BLOCK` was applied to `BaseOreBlock`, `ManagedDepositBlock` and `SANDSTONE_DEPOSIT`.
`minecraft:iron_ore` and `minecraft:gold_ore` cannot be covered this way. **This strengthens the
case for the approved iron/gold migration** — it is not only about provenance and identity, it is
the only way those resources can get the same physical protections as the rest of the ladder.

### 7. `/populateores clear` was worse than the audit's estimate in one respect

Confirmed by reading `Level.java:195-208`: `level.getChunk(x, z)` resolves to
`getChunk(x, z, ChunkStatus.FULL, true)`, which **generates** absent chunks and throws
`IllegalStateException` if it cannot. So the scan did not merely read 165 M blocks synchronously —
it could generate up to 1681 chunks first, on the server thread, as a side effect of a "clear"
command.

---

## Changes

| File | Change | Reason |
|---|---|---|
| `mining/MiningExtractionTool.java` **(new)** | Single authority for "may this tool work a Mining resource". Same predicate `CustomBlockBreakHandler` already used. | The gate had no tool policy and the yield handler had a private one. One seam, so they cannot disagree; also the exact point M2's item tag will replace. |
| `mining/MiningBreakGate.java` | Added `WRONG_TOOL` result; added `authorizedTool` to `Subject`; read the server-side main-hand item in `subject()`; inserted the tool check after the admin bypass and before skill-data/threshold; added the feedback key. | Skill alone was a complete answer, so a sufficiently skilled player reached vanilla breaking with anything in hand. |
| `event/CustomBlockBreakHandler.java` | `isBritanniaPickaxe` delegates to `MiningExtractionTool`. | Removes the second definition of the tool rule. Coverage unchanged — `ToolRegistry.PICKAXE` is a `QualityToolItem`. |
| `assets/…/lang/en_us.json` | Added `message.britannia_mod.mining.wrong_tool`. | A denial must say why. One-line insertion; BOM and file structure preserved. |
| `block/BaseOreBlock.java` | `.pushReaction(PushReaction.BLOCK)`. | A vein cell moved away from its restoration record leaves a debt pointing at empty stone. |
| `block/ManagedDepositBlock.java` | Overrides `getPistonPushReaction()` → `BLOCK`. | Overridden on the class, not per registration, so a deposit added later cannot be declared without it. |
| `registry/BlockRegistry.java` | `SANDSTONE_DEPOSIT` gains `.pushReaction(PushReaction.BLOCK)`. | It is a placed quarry face on the Mining path but a plain `Block`, so it was not covered by either class above. |
| `event/ManagedResourceExplosionHandler.java` **(new)** | Removes managed cells from `ExplosionEvent.Detonate.getAffectedBlocks()`. Covers all `ManagedDeposits` beds and all ACTIVE ORE mineables; excludes STONE terrain and player-placed blocks. | Explosions never fire `BreakEvent`, so no gate saw them; a creeper could permanently delete a vein. Follows the existing `FlowerInteractionHandler` / `ManagedVegetationInteractionHandler` pattern. |
| `BritanniaMod.java` | Registers the explosion handler. | Wiring. |
| `block/blockrestore/BlockRestoreHandler.java` | `canRestoreInto` now rejects fluid and block entities; new pure `cellStateAllowsRestoration(...)` core. | Water and lava are `.replaceable()` in 1.21.1, so restoration was deleting them. The pure core makes the policy testable without Minecraft. |
| `features/VeinPlacementValidation.java` **(new)** | Pure validator: known ore type, per-shape minimum and maximum radius, rotation whitelist, build-height and world-limit checks. | Malformed rows must not reach the shape algorithms, several of which throw. Pure Java so JUnit drives every boundary. |
| `commands/PopulateOresCommand.java` | Validates every row before dispatch and reports skips; removes coal; removes the nine never-placeable types; replaces `clear` and `undoores` with a refusal; replaces the static undo map with a per-invocation local. | The five containment items the milestone asked for on this command. |
| `gametest/OreVeinContainmentGameTests.java` **(new)** | 19 GameTests across all four closed routes plus the command scope. | Executable proof, not source-text assertions. |
| `test/…/features/VeinPlacementValidationTest.java` **(new)** | 11 unit tests including the known Snake and Geode crashes and a pin that every shipped vein row still places. | Containment must not silently become a content regression. |
| `test/…/mining/MiningBreakGateTest.java` | 5 `Subject` call sites updated; 6 tests added. | New decision step needs its ordering and its exceptions pinned. |
| `test/…/mining/MiningRestorationPolicyTest.java` | `restorationRefusesOccupiedCells` rewritten as a behavioural truth-table; added a wiring check. | It was pinning the defect's source text. |
| `gametest/SilverMiningGameTests.java` | Message reworded (assertion unchanged). | It referenced `/undoores`, which is now disabled; the invariant it actually proves is no-double-counting. |

---

## Tests

| Test / command | Result |
|---|---|
| `./gradlew runGameTestServer --no-configuration-cache` (baseline, unmodified tree) | **617 run, 615 passed, 2 failed** — both `GrapeArborGameTests`, pre-existing |
| `./gradlew test` (baseline, from M0) | **2759 run, 5 failed, 17 skipped** — all 5 pre-existing and unrelated |
| `./gradlew test --tests VeinPlacementValidationTest --tests mining.*` (focused) | **BUILD SUCCESSFUL** — 96 tests, 0 failures |
| `./gradlew test` (full, after M1) | **2777 run, 5 failed, 17 skipped** |
| `./gradlew runGameTestServer --no-configuration-cache` (full, after M1) | **636 run, 634 passed, 2 failed** |

### Regression accounting

| | Baseline | After M1 | Delta |
|---|---|---|---|
| JUnit tests run | 2759 | 2777 | **+18 new** |
| JUnit failures | 5 | 5 | **0 new** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 617 | 636 | **+19 new** |
| GameTest failures | 2 | 2 | **0 new** |

The two GameTest failures and the five JUnit failures after M1 are **byte-identical in name** to the
baseline sets. **No new regressions.** New test count confirmed independently: `grep -c "@GameTest("`
on the new class returns 19, and the repository total is 636, matching the run.

### Per-class unit counts (Mining / deposit / features)

| Class | Baseline | After M1 |
|---|---|---|
| `MiningBreakGateTest` | 9 | **15** |
| `MiningRestorationPolicyTest` | 7 | **8** |
| `VeinPlacementValidationTest` | — | **11** |
| `MineableCatalogContractTest` | 13 | 13 |
| `MineableCatalogValidationTest` | 12 | 12 |
| `MiningCalibrationTest` | 5 | 5 |
| `MiningEconomyIdentityTest` | 9 | 9 |
| `MiningFeedbackPolicyTest` | 8 | 8 |
| `MiningSkillTest` | 3 | 3 |
| `SilverVerticalSliceTest` | 10 | 10 |
| `ManagedClayDepositTest` | 6 | 6 |

All green.

### Requested coverage, mapped

| Required case | Where |
|---|---|
| high-skill + `TwoHandedAxeItem` | `aTwoHandedAxeCannotDestroyAGatedOreAtFullSkill` |
| high-skill + wrong vanilla tool | `aVanillaPickaxeCannotMineCataloguedVanillaIronOre`, `…GoldOre` |
| correct Britannia tool still succeeds | `theProjectPickaxeStillCompletesTheManagedExtraction`, plus all pre-existing Mining GameTests |
| piston behaviour | `managedResourceBlocksRefusePistonMovement` (10 blocks) |
| explosion behaviour | `anExplosionCannotDestroyAManagedOre`, `…Deposit`, `ordinaryStoneTerrainIsNotExplosionProtected`, `playerPlacedOreIsNotExplosionProtected` |
| water restoration blocked | `waterBlocksRestoration` + unit truth table |
| lava restoration blocked | `lavaBlocksRestoration` + unit truth table |
| ordinary valid restoration still succeeds | `anEmptyCellStillAcceptsRestoration` |
| malformed Snake radius | `snakeRefusesEveryRadiusThatWouldThrow` |
| malformed Geode radius | `geodeRefusesEveryRadiusThatWouldThrow` |
| clear command disabled | `theLegacyClearAndUndoOperationsAreDisabled` |
| undo command disabled | same test |
| coal out of the legacy path | `coalIsNoLongerPlaceableByTheLegacyCommand`, `vanillaCoalOreItselfIsUnchanged`, `coalIsNoLongerPlaceableThroughTheLegacyRoute` |

Also added beyond the minimum: bare hand and Britannia shovel wrong-tool cases, admin-bypass
ordering, actor-policy ordering, block-entity restoration blocking, blocked-cell debt retention,
every-shipped-row-still-places, and the nine never-placeable types.

---

## Invariants now established

The following are impossible for any non-administrator player or automation path:

1. **Breaking a Mining-catalogued resource without an authorized tool.** The gate returns
   `WRONG_TOOL` and cancels at `EventPriority.HIGH`, before `CustomBlockBreakHandler` or vanilla
   breaking can mutate anything. Skill level cannot substitute for the tool at any value up to 100.
2. **Reaching a vanilla drop through a managed resource.** Applies equally to `minecraft:iron_ore`
   and `minecraft:gold_ore`, which previously paid out their vanilla loot tables on this route.
3. **Creating a restoration debt from a denied break.** A cancelled event never reaches
   `BrokenBlockTracker`.
4. **Gaining Mining from a denied break.** `MiningSkill.awardForBreak` re-evaluates the gate and
   proceeds only on `ELIGIBLE`, which now includes the tool.
5. **Moving a managed resource off its coordinates with a piston.** All `BaseOreBlock` ores, both
   `ManagedDepositBlock` beds and `SANDSTONE_DEPOSIT` are `PushReaction.BLOCK`.
6. **Destroying a managed resource with an explosion.** Managed cells are removed from the blast:
   nothing dropped, nothing yielded, nothing scheduled, resource still standing.
7. **Restoration deleting water or lava.** Fluid blocks restoration; the debt is retained and
   retried rather than dropped.
8. **Restoration overwriting a block entity.**
9. **A malformed curated row reaching a shape algorithm.** Unknown type, out-of-range radius,
   unrecognised rotation, out-of-build-height or out-of-world coordinates are rejected with a
   reason and skipped; the rest of the command continues.
10. **`/populateores clear` generating chunks or rewriting blocks**, and **`/undoores` writing from
    a non-persistent, dimension-blind map.** Both refuse with an explanation.
11. **The legacy command placing unmanaged economic material.** Coal is withdrawn.
12. **Undo state leaking across invocations or dimensions.** The static map is gone.

Preserved unchanged: every approved Mining level, challenge value, yield, purity/grade behaviour,
economy commodity identity, the six-hour restoration delay, the single restoration scheduler, the
no-force-load guarantee, and the provenance model.

---

## Remaining risks

### Deferred by design to later milestones

| Risk | Milestone |
|---|---|
| The six shape algorithms still mutate the world directly, still consume `ServerLevel#getRandom()`, and are still non-deterministic and non-idempotent. M1 only guarantees malformed input cannot reach them. | M3 |
| `LayeredVein` still places into air in two phases and over arbitrary non-air in the third; `VerticalLayeredVein` is still air-only; `GeodeVein` is still not a geode; placement counts may still include duplicates. Contained, not corrected. | M3 |
| Rails fetch is still synchronous on the server thread (bounded at ~15 s by `BoundedHttp`). | M8 |
| Deposits still have no persistent identity; repeated `/populateores` still rerolls and reapplies. | M4 |
| Restoration is still `O(all pending records)` on every server pre-tick. | M4 |
| Vanilla ore features still generate everywhere. | M5 |
| `ManagedDeposits` is still a Java `List.of`, and the ore→shape mapping is now duplicated between `PopulateOresCommand` and `VeinPlacementValidation`. Deliberate and temporary. | M2 |

### New, needing an owner decision

1. **Vanilla iron/gold ore is now blast-proof in existing chunks** (Finding 3). One-line dial if the
   owner prefers to scope explosion protection to `britannia_mod` blocks until the iron/gold
   migration lands.
2. **Submerged deposits now accumulate permanently-pending restoration debts** (Finding 4). Correct
   per the required policy; M4 needs blocked-entry diagnostics, and the owner may want a rule for
   whether an underwater deposit is meant to come back at all.
3. **The wrong-tool denial is now reachable on ordinary stone.** A player holding a `TwoHandedAxeItem`
   who breaks natural `minecraft:stone` is refused where they previously were not. This follows
   directly from stone being a catalogued resource at requirement 0. `CityGameModeHandler` already
   made this route rare, but it is a real behaviour change worth knowing about.
4. **The two GameTest baseline failures remain unexplained.** `alegacyvineconverts…` in particular
   shows no Rails dependency and may be a genuine defect in `LegacyGrapeVineMigration`. Outside this
   project's scope; recommend a separate look.

### Explicitly checked and confirmed unaffected

- Vanilla coal world generation is untouched. No biome modifier was added or changed; only the
  `/populateores` placement route was withdrawn. Pinned by `vanillaCoalOreItselfIsUnchanged`.
- Existing worlds are not modified. Nothing in M1 writes to the world outside an operator command.
- No chunk is force-loaded anywhere in M1.
- All nine shipped `ore_veins.json` rows still validate and place, pinned by
  `everyShippedVeinRowStillPasses`.

---

## Playbook impact

### M2 — canonical resource definitions

Two changes to the plan:

- **The tool predicate is now one seam.** `MiningExtractionTool.isAuthorized` is the single call
  site the item tag replaces, instead of the three the audit found. The M2 estimate should shrink.
- **`TwoHandedAxeItem` must be explicitly excluded** from any mining extraction tag, and the
  `instanceof QualityToolItem` checks in `CityGameModeHandler` / `SurvivalZoneHandler` should be
  treated as **game-mode** policy, not mining policy. Generalising them as the audit suggested would
  widen who may break blocks in the world, not just who may mine.

### M3 — pure planners

`VeinPlacementValidation` already encodes each shape's real numeric constraints, derived from the
algorithms. It is the natural seed for the shape config codecs and should be folded into them rather
than re-derived. The ore→shape table it holds is the duplication M2 collapses.

Note also that `SilverMiningGameTests` observes `replaced.size() == placed` holding for
`VerticalLayeredVein` at radius 3 — one data point suggesting that shape at least does not
double-count, contrary to the general concern.

### M4 — identity and regeneration

Two additions:

- **Blocked-entry diagnostics are now load-bearing, not optional.** Fluid-blocked cells are a
  permanent state, not a transient one (Finding 4).
- `BlockRestoreHandler.cellStateAllowsRestoration` is a pure function and should survive the
  scheduler rewrite intact as the occupancy policy.

### M6 — rescoped silica (as approved)

No further change from M1. Confirmed again that the clay and silica beds were never exposed to the
wrong-tool defect: `ManagedDepositInteractionHandler.onBreak` already cancelled unconditionally
before mutation. M1 added piston and explosion protection to both, which were the two gaps the
deposit system did share with the ore system.

### The `deposit/` discovery, in hindsight

It changed M6 substantially (M0) and M1 modestly: the deposit half needed only piston and explosion
containment, while every wrong-tool, validation and command defect lived on the Mining/legacy side.
That asymmetry is a useful signal for M2 — the `ManagedDeposit` record really is the better model to
generalise, and this milestone is evidence for the M0 decision rather than against it.

---

## Git state

| Item | Value |
|---|---|
| Repository | `C:\projects\britannia\mod\Britannia_Mod` |
| Worktree | `.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| HEAD | `c2bc44f33be1c95a6f33c08536838c22ffd62d9d` — `chore(housing): sanitize the housing programme for the production branch` |
| Commit created | **No.** HEAD is unchanged from the start of M0. |

`git status --short`:

```
 M src/main/java/com/seggellion/britannia_mod/BritanniaMod.java
 M src/main/java/com/seggellion/britannia_mod/block/BaseOreBlock.java
 M src/main/java/com/seggellion/britannia_mod/block/ManagedDepositBlock.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BlockRestoreHandler.java
 M src/main/java/com/seggellion/britannia_mod/commands/PopulateOresCommand.java
 M src/main/java/com/seggellion/britannia_mod/event/CustomBlockBreakHandler.java
 M src/main/java/com/seggellion/britannia_mod/gametest/SilverMiningGameTests.java
 M src/main/java/com/seggellion/britannia_mod/mining/MiningBreakGate.java
 M src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java
 M src/main/resources/assets/britannia_mod/lang/en_us.json
 M src/test/java/com/seggellion/britannia_mod/mining/MiningBreakGateTest.java
 M src/test/java/com/seggellion/britannia_mod/mining/MiningRestorationPolicyTest.java
?? src/main/java/com/seggellion/britannia_mod/event/ManagedResourceExplosionHandler.java
?? src/main/java/com/seggellion/britannia_mod/features/VeinPlacementValidation.java
?? src/main/java/com/seggellion/britannia_mod/gametest/OreVeinContainmentGameTests.java
?? src/main/java/com/seggellion/britannia_mod/mining/MiningExtractionTool.java
?? src/test/java/com/seggellion/britannia_mod/features/
```

Diffstat: 12 modified files, +450 / −232, plus 4 new source files and one new test directory. The
376-line diff on `PopulateOresCommand.java` is genuine content (about 150 lines of clear/undo
scanning removed, the rest restructured), **not** line-ending churn — `git diff --stat
--ignore-cr-at-eol` produces identical totals.

Pre-existing unrelated work, untouched:

- `stash@{0}` — `On shrines-monoliths: gitignore logs/*.log`, dated 2026-08-02, from a different
  branch. Not inspected beyond its title, not applied, not dropped.
- `docs/OREVEIN_ENGINEERING_AUDIT.md`, `docs/ULTIMACRAFT_OREVEIN_REMEDIATION_PLAYBOOK.md` and
  `docs/ULTIMACRAFT_OREVEIN_KICKOFF_PROMPT.md` remain untracked in the main checkout, as found.

No destructive Git command was run. No branch or worktree was created or switched.

---

## Recommendation

**The repository is safe to proceed to M2.**

Every M1 exit criterion is met:

- no ordinary player or automation path can destroy a managed resource outside the authoritative
  extraction path — proved for wrong tools, pistons and explosions by 19 executable GameTests;
- invalid placement input cannot reach, let alone crash, a shape algorithm;
- restoration no longer overwrites water or lava, and keeps the debt when blocked;
- all pre-existing managed extraction tests remain green;
- the dangerous clear and undo operations are intentionally disabled with an operator-facing
  explanation rather than half-repaired;
- both full suites were run and reported with baseline failures held separate; **zero new
  regressions**.

Three items want an owner answer before or during M2 — the vanilla iron/gold explosion scope, the
submerged-deposit restoration policy, and whether the unrelated `GrapeArborGameTests` failures
should be looked at — but none of them blocks M2, and none is a correctness regression introduced
here.

**Stopping after M1 as instructed. Milestone 2 has not been started, and nothing has been committed.**
