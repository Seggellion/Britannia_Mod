# UltimaCraft OreVein Remediation — Milestone 9 Report

**Performance, Regression, Live-World QA, and Rollout Readiness**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-21 · nothing pushed

> **Two verdicts, deliberately separate.**
> **M9A — Engineering platform: PASS.**
> **M9B — Production suppression rollout: BLOCKED** on replacement completeness.

---

## Objective

Prove the resource platform built through M1–M8.5 is technically safe for a persistent multiplayer world at projected scale — and establish, separately and without conflating the two, whether the vanilla suppression it enables may be turned on in production.

M9 tests the integrated system. It does not redesign it, and it does not implement the missing replacement resources.

---

## Findings

**No architectural defect was found.** Every scale, persistence and authority property held on first measurement; nothing in the platform needed fixing, and **no production file was changed by this milestone**. That is the intended shape of a validation milestone and is worth stating plainly rather than manufacturing changes to look busy.

Four things worth recording:

1. **Cluster has the least headroom of any shape.** At its configured maximum radius of 22 it plans 15,105 cells — **75.5% of the 20,000-cell cap**. Every other shape sits between 2.1% and 47.5%. M3 tightened this maximum from a higher value for exactly this reason; the margin is real but it is the one to watch if anybody retunes cluster geometry.
2. **Every silica bed crosses a chunk border.** 1,721 of 1,721 candidates across eight seeds — a radius-8-to-13 lens against 16-block chunks essentially always spans one. The multi-chunk identity machinery is not an edge case in a live world; it is the normal case, and it is exercised on every single deposit.
3. **The restoration scheduler inspects literally zero debts** when nothing is loaded. Over 1,000 passes against 12,000 stored debts, `debtsInspected` was 0 and the whole run took 0.8 ms. M4's claim is not "sublinear", it is "nothing".
4. **No representative production world fixture exists in the worktree.** The live-world QA is therefore an automated equivalent, not a real production-world rehearsal, and is labelled as such throughout. This is a stated limitation, not a passed criterion.

---

## Architecture under test

Reconstructed from the code as it now stands.

### Natural resource generation

```
ChunkEvent.Load (isNewChunk, ServerLevel)            ← server thread, from ChunkStatusTasks.full
  └─ NaturalGenerationHandler
      └─ NaturalDepositService.populate(level, chunk)
          ├─ NaturalDepositSelector.candidatesReaching(chunk)          [PURE] 4 owner cells
          │     └─ candidateFor(seed, resource, natural, cellX, cellZ) [PURE]
          │           └─ DepositIdentity.natural(seed, dim, id, cell, salt)      ← M4 contract
          ├─ ChunkGenerator.getBaseHeight(origin)     — noise, loads nothing
          ├─ BiomeSource.getNoiseBiome(origin)        — noise, loads nothing
          ├─ PlacementPlanner.plan(...)               [PURE] → SedimentaryLensPlanner
          ├─ DepositRegistrar.describe → DepositLedger.register        ← persistent write
          └─ MaterializationService.materializeChunk(level, plan, chunk)  ← world write, this chunk only
```

### Rails / import path

```
/populateores [region <name>] [<ore_type>]
  └─ OreVeinFetcher.fetchOreVeinsAsync → ServerHttpExecutor          ← off the server thread
      └─ .whenComplete(… server.execute(…))                          ← back onto the server thread
          └─ per row: VeinPlacementValidation.reject(…)              ← validate before planning
                      DepositIdentity.rails(shard, dim, id, x,y,z, r, rot, region)
                      PlacementPlanner.plan(…)
                      DepositLedger.register → REGISTERED | ALREADY_REGISTERED | CONFLICT | REVISION_MISMATCH
                      MaterializationService.materialize(…, DEFAULT_BUDGET)
                      DepositRegistrar.recordCompletePass(…)
```

### Extraction

```
BlockEvent.BreakEvent
  ├─ ManagedResourceCreativeGuard              (HIGHEST) creative + sited deposit → CANCEL
  ├─ MiningGateHandler                         (HIGH)    provenance → actor → admin bypass → tool → skill
  ├─ ManagedDepositInteractionHandler          (HIGH)    sediment: CANCEL then ManagedDepositExtraction
  └─ CustomBlockBreakHandler                   (NORMAL)  ManagedExtractionPolicy.mayExtract → tool tag
        └─ single commit point: deplete → yield → BrokenBlockTracker debt → skill → charge tool once
```

### Restoration

```
BrokenBlockTracker → BrokenBlockDataStorage (chunk-indexed SavedData, schema 2)
  ChunkEvent.Load  → RestorationScheduler.activate(chunk)   — only that chunk's debts are queued
  ServerTickEvent  → runPass(storage, now, attempt)          — cadence 20 ticks, budget 64
        due? → attempt restore → restored | blocked → exponential backoff (30 s → 30 min cap)
```

### Vanilla authority

```
placed features  → neoforge:remove_features, #minecraft:is_overworld, underground_ores (19 features)
noise veins      → OreVeinifierMixin wraps OreVeinifier.create
                     → NoiseVeinOreAuthority.substitute: ore → that vein's own filler
```

---

## Performance

### Shape planning

Every shape at a representative radius and at its configured maximum. Times are supplementary; cells and bounds are the deterministic figures.

