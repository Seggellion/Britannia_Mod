# UltimaCraft OreVein Remediation — Milestone 7 Report

**Sedimentary Lens Geological Shape and Silica Distribution**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-21

---

## Objective

Add a reusable, deterministic sedimentary-lens shape, and use silica to prove that a resource can be introduced into natural world generation entirely through the M2–M4 platform — resource definition → distribution policy → shape → identity → chunk-safe planning → guarded materialisation — with no resource-name branching anywhere in Java.

---

## M6 amendment and commit

### Creative policy correction

The owner rejected decision B. An ordinary Creative break of a sited deposit is now **refused**, not permitted-without-yield: the block stays, nothing is yielded, no debt is filed, no skill is granted, no durability is spent, and no depletion transition occurs. Removing a deposit is an explicit administrative act.

Implemented as `ManagedResourceCreativeGuard`, a `HIGHEST`-priority `BreakEvent` listener that cancels ahead of both the Mining gate and the deposit handler, tells the player why, and resyncs the client (a Creative client removes the block optimistically).

**Two scoping decisions, stated rather than buried:**

- **Ambient rock is exempt.** `minecraft:stone`, `granite`, `deepslate`, `cobblestone` and the rest of the vanilla STONE family are managed resources in the catalogue, but they are the world's crust, not sited deposits. Refusing Creative breaks on them would stop builders terraforming, cutting cellars, or clearing house plots, for no protective benefit. The line is drawn in `ManagedExtractionPolicy.isDepositCell` from the resource family and the block's namespace — ORE and SEDIMENT are always deposits; STONE is a deposit only when the block is mod-owned (`britannia_mod:sandstone_deposit` and the four bespoke rocks). A resource added later inherits the right answer without being named. **If ambient rock should be protected too, it is one line.**
- **Player-placed blocks are still removable.** Provenance is asked first, so a builder may always remove what they placed.

**Existing explicit administrative removal was preserved, and none was added.** `/manageddeposit` and `/populateores` remain as they were; the complete operator suite stays M8, per instruction.

Also amended: `MiningGateGameTests.creativeBypassesTheThresholdWithoutGain` asserted the operator *deleted* the ore — the superseded behaviour. It is now `…WithoutGainOrDeletion`, asserting the bypass verdict directly and that the ore survives.

### Regression results

| Run | Result |
| --- | ------ |
| Focused M6 policy GameTests | all pass, including 3 new/amended Creative tests |
| `gradlew test` | **2,927 run · 5 failed · 0 errors · 17 skipped** |
| `gradlew runGameTestServer` | **689 run · 2 failed** |
| `gradlew runGameTestServer` (`--rerun-tasks`) | **689 run · 2 failed** |

The only failures are the established baseline pair (`aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`) and the five known JUnit failures. The intermittent textile GameTest appeared in one of five runs and passed in the others. **No new deterministic regression. M6 remains fully closed after the amendment.**

### Commit

**`bac36d96988c4bf85773e25aad36dd152cebce11`** — `fix(resources): harden managed extraction policies`. Worktree verified clean before M7 began.

### Post-M6 baseline, used as the M7 baseline

**JUnit 2,927 / 5 failed / 17 skipped · GameTests 689 / 2 failed.**

---

## Findings

### 1. `ChunkEvent.Load` is on the server thread, and `isNewChunk()` means exactly what M7 needs

Both established from the 1.21.1 sources rather than assumed.

`ChunkEvent.Load` is posted from `ChunkStatusTasks.full`, and that task is submitted with an executor that routes through `mainThreadMailBox()`. It therefore arrives **on the server thread**. That is what makes it safe to touch the deposit ledger (`SavedData`) and to write blocks.

`isNewChunk()` is `!(protochunk instanceof ImposterProtoChunk)`. A chunk read from disk at full status is always reconstructed as an `ImposterProtoChunk` (`ChunkSerializer` line 249), so the flag is true **only for a chunk that has just finished generating**. Existing worlds are therefore never retro-populated, and no persistent "already processed" marker is needed to guarantee it — which is a meaningful simplification, because such a marker would grow with every chunk ever generated.

### 2. A vanilla `PlacedFeature` would have been the wrong mechanism

Features run on world-generation worker threads. A natural deposit must register in the persistent ledger before it may place anything, and doing that from a generation thread races every other concurrently generating chunk and the server's own save. There is no way to make it safe that does not amount to moving the work back to the server thread — so it is done there directly. Nothing is lost: geometry is still deterministic, distribution is still seed-derived, and the deposit still appears as the chunk generates.

### 3. The GameTest harness cannot show accepting-side worldgen — but the biome source can be built directly

`GameTestServer` runs `WorldPresets.FLAT`, so no desert exists in it. Rather than accept a blind spot, the audit builds the **real Overworld generator** inside the GameTest from the server's own registries (`MultiNoiseBiomeSource.createFromPreset` + `NoiseBasedChunkGenerator` + `RandomState.create`). Biome and surface height are noise functions, so this answers for 76,800 chunks of genuine terrain across three seeds in seconds, without generating a single chunk. This technique is reusable and is the strongest evidence in this report.

