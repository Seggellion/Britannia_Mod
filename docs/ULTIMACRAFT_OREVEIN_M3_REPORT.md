# Milestone 3 - Deterministic Pure Shape Planners and Guarded Placement

**Project:** UltimaCraft OreVein Remediation
**Date:** 2026-08-20
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**M2 commit:** `a3e8734742edec0777e7ea951adb95010d936eb9`
**Status:** complete, uncommitted, awaiting review

---

## Objective

Replace the six direct-mutating vein algorithms with deterministic pure geological planners, and put
every world mutation behind one guarded materialisation boundary.

---

## Findings

### 1. Per-cell hashing, not a seeded stream, is what makes chunk slicing free

Seeding a random *stream* would have made the shapes reproducible but not order-independent: a
stream only gives the same deposit if every cell is visited in the same order, so slicing by chunk
would reroll it. Hashing each cell's own coordinates with the deposit seed removes both problems at
once — a cell's fate is a pure function of `(seed, salt, x, y, z)`, so any subset can be evaluated,
in any order, and agree.

The consequence is stronger than the milestone asked for: **planning never touches the world at
all**, so a deposit spanning four chunks can be planned and sliced four ways before any of those
chunks exist. Slicing is a filter over a computed list, so the union of slices *is* the plan by
construction, and order-independence is structural rather than something to be careful about.

### 2. The geode's `radius` was never a radius — and correcting it has an operational cost

`GeodeVein` scattered `radius * 2` lone points through a box. Agapite's curated radius of 55 was a
count multiplier (110 points) and a scatter range (±27), not a size. Read as an actual radius, 55
describes a crust of roughly half a million cells.

So the correction is not only to the algorithm. Agapite's configured range is now 3–16, and a
curated row still asking for 55 is **refused and reported by name** rather than placed. That is M1's
containment working exactly as designed — no crash, no corruption, a named skip — but it means:

> **Owner action: the Rails-curated `ore_veins` table's agapite row needs a geode-scale radius
> (3–16). Until it is changed, agapite will not place.** The shipped `ore_veins.json` fixture was
> corrected from 55 to 8; the live Rails table cannot be reached from here.

Nothing is deployed (project memory records no deployment since `e4418d6f`), so there is no live
world losing agapite today.

### 3. Correcting the air-only defects changes deposit sizes — mostly *toward* what was intended

`LayeredVein` and `VerticalLayeredVein` gated their cells on `isAir()`. Silver, the only user of the
latter, therefore generated **nowhere** in solid rock. Now that geometry no longer consults the
world, both place a full seam. Measured against what the legacy phases were *asking* for before the
filter discarded them:

| Resource | Legacy asked for | Legacy actually placed in rock | M3 plans |
|---|---|---|---|
| silver (r=50) | ~2,750 | **~0** | **2,510** |
| tin (r=35) | ~1,925 | ~210 (scatter phase only) | **1,180** |

Silver lands within 9% of the legacy intent and tin below it. The `(1 - d/r)²` falloff was chosen
for exactly this reason: it is a plausible bed *and* it reproduces the count the legacy phases were
reaching for. This is a defect being fixed, not a balance change made for its own sake — but silver
deposits going from nothing to ~2,500 cells is a visible difference and is flagged accordingly.

### 4. Two shapes' counts fell, because duplicates are gone

Every legacy shape incremented its counter per `setBlock` call, not per distinct cell, so a random
walk that crossed itself over-reported. Gold went from ~480 reported to **255 unique**; iron from
~360 to **286 unique**. The deposits are the same size they always physically were; the numbers are
now true.

### 5. `AltitudeScaledFeature` was dead code holding a `setBlock`

M0 recorded it as never registered. It was the last direct-mutating ore writer outside the new
boundary, so it was deleted rather than left as a class a future caller could find. Verified
unreferenced first.

### 6. The M2 "shared Mining requirement" wording was imprecise — and the loose reading is false

See the dedicated section below. Short version: two resources may absolutely share a numeric
requirement, and six groups in the shipped catalogue already do. No debt, no correction needed.

---

## Shape decisions