| Resource | Shape | Radius | Cells | Bounds volume | Fill | ms |
| -------- | ----- | -----: | ----: | ------------: | ---: | -: |
| iron | vertical | 42 / **128** | 146 / **427** | 325,467 / 8,653,449 | 0.0% | 0.4 / 0.3 |
| silver | vertical_layered | 32 / **96** | 1,058 / **9,221** | 12,675 / 111,747 | 8.3% | 1.2 / 5.0 |
| tin | layered | 32 / **96** | 1,011 / **9,239** | 12,675 / 111,747 | 8.3% | 0.7 / 5.1 |
| shadow_iron | vertical | 42 / **128** | 146 / **427** | — | 0.0% | 0.1 / 0.3 |
| copper | cluster | 7 / **22** | 512 / **15,121** | 3,375 / 91,125 | 16.6% | 0.3 / 4.3 |
| gold | snake | 42 / **128** | 219 / **579** | — | 0.0% | 0.2 / 0.2 |
| agapite | geode | 5 / **16** | 174 / **9,445** | 1,331 / 35,937 | 26.3% | 0.1 / 3.2 |
| verite | cluster | 7 / **22** | 512 / **15,121** | — | 16.6% | 0.2 / 4.7 |
| valorite | vertical | 42 / **128** | 146 / **427** | — | 0.0% | 0.0 / 0.1 |
| silica | sedimentary_lens | 8 / **13** | 555 / **1,473** | 2,023 / 5,103 | 28.9% | 2.8 / 3.5 |

**Headroom at each configured maximum, against the 20,000-cell cap:**

| Shape | Cells at max | % of cap |
| ----- | -----------: | -------: |
| **cluster** (copper, verite) | **15,105** | **75.5%** |
| agapite (geode) | 9,492 | 47.5% |
| tin (layered) | 9,149 | 45.7% |
| silver (vertical_layered) | 9,058 | 45.3% |
| gold (snake) | 598 | 3.0% |
| iron / shadow_iron / valorite (vertical) | 428 | 2.1% |
| silica (sedimentary_lens) | 1,577 | 7.9% |

No shape exceeds the cap. No shape takes longer than ~5 ms to plan at its maximum. Planning is deterministic across repeated runs for every shape. **No geometry was rebalanced** — no pathological cost was found.

### Materialisation

Eight candidate cells, one of each kind the policy has an opinion about:

```
8 candidates → 2 written, 6 rejected
  FLUID=1  BLOCK_ENTITY=1  INDESTRUCTIBLE=1  OTHER_MANAGED_RESOURCE=1  NOT_A_HOST=2
  OUTSIDE_BUILD_HEIGHT=0  ALREADY_PRESENT=0  PLAYER_PLACED=0
placed + rejected == candidates offered            ✅ every candidate accounted for
repeat pass → 0 written, ALREADY_PRESENT=2         ✅ idempotent, counted not rewritten
budget = 1 over 8 candidates → 1 written, remaining 7, truncated=true   ✅ bounded
```

**Budgeting proven**: one deposit or import cannot perform an unbounded synchronous write burst; it stops at its budget, reports the remainder, and resumes on a later run.

### Deposit ledger

| Measure | Value |
| ------- | ----- |
| Deposits | **4,000** |
| Registration | 59 ms total (≈15 µs each) |
| 4,000 lookups by id | 2.1 ms |
| Save (`SavedData.save`) | 2 ms |
| Load (deserialise) | 11 ms |
| Serialized NBT | 1,130,923 bytes — **283 bytes/deposit** |
| Re-registering all 4,000 | 4,000 × `ALREADY_REGISTERED`, ledger size unchanged |
| `/orevein stats` over the live ledger | 0.8 ms |

Projection: 20,000 deposits ≈ 5.7 MB of ledger NBT, ~55 ms to load. Comfortable.

### Restoration scheduler

| Measure | Value |
| ------- | ----- |
| Stored debts | **12,000**, all unloaded |
| Build | 38 ms |
| **1,000 scheduler passes** | **0.8 ms total** |
| **Debts inspected across those 1,000 passes** | **0** |
| Queue size / active chunks | 0 / 0 |
| Serialized NBT | 2,736,031 bytes — **228 bytes/debt** |
| Reload | 23 ms |

**Confirmed: 10,000+ unloaded debts do not produce O(n) work per cadence. They produce zero.**

### Mass overdue backlog

5,000 overdue debts across 40 chunks, all loaded at once:

| Measure | Value |
| ------- | ----- |
| Queued on load | 5,000 |
| Passes to drain | **79** |
| Restored | 5,000 |
| **Largest single pass** | **64 — exactly `WORK_BUDGET`** |

The backlog drains progressively at 64 units per pass on a 20-tick cadence. No spike.

### Backoff

Blocked debt: first retry **30 s**, sixth retry **960 s**, capped at **1,800 s**. A blocked debt is not retried on the next pass — measured directly.

### Rails import

Network work runs on `ServerHttpExecutor`; application is hopped back to the server thread with `server.execute`. Application is bounded by `MaterializationService`'s budget. The old inline blocking fetch is gone and the blocking entry point is now package-private so reintroducing it takes a deliberate act.

---

## Scale results