### 4. A 4,096-chunk force-load in a `minecraft:load` function hangs the server

Discovered while attempting a generated-world audit. Sixteen `forceload add` commands (256 chunks each) issued from a datapack load function left the server thread blocked with no tick output for 25 minutes. **The mod is not implicated**: an identical run with a single 256-chunk box ticked normally throughout while natural generation was active at elevated density. This is a harness limit worth recording for any future milestone that wants a large generated sample.

### 5. The shipped rarity, as first configured, was unfindable

The first configuration (cell 24 chunks, chance 0.35) measured at **one accepted deposit per 21,000–74,000 chunks**. A player would never meet silica. Retuned to cell 10 / chance 0.85 — see *Statistical validation*. This is exactly the failure that inspecting one pretty deposit would have missed.

### 6. The altitude band is inert for the current biome set

Once the biome gate is asked first, **zero** candidates are rejected on altitude across all three seeds. The `min_y`/`max_y` band never disagrees with a desert or a beach; it is a safety rail against a future biome addition, not an active filter. Reported rather than removed.

---

## Natural-generation architecture

```
world seed
  │
  ├─ NaturalDepositSelector.candidateFor(seed, resource, natural, cellX, cellZ)   [PURE]
  │     occurrence roll · origin in the central half of the cell · radius · depth
  │     └─ DepositIdentity.natural(seed, dimension, resourceId, cellX, cellZ, salt)   ← M4 contract
  │
  ▼
ChunkEvent.Load  (isNewChunk, ServerLevel)                       ← SERVER THREAD from here down
  │
  └─ NaturalDepositService.populate(level, chunk)
        │
        ├─ candidatesReaching(chunk)                             [PURE]  — 4 owner cells
        ├─ BiomeSource.getNoiseBiome(origin)                     — noise, loads nothing
        ├─ ChunkGenerator.getBaseHeight(origin)                  — noise, loads nothing
        ├─ PlacementPlanner.plan(resource, origin, radius, seed) [PURE]  — M3
        │     └─ SedimentaryLensPlanner                          [PURE]
        ├─ DepositRegistrar.describe → DepositLedger.register    ← PERSISTENT MUTATION
        │     REGISTERED / ALREADY_REGISTERED / CONFLICT / REVISION_MISMATCH   — M4, unchanged
        └─ MaterializationService.materializeChunk(level, plan, chunk)   ← WORLD MUTATION
              this chunk's slice only · UPDATE_CLIENTS only · host policy
```

**Asynchronous-safe portions:** everything marked `[PURE]` — the selector and the shape planner. They touch no level, no chunk, no block and no shared RNG, and are driven directly by unit tests with no server at all.

**Persistent and world mutation:** only inside `NaturalDepositService.populate`, reached only from `ChunkEvent.Load`, which is server-thread by the contract established above. Ledger registration is the only persistent write; `MaterializationService` is the only world write.

**No neighbouring chunk is force-loaded.** The two world questions are noise queries. Materialisation is restricted to `positionsIn(chunk)`. Writes use `Block.UPDATE_CLIENTS` (flag 2), so no neighbour update escapes the chunk. Asserted in `aLensAcrossFourChunksIsOneDepositInEitherOrder` by comparing `getLoadedChunksCount()` before and after.

**Freedom from resource-name branching:** `populate` iterates `naturalResourcesIn(level)`, which is every catalogue entry whose `generation.natural` names this dimension. `NaturalDepositSelectorTest.aSyntheticSecondResourceDistributesWithoutAnyCodeOfItsOwn` drives the whole selector with a synthetic resource that shares nothing with silica but the platform — different id, salt, shape tuning, cell size, radius band — and asserts it gets the standard natural identity and its own independent distribution.

---

## Sedimentary lens

`britannia_mod:sedimentary_lens` — `SedimentaryLensPlanner`, implementing the M3 pure-planner contract exactly (no world, no `ServerLevel`, no mutation, no shared RNG, deterministic, unique cells, declared bounds, refuses bad configuration before planning).

### Geometry

For each column `(x, z)` within the footprint:

| Step | Rule |
| ---- | ---- |
| **Elliptical footprint** | `d = sqrt((x/rx)² + (z/rz)²)` with `rx`/`rz` differing by a seed-derived aspect in `[0.62, 1.0]`; which axis is narrow is also seed-derived |
| **Ragged rim** | `rim = 1 + irregularity · smoothSigned2(seed, x, z, 11)`; the column is in the bed when `d ≤ rim`. Applied to the *boundary*, not per cell, so the outline is irregular without being noisy |
| **Lens profile** | `taper = sqrt(1 − (d/rim)²)`; `columnHalf = round(halfThickness · taper)`. Full thickness at the core, one cell at the rim |
| **Undulating median** | `median = round(drift · taper · smoothSigned2(seed, x, z, 17))`, `drift = halfThickness/2`. Scaled by the same taper, so the rim pinches out on a level datum while the body dips |
| **Course** | Every cell from `y = 0` to `median` is always present |
| **Porosity** | `columnHalf` cells above and below the median, each dropped with probability `gapChance` |
| **Single body** | Connected components computed; only the largest is kept |