| Shape | Previous behavior | M3 decision | New deterministic behavior |
|---|---|---|---|
| **Cluster** | Squashed-ellipsoid scan with density falloff, air-pocket and rim thinning. Three draws per cell from the level's shared RNG. **No host check at all** — took bedrock, water, lava, block entities. Counted duplicate writes. | **Preserve, made deterministic** | Identical geometry and identical thinning rates, but each cell's three decisions are now `ShapeNoise` over its own coordinates. Host policy removed entirely. Corrected the comment calling `ySquash` a flattening factor — dividing Y by it makes the blob *taller*. |
| **Vertical** | Climbed `radius` steps drifting ±1 in X/Z, fattening each to 2–6 cells. Shared RNG. Skipped a step that landed on bedrock, so geometry depended on the terrain. Accepted a rotation parameter and never read it. | **Preserve, made deterministic; rotation made explicit** | Same climb, same width range; the walk is derived from the seed by step index. Terrain no longer affects geometry. `usesRotation()` returns `false` rather than silently ignoring the parameter. |
| **Snake** | Main vein plus three tendrils. `nextInt(radius - 9) + 10` threw for radius ≤ 9 — and was evaluated *before* the branch that would have overridden it, so it threw unconditionally on the first tendril, part-way through a command that had already written blocks. | **Correct** | The crashing expression is gone: tendril height is derived within an explicitly positive range. Minimum radius is **4**, what the geometry actually needs; the old floor of 10 was the bug's artefact. Gold's data still says 10 — data narrowing a shape's range, which it may do. Each vein gets an independent sub-seed. |
| **Geode** | **Not a geode.** `radius * 2` uniform random points scattered through a box: no shell, no interior, no centre relationship. `nextInt(radius / 2)` threw below radius 2. | **Deprecate the algorithm; implement a real geode** | A crust of thickness ≈0.35·r at the configured radius, both surfaces deterministically roughened by up to a cell, ~12% gaps, hollow centre. Radius means radius. Minimum 3 — the smallest crust-and-hollow. See Finding 2 for the balancing consequence. |
| **Layered** | Two phases placed **only** where `isAir()`; a third placed only where **not** air and would take bedrock, fluids or a chest. Geometry and host policy tangled together and disagreeing with each other. | **Correct** | A horizontal seam: disc of the configured radius, three cells thick, `(1 - d/r)²` falloff, patchier above and below its own course. Knows nothing about the world. |
| **VerticalLayered** | All three phases guarded by `isAir()` — silver generated only into cave air, i.e. nowhere. The rotation `switch` had no `default`, so an unrecognised value left every offset at zero and stacked the whole deposit into one cell. | **Correct** | A standing sheet: two long axes selected by rotation, thin on the third, same `(1 - d/r)²` falloff. World-blind. Rotation is an enum, so there is no unrecognised value left to fall through. |

---

## Architecture

The implemented flow, end to end:

```
ResourceDefinition                     resources.json — shape, block, radius range, host tag
        |
        v
VeinPlacementValidation.reject(row)    external-input concerns only: is this a placeable
        |                              resource, is the rotation a word we know, is the origin
        |                              somewhere a world can exist
        v
DepositSeed.forCuratedVein(...)        deterministic seed from immutable row identity
        |                              (dimension, resource, origin, radius, rotation)
        v
PlacementPlanner.plan(...)             reads the definition; builds a ShapeConfig
        |
        v
ResourceShape.planner().plan(config)   PURE. No level, no chunk, no block, no shared RNG.
        |                              Returns ShapePlan: unique, canonically ordered
        |                              ShapeOffsets plus declared ShapeBounds.
        v
PlannedDeposit                         origin + plan. positions() / positionsIn(ChunkPos) /
        |                              touchedChunks() — all filters over the computed plan,
        |                              none of which read the world.
        v
MaterializationService.materialize()   <<< THE ONLY PLACE A BLOCK IS WRITTEN >>>
```

**World mutation is allowed in exactly one line:** `MaterializationService.java:155`. Verified by
grep across `resource/`, `features/` and `PopulateOresCommand`: one `setBlock`, and it is that one.

The shape layer contains **zero Minecraft imports** — verified by grep. That is what makes
`ShapePlannerTest` a plain JUnit class with no bootstrap, and it is structural rather than a
convention someone could break with an import.

---

## Materialization policy

Every candidate is checked in this order; the first match refuses the cell.