| Scenario | Count | Result |
| -------- | ----: | ------ |
| Deposits in ledger | 4,000 | register/lookup/save/load all sub-60 ms; 283 B each |
| Duplicate registrations | 4,000 | all `ALREADY_REGISTERED`; no growth |
| Depletion debts, unloaded | 12,000 | 0 inspected over 1,000 passes |
| Overdue debts, loaded at once | 5,000 over 40 chunks | 79 bounded passes, max 64/pass |
| Blocked debt | 1 | backs off 30 s → 960 s, capped 1,800 s |
| Materialisation candidates | 8 kinds + budget test | fully accounted, idempotent, bounded |

---

## Persistence / restart QA

A GameTest cannot restart a server, so the restart boundary is exercised where it actually lives — `SavedData` serialisation into fresh objects, which is everything a restart does to this state. Stated as a limitation, not glossed over.

| Case | Result |
| ---- | ------ |
| Deposit instance across save/load | id, seed, radius, origin, planned cells, source **all identical** |
| Rails fallback identity after restart | same immutable row → **same id**; re-register → `ALREADY_REGISTERED`, no duplicate |
| Changed immutable row input | different id, **by design** (documented fallback consequence) |
| Revision mismatch | `REVISION_MISMATCH`, `mayMaterialize()` false, original revision kept — **no silent geometry rewrite** |
| Depleted debt, not yet due | due moment preserved exactly across the boundary |
| Offline time | debt whose due moment passed while stopped is preserved as overdue |
| Offline eligibility | not queued until its chunk loads; then queued and restored on the first pass |
| Blocked debt | backoff scheduled, not retried next pass |

---

## Fixed-seed vanilla authority audits

Three independent seeds, each 256 chunks, chunks (−8,−8)…(7,7), full Y range, Overworld, real dedicated-server generation. **No mass force-loading** — the 256-chunk scale only.

| Seed | Chunks | Coal | Iron | Deepslate iron | Gold | Redstone | Diamond | Lapis | Emerald | Copper | Deepslate copper | Raw iron | Raw copper |
| ---- | -----: | ---: | ---: | -------------: | ---: | -------: | ------: | ----: | ------: | -----: | ---------------: | -------: | ---------: |
| `britannia-m5-audit` | 256 | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** |
| `britannia-m9-alpha` | 256 | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** |
| `britannia-m9-beta` | 256 | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** | **0** |

**768 chunks across three independent seeds: every denied vanilla mineral at zero, on every route.** No denied ore was found, so no source classification was required.

The `britannia-m5-audit` seed additionally provides the before/after pair from M8.5: the same 256 chunks contained 3,464 copper ore, 709 deepslate iron ore and 68 raw blocks before the noise-vein authority landed.

**Precise claim, as instructed:** *No denied vanilla economic ore is produced through any generation route currently identified and covered by the project.* This is not a proof that no unknown route can exist — M8.5 itself found the second route by empirical auditing, which is exactly why these independent scans are run as defence in depth.

---

## Allowed-geology verification

Representative counts per chunk, three seeds:

| Geology | m5-audit | m9-alpha | m9-beta |
| ------- | -------: | -------: | ------: |
| granite | 1,073 | 962 | 882 |
| tuff | 1,135 | 1,063 | 1,153 |
| andesite | 1,154 | 1,035 | 1,031 |
| diorite | 1,109 | 972 | 980 |
| gravel | 492 | 498 | 514 |
| dripstone_block | 303 | 537 | 0 * |
| calcite / amethyst / smooth_basalt | present | present | present |

\* seed `britannia-m9-beta` has no dripstone caves in the audited box — a biome fact, not a suppression effect.

**Geological continuity holds.** Granite and tuff are present in strength in every seed, at the same order of magnitude as before the noise-vein change (M8.5 measured granite +1,934 and tuff +810 over the identical box — filler gained, not lost). Byte-identical terrain is correctly *not* the invariant: substituting ore for filler creates cells that later features may legally transform, which is ordinary vanilla geology continuing to work.

---

## Silica QA

Eight seeds × 256 owner cells = **204,800 chunks** of real Overworld terrain, queried through the actual `MultiNoiseBiomeSource` and `NoiseBasedChunkGenerator` without generating chunks.

| Seed | Candidates | Accepted | Rate |
| ---- | ---------: | -------: | ---- |
| 1 | 205 | 4 | 1 per 6,400 chunks |
| 987654321 | 214 | 1 | 1 per 25,600 |
| −4242 | 223 | 63 | 1 per 406 |
| 24301 | 215 | 6 | 1 per 4,266 |
| 77 | 214 | 4 | 1 per 6,400 |
| −19 | 220 | 10 | 1 per 2,560 |
| 31337 | 213 | 2 | 1 per 12,800 |
| 606 | 217 | 12 | 1 per 2,133 |
| **Total** | **1,721** | **102** | **5.9% acceptance** |

| Property | Measurement |
| -------- | ----------- |
| Biome restriction | desert 61, beach 41 — **nothing else, ever** |
| Altitude | min 41, median 60, max 79 |
| Deposit size (planned) | min 328, median 917, mean 953, max 1,983 cells |
| Nearest neighbour (accepted) | min 89, median 170, max 1,503 blocks |
| **Multi-chunk** | **1,721 of 1,721 — 100%** |
| Identity, extraction, restart, 24 h restoration | all pass (M7 suite, re-run) |
| Host rejection in real terrain | **still unmeasured** — see debt |

