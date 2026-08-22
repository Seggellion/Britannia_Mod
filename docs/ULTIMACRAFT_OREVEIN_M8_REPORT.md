# UltimaCraft OreVein Remediation — Milestone 8 Report

**Admin, Diagnostics, Rails Import, and Existing-World Migration**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-21 · nothing pushed

---

## Git state

### 1. Branch / worktree

`claude/ultimacraft-orevein-remediation-70bfeb` in `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e`.

### 2. Starting HEAD

`bac36d96988c4bf85773e25aad36dd152cebce11`.

### 3. What `bac36d96988c4bf85773e25aad36dd152cebce11` contains

**The M6 amendment only — 15 files, 1,183 insertions.** `ManagedExtractionPolicy`, `ManagedResourceCreativeGuard`, `ManagedResourceTestPlayers`, the extraction-policy GameTests and unit test, the four touched handlers/gates, and one lang key.

It contains **no M7 artefact**. Verified by name (`SedimentaryLensPlanner`, `ShapeTuning`, `NaturalDepositSelector`, `NaturalGeneration`, `silica_hosts`, `has_silica_deposits` — zero matches) and by content (`git show bac36d96:…/resources.json | grep sedimentary_lens` → 0).

### 4. Whether M7 had uncommitted work

**Yes — all of it.** At the start of this session the worktree held 10 modified and 9 untracked paths, all M7, with nothing staged.

**There was no contradiction in the M7 report.** Both statements were true and referred to different milestones: `bac36d96` was the M6 commit (the report says so under *M6 amendment and commit*), and *"M7 remains uncommitted"* was the accurate state of M7 under *Git state*. The M7 report also recorded `Current HEAD: bac36d96… — unchanged; M7 is uncommitted`, which is exactly what was found. Nothing was mis-stated; the two lines simply sit in different sections.

### 5. M7 commit created before M8

**`c2a4678c2ffae4c0a367f8ddfa141be56a830619`** — `feat(worldgen): generate silica as natural sedimentary lenses`, 23 files, 2,895 insertions. Committed only after re-running the suites on the uncommitted tree: **JUnit 2,960 / 5 failed / 17 skipped** and **GameTests 700 / 2 failed**, all baseline. Kept logically separate from M8.

### 6. Final worktree cleanliness

**Clean.** `git status --short` is empty. Local history, newest first:

```
85782243  feat(oreveins): add deposit diagnostics and take the Rails import off the server thread   ← M8
c2a4678c  feat(worldgen): generate silica as natural sedimentary lenses                             ← M7
bac36d96  fix(resources): harden managed extraction policies                                        ← M6
5ee93650  feat(worldgen): suppress vanilla overworld mineral features                               ← M5
```

Nothing pushed. Nothing merged into `patch-18`.

---

## Milestone 8

### 7. Exact title

> **Milestone 8 - Admin, Diagnostics, Rails Import, and Existing-World Migration**
>
> Objective: *Give operators enough visibility and migration control to run an authoritative resource economy safely.*

Playbook lines 1119–1206. **No `OWNER DECISION` marker appears anywhere in the playbook.** M8 is **NeoForge-side only in code**, but it has a **Rails contract implication** — recorded under *Known debt*.

### Prerequisite work already done, not duplicated

Reconciling the playbook against M3/M4/M6/M7 first, several M8 requirements were already satisfied and were **verified rather than reimplemented**:

| M8 requirement | Already provided by | Action taken |
| -------------- | ------------------- | ------------ |
| Stable instance identity for Rails rows | M4 `DepositIdentity.rails` | verified, unchanged |
| Validation before placement | M1/M3 `VeinPlacementValidation` + `PlacementPlanner.reject` | verified, unchanged |
| No reroll on repeated command | M3 derived planner seed | verified, unchanged |
| Idempotent repeated import | M3 `MaterializationService` + M4 ledger three-way registration | verified, **new GameTest** |
| Dimension explicit or safely resolved | resolved from the command's own level | verified, unchanged |
| No duplicate application | M4 `CONFLICT` / `ALREADY_REGISTERED` | verified, **new GameTest** |
| Existing chunks unchanged | M7 `isNewChunk()` | verified, **new contract test** |
| Legacy clear behaviour retired | M1 refusals | verified, unchanged |
| Deposit inspect at a position | `/manageddeposit inspect` | left alone; `/orevein` inspects by id |