**Two decisions worth their reasoning.** `round` rather than `floor` on the taper: flooring looks equivalent and is not — the taper is within a whisker of 1.0 across the whole middle of the bed, so `floor(2 × 0.993)` is 1 and only the single cell at the exact origin ever reaches full thickness, producing a bed of uniform thinness with a pinprick in the centre. And the **course guarantees connectivity**: every column contains `y = 0`, and the accepted columns are the interior of one closed boundary curve, so the datum plane is one sheet and the whole body hangs off it. Gaps are only ever applied above or below the course, so removing one cannot detach anything. A smoothly wandering rim can still pinch a small lobe loose at small radii, so the largest-body filter makes the guarantee unconditional rather than probable — asserted by a six-connectivity flood fill over 24 radius/seed combinations.

### Configuration and validation

Shape knobs live in `ShapeTuning` (`thickness`, `irregularity`, `gapChance`), carried on `ShapeConfig` as a fourth component with a three-argument constructor retained, so every pre-existing caller is untouched. Validation is in the shape and configuration layer, never in command code:

| Rule | Where |
| ---- | ----- |
| thickness `0…16`, irregularity `0…1`, gap chance `0…0.9` | `ShapeTuning` constructor |
| radius ≥ 6 (usable with the default thickness) | `SedimentaryLensPlanner.minimumRadius` |
| radius ≥ 1.5 × thickness — "broader than it is deep" | `SedimentaryLensPlanner.validate` |
| planning-cost ceiling `π·r²·(2·half + drift + 1) ≤ 20,000` | `SedimentaryLensPlanner.validate` |
| both ends of a resource's configured radius range must plan | `ResourceCatalog.parseNatural` — at load |

The cost ceiling is a strict upper bound (it ignores the aspect, the taper and the gaps, all of which only reduce the count), so it can never admit a configuration that would then blow `ShapePlan.MAX_CELLS`. In practice it caps a 5-thick bed at radius ~30.

**The load-time check earned its place immediately**: the first silica configuration used radius 8 with thickness 5, and the server refused to start with *"a sedimentary lens must be broader than it is deep: radius 6 with thickness 5 needs a radius of at least 8"*.

---

## Silica distribution definition

| Policy | Value | Provisional? |
| ------ | ----- | ------------ |
| Dimension | `minecraft:overworld` | no |
| Biome tag | `britannia_mod:has_silica_deposits` | membership provisional |
| Host tag | `britannia_mod:silica_hosts` | no |
| Altitude band | y 40 … 96 (safety rail; rejects nothing today) | no |
| Depth below surface | 5 ± 2 | **provisional** |
| Grid / spacing | owner cell of 10 chunks (160 blocks); origin in the central half, so adjacent origins are ≥ 80 blocks apart | **provisional** |
| Occurrence chance | 0.85 per owner cell | **provisional** |
| Radius | 8 … 13 | **provisional** |
| Thickness | 5 (irregularity 0.20, gap chance 0.18) | **provisional** |
| Salt | 7311 | no |
| Shape | `sedimentary_lens` | no |
| Regeneration | **24 h** — unchanged | no |

Everything marked provisional is balancing configuration, not game-design truth. There is no economic evidence in the repository for the ideal size or frequency of a silica bed, so these were chosen conservatively from a measured sweep and should be revisited at M9 with real economy data. See *Remaining balancing*.

---

## Biome membership

`britannia_mod:has_silica_deposits` = **`minecraft:desert`, `minecraft:beach`**.

The repository has no biome tags and no custom biomes; existing biome modifiers name vanilla biomes explicitly, so this is the first biome tag and it follows the same vocabulary.

| Biome | Why |
| ----- | --- |
| `minecraft:desert` | Aeolian quartz sand — well-sorted, mature, high-silica. Deep sand over sandstone gives the host material the lens actually needs, so acceptance is high and beds sit inside real sediment. This is the archetypal silica source and, empirically, 61 of the 68 accepted deposits |
| `minecraft:beach` | Marine quartz sand, the other classic industrial silica source. Thin sand over stone, so beds are more marginal, but geologically correct |

**Deliberately excluded:**

- **Badlands** (`minecraft:badlands`, `eroded_badlands`, `wooded_badlands`). Red sand is iron-stained — geologically the *opposite* of high-purity silica. Excluding it is the evidence-based call, and it is why `minecraft:red_sand` and `red_sandstone` are absent from the host tag too.
- **Warm/ocean biomes.** Sandy floors, but underwater, and `MaterializationService` rejects fluid cells — the deposits would exist in the ledger and barely in the world.
- **Snowy beach, stony shore, rivers.** Either negligible sand or not a silica context. None were added to raise occurrence counts.

Two members is a deliberately narrow first pass, and it is the single biggest lever on how common silica is.