| # | Rule | Rejection | Why |
|---|---|---|---|
| 1 | Outside `[minBuildHeight, maxBuildHeight)` | `OUTSIDE_BUILD_HEIGHT` | `setBlock` silently does nothing there, and the legacy shapes counted it as a placement |
| 2 | Cell already holds this resource's block | `ALREADY_PRESENT` | Idempotence: a repeat is a no-op, not a rewrite |
| 3 | Any fluid, source or flowing | `FLUID` | The same rule M1 gave restoration — the resource system does not edit the world's water |
| 4 | A block entity is present | `BLOCK_ENTITY` | Containers carry state a vein has no business deleting |
| 5 | `getDestroySpeed < 0` | `INDESTRUCTIBLE` | Bedrock, barriers, anything unbreakable |
| 6 | `MiningProvenance.isPlayerPlaced` | `PLAYER_PLACED` | Somebody's own wall is construction |
| 7 | A managed resource of family ORE or SEDIMENT | `OTHER_MANAGED_RESOURCE` | One deposit does not eat another |
| 8 | Not in the resource's configured host tag | `NOT_A_HOST` | Air is not host rock — the correction to the two air-only shapes |

**Accepted:** members of `britannia_mod:ore_hosts`, which composes `#minecraft:stone_ore_replaceables`
and `#minecraft:deepslate_ore_replaceables`. Verified accepted in-world: stone, granite, andesite,
diorite, tuff, deepslate.

**The STONE-family exemption in rule 7 is deliberate.** Stone, granite and deepslate are catalogued
mineables *and* the rock ore grows in; without the exemption a deposit could never be placed
anywhere at all. It is the same family boundary the explosion policy drew at M1.

**Bounded work:** a run writes at most `budget` blocks (default 4,096) and reports `remaining`.
Because the plan is deterministic and a correct cell is a no-op, running again *resumes* rather than
repeats — so a large deposit completes in bounded slices with no state carried between them.

---

## Changes

| File | Change | Reason |
|---|---|---|
| `resource/shape/ShapeNoise.java` **(new, 74)** | Per-cell deterministic hashing; SplitMix64 finaliser written out | Order-independence, and a mixing function that cannot drift between library versions — these seeds decide what a persistent world looks like |
| `resource/shape/ShapeOffset.java` **(new, 30)** | A relative cell, with canonical ordering | Deliberately not `BlockPos`, so the shape layer's purity is structural |
| `resource/shape/ShapeBounds.java` **(new, 59)** | Declared bounds, checked against the plan | Lets a caller reason about reach without generating |
| `resource/shape/ShapeConfig.java` **(new, 27)** | radius + rotation + seed, and nothing else | No level, no position, no block — the geometry's inputs are its whole world |
| `resource/shape/ShapePlan.java` **(new, 55)** | Immutable, unique (`TreeSet`-backed), bounds-checked, 20,000-cell cap | Uniqueness is structural, so a planner cannot over-report as all six legacy shapes did |
| `resource/shape/ShapePlanner.java` **(new, 58)** | The contract, including self-validation | A planner refuses its own bad configuration, so no caller can reach the failure by forgetting to check |
| `resource/shape/ShapeRotation.java` **(new, 38)** | The four rotations as an enum | Removes the unrecognised-value branch that stacked a deposit into one cell |
| `resource/shape/planner/*.java` **(new, 6 files, 568)** | The six deterministic planners | See the shape decisions table |
| `resource/placement/DepositSeed.java` **(new, 76)** | Seed from immutable row identity; FNV-1a written out | Stops `/populateores` rerolling. Explicitly *not* M4's instance identity |
| `resource/placement/PlannedDeposit.java` **(new, 76)** | Plan + origin; `positions`, `positionsIn`, `touchedChunks` | The geometry/world boundary. Slicing is a filter, never a re-plan |
| `resource/placement/PlacementPlanner.java` **(new, 114)** | Definition → config → planner; radius refusal shared with the command | Already takes a seed, so M4 passes a persisted one with no redesign |
| `resource/placement/MaterializationService.java` **(new, 200)** | The single guarded write path | Replaces six shapes' six different opinions with one |
| `resource/ResourceShape.java` | Each constant holds its planner; `minimumRadius()` delegates | One source for a shape's limits — the enum, the catalogue and the planner cannot disagree |
| `resource/ResourceDefinition.java` | `Generation` gains `hostTag` | Host policy belongs to the resource, not to the geometry |
| `resource/ResourceCatalog.java` | Parses and validates `host`; minimum checked against the planner | Data may narrow a shape's range, never widen it |
| `data/…/resources/resources.json` | Added `host` to all nine; re-tuned maxima (cluster 32→22, silver/tin 128→96, agapite 3–16) | Bound planning work under the cell cap; make the geode's radius mean a radius |
| `data/…/tags/block/ore_hosts.json` **(new)** | Composes the two vanilla ore-replaceable tags | Follows the stone families Minecraft itself treats as ore hosts; silica's sediment tag will be its own at M7 |
| `util/ModTags.java` | `Blocks.ORE_HOSTS` | Java handle for the tag |
| `commands/PopulateOresCommand.java` | Plans and materialises; no algorithm selection, no randomness, no writes | The compatibility route into the new architecture |
| `features/VeinPlacementValidation.java` | Reduced to external-input concerns; radius delegated to `PlacementPlanner` | Shape knowledge finished moving to the shapes |
| `features/{Cluster,Vertical,Snake,Geode,Layered,VerticalLayered}Vein.java` | **Deleted (419 lines)** | No production path can reach a legacy mutating shape by accident |
| `features/AltitudeScaledFeature.java` | **Deleted (55 lines)** | Dead since before M0, and the last `setBlock` outside the boundary |
| `data/…/ore_veins.json` | agapite 55 → 8 | The fixture's own row must be geode-scaled; see Finding 2 |
| `gametest/SilverMiningGameTests.java` | Placement test rewritten onto planner + materialisation | It called a deleted class, and it had passed only because its template was empty — the exact defect |
| `gametest/MaterializationPolicyGameTests.java` **(new, 13 tests)** | The boundary, cell by cell, in a world | |
| `test/…/shape/ShapePlannerTest.java` **(new, 19 tests)** | The geometry, exhaustively, with no world | |
| `test/…/placement/PlacementPlannerTest.java` **(new, 12 tests)** | Seeds, translation, chunk slicing | |
| `test/…/ResourceCatalogTest.java` | +2: planner-owned minimums, shared numeric requirements | |
| `test/…/ResourceCatalogValidationTest.java` | +2: missing and malformed host tag | |
| `test/…/VeinPlacementValidationTest.java` | Re-tuned expectations; cases renamed to what they now mean | |