### 8–9. Exit criteria, and pass/fail for each

| # | Exit criterion (verbatim) | Result | Evidence |
| - | ------------------------- | ------ | -------- |
| 1 | Operators can inspect and count persistent deposit instances. | **PASS** | `/orevein stats\|list\|locate\|inspect\|refusals`; `operatorsCanCountAndListDeposits`, `inspectExposesIdentityBoundsAndProgress` |
| 2 | Rails import is deterministic/idempotent. | **PASS** | `repeatingAnImportIsIdempotent` — second import registers 0 new instances and writes 0 cells |
| 3 | Existing chunks are untouched by default. | **PASS** | `anExistingChunkIsNeverRetroPopulated` — a non-new `ChunkEvent.Load` registers nothing |
| 4 | Any retrofit is bounded, previewable, and durable. | **PASS (vacuously — no retrofit implemented)** | See below |
| 5 | Legacy dangerous clear behaviour is no longer needed. | **PASS** | Refusals still in force (`OreVeinContainmentGameTests`); the diagnostics that replace the need are read-only (`theDiagnosticsThatReplaceLegacyClearWriteNothing`) |

**On criterion 4.** The playbook is conditional — *"If a retrofit is implemented, require…"* — and the standing instruction is not to add retrofit scans, migration passes or existing-world population unless M8 explicitly requires them. It does not: its stated existing-world policy is *"existing chunks remain unchanged; new chunks use suppressed vanilla ore generation and managed deposits; curated existing Rails deposits can be registered/imported deliberately."* All three already hold. **No retrofit framework was built**, no "already processed" marker was added, and no world scan exists. If a retrofit is ever wanted, its ten requirements remain unimplemented and unclaimed.

### 10. Production files changed

| File | Change |
| ---- | ------ |
| `commands/OreVeinDiagnosticsCommand.java` | **New.** `/orevein stats \| list [resource] \| locate <resource> \| inspect <id> \| regenerate <id> \| refusals`, permission level 2 |
| `commands/PopulateOresCommand.java` | Rails fetch moved off the server thread; typed failure reporting; placement still applied on the server thread |
| `util/OreVeinFetcher.java` | `fetchOreVeinsAsync` via the existing bounded HTTP pool; typed `FetchResult`; per-row tolerant parsing; blocking entry point made package-private |
| `resource/deposit/DepositLedger.java` | Bounded in-memory refusal journal + `refusals()` |
| `registry/CommandRegistry.java` | Registers the diagnostics command |

### 11. Data / resources changed

**None.** No resource definition, tag, loot table, lang key or worldgen file was touched by M8. `resources.json` is byte-identical to M7.

### 12. Tests added / modified

| File | Kind | Count |
| ---- | ---- | ----: |
| `gametest/OreVeinDiagnosticsGameTests.java` | **New** GameTests | 7 |
| `gametest/NaturalSilicaGameTests.java` | +1 GameTest (`anExistingChunkIsNeverRetroPopulated`) | 1 |
| `test/…/extraction/AmbientRockIsNotADepositTest.java` | **New** unit tests | 5 |
| `test/…/util/OreVeinFetcherParseTest.java` | **New** unit tests | 7 |

### 13. Commit hash

**`85782243fa770639c8fb79ec4869ea83473f4a00`** — `feat(oreveins): add deposit diagnostics and take the Rails import off the server thread`. 9 files, 1,266 insertions, 52 deletions. Not pushed.

---

## Ambient-rock decision

### 14. Confirmation

**Confirmed.** `minecraft:stone`, `minecraft:granite` and `minecraft:deepslate` — and the rest of the vanilla STONE family — remain fully terraformable. They are **not** protected as managed deposits merely because their block type is catalogued as mineable. Managed-deposit protection was **not** broadened. The M7 `isDepositCell` distinction is unchanged; inspection revealed no correctness defect in it.