---

## Host membership

`britannia_mod:silica_hosts` = **`minecraft:sand`, `minecraft:sandstone`**.

Every `sandstone` block this mod registers is a *building* block — walls, posts, windows, battlements, brick roads. None is natural terrain, so none belongs in a host tag. `britannia_mod:sandstone_deposit` is a managed resource and is excluded by definition (and rejected independently by the managed-resource collision rule). `minecraft:suspicious_sand` is excluded: it carries a block entity, and `MaterializationService` rejects those anyway.

The narrowness is the point — silica cannot tunnel through stone, soil, gravel, fluids, containers or player construction, because none of those is a host.

`MaterializationService` continues to own fluid rejection, block-entity rejection, provenance/player-block rejection, managed-resource collision rejection, and indestructible-block rejection. M7 added no second opinion about any of them.

---

## Statistical validation

### Candidate-configuration comparison

Five grid settings × three radius bands × three thicknesses were measured over four seeds before choosing. Representative rows (planned cells per deposit, before host rejection):

| Cell | Chance | Radius | Thickness | Chunks/candidate | Cells min/median/mean/max | Nearest pair |
| ---: | -----: | -----: | --------: | ---------------: | ------------------------- | -----------: |
| 16 | 50% | 6–9 | 3 | 512 | 118 / 309 / 327 / 691 | 63 |
| 24 | 35% | 8–13 | 3 | 1,646 | 207 / 626 / 656 / 1,360 | 111 |
| **24** | **35%** | **8–13** | **5** | **1,646** | **309 / 906 / 952 / 1,968** | **111** |
| 24 | 35% | 10–16 | 5 | 1,646 | 544 / 1,382 / 1,450 / 2,871 | 96 |
| 32 | 35% | 8–13 | 5 | 2,926 | 207 / 626 / 656 / 1,360 | 91 |
| 40 | 50% | 10–16 | 5 | 3,200 | 369 / 953 / 997 / 2,074 | 108 |

The 24/35%/8–13/5 row was chosen first — mid-range on every axis, deposits under a thousand cells. **Measuring it against real terrain showed it was wrong**, which is the whole reason the audit exists.

### Real-terrain audit — the rejected first configuration

Three seeds × 147,456 chunks = **442,368 chunks** of genuine Overworld:

| Seed | Candidates | Accepted | Rejected (biome) | Accepted rate |
| ---- | ---------: | -------: | ---------------: | ------------- |
| 1 | 87 | 2 | 77 | 1 per 73,728 chunks |
| 987654321 | 78 | 2 | 62 | 1 per 73,728 chunks |
| −4242 | 83 | 7 | 59 | 1 per 21,065 chunks |

**One deposit per 21,000–74,000 chunks.** Unfindable. Retuned to cell 10 / chance 0.85 — an 8.5× density increase — and the origin-placement rule was changed from a reach-sized margin to the central half of the cell, so that shrinking the cell could not let neighbouring deposits approach each other.

### Real-terrain audit — the shipped configuration

Seed / bounds / chunks, three seeds × 256 owner cells = **25,600 chunks each, 76,800 total**. Method: the real `MultiNoiseBiomeSource` and `NoiseBasedChunkGenerator` for each seed, queried at every candidate origin. No chunks generated.

| Metric | Seed 1 | Seed 987654321 | Seed −4242 | Total |
| ------ | -----: | -------------: | ---------: | ----: |
| Owner cells | 256 | 256 | 256 | 768 |
| Chunks covered | 25,600 | 25,600 | 25,600 | 76,800 |
| Candidates | 205 | 214 | 223 | **642** |
| Accepted | 4 | 1 | 63 | **68** |
| Rejected — biome | 201 | 213 | 160 | 574 |
| Rejected — altitude | 0 | 0 | 0 | **0** |
| Accepted rate | 1 / 6,400 | 1 / 25,600 | 1 / 406 | — |
| Biomes of accepted | beach ×4 | beach ×1 | desert ×61, beach ×2 | desert 61, beach 7 |
| Altitude min/median/max | 56 / 60 / 62 | 59 | 42 / 61 / 79 | 42 … 79 |
| Nearest accepted pair | 178 blocks | — | 95 blocks | ≥ 95 |

**Acceptance 10.6%.** The per-seed spread — 1 per 406 chunks against 1 per 25,600 — is not noise; it is the design working. Silica is common in a sandy region and absent from a jungle one, which is what "regionally uncommon" should mean.

### Deposit size

Over all 642 candidates: **min 348, median 921, mean 952, max 1,937 planned cells.** Well under the "thousands of cells" the brief warns against without economic evidence. Host rejection reduces the materialised count further; that fraction is a stated gap below.

### Offline distribution statistics

`NaturalDistributionStatisticsTest`, four seeds × 48,400 owner cells each, asserts: the occurrence chance is honoured within 2%; the grid guarantees a half-cell (80 block) minimum separation; the whole configured radius range occurs; radius and depth never escape their configured bands; a chunk considers exactly 4 owner cells.