---

## Tests

| Test / command | Result |
|---|---|
| `./gradlew test --tests "…resource.shape.*"` (focused) | **19 run, 0 failed** |
| `./gradlew test --tests "…resource.placement.*"` (focused) | **12 run, 0 failed** |
| `./gradlew test --tests "…resource.* …features.*"` (focused) | **BUILD SUCCESSFUL**, 0 failed |
| `./gradlew test` (full) | **2854 run, 5 failed, 17 skipped** |
| `./gradlew runGameTestServer --no-configuration-cache` (full) | **660 run, 658 passed, 2 failed** |

### Regression accounting

| | M2 baseline | After M3 | Delta |
|---|---|---|---|
| JUnit run | 2819 | 2854 | **+35 new** |
| JUnit failed | 5 | 5 | **0 new** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 647 | 660 | **+13 new** |
| GameTests failed | 2 | 2 | **0 new** |

Known baseline failures, unchanged and untouched: `RoutineSkillGainPresentationTest`,
`MilestoneEightContentReportTest`, `CorrectiveMilestoneNineARenderAlignmentTest`, two
`MonolithMilestoneSevenRenderingTest` asset-hash pins (JUnit); the two `GrapeArborGameTests`
failures (GameTest), not investigated per instruction. **No environmental failures. No new
regressions.**

New counts verified independently: `ShapePlannerTest` 19 + `PlacementPlannerTest` 12 +
`ResourceCatalogTest` +2 + `ResourceCatalogValidationTest` +2 = 35;
`grep -c "@GameTest("` on the new class = 13, repo total 660.

### Performance guardrail — measured planned-cell counts