### Verdict on M7's provisional tuning: **acceptably provisional**

Not too common: 5.9% acceptance, and never outside desert or beach. Not too large: median 917 planned cells, max under 2,000. Not too small: a bed is a substantial find. Rarity spans 1 per 406 chunks in sandy country to 1 per 25,600 where there is none, which is the intended regional behaviour rather than a defect to average away.

The one soft spot is the far end of that spread — a player in a world like seed `987654321` may never meet silica naturally. **No change is recommended or made.** If the owner later wants the floor raised, the smallest lever is biome membership (adding one more sandy biome) rather than the grid, and that is a balancing decision with gameplay evidence behind it, not a correctness fix.

---

## Silver / tin balancing assessment

Measured at the curated radius the live Rails rows actually use (35), 200 seeds each.

| Resource | Shape | Radius | Min | Median | Mean | Max |
| -------- | ----- | -----: | --: | -----: | ---: | --: |
| **Silver** | vertical_layered | 35 | 1,136 | **1,217** | 1,220 | 1,312 |
| **Tin** | layered | 35 | 1,128 | **1,221** | 1,220 | 1,329 |

For comparison, at radii the same data uses elsewhere:

| Resource | Shape | Radius | Median cells |
| -------- | ----- | -----: | -----------: |
| iron / valorite / shadow_iron | vertical | 24 | 80 |
| gold | snake | 24 | 117 |
| agapite | geode | 8 | 943 |
| copper / verite | cluster | 22 | 15,152 |
| silica | sedimentary_lens | 13 | 1,437 |

**Assessment.** Silver and tin land at ~1,220 planned cells — the same order as agapite (943) and silica (1,437), and three orders below cluster (15,152). Both are worked with the same pickaxe, on 6-hour regeneration, and their deposits are broad thin seams rather than dense blobs, so the accepted/materialised ratio in `#britannia_mod:ore_hosts` should be high (the seam sits in stone, which is the host).

M3's correction deliberately restored the cell count the legacy algorithm was *trying* to place before its air-filter defect threw most of it away — 1,924 intended at radius 35 for tin — so today's ~1,220 is in the same neighbourhood as the original design intent, not an inflation of it.

**Recommendation for both: KEEP.** No correctness or performance problem exists, the sizes are proportionate to their peers, and changing them would be a gameplay decision with no evidence behind it. **No values were changed.**

The genuine outlier is **cluster at radius 22 (copper, verite) at 15,152 cells** — an order of magnitude above every other family and 75.5% of the plan cap. That is pre-existing, is not a milestone-9 regression, and is flagged as an **owner balancing decision** rather than changed here.

---

## Agapite assessment

| Property | Value |
| -------- | ----- |
| Shape | genuine `GEODE` (M3) |
| Configured valid range | **3–16** |
| Provisional radius | **8** |
| Planned cells at radius 8 | min 899, **median 943**, max 987 |
| Fill at radius 8 | dense enough to read as a geode; 26.3% fill at radius 16 |

A radius-8 geode planning ~943 cells is a reasonable provisional geode — comparable to silver/tin and silica, far below cluster. **Recommend keeping radius 8.**

### ⚠ External production action, still outstanding

**The live Rails `ore_veins` row for agapite still carries radius 55.** That is outside the geode's valid range entirely, and the importer now refuses it with a message naming the offending value — verified by `theLegacyAgapiteRadiusOfFiftyFiveIsRefusedWithAnActionableMessage`. Until the row is corrected **`55 → 8` in the live Rails database**, that agapite vein will be skipped on every import.

**This milestone did not connect to or modify the live Rails database.** It is an owner action outside the mod.

---

## Admin / diagnostic QA

All exercised through `OreVeinDiagnosticsGameTests` (7) against real ledger and restoration state:

| Question | Command | Verified |
| -------- | ------- | -------- |
| How many deposits exist | `/orevein stats` | ✅ matches ledger size exactly; 0.8 ms over a 4,000-entry ledger |
| Which resources, and where | `/orevein list [resource]` | ✅ unfiltered covers every deposit; filter works; capped at 40 rows with an overflow note |
| Where is the nearest | `/orevein locate <resource>` | ✅ nearest by distance, with a count of candidates |
| Identity, source, bounds, progress | `/orevein inspect <id>` | ✅ id, resource + revision, source, source identity, origin/radius/rotation, bounds + chunk count, planned / materialised / blocked, depleted now, next due |
| Restoration due state | `/orevein inspect` | ✅ "next due in Xh Ym" or "nothing outstanding" |
| Was a duplicate rejected | `/orevein refusals` | ✅ bounded journal; matches the ledger |
| Controlled regeneration | `/orevein regenerate <id>` | ✅ advances only existing debts; a deposit owing nothing is told so; debt count unchanged |
| Bad input | all | ✅ malformed ids, unknown ids and unknown resources are refused, not guessed |
| Read-only | `stats`, `list` | ✅ ledger size unchanged after running them |

**No new admin features were invented.** No diagnostic was found misleading at scale.

---

## Rails importer QA