### What the audit does not cover

**Host rejection rate in real terrain.** That needs real blocks. Three generated-world runs were made (256 chunks each, elevated density) and all three landed in ocean biomes, which correctly produced nothing. The host policy itself is proven by `onlyApprovedHostsBecomeSilica`, which drives ten different block types through the real materialisation path. The *percentage* of planned cells rejected by host policy in a real desert is **not measured** and is carried forward as debt.

---

## Chunk-order / identity proof

`aLensAcrossFourChunksIsOneDepositInEitherOrder` plans a radius-13 lens on a chunk corner, confirms it spans ≥ 4 chunks and that all of them are already loaded, then:

| Assertion | Result |
| --------- | ------ |
| Four chunks register the deposit; exactly one creates it | ✅ 1 `REGISTERED`, rest `ALREADY_REGISTERED` |
| Ledger grows by exactly one entry | ✅ |
| Slices materialised A→D and D→A produce the identical union | ✅ cell for cell |
| Union size equals the full plan | ✅ |
| Loaded-chunk count unchanged across the whole operation | ✅ no force-loading |
| Re-processing a chunk creates no duplicate | ✅ `ALREADY_REGISTERED` |

`aRevisedDefinitionIsReportedRatherThanRegenerated` offers the same instance under a bumped definition revision: the ledger answers `REVISION_MISMATCH`, refuses materialisation, and keeps the original revision. M4's conservative rule is intact and reconciliation remains M8's.

**Restart between slices** is covered structurally rather than by restarting a server: the identity is a pure function of `(worldSeed, dimension, resourceId, cell, salt)` with no runtime state, asserted by `theIdentityIsTheMilestoneFourNaturalContract` and `everyChunkTheDepositReachesDerivesTheIdenticalCandidate`, and the ledger is `SavedData` that already survives restart by M4's own tests. The remaining risk is not that the id changes but that the ledger fails to persist, which M4 proved.

---

## End-to-end silica proof

`aNaturallyMaterialisedBedIsWorkedAndComesBack` runs the whole chain in one test, so no step is proved against a fixture the previous step would not have produced:

```
sand host → plan (sedimentary_lens) → register (NATURAL, M4 identity) → materialise
   → survival player with Britannia shovel → EXTRACTED
   → exactly one yield, and not ordinary sand
   → exactly one durability point
   → restoration debt filed, holding the silica block
   → regeneration resolves to 86,400,000 ms (24 h)
   → debt aged past its own delay, real scheduler restores
   → bed present again, ledger entry intact, origin unchanged
```

`aGeneratedBedObeysEveryExtractionPolicy` confirms the M6 policies hold for a generated bed: vanilla shovel `WRONG_TOOL`, Britannia pickaxe `WRONG_TOOL`, bare hand `WRONG_TOOL`, fake player `DENIED_ACTOR` — every refusal leaving the bed standing, with no yield and no durability cost. Fortune, Silk Touch and the Creative refusal are covered for silica by the M6 suite, which runs silica as one of its four families.

---

## Changes

| File | Change | Reason |
| ---- | ------ | ------ |
| `resource/shape/ShapeNoise.java` | Added `smooth2` / `smoothSigned2` value noise | White noise cannot describe a surface; a bed's rim and thickness must drift smoothly |
| `resource/shape/ShapeTuning.java` | **New.** thickness / irregularity / gap chance, self-validating | The first shape whose geometry is not fully described by a radius |
| `resource/shape/ShapeConfig.java` | Fourth component `tuning`, three-arg constructor retained | Every pre-existing caller compiles unchanged |
| `resource/shape/planner/SedimentaryLensPlanner.java` | **New.** The lens | The milestone's reusable geological shape |
| `resource/ResourceShape.java` | Registered `SEDIMENTARY_LENS` | Data can name it |
| `resource/natural/NaturalGeneration.java` | **New.** Distribution policy as data, self-validating | Natural occurrence is opt-in per resource, not eight fields on every resource |
| `resource/natural/NaturalDepositSelector.java` | **New.** Pure owner-cell selector | Deterministic candidates, analysable without a server, identical from every chunk |
| `resource/natural/NaturalDepositService.java` | **New.** Server-thread placement | The only place natural deposits reach the ledger and the world |
| `resource/natural/NaturalGenerationHandler.java` | **New.** `ChunkEvent.Load` listener | The safe seam; documents why not a `PlacedFeature` |
| `resource/ResourceDefinition.java` | `Generation` carries optional `natural`; `natural()` accessor | Opt-in through data |
| `resource/ResourceCatalog.java` | Parses `natural` and `tuning`; validates both radius extremes at load; the M2 blanket refusal of sediment generation narrowed to "beds generate as lenses" | M2's rule named milestone 7 as the moment to lift it |
| `resources.json` | Silica gains `generation` + `natural` | The proof resource |
| `tags/worldgen/biome/has_silica_deposits.json` | **New.** desert, beach | Curated occurrence, data-driven |
| `tags/block/silica_hosts.json` | **New.** sand, sandstone | Narrow host policy |
| `BritanniaMod.java` | Registers `NaturalGenerationHandler` | Wiring |
| `gametest/NaturalSilicaGameTests.java` | **New.** 9 GameTests | Biome refusal, host policy, chunk-order identity, revision mismatch, end-to-end, M6 policies |
| `gametest/SilicaDistributionAuditGameTests.java` | **New.** 2 GameTests | The real-terrain statistical audit |
| `test/…/SedimentaryLensPlannerTest.java` | **New.** 16 tests | Determinism, bounds, taper, ellipse, connectivity, slicing, configuration limits |
| `test/…/NaturalDepositSelectorTest.java` | **New.** 10 tests | Determinism, identity contract, independence, spacing, the synthetic second resource |
| `test/…/NaturalDistributionStatisticsTest.java` | **New.** 7 tests | Occurrence rate, rarity band, spacing, size, ranges, per-chunk cost |
| `test/…/ResourceCatalogTest.java` | Two tests updated | Silica now generates; the placeable set is ten, not nine |
| `test/…/ResourceCatalogValidationTest.java` | One test updated | The sediment rule narrowed rather than disappeared |
| `gametest/OreVeinContainmentGameTests.java` | Placeable count 9 → 10, plus an explicit silica assertion | Silica gained a shape |