### 15. Exact rule

In `ManagedExtractionPolicy.isDepositCell(ResourceDefinition, BlockState)`:

```
family != STONE                    → deposit          (every ORE and SEDIMENT resource)
family == STONE, block namespace == britannia_mod  → deposit   (mod-owned rock: only exists where the mod sited it)
family == STONE, block namespace == minecraft      → ambient crust  (not protected)
```

plus, ahead of it, `MiningProvenance.isPlayerPlaced` — a builder may always remove what they placed.

The rule reads the data, never a list of names, so a resource added later lands on the correct side without being mentioned in any Java branch. Concretely: 15 blocks classify as deposits (9 ore families plus `sandstone_deposit`, the four bespoke rocks, and the two sediment beds), 13 vanilla stone-family blocks classify as ambient crust.

### 16. Tests pinning it

**`AmbientRockIsNotADepositTest` — 5 tests, new in M8, added specifically so this cannot drift:**

| Test | Pins |
| ---- | ---- |
| `everyOreAndSedimentResourceIsADepositCell` | Both non-STONE families are always deposits |
| `ordinaryVanillaRockIsNotADepositCell` | 13 vanilla blocks named individually, including stone/granite/deepslate, each asserted **not** a deposit *and* still STONE-family |
| `aModOwnedStoneBlockIsStillADepositCell` | The five mod-owned rocks stay protected — the exemption is not "STONE is never protected" |
| `theRuleHoldsForEveryBlockInTheShippedCatalogue` | Sweeps all 28 resources; a resource added later that classifies against the rule fails here |
| `bothSidesOfTheLineArePopulated` | Neither side is empty, so the rule cannot degenerate |

Behaviour against real block states remains covered by `ManagedExtractionPolicyGameTests.aCreativeOperatorNeitherEarnsNorDeletesADeposit`, which asserts a Creative break leaves a deposit standing **and** that ambient stone still breaks.

---

## Silica generation

### 17. Whether M7 configuration changed

**No.** M8 changed no data file. Every M7 generation decision is preserved byte-for-byte.

### 18–24. Final state, all carried forward unchanged

| Question | State |
| -------- | ----- |
| **18. Cell / chance** | `cell_chunks: 10`, `chance: 0.85`; radius 8–13; depth 5 ± 2; band y 40–96; salt 7311; thickness 5, irregularity 0.20, gap chance 0.18 |
| **19. Biome policy** | `britannia_mod:has_silica_deposits` = `minecraft:desert`, `minecraft:beach`. Badlands excluded (iron-stained red sand is the opposite of high-purity silica) |
| **20. Host policy** | `britannia_mod:silica_hosts` = `minecraft:sand`, `minecraft:sandstone`. **Red sand excluded.** `MaterializationService` still owns fluid, block-entity, provenance, managed-collision and indestructible rejection |
| **21. Existing-chunk safety** | Newly generated chunks only. `isNewChunk()` is false for a disk-loaded chunk, so an updated server never retro-populates. **No persistent marker, no retrofit scan, no migration pass** — now pinned by `anExistingChunkIsNeverRetroPopulated` |
| **22. Threading model** | `ChunkEvent.Load` → server thread (posted from `ChunkStatusTasks.full` through `mainThreadMailBox()`). New chunk = real `ProtoChunk`; disk-loaded = `ImposterProtoChunk`. **Not** moved to a `PlacedFeature` — the async feature path would race the ledger |
| **23. Ledger behaviour** | One `DepositInstance` per deposit however many chunks it spans; M4 three-way registration intact; refusals now journalled for diagnostics |
| **24. Determinism** | Candidate is a pure function of `(worldSeed, resource, natural config, owner cell)`; geometry is a pure function of the derived planner seed; forward and reverse chunk order produce identical unions |