| Condition | Result |
| --------- | ------ |
| Successful payload | ✅ all rows parsed |
| Repeated identical payload | ✅ identical parse; ledger answers `ALREADY_REGISTERED`; **0 cells rewritten** |
| Duplicate row in one payload | ✅ parsed twice, deduplicated by the ledger — identity is the ledger's decision, not the parser's |
| Invalid resource (`mithril`, `coal`) | ✅ refused before planning |
| **Legacy agapite radius 55** | ✅ refused, message names the value |
| Radius below a shape minimum | ✅ refused |
| Malformed coordinates | ✅ that row is skipped and counted; the other rows survive |
| Row outside build range | ✅ refused (both ceiling and floor) |
| Network/transport failure | ✅ typed `TRANSPORT_ERROR`, distinct from an empty shard, says "Nothing was placed" |
| Auth unavailable | ✅ typed, request never made |
| HTTP error | ✅ typed with the status code |
| Malformed payload | ✅ typed, nothing placed |
| Partial / batched application | ✅ budget stops the pass, reports `remaining` and `truncated`, resumes on re-run |
| Server thread | ✅ fetch on `ServerHttpExecutor`, application via `server.execute` |
| Restart during/after application | ✅ ledger and debts survive the save/load boundary; re-import is idempotent |

**Stable Rails row-id path:** not implemented — Rails still supplies no stable id, so only the fallback immutable-field path exists and only it was testable. Behaviour is as designed: same row → same id; changed immutable input → different id.

**Rails itself was not modified. No live Rails connection was made.**

---

## Extraction / integrity regression

Integration regression only — M6 policy was not rewritten.

| Case | Ore | Clay | Silica | Result |
| ---- | --- | ---- | ------ | ------ |
| Correct Britannia tool | ✅ | ✅ | ✅ | configured yield ×1, 1 debt, 1 durability |
| Fortune III | ✅ | ✅ | ✅ | yield unchanged |
| Silk Touch | ✅ | ✅ | ✅ | managed block never dropped |
| Wrong tool (cross-family, vanilla, axe, stick, bare hand) | ✅ | ✅ | ✅ | refused; **0 yield, 0 debt, 0 durability, 0 skill** |
| Fake player | ✅ | ✅ | ✅ | denied; block unchanged; nothing charged |
| **Creative, sited deposit** | ✅ | ✅ | ✅ | **break refused, block remains**, no yield/debt/skill/durability |
| Creative, ambient rock | ✅ | — | — | still breaks — terraforming preserved |
| Creative, player-placed block | ✅ | — | — | still breaks — provenance wins |
| Duplicate/reentrant break | ✅ | ✅ | ✅ | commits once; a second break of a depleted cell does nothing |

Explicit administrative bypass: `/orevein regenerate` restores existing debts **without economic yield**; `/manageddeposit remove` remains the explicit removal route. Neither mints resources.

---

## Full regression

| Test / command | Result |
| -------------- | ------ |
| `gradlew test` | **2,986 run · 5 failed · 0 errors · 17 skipped** |
| `gradlew runGameTestServer --no-configuration-cache` | **724 run · 2 failed** |
| `gradlew build -x test` | **BUILD SUCCESSFUL** |
| `gradlew validateTrainingDummyGeo` | **PASS** (exit 0) — the project's only bespoke validation task |
| Data/resource validation | catalogue, mineable, extraction-tag and shape validation all run at server start; **five** dedicated-server boots this milestone loaded every data file with no error |
| Datagen | **not applicable** — the mod registers no `GatherDataEvent` provider, so `runData` would generate nothing. Stated rather than run for appearance |
| Mixin validation | no dedicated task exists; the mixin is validated by the annotation processor at compile time and by applying successfully in five server boots |

### Baseline comparison

| | M8.5 baseline | M9 | Delta |
| --- | --- | --- | --- |
| JUnit run | 2,972 | 2,986 | **+14** |
| JUnit failed | 5 | 5 | **0** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 717 | 724 | **+7** |
| GameTests failed | 2 | 2 | **0** |

### Failure classification

**Known JUnit baseline (5), unchanged, not investigated:** `RoutineSkillGainPresentationTest`, `structure.hardening.MilestoneEightContentReportTest`, `structure.render.CorrectiveMilestoneNineARenderAlignmentTest`, `structure.render.MonolithMilestoneSevenRenderingTest` ×2.

**Known GameTest baseline (2), unchanged, not investigated:** `aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`.

**Textile flake:** `spinningandweavingareexactandrejectspiderssilk` did **not** appear in either M9 GameTest run. Its signature was unchanged when last seen at M8.5. No textile code touched.

**New deterministic regressions: none.**

---

## Replacement-generation completeness