---

## Tests

| Test / command | Result |
| -------------- | ------ |
| `gradlew test --tests *SedimentaryLensPlannerTest*` | **16 run · 0 failed** |
| `gradlew test --tests *NaturalDepositSelectorTest*` | **10 run · 0 failed** |
| `gradlew test --tests *NaturalDistributionStatisticsTest*` | **7 run · 0 failed** |
| `gradlew test` (full JUnit) | **2,960 run · 5 failed · 0 errors · 17 skipped** |
| `gradlew runGameTestServer` | **700 run · 2 failed** |
| `gradlew runGameTestServer` (`--rerun-tasks`) | **700 run · 2 failed** |
| `gradlew build -x test` | **BUILD SUCCESSFUL** |
| Data validation | catalogue, tag and shape validation all run at server start; three dedicated-server boots loaded the new tags and resource data with no error |

### Baseline comparison

| | Post-M6 baseline | M7 | Delta |
| --- | --- | --- | --- |
| JUnit run | 2,927 | 2,960 | **+33** |
| JUnit failed | 5 | 5 | **0** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 689 | 700 | **+11** |
| GameTests failed | 2 | 2 | **0** |

### Failure classification

**Known JUnit baseline failures — 5, unchanged:** `RoutineSkillGainPresentationTest`, `structure.hardening.MilestoneEightContentReportTest`, `structure.render.CorrectiveMilestoneNineARenderAlignmentTest`, `structure.render.MonolithMilestoneSevenRenderingTest` (2).

**Known pre-existing GameTest failures — 2, unchanged:** `aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`. Not investigated, per instruction.

**Intermittent textile flake:** did not appear in any of the four final M7 GameTest runs.

**New deterministic regressions: none.**

Transient failures encountered and resolved during development, none surviving: three tests used fixed deposit ids that collided with ledger entries left by *earlier runs* (the ledger is `SavedData` and persists between runs — the conflict semantics working exactly as designed); ids are now derived from position through the real natural contract. Three existing tests encoded superseded facts (sediment beds carry no generation; nine placeable resources; the blanket sediment refusal) and were updated to the new rules rather than around them.

---

## Performance observations

| Measure | Value |
| ------- | ----- |
| Owner cells considered per generated chunk | **4** (asserted; the search window is `⌈(16 + 2·reach)/cellBlocks⌉ + 2` per axis) |
| Maximum candidates reaching one chunk | 4 |
| Planner cells for a representative lens | ~920 (median), 1,937 (largest observed) |
| Planner cells, hard ceiling | 20,000 (`ShapePlan.MAX_CELLS`), refused before planning |
| Materialisation writes per chunk slice | bounded by the slice; budget 4,096 |
| Work touching neighbouring or unloaded chunks | **none** — biome and height are noise queries; writes are `positionsIn(chunk)` with `UPDATE_CLIENTS` |
| Persistent ledger operations per chunk | at most one `register` per candidate (≤ 4), and `register` is a map lookup |
| Observed effect on a live server | a 256-chunk force-load with natural generation at ~8× shipped density ticked normally for 13 minutes; the server-thread block seen at 4,096 chunks reproduced without the mod's involvement |

Full profiling remains M9's.

---

## Invariants now established

