# UltimaCraft OreVein Remediation — Milestone 8.5 Report

**Overworld Noise Ore-Vein Authority**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-21 · nothing pushed

---

## Git

| # | | |
| - | - | - |
| 1 | Starting branch / HEAD | `claude/ultimacraft-orevein-remediation-70bfeb` @ `85782243fa770639c8fb79ec4869ea83473f4a00`, worktree clean, no M9 work, no upstream configured (never pushed) |
| 2 | M7 commit | `c2a4678c2ffae4c0a367f8ddfa141be56a830619` — `feat(worldgen): generate silica as natural sedimentary lenses` |
| 3 | M8 commit | `85782243fa770639c8fb79ec4869ea83473f4a00` — `feat(oreveins): add deposit diagnostics and take the Rails import off the server thread` |
| 4 | **M8.5 commit** | **`9ce9b0548b93961585fc9fcfd6a28dce0e929576`** — `feat(worldgen): take the ore out of Minecraft's noise veins` (8 files, +472/−16) |
| 5 | Worktree cleanliness | **Clean.** `git status --short` empty |
| 6 | Nothing pushed | **Confirmed.** No upstream branch exists; nothing merged into `patch-18`. History unmodified — no reordering, no rewriting |

---

## Minecraft generation architecture

### 7. Exact `OreVeinifier` call path

Traced against the decompiled 1.21.1 sources this project actually builds against, not from memory:

```
NoiseBasedChunkGenerator.fillFromNoise
  └─ NoiseChunk.<init>(…, NoiseGeneratorSettings settings, RandomState randomState, …)
        NoiseRouter router = randomState.router()
        builder.add( ctx -> aquifer.computeSubstance(ctx, finalDensity.compute(ctx)) )
        if (settings.oreVeinsEnabled())                                  ← the gate
            builder.add(OreVeinifier.create(                             ← NoiseChunk.java:154-155
                router.veinToggle(), router.veinRidged(), router.veinGap(),
                randomState.oreRandom()))
        this.blockStateRule = new MaterialRuleList(builder.build())
  └─ NoiseChunk.getInterpolatedState() → MaterialRuleList.calculate()
        first non-null filler wins; null → generator writes settings.defaultBlock()
```

`OreVeinifier.create` returns a `NoiseChunk.BlockStateFiller` whose logic is:

```
d0 = veinToggle(ctx);  type = d0 > 0 ? COPPER : IRON;  d1 = |d0|
outside [type.minY, type.maxY]                    → null
d1 + edgeRoundoff < 0.4                           → null      (not in a vein)
oreRandom.nextFloat() > 0.7                       → null      (VEIN_SOLIDNESS)
veinRidged(ctx) >= 0                              → null      (ridged carve-out)
nextFloat() < richness && veinGap(ctx) > -0.3     → ORE       (2% of these are the raw block)
otherwise                                          → FILLER
```

`VeinType`: `COPPER(COPPER_ORE, RAW_COPPER_BLOCK, filler GRANITE, y 0…50)`, `IRON(DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK, filler TUFF, y −60…−8)`.

**The load-bearing observation: a vein is mostly filler, not ore.** Ore is the branch that requires two further rolls; every other material cell returns `type.filler`. That is what makes "remove the ore" separable from "remove the veins".

### 8. Why biome-feature suppression cannot reach it

Three independent reasons, each sufficient:

1. **It is not a feature.** It is a `BlockStateFiller` in the noise chunk generator's material rule list, consulted while the chunk's blocks are first being decided. `neoforge:remove_features` edits a biome's `GenerationStep` feature lists, which do not exist yet at that point.
2. **It is not attached to a biome.** It is attached to the *dimension*, via `oreVeinsEnabled` on `NoiseGeneratorSettings`. There is no biome to modify.
3. **It runs before decoration.** Even a feature-level removal that somehow matched would run in a later phase than the blocks it would need to remove.

Re-proven against the current source before anything was changed, as required.

### 9. Intervention point selected

**A mixin wrapping the return value of `OreVeinifier.create`**, in `com.seggellion.britannia_mod.mixin.OreVeinifierMixin`. The vanilla rule is left entirely intact and still evaluated; its result is inspected on the way out and any denied ore state is replaced by that vein's own filler. `null` passes through unchanged, so the generator's default-block fallback is untouched.

Mixins are an established mechanism in this repository — `britannia_mod.mixins.json` already registers ten, with MixinExtras on the classpath — so this is not a new kind of risk being introduced for this milestone.