| Configuration | Shape | radius | planned cells | plan time |
|---|---|---|---|---|
| copper (shipped) | cluster | 15 | 4,833 | 8.5 ms¹ |
| verite (shipped) | cluster | 20 | 11,409 | 6.7 ms |
| iron (shipped) | vertical | 90 | 286 | 1.1 ms |
| vertical maximum | vertical | 128 | 420 | 0.4 ms |
| gold (shipped) | snake | 50 | 255 | 0.2 ms |
| snake maximum | snake | 128 | 629 | 0.4 ms |
| agapite (corrected) | geode | 8 | 931 | 0.6 ms |
| geode maximum | geode | 16 | 9,485 | 7.0 ms |
| tin (shipped) | layered | 35 | 1,180 | 1.0 ms |
| layered maximum | layered | 96 | 9,171 | 5.7 ms |
| silver (shipped) | vertical_layered | 50 | 2,510 | 3.7 ms |
| vertical_layered maximum | vertical_layered | 96 | 9,184 | 4.7 ms |

¹ First call, includes JIT warm-up.

Cluster at radius 24 measured 19,647 cells — uncomfortably close to the 20,000 cap for an unlucky
seed — so its configured maximum was tightened to 22. Every configured maximum planning under the
cap is pinned by `theLargestConfiguredRadiusOfEveryShapeStaysWithinTheCellCap`.

---

## Determinism proof

| Property | How it is proven |
|---|---|
| Same seed + config → identical **ordered** result | `everyShapeIsDeterministicForIdenticalInputs` — replans each shape three ways and compares lists, not sets |
| Different seed → different geometry | `everyShapeRespondsToItsSeed` — a shape that ignored its seed would fail |
| No shared runtime RNG | Structural: grep confirms zero `getRandom()`/`RandomSource` in `resource/`. Behavioural: `planningOneShapeDoesNotDisturbAnother` plans every other shape in between and re-compares |
| No execution-order dependence | Same test — the legacy shapes could not have passed it |
| Uniqueness | `everyShapePlansEachCellAtMostOnce`, plus `ShapePlan` being `TreeSet`-backed so it is structural |
| Bounds | `everyPlannedCellIsInsideTheDeclaredBounds` across four seeds; `ShapePlan.of` re-checks and throws |
| Invalid config refused before planning | `everyPlannerRefusesARadiusBelowItsMinimum`; `theHistoricalSnakeAndGeodeCrashRadiiAreRefusedNotThrownFrom` names both historical crashes |
| Geode is a real geode | `theGeodeIsARadialCrustAroundAHollow` pins radial containment, a hollow centre and shell-not-ball span — properties, not a coordinate snapshot, so it can still be balanced |
| Bedded shapes are world-blind | Structural (no Minecraft import in the layer); behavioural via `theBeddedShapesPlanASeamRegardlessOfAnything` at five seeds |
| Chunk slice union = full plan | `theUnionOfEveryChunkSliceIsTheWholePlan`, over every generatable resource, at an origin chosen to straddle |
| Chunk order independence | `chunkOrderDoesNotChangeTheOutcome` — forwards vs reversed |
| No neighbour force-load | Structural: planning reads no blocks. In-world: `materialisingOneChunkTouchesOnlyThatChunk` |
| Repeat placement is a no-op | `repeatedMaterialisationWritesNothingAndRerollsNothing` — second run places 0, every cell `ALREADY_PRESENT`, and replanning gives identical positions |
| Counts are real transitions | `placementCountsOnlyRealStateTransitions` — a duplicate and two out-of-world candidates are refused, not counted |
| Budget bounds work and resumes | `materialisationRespectsItsWorkBudgetAndResumes` |

---

## Invariants now established

1. **No active shape calls `setBlock`.** Exactly one write exists in the whole OreVein path,
   `MaterializationService.java:155`. Grep-verified.
2. **No active shape consumes shared world RNG.** Zero `getRandom()`/`RandomSource` references in
   `resource/`. Grep-verified.
3. **The shape layer cannot touch Minecraft at all** — zero `net.minecraft` imports.
4. **Identical inputs produce identical geometry**, as an ordered list.
5. **Planned positions are unique**, structurally.
6. **Every planned cell lies inside declared bounds**, or the plan throws while being built.
7. **No unsafe arbitrary replacement**: fluids, block entities, indestructible blocks, player-placed
   blocks, other managed resources and non-host blocks are all refused, each with a named reason.
8. **Air is not a host.**
9. **No neighbour chunk is force-loaded** to complete a deposit; planning reads no blocks at all.
10. **Repeated placement is idempotent at the placement layer**: the same row re-derives the same
    seed, re-plans the same cells, and writes nothing.