| Resource | Vanilla denied? | Managed identity? | Natural controlled generation? | Rails/admin placement? | Extraction defined? | Regen defined? | **Replacement complete?** |
| -------- | --------------- | ----------------- | ------------------------------ | ---------------------- | ------------------- | -------------- | ------------------------- |
| **Silica** *(reference)* | n/a | ✅ `silica_sand_deposit` | ✅ `sedimentary_lens` | ✅ | ✅ `silica_shovels` | ✅ 24 h | ✅ **COMPLETE** |
| **Coal** | ✅ 2 features | ❌ **none** | ❌ | ❌ | ❌ | ❌ | ❌ **nothing exists** |
| **Iron** | ✅ 3 features + noise vein | ✅ `britannia_mod:iron` | ❌ | ✅ | ✅ `mining_pickaxes` | ✅ 6 h | ❌ **generation only** |
| **Gold** | ✅ 3 features | ✅ `britannia_mod:gold` | ❌ | ✅ | ✅ `mining_pickaxes` | ✅ 6 h | ❌ **generation only** |
| **Copper** | ✅ 2 features + noise vein | ✅ `britannia_mod:copper` | ❌ | ✅ | ✅ `mining_pickaxes` | ✅ 6 h | ❌ **generation only** |
| **Redstone** | ✅ 2 features | ❌ **none** | ❌ | ❌ | ❌ | ❌ | ❌ **nothing exists** |
| **Diamond** | ✅ 4 features | ❌ **none** | ❌ | ❌ | ❌ | ❌ | ❌ **nothing exists** |
| **Lapis** | ✅ 2 features | ❌ **none** | ❌ | ❌ | ❌ | ❌ | ❌ **nothing exists** |
| **Emerald** | ✅ 1 feature | ❌ **none** | ❌ | ❌ | ❌ | ❌ | ❌ **nothing exists** |

Britannia-only minerals on the same platform, for context — all complete except natural generation, and none of them is suppressed, so none blocks rollout:

| Resource | Identity | Shape | Extraction | Regen | Natural |
| -------- | -------- | ----- | ---------- | ----- | ------- |
| silver | ✅ | vertical_layered | pickaxe | 6 h | ❌ |
| tin | ✅ | layered | pickaxe | 6 h | ❌ |
| shadow_iron | ✅ | vertical | pickaxe | 6 h | ❌ |
| agapite | ✅ | geode | pickaxe | 6 h | ❌ |
| verite | ✅ | cluster | pickaxe | 6 h | ❌ |
| valorite | ✅ | vertical | pickaxe | 6 h | ❌ |
| sandstone | ✅ | — | pickaxe | 6 h | ❌ |
| clay | ✅ | — | clay shovel | 6 h | ❌ |

**Exactly what is missing, per family:**

- **Iron, gold, copper** — a `natural` block in `resources.json`: dimension, biome tag, cell size, chance, radius band, depth, altitude band, salt. Nothing else. No Java, no new block, no new shape.
- **Coal, redstone, diamond, lapis, emerald** — everything: a managed block or an approved vanilla-block claim, a `ResourceDefinition`, a `MineableDefinition` (Mining requirement + challenge), an economy commodity, an extraction tag membership, and then the `natural` block above.

---

## Missing-resource follow-up plan

The smallest path to controlled natural generation for each family. **None of this was implemented.**

| Family | Managed block exists? | ResourceDefinition? | Mining definition? | Rails coords? | Suggested shape | What is missing | Owner decision needed |
| ------ | --------------------- | ------------------- | ------------------ | ------------- | --------------- | --------------- | --------------------- |
| **Iron** | ✅ claims `minecraft:iron_ore` + deepslate | ✅ | ✅ `iron` | ✅ | **Vertical** (already configured) | `natural` data block only | biome/altitude band, frequency |
| **Gold** | ✅ claims vanilla gold + `britannia_mod:gold_ore` | ✅ | ✅ `gold` | ✅ | **Snake** (already configured) | `natural` data block only | frequency, badlands policy |
| **Copper** | ✅ `britannia_mod:copper_ore` | ✅ | ✅ `copper` | ✅ | **Cluster** (already configured) | `natural` data block only | frequency; **cluster size (15,152 cells) should be reviewed first** |
| **Coal** | ❌ | ❌ | ❌ | unknown | **Layered** — coal is a bedded sedimentary seam, and `LayeredPlanner` is exactly that | block, definition, mineable, commodity, tag, natural data | Mining requirement, yield mode, commodity identity |
| **Redstone** | ❌ | ❌ | ❌ | unknown | **VerticalLayered** or Cluster — deep, patchy | as coal | as coal, plus depth band |
| **Diamond** | ❌ | ❌ | ❌ | unknown | **Cluster** at small radius — rare pockets | as coal | rarity is the whole design question |
| **Lapis** | ❌ | ❌ | ❌ | unknown | **Cluster** small — vanilla lapis is a tight pocket | as coal | as coal |
| **Emerald** | ❌ | ❌ | ❌ | unknown | **Vertical** small, biome-restricted to mountains | as coal, plus a mountain biome tag | whether emerald stays mountain-only |

**No genuinely new shape is required for any family.** The seven existing planners cover every case.

### Recommended structure: **B — several resource-content milestones**

Not one Milestone 10, and not a separate project.

- **The three that need data only (iron, gold, copper)** are a single small milestone: three `natural` blocks, three sets of measured distribution statistics, one audit. The M7 seam already supports it with no Java, and the M9 harness (`SilicaDistributionAuditGameTests`) already measures it.
- **The five that need everything (coal, redstone, diamond, lapis, emerald)** are content work of a different kind: each needs a block, an economy identity, a Mining requirement and a balancing decision. Bundling all five into one milestone would produce a single enormous review with five independent balancing arguments in it.

A single "Milestone 10: replacement generation" would mix a data-only change with five content designs, and would be reviewed as one indivisible thing. Splitting it lets iron/gold/copper land quickly — which alone moves three of the eight families to complete — while the five new resources are designed at whatever pace their balancing deserves.

**This also matches the owner's stated preference:** M9 proved the platform; a follow-up populates it.

---