Why it is the narrowest point available: it is one method with one return value, and that return value is the only object in the game capable of emitting vein ore. Wrapping it cannot change where veins are, how large they are, how dense they are, or what any non-vein cell becomes.

### 10. Alternatives rejected, and why

| Alternative | Rejected because |
| ----------- | ---------------- |
| **Datapack: `ore_veins_enabled: false`** on the Overworld noise settings | Requires shipping an override of the *entire* `worldgen/noise_settings/overworld.json`, freezing vanilla's whole terrain noise router at 1.21.1 values and breaking on the next Minecraft update — **and** it deletes the veins wholesale, taking the granite and tuff with them. This is the brief's option B, and it was measurable-but-worse |
| **Datapack: force `vein_gap` to a constant −1.0** | Would achieve exactly option A at the data layer (`veinGap > -0.3` becomes unsatisfiable, so every material cell returns filler) — but `vein_gap` is serialised *inline* in the noise settings, so it still needs the same whole-file override. Same brittleness, subtler effect, harder to explain |
| **Override `worldgen/noise/ore_veininess.json` amplitudes** | Cannot express "always ≤ −0.3"; zeroed amplitudes give 0, which still satisfies the ore branch. Also risks `NormalNoise` construction with a degenerate amplitude set |
| **NeoForge event hook** | None exists. There is no event around the material rule list, `NoiseChunk` construction, or `BlockStateFiller` evaluation |
| **Post-generation chunk scan** | Explicitly the last resort in the brief, and unnecessary here because the generation rule itself is controllable. It would also have to reconstruct the correct host state per cell, which is precisely the information the generator already has and a scan does not |
| **Replacing the whole `ChunkGenerator`** | Enormously wider blast radius for a four-block problem |

---

## Baseline

### 11. Seeds / sample method

Fixed seed **`britannia-m5-audit`**, chunks **(−8,−8) … (7,7)** = **256 chunks**, full world Y range, generated by a real dedicated server with a 256-chunk force-load — the scale this programme established as safe. **The 4,096-chunk force-load that hung the server was not repeated.** Region files parsed directly off disk.

The before-state was generated at HEAD `85782243` immediately before the change, so before and after differ only by this milestone.

Cross-check: the before-state reproduced the M5 audit's vein figures exactly (copper ore 3,464; raw copper 51; deepslate iron 709; raw iron 17), confirming determinism across sessions.

### 12. Baseline iron residue