| Invariant | Status |
| --------- | ------ |
| Silica can generate naturally | ✅ Registry-level, policy-level and end-to-end; 68 accepted deposits across 76,800 chunks of real terrain |
| Lens geometry is deterministic | ✅ Identical seed → identical cells, in order |
| Natural distribution is deterministic | ✅ Pure function of seed and owner cell; no runtime RNG, no generation-order dependence |
| One multi-chunk deposit has one id | ✅ Four chunks, one `REGISTERED`, one ledger entry |
| Generation order cannot reroll it | ✅ Forward and reverse unions identical, cell for cell |
| Neighbouring chunks are not force-loaded | ✅ Loaded-chunk count unchanged; noise-only world queries |
| Only approved biomes and hosts are used | ✅ Two biomes, two host blocks, both data-driven; refusal proven in a plains chunk |
| Silica never becomes ordinary sand | ✅ Sand is a *host*, not the resource; only 10.6% of candidates are even sited, and each occupies ≤ 1,937 cells |
| M6 extraction policy remains intact | ✅ Re-proven against a generated bed |
| 24-hour restoration works for naturally generated silica | ✅ End-to-end, using the real scheduler and the real 86,400,000 ms delay |
| A second resource needs no resource-name Java switch | ✅ Synthetic resource drives the full selector and gets standard identity |
| Existing chunks are untouched | ✅ `isNewChunk()` is false for any chunk read from disk |

---

## Production readiness

### Silica natural generation — technically ready, balancing provisional

The mechanism is ready: deterministic, chunk-safe, thread-correct, identity-stable, restoration-integrated, and free of resource-specific branching. What is **not** settled is the balancing. The frequency was already wrong once and was corrected only because it was measured; the current values are a conservative second attempt with no economic evidence behind them. The host-rejection fraction in real desert terrain is unmeasured, so the *materialised* size of a bed is an estimate rather than a figure.

Silica natural generation could ship independently of M5 suppression — it adds a resource rather than removing one, so it cannot create a gap.

### M5 suppression — still production-gated

**Unchanged by M7.** M7 introduces controlled natural generation for **silica only**. It does not provide replacement generation for any suppressed family:

| Suppressed family | Controlled replacement generation after M7 |
| ----------------- | ------------------------------------------ |
| Coal | **none** |
| Iron | **none** |
| Gold | **none** |
| Redstone | **none** — and no managed resource identity |
| Diamond | **none** — and no managed resource identity |
| Lapis | **none** — and no managed resource identity |
| Emerald | **none** |
| Copper | **none** |

Activating M5 on production would still empty eight families out of every newly generated chunk. **M5 remains not approved for production activation.**

### Iron/copper noise veins — unresolved, and a required pre-production item

Carried forward verbatim and deliberately not worked on during M7:

- Denied vanilla **placed-feature** ore generation **is** suppressed — all 19 features, in every `#minecraft:is_overworld` biome, verified at registry level and by block counts over 256 chunks.
- Minecraft's **separate `OreVeinifier` mechanism** still produces vanilla mineral material: `minecraft:copper_ore` + `raw_copper_block` at y0–50, and `minecraft:deepslate_iron_ore` + `raw_iron_block` at y−60…−8. It is enabled by `ore_veins_enabled` on the Overworld's *noise settings*, is not a feature, and no biome modifier can reach it.
- Measured residue: **~4.0% of vanilla copper and ~3.4% of vanilla iron** survive suppression, in 256 fixed-seed chunks against a same-seed control.
- **UltimaCraft therefore does not yet have absolute authority over every natural iron and copper occurrence.**
- M5 suppression remains **not approved for production activation**.
- **Complete iron/copper noise-vein suppression or remediation is a required pre-production item.**

Nothing in M7 changed noise settings, terrain generation, or vanilla iron/copper behaviour.

**Recommendation: a dedicated milestone before M9, not a task buried inside it.** Three reasons. It is a *different mechanism* from everything the programme has touched — a dimension-level noise setting, not a biome or a feature — and the two credible fixes (overriding the whole Overworld `noise_settings`, or neutering the `ore_veininess` noise parameters) both have blast radii that reach terrain and geology, including the granite and tuff the veins deposit as filler. It has its own audit shape: a before/after fixed-seed comparison of granite and tuff volumes, not just ore counts. And burying it in M9 puts a decision with terrain-wide consequences into the same milestone as economic balancing, where it will be judged against the wrong evidence. Call it **M8.5**, gate M9 activation on it, and give it the same before/after control-world treatment M5 used.

**Full world-resource authority must not be called complete while this is open.**

---

## Remaining balancing

| Item | Status |
| ---- | ------ |
| Agapite live Rails radius `55 → 8` | Carried forward, untouched |
| Silver/tin tuning | Carried forward, deferred to M9 |
| **Silica frequency** | **Provisional.** Corrected once already, from 1 per 21,000–74,000 chunks to 1 per 406–25,600 depending on regional sandiness. Wants play-testing |
| **Silica deposit size** | **Provisional.** Median 921 planned cells; the materialised figure is unmeasured pending a real-terrain host-rejection measurement |
| **Silica biome membership** | **Provisional.** Two biomes; the single biggest lever on how common silica is |
| **Host rejection rate in real terrain** | **Unmeasured.** Three generated-world attempts landed in ocean; needs a desert-seeded run or a deposit placed by command in a desert |
| M5 production activation gate | Open, unchanged |
| Replacement-generation gaps | Open, unchanged — eight families |
| Iron/copper noise veins | Open — recommended as a dedicated pre-M9 milestone |
| Synchronous Rails fetch | Open — M8 |
| Rails stable row id | Open — M8 |