## Rollback rehearsal

Bounded rehearsal performed on disposable worlds only. **The production world was never touched, and step 4 — enabling suppression — is explicitly NOT approved for production.**

### What was rehearsed

| Step | Done | Result |
| ---- | ---- | ------ |
| 1. Back up / copy representative world | ⚠ **partial** — no production world fixture exists in the worktree; disposable seeded worlds were used | limitation stated |
| 2. Load/import curated state | ✅ automated equivalent | idempotent, identity-stable |
| 3. Verify restoration migration | ✅ schema-2 storage, legacy due-time migration | preserved |
| 4. New-chunk suppression **in a disposable world** | ✅ three seeds | **not approved for production** |
| 5. Verify zero denied vanilla ores | ✅ 768 chunks | zero |
| 6. Verify silica generation | ✅ 8 seeds | as designed |
| 7. Mine/regenerate representative resources | ✅ GameTests | one yield, one debt, one durability |
| 8. Restart | ✅ save/load boundary | identity and timers preserved |
| 9. Verify timers/state | ✅ | preserved, including offline time |
| 10. Profile | ✅ | see Performance |
| 11. Rehearse rollback | ✅ documented below | see below |

### What activating suppression today would actually do

This is the demonstration the brief asked for, and it reinforces the gate rather than bypassing it. In each newly generated chunk, relative to vanilla:

| Family | Would be removed | Would be replaced by | Net |
| ------ | ---------------- | -------------------- | --- |
| Coal | ~134/chunk (measured M5 control) | nothing | **total loss** |
| Iron | ~82/chunk | nothing | **total loss** |
| Copper | ~337/chunk | nothing | **total loss** |
| Gold | ~26/chunk | nothing | **total loss** |
| Redstone | ~36/chunk | nothing | **total loss** |
| Diamond | ~25/chunk | nothing | **total loss** |
| Lapis | ~25/chunk | nothing | **total loss** |
| Emerald | biome-limited | nothing | **total loss** |
| Silica | — | ~1 bed per 2,000–6,000 chunks | the only addition |

Eight families removed, one added.

### Rollback boundary — and why it is not "remove the modifier and restart"

| Layer | Rollback | Reversible? |
| ----- | -------- | ----------- |
| **Code (mixin)** | revert the commit, rebuild, restart | ✅ fully — the noise-vein authority is a generation rule with no persistent state |
| **Configuration (biome modifier + policy data)** | delete/revert the two data files, restart | ✅ fully, for **future** chunks |
| **Chunks generated while suppression was on** | **not reversible by any code change** | ❌ **This is the real boundary.** Those chunks were written without vanilla ore and are now ordinary terrain on disk. Removing the modifier makes *new* chunks vanilla again; it does nothing for the ones already written |
| **Deposit ledger SavedData** | schema 1; older builds do not read it | ⚠ forward-compatible only. Rolling back to a pre-M4 build leaves the ledger orphaned — harmless, but deposits lose their identity records |
| **Restoration SavedData** | schema 2, with legacy migration built in | ⚠ **one-way.** Schema 2 was migrated from legacy on first load; a pre-M4 build cannot read it. Restoring debts after a rollback requires the world backup |
| **M8 migrations** | none exist — no retrofit, no markers, no chunk migration was ever implemented | ✅ nothing to roll back |

**Therefore the only complete rollback is a world backup restore**, and its cost is every chunk generated since the backup. The practical procedure:

1. Take a world backup **immediately before** enabling suppression, and keep it until the rollout is accepted.
2. If rollback is needed and few chunks were generated: revert code + config, restart, and accept that the affected chunks lack vanilla ore permanently — or delete just those region files so they regenerate.
3. If many chunks were generated: restore the world backup. Deposit and restoration SavedData restore with it and stay consistent, because both live inside the world folder.
4. Never roll the *mod* back below M4 while keeping a post-M4 world — the restoration schema will not be readable.

---

## Invariants proven

| Invariant | Status |
| --------- | ------ |
| Denied vanilla ores do not generate in new chunks | ✅ 768 chunks, 3 independent seeds, all zero |
| Noise-vein route produces no ore | ✅ by contract and by measurement |
| Allowed geology survives | ✅ granite/tuff/andesite/diorite present in strength in all seeds |
| Existing chunks are never rewritten | ✅ structural (`isNewChunk`), tested; no scan, marker or migration exists anywhere |
| Controlled deposit generation is deterministic | ✅ every shape, every seed, repeated runs |
| One multi-chunk deposit has one id | ✅ and 100% of silica beds are multi-chunk |
| Duplicate protection works | ✅ 4,000/4,000 re-registrations recognised |
| Restoration cost is not proportional to unloaded debt | ✅ **0 debts inspected** over 1,000 passes at 12,000 stored |
| Restoration drains backlogs progressively | ✅ 79 bounded passes, max 64 = budget |
| Blocked debts back off | ✅ 30 s → 960 s, capped 1,800 s |
| Materialisation is bounded and fully accounted | ✅ budget honoured, every candidate counted |
| Identity, seed, geometry survive restart | ✅ |
| Revision mismatch never silently regenerates | ✅ |
| Rails import is deterministic and idempotent | ✅ |
| Rails network never blocks the server thread | ✅ |
| Silica extraction/regeneration works after restart | ✅ 24 h resolved and honoured |
| Creative cannot mint or delete a sited deposit | ✅ |
| Ambient rock remains terraformable | ✅ |
| No chunk is ever force-loaded | ✅ |