Silica remains a **sedimentary** managed resource: Britannia **shovel** extracts it via `britannia_mod:silica_shovels`; the Britannia **pickaxe** is refused; the shovel is not authorised for stone or ore. Ordinary `minecraft:sand` and `minecraft:red_sand` are terrain, not economy — only a sited `britannia_mod:silica_sand_deposit` in the ledger is a deposit, and a Creative or wrong-tool break of one is still refused.

---

## Testing

### 25. Focused results

| Focused suite | Result |
| ------------- | ------ |
| `AmbientRockIsNotADepositTest` | **5 run · 0 failed** |
| `OreVeinFetcherParseTest` | **7 run · 0 failed** |
| `OreVeinDiagnosticsGameTests` | **7 run · 0 failed** |
| `NaturalSilicaGameTests` (10 incl. the new one) | **0 failed** |
| Programme contracts re-run: silica generation (new-chunk only, disk-loaded skipped, biome policy, badlands exclusion, red-sand host exclusion, candidate determinism, forward/reverse order, one ledger entry, no force-loading, materialisation, deposit protection); managed extraction (shovel, wrong tools refused, no Creative bypass, ambient rock exempt, no side effects on refusal); restoration (depletion, persistent debt, wall-clock, exact-location) | all pass |

### 26. JUnit

**2,972 run · 5 failed · 0 errors · 17 skipped.**

### 27. GameTest

**708 run · 2 failed** (`gradlew runGameTestServer --no-configuration-cache`).

### 28. New failures / resolutions

**No new deterministic failures.** Deltas against the verified M7 baseline:

| | M7 (verified) | M8 | Delta |
| --- | --- | --- | --- |
| JUnit run | 2,960 | 2,972 | **+12** |
| JUnit failed | 5 | 5 | **0** |
| GameTests run | 700 | 708 | **+8** |
| GameTests failed | 2 | 2 | **0** |

One defect of my own was found and fixed before commit: the refactor of `/populateores region` introduced a line comparing a vein's ore type with itself, which was dead rather than harmful, and was removed.

### 29. Baseline failures

Unchanged throughout, and not investigated:

- **JUnit (5):** `RoutineSkillGainPresentationTest`, `structure.hardening.MilestoneEightContentReportTest`, `structure.render.CorrectiveMilestoneNineARenderAlignmentTest`, `structure.render.MonolithMilestoneSevenRenderingTest` (2).
- **GameTests (2):** `aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`.
- **Intermittent textile GameTest:** did not appear in any M8 run.

No mass force-loading was used for validation anywhere in M8; the 4,096-chunk experiment was not repeated.

---

## Known debt

### 30. Real-terrain host rejection measurement

**Still unmeasured, carried forward unchanged.** Host-policy *logic* is proven against ten block types (`onlyApprovedHostsBecomeSilica`); the *percentage* of planned cells a real desert refuses is not known, so the materialised size of a bed remains an estimate against a median **921 planned** cells.

M8's exit criteria do not require materialised deposit sizing, so it was not a blocker. M8 also did not create a better deterministic way to measure it: `/orevein inspect` reports `materialised` and `blocked` for any deposit that has had a complete pass, which will answer the question directly the first time a silica bed generates in a real desert — but that needs terrain M8 had no reason to generate. Recorded, not closed.

### 31. Remaining unsupported deposit families

M7 gave replacement generation to **silica only** — it is the sole resource of 28 with a `natural` block. For the eight suppressed vanilla families:

| Family | Managed identity | Economic identity | Player extraction | Regeneration | Replacement generation |
| ------ | ---------------- | ----------------- | ----------------- | ------------ | ---------------------- |
| Coal | **none** | none | none | n/a | **none** |
| Iron | `britannia_mod:iron` | purity ore | `mining_pickaxes` | 6 h | **none** |
| Gold | `britannia_mod:gold` | purity ore | `mining_pickaxes` | 6 h | **none** |
| Redstone | **none** | none | none | n/a | **none** |
| Diamond | **none** | none | none | n/a | **none** |
| Lapis | **none** | none | none | n/a | **none** |
| Emerald | **none** | none | none | n/a | **none** |
| Copper | `britannia_mod:copper` | purity ore | `mining_pickaxes` | 6 h | **none** |