---

## Playbook impact

**M8 needs two amendments:**

1. The **operator suite** should include explicit deposit removal. M6's Creative refusal is correct, but it means an operator currently has no complete by-hand route to remove a badly sited deposit — including a naturally generated one, which M7 has now made possible to encounter.
2. **Existing-world retrofit** now has a second population to think about: worlds generated before M7 have no natural silica, and `isNewChunk()` deliberately never revisits them. Whether that is acceptable or wants a retrofit pass is an M8 decision.

**M9 needs one amendment:** its balancing pass inherits four provisional silica values and an unmeasured host-rejection figure, and should re-run the audit in this report (the technique is reusable, and `SilicaDistributionAuditGameTests` is the harness).

**A new milestone is recommended before M9** for the iron/copper noise veins, as argued above.

**Nothing else in M8/M9 needs restructuring.** The natural-generation seam is general: a second resource joins it by gaining a `natural` block in `resources.json`, which is what M8's iron/gold cutover and any future replacement generation will need.

---

## Git state

| | |
| --- | --- |
| M6 commit | `bac36d96988c4bf85773e25aad36dd152cebce11` — `fix(resources): harden managed extraction policies` |
| Current HEAD | `bac36d96988c4bf85773e25aad36dd152cebce11` — **unchanged; M7 is uncommitted** |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| Worktree | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |

```
$ git status --short
 M src/main/java/com/seggellion/britannia_mod/BritanniaMod.java
 M src/main/java/com/seggellion/britannia_mod/gametest/OreVeinContainmentGameTests.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceCatalog.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceDefinition.java
 M src/main/java/com/seggellion/britannia_mod/resource/ResourceShape.java
 M src/main/java/com/seggellion/britannia_mod/resource/shape/ShapeConfig.java
 M src/main/java/com/seggellion/britannia_mod/resource/shape/ShapeNoise.java
 M src/main/resources/data/britannia_mod/resources/resources.json
 M src/test/java/com/seggellion/britannia_mod/resource/ResourceCatalogTest.java
 M src/test/java/com/seggellion/britannia_mod/resource/ResourceCatalogValidationTest.java
?? src/main/java/com/seggellion/britannia_mod/gametest/NaturalSilicaGameTests.java
?? src/main/java/com/seggellion/britannia_mod/gametest/SilicaDistributionAuditGameTests.java
?? src/main/java/com/seggellion/britannia_mod/resource/natural/
?? src/main/java/com/seggellion/britannia_mod/resource/shape/ShapeTuning.java
?? src/main/java/com/seggellion/britannia_mod/resource/shape/planner/SedimentaryLensPlanner.java
?? src/main/resources/data/britannia_mod/tags/block/silica_hosts.json
?? src/main/resources/data/britannia_mod/tags/worldgen/
?? src/test/java/com/seggellion/britannia_mod/resource/natural/
?? src/test/java/com/seggellion/britannia_mod/resource/shape/SedimentaryLensPlannerTest.java
```

**M7 files changed:** 10 modified, 11 new (2 shape, 4 natural-generation, 2 tags, 2 GameTest classes, 3 test classes — 11 paths above expand to those files).

**M7 remains uncommitted**, pending review. The stash is untouched, no unrelated files are staged, and the project documentation in the main checkout is outside this worktree. The audit worlds and their datapacks live under `run/`, which is gitignored; the audit's temporary density override was restored on every exit path and the shipped configuration was verified afterwards (`cell_chunks = 10`, `chance = 0.85`).

---

## Recommendation

### Safe to proceed to M8

The milestone's actual deliverable — a natural-generation seam a future resource can use without Java — is built and demonstrated with a synthetic second resource. The threading question was answered from the sources rather than assumed, and the answer determined the design. The shape is a real lens rather than a renamed seam, and its most important property is guaranteed by construction and asserted by a connectivity test rather than by argument. The chunk-order proof, which everything else rests on, passes forward and backward with one ledger entry and no force-loading.

Two qualifications, both honest rather than blocking:

1. **The balancing values are provisional and one of them was already wrong.** The first configuration would have made silica effectively unfindable and was caught only by measuring against real terrain. The current values are better-evidenced but still unvalidated by play.
2. **Host rejection in real terrain is unmeasured**, so the materialised size of a bed — as opposed to its planned size — is an estimate. Closing that needs one generated world in a desert region.

### The branch is **not** safe for production activation of M5 suppression

Unchanged and unchangeable by this milestone. M7 adds controlled natural generation for silica alone; coal, iron, gold, redstone, diamond, lapis, emerald and copper still have none, and three of them have no managed resource identity at all. Separately, the `OreVeinifier` residue means vanilla iron and copper still generate by a route the suppression cannot reach, so **full world-resource authority is not complete** and should not be described as such.

**Stopping after M7 as instructed. Milestone 8 not begun.**