---

## Engineering readiness verdict

# M9A — **PASS**

Every scale, persistence, determinism and authority property held on first measurement. No architectural defect was found, no production file needed changing, and there are no new deterministic failures. The platform is technically safe for a persistent multiplayer world at the projected scale, and is ready to have resources added to it.

Non-blocking items carried: real-terrain host-rejection percentage for silica is unmeasured; cluster's 15,152-cell deposits at radius 22 warrant an owner balancing look; silica's provisional tuning is acceptable but untested by play.

---

## Production suppression readiness verdict

# M9B — **BLOCKED**

**Exact blockers:**

1. **Five families have no managed identity at all** — coal, redstone, diamond, lapis, emerald. No block, no `ResourceDefinition`, no Mining definition, no economy identity. They cannot be placed by an operator, let alone generated.
2. **Three families have identity but no natural generation** — iron, gold, copper. Each needs a `natural` data block; nothing else.
3. **Consequence if activated today:** eight resource families disappear from every newly generated chunk, with one (silica) added. Quantified in the rehearsal above.

Not blockers, but must accompany any eventual rollout:

4. **Live Rails agapite row still says radius 55** — the importer refuses it; the row needs correcting to 8 in Rails.
5. **No production world backup procedure has been exercised on a real world** — the rollback boundary is documented but rehearsed only on disposable copies.

**Do not enable the M5/M8.5 suppression set in production.** The platform is ready; the content is not.

---

## Playbook completion status

The playbook ends at M9, but ending the playbook is not the same as finishing the job.

- **Remediation / platform project: COMPLETE.** Every defect the M0 audit identified is fixed, every milestone's exit criteria are met, and the platform is validated at scale. M1–M8.5 delivered what they were asked to deliver.
- **Replacement-content rollout: INCOMPLETE.** Eight suppressed families need controlled natural generation before suppression can ship. That work was never in the playbook's scope — the playbook built the machine; it did not populate it.

Reporting this as "the project is complete" would be false in the way that matters most: it would imply production is ready when eight resource families would vanish on activation.

---

## Git state

| | |
| --- | --- |
| M8.5 commit | `9ce9b0548b93961585fc9fcfd6a28dce0e929576` |
| Current HEAD | `9ce9b0548b93961585fc9fcfd6a28dce0e929576` — **unchanged; M9 is uncommitted** |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| Worktree | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Pushed | **No.** No upstream configured |
| Merged to `patch-18` | **No.** `patch-18` is still at `c2bc44f3` |

```
$ git status --short
 M src/main/java/com/seggellion/britannia_mod/gametest/SilicaDistributionAuditGameTests.java
?? src/main/java/com/seggellion/britannia_mod/gametest/PlatformScaleM9GameTests.java
?? src/test/java/com/seggellion/britannia_mod/commands/RailsImportFailureM9Test.java
?? src/test/java/com/seggellion/britannia_mod/resource/PlatformValidationM9Test.java
```

**M9 files changed: 4 — all tests. No production code was modified.**

| File | Kind | Tests |
| ---- | ---- | ----: |
| `gametest/PlatformScaleM9GameTests.java` | new | 6 |
| `test/…/resource/PlatformValidationM9Test.java` | new | 5 |
| `test/…/commands/RailsImportFailureM9Test.java` | new | 9 |
| `gametest/SilicaDistributionAuditGameTests.java` | +1 test | 1 |

**M9 remains uncommitted**, pending review. Stash untouched, no unrelated files, project documentation outside this worktree.

---

## Recommendation

**1. Are the M9 changes safe to commit?**
**Yes.** They are four test files and one added test method. No production code was touched, so the commit cannot change runtime behaviour; it can only add coverage and the measurements this report cites. Recommended message: `test(oreveins): validate the resource platform at scale`.

**2. Is the resource platform ready for future expansion?**
**Yes.** Adding iron, gold or copper natural generation is a data change with no Java — the seam is proven by a synthetic second resource and by silica in production data. Adding a wholly new resource needs a block, a definition and a Mining entry, all of which are established patterns with validation that fails loudly at load.

**3. May production vanilla suppression be enabled?**
**No.** Blocked on replacement completeness — five families with no identity, three needing distribution data. See the M9B verdict.

**4. What should happen next?**

- **Immediately:** commit M9 (on authorisation). Correct the live Rails agapite row `55 → 8`.
- **Next milestone — "Controlled generation for iron, gold and copper":** three `natural` data blocks, measured against the M9 harness, audited on three seeds. Small, data-only, and moves three of eight families to complete.
- **Then, one milestone per new resource or per small group:** coal (Layered), redstone (VerticalLayered), diamond (Cluster, small), lapis (Cluster, small), emerald (Vertical, mountain-restricted). Each needs a block, a `ResourceDefinition`, a Mining requirement, an economy commodity and a balancing decision.
- **Only after all eight are complete:** re-run this milestone's audits and revisit the M9B verdict.
- **Separately, at the owner's discretion:** review cluster's 15,152-cell deposits at radius 22.

**Stopping after M9 as instructed.** Replacement-generation work not begun, nothing pushed, nothing merged into `patch-18`.