Three families are fully modelled and only lack natural generation; five have no managed identity at all and would need a resource definition before they could be generated or extracted. M8 did **not** expand generation to any of them — its written scope does not call for it.

### 32. Current copper / iron vanilla noise-vein residue

Unchanged and untouched by M8. Denied vanilla **placed features** are suppressed — all 19, in every `#minecraft:is_overworld` biome. Minecraft's separate `OreVeinifier` still produces `minecraft:copper_ore` + `raw_copper_block` at y 0–50 and `minecraft:deepslate_iron_ore` + `raw_iron_block` at y −60…−8, enabled by `ore_veins_enabled` on the Overworld's *noise settings* — not a feature, and unreachable by any biome modifier. Measured residue over 256 fixed-seed chunks against a same-seed control: **copper ~4.0%**, **iron ~3.4%** of vanilla volume.

**UltimaCraft therefore does not yet have absolute authority over natural iron and copper generation.**

### 33. Production approval

**Full natural-ore authority is NOT production-approved, and M8 passing does not change that.**

M5 suppression remains gated. Activating it would still empty eight families out of every newly generated chunk — five of them without even a managed identity to replace with — and the noise-vein residue means two of those families keep generating by a route the suppression cannot reach. M8 delivered operator visibility and a safe import; it delivered no replacement generation and no terrain authority.

Restoration semantics are also unchanged: one backend (`BrokenBlockTracker` → `BrokenBlockDataStorage` → `BlockRestoreHandler`), 6-hour wall-clock with restart persistence, silica's approved 24 hours the only exception. **No second scheduler was added** — `/orevein regenerate` only advances existing debts through that one store.

---

## Next step

### 34. Exact next playbook milestone

**Milestone 9 — Performance, Regression, Live-World QA, and Rollout Readiness** (playbook line 1207), the last milestone in the document.

### 35. Should a dedicated copper/iron noise-vein milestone be inserted before M9?

**Yes.** Insert it between M8 and M9. It was not touched during M8: M8's written purpose is admin, diagnostics, import and existing-world policy, and the noise veins are none of those.

### 36. Proposed title

> **Milestone 8.5 — Overworld Noise Ore-Vein Authority**

### 37. Scope

Obtain genuine UltimaCraft authority over the vanilla copper and iron that `OreVeinifier` writes directly into the Overworld's noise columns, which the M5 feature suppression structurally cannot reach. The milestone should first decide, explicitly, between three options already identified — accept the residue as a deliberate trickle; override the Overworld `worldgen/noise_settings` wholesale to clear `ore_veins_enabled`; or neutralise the `ore_veininess` noise parameters — and then implement the chosen one behind the same existing-chunk guarantee the programme has held since M5, Overworld-only, with the Nether untouched. Validation must be a **before/after fixed-seed terrain audit** rather than an ore count, because both closing options also delete the granite and tuff the veins deposit as filler: the audit has to show what happened to the rock, not just to the metal. The reusable harness exists — `SilicaDistributionAuditGameTests` builds the real Overworld generator from the registries without generating chunks, and the M5 same-seed control-world method measured the residue in the first place.

### 38. Why it must not be folded into M9

Three reasons, in order of weight. First, it is **terrain and geology work, not economic cleanup** — the two credible fixes reach the dimension's noise settings and change how much granite and tuff the world contains, which is a different kind of change from anything else in the programme and deserves to be judged on terrain evidence. Second, **M9 is the rollout milestone**, and rollout readiness cannot honestly be assessed while the question of whether UltimaCraft controls iron and copper is still open inside it; the gate and the thing being gated must not be the same milestone. Third, it carries a **real chance of being rejected**: accepting the residue is a legitimate outcome, and an owner should be able to decline the change without that decision being entangled with performance work, regression sweeps and live QA sign-off.

---

**Stopping after M8 as instructed.** Milestone 9 not begun, the proposed noise-vein milestone not implemented, nothing pushed, nothing merged into `patch-18`, worktree clean.