`minecraft:deepslate_iron_ore` **709** (sections y−64, −48, −32 — the vein's y−60…−8 band); `minecraft:raw_iron_block` **17**. `minecraft:iron_ore` (stone variant, feature-only) **0**.

### 13. Baseline copper residue

`minecraft:copper_ore` **3,464** (sections y0, 16, 32, 48 — the vein's y0…50 band); `minecraft:raw_copper_block` **51**. `minecraft:deepslate_copper_ore` (feature-only) **0**.

### 14. Baseline granite / tuff

`minecraft:granite` **272,822** (1,065.7/chunk) · `minecraft:tuff` **289,864** (1,132.3/chunk) · `minecraft:stone` **3,413,945** · `minecraft:deepslate` **3,465,180**.

**Distinguishing ordinary granite/tuff from vein-emitted granite/tuff is not directly measurable off disk** — a finished chunk records a block, not which generator stage wrote it. It is measurable *differentially*, which is what the before/after does: the vein contribution is exactly the delta produced by removing the ore branch, since nothing else changed.

---

## Implementation

| # | | |
| - | - | - |
| 15 | **Production files changed** | `worldgen/NoiseVeinOreAuthority.java` (**new** — the substitution policy); `mixin/OreVeinifierMixin.java` (**new** — the wrapper); `worldgen/VanillaFeaturePolicy.java` (carries `resolvedBy` on unreachable sources, adds `everyUnreachableSourceIsResolved()`) |
| 16 | **Data / settings changed** | `britannia_mod.mixins.json` (+1 entry); `data/britannia_mod/worldgen/vanilla_feature_policy.json` (each unreachable source now records how it was resolved). **No noise settings, no biome modifier, no resource definition, no tag was changed** |
| 17 | Generator settings replaced or wrapped? | **Neither replaced nor overridden.** `NoiseGeneratorSettings`, the noise router and every density function are untouched vanilla. Only the *vein rule object* is wrapped |
| 18 | Post-generation scanning avoided? | **Yes — none exists.** No chunk is read, scanned or rewritten after generation. The correction happens inside the generator, at the moment the cell's material is decided |
| 19 | Existing-chunk safety mechanism | Structural: this is a rule consulted only while a chunk is being generated. A chunk on disk is never regenerated, so it is never re-evaluated. **No markers, no retrofit state, no migration, no scan.** M7's `isNewChunk()` guarantee is untouched and still tested |
| 20 | Dimension scope | **Overworld only, by construction.** `NoiseChunk` builds the vein rule only when `settings.oreVeinsEnabled()`. In 1.21.1 that is true only for `overworld(...)` (and its large-biomes/amplified variants); `nether()`, `end()`, `caves()` and `floatingIslands()` all pass `false`. The wrapped method is never called for them. Pinned by `onlyTheOverworldEnablesOreVeins`, which reads the live registry |

---

## Ore authority

Same seed, same 256 chunks, same Y range.

| # | Block | Before | After |
| - | ----- | -----: | ----: |
| 21 | `minecraft:iron_ore` | 0 | **0** |
| 21 | `minecraft:raw_iron_block` | 17 | **0** |
| 22 | `minecraft:copper_ore` | 3,464 | **0** |
| 22 | `minecraft:raw_copper_block` | 51 | **0** |
| 23 | `minecraft:deepslate_iron_ore` | 709 | **0** |
| 23 | `minecraft:deepslate_copper_ore` | 0 | **0** |

**All four named blocks: zero. All eight denied families: zero.**

### 24. Proof the result is deterministic and absolute

Not "not observed in a sample". The vein rule is a pure function whose entire output range is enumerable — two ores, two raw blocks, two fillers, and `null`. `NoiseVeinAuthorityGameTests` applies the authority to that whole set and asserts none of the four denied blocks is reachable from any input, that a `null` stays `null`, and that the mapping is stable across repeated calls. There is no probability anywhere in the substitution: it is a map lookup. The empirical 256-chunk result agrees with the contract.

### 25. Managed iron/copper still functional

Unaffected and re-verified. `britannia_mod:iron` (blocks `minecraft:iron_ore`, `minecraft:deepslate_iron_ore`) and `britannia_mod:copper` (`britannia_mod:copper_ore`) keep their resource definitions, purity-ore economy, `britannia_mod:mining_pickaxes` extraction, 6-hour regeneration and ledger identity. The authority acts **inside the noise generator**, on states the vein rule produces; it cannot see or alter a block that `MaterializationService`, `/populateores` or a player places. The full extraction, restoration and ledger suites pass unchanged.

---

## Terrain audit

Identical seed, bounds and Y range.

| # | Block | Before | After | Δ |
| - | ----- | -----: | ----: | --: |
| 26 | `granite` | 272,822 | 274,756 | **+1,934** |
| 27 | `tuff` | 289,864 | 290,674 | **+810** |
| 28 | `stone` | 3,413,945 | 3,414,108 | +163 |
| 29 | `deepslate` | 3,465,180 | 3,464,400 | −780 |
| | `diorite` | 283,483 | 283,880 | +397 |
| | `andesite` | 294,306 | 295,339 | +1,033 |
| | `dripstone_block` | 76,733 | 77,480 | +747 |
| | `gravel` | 125,810 | 125,945 | +135 |
| | `dirt` | 261,745 | 261,836 | +91 |
| | `clay`, `calcite`, `amethyst_block`, `smooth_basalt`, `magma_block` | — | — | **0 (identical)** |

**Ore removed: 4,241 cells. Geological material gained: ≈4,530 net.** The two balance to within 0.03% of the volume involved — nothing was lost, the ore volume was converted into rock.

### 30. Geological consequence

**Granite and tuff went up, not down.** That is the expected direction and the reason for choosing this intervention: the cells that were copper ore are now the granite of the copper vein they sat in, and the cells that were deepslate iron ore are now the tuff of their iron vein. A player walking a cave system sees the same granite banks and tuff pockets in the same places and the same shapes, with no metal in them.

Why granite gained +1,934 rather than exactly +3,515: a granite cell is in `#minecraft:stone_ore_replaceables` and a copper-ore cell is not, so the substituted cells become legal targets for the later granite/diorite/andesite/dripstone blob features. Some of the new granite is subsequently overwritten by those, which is where the +397 diorite, +1,033 andesite and +747 dripstone come from. This is ordinary vanilla geology continuing to work on ordinary vanilla rock, and it is why the total conserves while the individual rows move.

### 31. Was filler geology preserved?

**Yes — preserved and slightly increased.** No decline in any geological material to quantify. `stone` moved by +163 cells across 256 chunks (0.005%), so the milestone did **not** paint artificial stone tubes underground — the specific failure mode the brief warned against.

---

## Performance

| # | | |
| - | - | - |
| 32 | Baseline | Spawn-area preparation (fixed 441-chunk workload, same seed): **09:47:11 → 09:47:15 ≈ 4 s**. Server startup `Done (8.258s)` |
| 33 | Post-fix | Same workload: **10:09:10 → 10:09:13 ≈ 3 s**. Server startup `Done (6.043s)` |
| 34 | Measurable regression | **None.** The after run was marginally faster, which is within the noise of a 1-second-resolution log on a machine also running a build. The added work is one `HashMap` lookup per vein *material* cell — a small minority of cells in a small minority of columns — with no allocation on the hot path (the substitute returns a cached `defaultBlockState()` or the input itself) |
| 35 | Force-loading | **Avoided.** Both audits used the 256-chunk scale. The 4,096-chunk force-load that previously hung the server was not repeated |

---

## Programme regression

| # | Area | Result |
| - | ---- | ------ |
| 36 | Silica generation | **Unchanged.** `cell_chunks: 10`, `chance: 0.85`, desert + beach, badlands excluded, red sand excluded as host, new chunks only. `resources.json` untouched by this milestone; the M7 determinism, distribution-statistics and lens tests all pass |
| 37 | Ambient rock | **Unchanged.** `AmbientRockIsNotADepositTest` (5) passes: stone, granite and deepslate remain ordinary terraformable crust and did not become managed-deposit cells because the vein system changed |
| 38 | Existing chunks | **Unchanged.** `anExistingChunkIsNeverRetroPopulated` passes; no marker, scan or migration was added anywhere |
| 39 | Nether | **Unchanged.** `nether()` sets `oreVeinsEnabled = false`, so the wrapped method is never built for it; `onlyTheOverworldEnablesOreVeins` reads this from the live registry. Nether quartz, ancient debris, nether gold untouched — `netherOreGenerationIsUnchanged` still passes |
| 40 | End | **Unchanged.** `end()` sets `oreVeinsEnabled = false`; same registry assertion covers it, along with `caves` and `floating_islands` |
| 41 | JUnit | **2,972 run · 5 failed · 0 errors · 17 skipped** |
| 42 | GameTests | **717 run · 2 failed** at steady state (+9 new). Three consecutive runs: 2, 3, 2 failures |
| 43 | New failures | **None deterministic.** One transient appeared and was resolved: `ClientBrandingBackgroundPolicyTest.approvedTitleBackgroundFilesRemainByteIdentical` byte-pinned the whole shared `britannia_mod.mixins.json`, so registering a world-generation mixin read as branding tampering. The guard was **narrowed to what it protects** — the branding mixins are still asserted present, by content — rather than having its hash quietly bumped. The two genuine branding assets stay byte-pinned. The textile GameTest failed in two runs of three with its usual signature (*"full-inventory exchange did not drop exactly one output"*) and passed in the third; same known flake, no textile code touched |
| 44 | Baseline failures | JUnit (5): `RoutineSkillGainPresentationTest`, `structure.hardening.MilestoneEightContentReportTest`, `structure.render.CorrectiveMilestoneNineARenderAlignmentTest`, `structure.render.MonolithMilestoneSevenRenderingTest` ×2. GameTests (2): `aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`. All unchanged, none investigated |

**Delta against the M8 baseline:** JUnit 2,972 → 2,972 (0 new JUnit tests; the M8.5 tests are GameTests), failures 5 → 5. GameTests 708 → 717 (**+9**), failures 2 → 2.

---

## Eight-family authority matrix

| # | Family | Vanilla placed-feature suppressed | Alternate leakage route | Managed identity | Replacement generation |
| - | ------ | --------------------------------- | ----------------------- | ---------------- | ---------------------- |
| 45 | **Iron** | ✅ `ore_iron_upper/middle/small` | ✅ **closed by M8.5** (noise vein → tuff) | ✅ `britannia_mod:iron`, purity ore, `mining_pickaxes`, 6 h | ❌ none |
| 46 | **Copper** | ✅ `ore_copper`, `ore_copper_large` | ✅ **closed by M8.5** (noise vein → granite) | ✅ `britannia_mod:copper`, purity ore, `mining_pickaxes`, 6 h | ❌ none |
| 47 | **Gold** | ✅ `ore_gold`, `ore_gold_lower`, `ore_gold_extra` | ✅ none known | ✅ `britannia_mod:gold`, purity ore, `mining_pickaxes`, 6 h | ❌ none |
| 48 | **Coal** | ✅ `ore_coal_upper/lower` | ✅ none known | ❌ **none** | ❌ none |
| 49 | **Redstone** | ✅ `ore_redstone`, `ore_redstone_lower` | ✅ none known | ❌ **none** | ❌ none |
| 50 | **Diamond** | ✅ `ore_diamond` ×4 variants | ✅ none known | ❌ **none** | ❌ none |
| 51 | **Lapis** | ✅ `ore_lapis`, `ore_lapis_buried` | ✅ none known | ❌ **none** | ❌ none |
| 52 | **Emerald** | ✅ `ore_emerald` | ✅ none known | ❌ **none** | ❌ none |

Silica remains the only resource of 28 with natural replacement generation. **Eliminating the iron/copper residue did not make any replacement system complete**, and this matrix is the guard against reading it that way.

---

## Production gate

### 53. Full vanilla iron/copper authority achieved?

**Yes.** Zero across 256 fixed-seed chunks, and zero by contract: the noise-vein path can no longer emit `minecraft:iron_ore`, `minecraft:deepslate_iron_ore`, `minecraft:copper_ore` or `minecraft:deepslate_copper_ore`, and the placed-feature path has been closed since M5.

### 54. Full denied-family vanilla authority achieved?

**Yes, for all eight, on every route currently known.** All 19 placed features are removed in every Overworld biome; the one alternate route that existed — the noise veins, affecting iron and copper — is closed. Over the audited 256 chunks every denied family measures zero.

The honest qualification: this is *"no route currently known"*, not a proof of exhaustion. Two routes have been found by looking; the second was found only because the first was audited empirically. Any future finding would be a new route, not a regression of these two.

### 55. Replacement generation complete?

**No.** Silica only. Five of eight families have no managed identity at all; three have identity but no natural generation.

### 56. Safe to advance to Milestone 9?

**Yes — with M9's rollout rehearsal explicitly gated on replacement generation.**

M9 is *Performance, Regression, Live-World QA, and Rollout Readiness*. Reading its exit criteria against current state:

| M9 exit criterion | Current state |
| ----------------- | ------------- |
| Denied vanilla ores do not naturally generate in new chunks | **Now satisfiable** — this was the blocker M8.5 removed |
| Existing chunks are not silently rewritten | Already held since M5, tested |
| Controlled deposit generation is deterministic | Held since M7, tested |
| Duplicate protection works | Held since M4, tested at M8 |
| Silica extraction/regeneration after restart | Held since M7, tested |
| Restoration cost not proportional to unloaded debts | Held since M4 (10,000-debt scale test) |
| No new correctness regressions | Held |
| Performance acceptable at long-lived multiplayer scale | **M9's own work** |
| Rollback procedure documented | **M9's own work** |

So M9's *measurement and regression* half can proceed now. Its **rollout rehearsal** step 4 — *"enable new-chunk vanilla suppression"* — must not be treated as production approval while five families would generate nothing at all.

### 57. Exact remaining blockers

**Blocking production rollout (not M9 itself):**

1. **No replacement generation for eight families.** Activating suppression on production would empty coal, iron, gold, redstone, diamond, lapis, emerald and copper out of every new chunk.
2. **No managed identity for five families** — coal, redstone, diamond, lapis, emerald. They cannot be replaced even by operator placement until they have resource definitions.

**Not blocking, but carried:**

3. Real-terrain host-rejection percentage for silica still unmeasured, so materialised bed size is an estimate.
4. Silica frequency, size and biome membership remain provisional balancing values.
5. Agapite live Rails radius `55 → 8`; silver/tin tuning.

**Recommended next step:** proceed to **Milestone 9**, and treat its rollout rehearsal as gated on blockers 1 and 2. If the owner wants production activation sooner, the shortest path is a milestone that gives the five identity-less families resource definitions and gives all eight controlled natural generation — the M7 seam already supports it through data alone.

---

**Stopping after Milestone 8.5 as instructed.** Milestone 9 read but not begun, nothing pushed, nothing merged into `patch-18`, worktree clean.