11. **Placement counts are state transitions**, not candidates or duplicate writes.
12. **Placement work is bounded** per invocation and resumable.
13. **A configuration a planner cannot serve is refused by the planner itself**, so no caller can
    reach the failure by forgetting to validate.

**Explicitly not claimed:** persistent deposit-instance identity or durable duplicate detection.
Nothing yet records that a deposit exists, so nothing can report it, locate it, or refuse a second
one at a different origin. That is M4.

---

## Temporary adapters / debt

| Item | Status | Removal |
|---|---|---|
| `VeinPlacementValidation` | **Reduced, retained.** Shape knowledge gone; what remains is external-input validation of a curated row — placeable resource, known rotation, origin in a possible world. Radius is delegated to `PlacementPlanner`, so there is one answer. | **M8**, with the validated Rails importer that inherits it |
| Legacy shape classes | **Deleted**, all six, plus the dead `AltitudeScaledFeature`. No compatibility shims. | done |
| `DepositSeed.forCuratedVein` | **Temporary by design.** Derives from row identity because Rails supplies no stable row id (M0 finding). Editing a row's position or radius therefore re-rolls its geometry — a known limitation of a compatibility path. `PlacementPlanner.plan` already takes an explicit seed. | **M4** persists a seed; **M8** mixes in a Rails row id if one is ever added |
| Rails fetch | **Still synchronous** on the server thread, bounded by `BoundedHttp` at ~15 s. Untouched: changing it was not needed to establish the planner/materialisation boundary. | **M8** |
| `purity_ore` / `graded_stone` producers | **Still in `CustomBlockBreakHandler`.** Untouched — nothing in M3 required it. | **M4**, with the extraction transaction work |
| `MaterializationService` budget | Single generous default; no scheduling or spreading across ticks. | **M4/M8** if a real importer needs it |
| Agapite's curated Rails radius | **Owner action** — see Finding 2 | data change, not code |

---

## Mining requirement clarification

M2 reported: *"Two resources sharing one Mining requirement is unrepresentable."* That wording
conflated two different things. Precisely:

**Case 1 — two resources referencing the same Mineable identity: prevented, and correctly so.**
Two definitions naming the same `mineable` would make one catalogue row's blocks and level belong to
two resources at once. It fails either as a duplicate block claim or as a block-set mismatch,
depending on how it is spelled; `twoResourcesCannotShareOneMiningRequirement` pins both.

**Case 2 — two independent resources resolving to the same numeric requirement: fully supported,
and always was.** Each references its *own* mineable row; the rows may hold identical numbers. The
shipped catalogue already does this six times over:

| `required_mining` | Resources |
|---|---|
| 0.0 | iron, stone, cobblestone |
| 5.0 | calcite, sandstone |
| 30.0 | deepslate, cobbled_deepslate, metamorphic_rock |
| 35.0 | glacial_rock, dripstone |
| 40.0 | basalt, igneous_rock |
| 45.0 | blackstone, volcanic_rock |

**There is no limitation and no technical debt here.** A new rock can be priced identically to an
existing one with no friction at all. Now pinned by
`distinctResourcesMayShareANumericMiningRequirement`, so the imprecise reading cannot resurface.
Nothing about the Mining catalogue was redesigned.

---

## Playbook impact

**M4 — no amendment needed; two things got easier.** `PlacementPlanner.plan` already takes an
explicit seed, so a persisted deposit-instance seed drops in with no redesign — only
`planCuratedVein` changes. `PlannedDeposit.touchedChunks()` computes a deposit's chunk footprint
from bounds without reading the world, which is the same shape the chunk-indexed depletion ledger
needs. `MaterializationService.Rejection` already enumerates the blocked-cell reasons M4's
diagnostics must surface.

**M5 — unchanged.**

**M6 — unchanged** (still the enchantment/fake-player/administrative policy work, retained as its
own milestone per instruction).

**M7 — better positioned than expected.** Silica's sedimentary lens is a seventh planner plus a
`britannia_mod:silica_hosts` block tag named by its definition's `host` field. No change to the
planner contract, the placement layer or the materialisation boundary is anticipated. Sand and
sandstone are deliberately *not* in `ore_hosts`, so silica's hosts are additive rather than a
carve-out.

**M8 — one item added.** Alongside the async importer and stable row ids, the curated table needs an
agapite radius correction (Finding 2). Worth doing as part of the same Rails pass.

---

## Git state

| Item | Value |
|---|---|
| Repository | `C:\projects\britannia\mod\Britannia_Mod` |
| Worktree | `.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| **M2 commit** | **`a3e8734742edec0777e7ea951adb95010d936eb9`** — `refactor(resources): establish canonical resource definitions` (25 files; worktree verified clean immediately after) |
| Current HEAD | `a3e8734742edec0777e7ea951adb95010d936eb9` — unchanged |
| **M3 committed?** | **No.** All M3 work is uncommitted in the working tree. |

`git status --short`:

```
 M src/main/java/com/seggellion/britannia_mod/commands/PopulateOresCommand.java
D  src/main/java/com/seggellion/britannia_mod/features/AltitudeScaledFeature.java
D  src/main/java/com/seggellion/britannia_mod/features/ClusterVein.java
D  src/main/java/com/seggellion/britannia_mod/features/GeodeVein.java
D  src/main/java/com/seggellion/britannia_mod/features/LayeredVein.java
D  src/main/java/com/seggellion/britannia_mod/features/SnakeVein.java
 M src/main/java/com/seggellion/britannia_mod/features/VeinPlacementValidation.java
D  src/main/java/com/seggellion/britannia_mod/features/VerticalLayeredVein.java
D  src/main/java/com/seggellion/britannia_mod/features/VerticalVein.java
 M src/main/java/com/seggellion/britannia_mod/gametest/SilverMiningGameTests.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceCatalog.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceDefinition.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceShape.java
 M src/main/java/com/seggellion/britannia_mod/util/ModTags.java
 M src/main/resources/data/britannia_mod/ore_veins.json
 M src/main/resources/data/britannia_mod/resources/resources.json
 M src/test/java/com/seggellion/britannia_mod/features/VeinPlacementValidationTest.java
 M src/test/java/com/seggellion/britannia_mod/resource/ResourceCatalogTest.java
 M src/test/java/com/seggellion/britannia_mod/resource/ResourceCatalogValidationTest.java
?? src/main/java/com/seggellion/britannia_mod/gametest/MaterializationPolicyGameTests.java
?? src/main/java/com/seggellion/britannia_mod/resource/placement/
?? src/main/java/com/seggellion/britannia_mod/resource/shape/
?? src/main/resources/data/britannia_mod/tags/block/ore_hosts.json
?? src/test/java/com/seggellion/britannia_mod/resource/placement/
?? src/test/java/com/seggellion/britannia_mod/resource/shape/
```

19 tracked files changed (7 of them deletions totalling 474 lines), plus 20 new files: 17 source
(1,375 lines), 1 block tag, 1 GameTest class, 2 unit test classes. Net: the placement architecture
is larger than what it replaced, and the six algorithms it replaced are gone rather than wrapped.

Pre-existing unrelated work, untouched: `stash@{0}` (`On shrines-monoliths`, 2026-08-02), and the
untracked `docs/` project files in the main checkout, deliberately excluded from both commits.

---

## Recommendation

**Safe to proceed to M4.**

Every M3 exit criterion is met and independently verified rather than asserted: no active shape
calls `setBlock` or touches shared RNG (grep-verified, and the shape layer imports no Minecraft at
all); geometry is deterministic, unique, bounded and testable without a world; all placement passes
one guarded boundary that enforces build limits, host policy, fluids, block entities, indestructible
blocks, provenance, resource ownership and a work budget; chunk slicing is exact and order-free
without force-loading; repeat placement is a no-op. Both suites ran with baselines held separate and
**zero new regressions**.

Two things want your attention, neither blocking M4:

1. **The agapite curated row must be re-tuned to a geode-scale radius (3–16)**, or agapite will be
   safely skipped and reported rather than placed. The only true code-driven data dependency this
   milestone created.
2. **Silver deposits go from placing nothing to ~2,500 cells**, and tin from ~210 to ~1,180 — both
   because the air-only defect is fixed, and both close to what the legacy shapes were *asking* for.
   If those sizes are not wanted, the lever is the curated radius, not the geometry.

**Stopping after M3 as instructed. Milestone 4 has not been started, and M3 remains uncommitted.**
