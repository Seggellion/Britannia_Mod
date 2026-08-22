# UltimaCraft OreVein Remediation — Milestone 5 Report

**Controlled Vanilla Ore Suppression**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-20

---

## Objective

Make UltimaCraft authoritative over selected vanilla mineral generation in **future** Overworld chunks, by removing the vanilla economic-mineral placed features from Overworld biome generation with `neoforge:remove_features`, while leaving the rock and world-formation geology the Mining ladder depends on completely untouched.

M5 changes generation policy only. It does not scan, convert or rewrite anything that already exists, and it does not move iron or gold onto managed block identities.

---

## Findings

### The audit and the playbook both missed a second vanilla mineral generator

This is the significant result of M5, and it changes what "suppressed" means for two families.

Minecraft has **two** systems that put ore in the ground, not one:

1. **Placed features**, attached to biomes, run during the `underground_ores` decoration step. This is what `neoforge:remove_features` removes, and it is what the audit and the playbook describe.
2. **`OreVeinifier`**, part of the noise chunk generator. It writes blocks directly while the column is being carved, *before any decoration step runs*. It is switched on by the `ore_veins_enabled` field on the dimension's **noise settings**, not by any biome. No biome modifier can reach it.

`net.minecraft.world.level.levelgen.OreVeinifier.VeinType` in 1.21.1:

| Vein | Ore block | Raw block (2% of hits) | Filler | Y range |
| ---- | --------- | ---------------------- | ------ | ------- |
| COPPER | `minecraft:copper_ore` | `minecraft:raw_copper_block` | `minecraft:granite` | 0 … 50 |
| IRON | `minecraft:deepslate_iron_ore` | `minecraft:raw_iron_block` | `minecraft:tuff` | −60 … −8 |

`NoiseGeneratorSettings.overworld(...)` passes `oreVeinsEnabled = true`, so the Overworld runs both.

**How it was found.** The first fixed-seed audit came back with every denied family at zero *except* copper, which sat at 1,672 blocks. Three pieces of evidence identified the source rather than guessing at it:

- The residual copper occupied exactly y0–y50 and nothing below y0, whereas the copper placed features span y−16 … y112.
- `minecraft:raw_copper_block` was present at 30 blocks — **no placed feature in the game produces that block**; only `OreVeinifier` does.
- The observed raw-block share was 30 ÷ 1,702 = 1.76%, against `CHANCE_OF_RAW_ORE_BLOCK = 0.02F`.

The same-seed control world (below) then closed it conclusively: `raw_copper_block` = 51 and `raw_iron_block` = 17 in **both** the suppressed world and the unsuppressed control — bit-identical. Feature removal has no effect whatsoever on the vein output, because the veins are not features.

**Consequence.** Copper and iron are suppressed *on the feature route only*. Measured over 256 chunks, 96.0% of vanilla copper and 96.6% of vanilla iron are gone; the remainder is vein output. The other six families are eliminated outright.

This is recorded in the shipped data as a fourth, explicitly named category — see *Classification validation*. It is **not** fixed in M5, and deliberately so: the only levers are the dimension's noise settings or noise parameters, both of which would also delete the granite and tuff those veins deposit as filler. That is a change to Overworld geology, which M5 was told to preserve, and it is an owner decision rather than an implementation detail. See *Recommendation*.

### Two smaller corrections to the audit's names

- `minecraft:ore_blackstone` is **Nether-only** and generates in `underground_decoration`. The M3 report listed it as an allowed Overworld feature; it is out of scope, not allowed.
- `minecraft:underwater_magma` shares the governed `underground_ores` step but is a cave feature, not an ore. It is classified `allowed` so that the step's membership is complete.

---

## Verified feature inventory

Every key below was read from the live placed-feature registry and cross-checked against decompiled `OrePlacements` / `BiomeDefaultFeatures` for 1.21.1. None were copied from the audit.

### Suppressed — 19 features, 9 families, all in `underground_ores`

| Resource family | Minecraft 1.21.1 placed feature key(s) | M5 policy |
| --------------- | -------------------------------------- | --------- |
| Coal | `ore_coal_upper`, `ore_coal_lower` | Suppressed |
| Iron | `ore_iron_upper`, `ore_iron_middle`, `ore_iron_small` | Suppressed *(vein residue remains)* |
| Gold | `ore_gold`, `ore_gold_lower` | Suppressed |
| Gold (badlands extra) | `ore_gold_extra` | Suppressed |
| Redstone | `ore_redstone`, `ore_redstone_lower` | Suppressed |
| Diamond | `ore_diamond`, `ore_diamond_medium`, `ore_diamond_large`, `ore_diamond_buried` | Suppressed |
| Lapis | `ore_lapis`, `ore_lapis_buried` | Suppressed |
| Emerald | `ore_emerald` | Suppressed |
| Copper | `ore_copper`, `ore_copper_large` | Suppressed *(vein residue remains)* |

### Allowed — 11 features, 8 families, same step, deliberately kept

| Resource family | Minecraft 1.21.1 placed feature key(s) | M5 policy |
| --------------- | -------------------------------------- | --------- |
| Andesite | `ore_andesite_upper`, `ore_andesite_lower` | Allowed — ACTIVE Mineable, Mining 15 |
| Diorite | `ore_diorite_upper`, `ore_diorite_lower` | Allowed — ACTIVE Mineable, Mining 10 |
| Granite | `ore_granite_upper`, `ore_granite_lower` | Allowed — ACTIVE Mineable, Mining 20 |
| Tuff | `ore_tuff` | Allowed — ACTIVE Mineable, Mining 25 |
| Gravel | `ore_gravel` | Allowed — world formation |
| Dirt | `ore_dirt` | Allowed — world formation |
| Clay | `ore_clay` | Allowed — lush caves; the managed clay bed is a placed deposit, not this |
| Underwater magma | `underwater_magma` | Allowed — shares the step, is not an ore |

### Out of scope — 11 features, 8 families, all in `underground_decoration`

| Resource family | Minecraft 1.21.1 placed feature key(s) | M5 policy |
| --------------- | -------------------------------------- | --------- |
| Nether gold | `ore_gold_nether`, `ore_gold_deltas` | Out of scope (Nether) |
| Nether quartz | `ore_quartz_nether`, `ore_quartz_deltas` | Out of scope (Nether) |
| Nether gravel | `ore_gravel_nether` | Out of scope (Nether) |
| Blackstone | `ore_blackstone` | Out of scope (Nether) |
| Nether magma | `ore_magma` | Out of scope (Nether) |
| Soul sand | `ore_soul_sand` | Out of scope (Nether) |
| Ancient debris | `ore_ancient_debris_large`, `ore_debris_small` | Out of scope (Nether) |
| Infested stone | `ore_infested` | Out of scope — Overworld, but not an economic mineral |

### Discovered but unresolved

| Mechanism | Family | Produces | Y range | Status |
| --------- | ------ | -------- | ------- | ------ |
| `OreVeinifier` copper vein | copper | `copper_ore`, `raw_copper_block` | 0 … 50 | **Unresolved** — not a feature; out of `remove_features` reach |
| `OreVeinifier` iron vein | iron | `deepslate_iron_ore`, `raw_iron_block` | −60 … −8 | **Unresolved** — same |

41 features are classified; the two vein mechanisms are recorded separately, because calling them "features" would be false.

---

## Suppression implementation

One biome modifier, `britannia_mod:suppress_vanilla_overworld_ores`:

```json
{
  "type": "neoforge:remove_features",
  "biomes": "#minecraft:is_overworld",
  "features": [ … the 19 suppressed keys … ],
  "steps": "underground_ores"
}
```

Three deliberate choices:

- **`#minecraft:is_overworld`**, not a hand-picked biome list. Any Overworld biome added later by Minecraft, NeoForge or another mod is covered automatically. A subset would silently leak the moment a biome was added.
- **`steps: "underground_ores"`** is a second belt. Every Nether ore lives in `underground_decoration`, so even a mistaken feature id in the list could not remove a Nether resource through this modifier.
- **The feature list is derived from the classification, never hand-maintained.** `theBiomeModifierRemovesExactlyTheSuppressedSet` asserts set equality between the modifier's `features` array and `VanillaFeaturePolicy.suppressed()`, so the two cannot drift in either direction.

The classification itself lives in `data/britannia_mod/worldgen/vanilla_feature_policy.json` and is read by `VanillaFeaturePolicy`, a pure-Java classpath-JSON loader in the same fail-fast style as `MineableCatalog` and `ResourceCatalog`. The mechanism says *what is removed*; the classification says *what was deliberately kept*, *what was deliberately not decided*, and *what cannot be reached* — none of which a removal list can express.

---

## Why allowed geology survives

The Mining ladder's mid-tier progression is granite → diorite → andesite → tuff. Removing any of those features would delete a whole band of Mining progression, and it is the easiest mistake to make here, because those features sit in the same decoration step as the ores and are named `ore_*` in vanilla.

Four independent guards:

1. **They are not in the removal list.** The modifier names 19 keys; none is a rock family.
2. **They are classified `allowed` with a stated reason**, and `everyAllowedEntryExplainsWhyItSurvives` fails if an entry has no reason.
3. **`thoseRockFamiliesAreActiveMineables`** cross-checks the four rock families against `MineableCatalog`, so the protection is tied to the actual Mining data rather than to a hard-coded list. If a rock family were retired from the catalogue, or renamed, this fails.
4. **`theMiningLaddersRockStillGeneratesEverywhereItDid`** (GameTest) walks the live biome registry and asserts the rock features are still attached.

Empirically, over the identical 256 chunks with and without suppression:

| Block | Control (no suppression) | Suppressed | Δ |
| ----- | ------------------------ | ---------- | --- |
| `granite` | 264,418 | 270,961 | +6,543 |
| `diorite` | 273,629 | 282,514 | +8,885 |
| `andesite` | 285,474 | 291,941 | +6,467 |
| `tuff` | 287,700 | 287,904 | +204 |
| `gravel` | 125,296 | 125,508 | +212 |
| `dirt` | 261,532 | 260,874 | −658 |
| `clay` | 223 | 187 | −36 |
| `calcite` | 5,004 | 5,004 | 0 |
| `amethyst_block` | 3,888 | 3,888 | 0 |
| `smooth_basalt` | 6,141 | 6,141 | 0 |
| `dripstone_block` | 75,111 | 77,187 | +2,076 |

Rock counts go **up** slightly, which is the expected direction: an ore block that is no longer placed leaves its host rock in place. Summing every allowed-geology delta plus stone and deepslate gives **+166,615**, against **166,383** denied ore blocks removed — a 0.14% discrepancy. Essentially every removed ore block came back as its host rock, and nothing else in the geology moved.

---

## Existing-world safety

`neoforge:remove_features` edits the biome's generation settings that the chunk generator consults **when generating a chunk**. It has no retroactive component:

- **Already-generated chunks are never revisited.** Nothing in M5 reads, iterates, loads or force-loads existing chunks. There is no world scan, no chunk sweep, and no migration pass. The M5 diff adds two data files, one loader and two test files — there is no runtime code path that touches a `LevelChunk` at all.
- **Structure blocks are untouched.** Structures place their own blocks from their own templates; they do not go through the biome's `underground_ores` feature list. A mineshaft, ruined portal or ancient city containing ore keeps it.
- **Manually placed blocks are untouched.** Suppression is a generation-time policy. Nothing prevents a player, a command, a test fixture, or the M3/M4 materialisation service from placing any ore block anywhere, at any time. `deliberatelyPlacedOreBlocksAreUntouched` (GameTest) places denied ore blocks and asserts they survive.

The architectural invariant M5 establishes concerns **denied vanilla natural mineral generation**, not the existence of the block state anywhere in the world.

---

## Fixed-seed world audit

The project's GameTest harness cannot answer this question: `GameTestServer.create` hard-codes `WorldPresets.FLAT`, so no ore feature generates in a GameTest world at all and a chunk scan there would pass no matter what. The audit therefore used a real dedicated server plus an off-line Anvil/NBT region reader.

**Method.** `runServer` with a fixed seed; a throwaway world datapack force-loads a stated chunk box so the sample is bounded and reproducible rather than "whatever happened to be flushed"; region files are then parsed directly off disk (1.18+ `sections[].block_states` palette/`data` bit-unpacking).

> The force-load is part of the **audit harness**, not the suppression mechanism. It lives in `run/server/world/datapacks/m5_audit/`, which is gitignored, and no shipped code force-loads anything. The scope rule "do not force-load chunks for suppression" is not affected.

| Parameter | Value |
| --------- | ----- |
| Seed | `britannia-m5-audit` |
| Level type | `minecraft:normal` (default Overworld generator) |
| Dimensions inspected | `minecraft:overworld`, `minecraft:the_nether` |
| Overworld bounds | chunks (−8,−8) … (7,7) — blocks −128…127 on both axes |
| Overworld chunks | **256**, all at `minecraft:full`; 0 partial, 0 unreadable |
| Nether bounds | chunks (−4,−4) … (3,3) |
| Nether chunks | **64**, all at `minecraft:full` |
| Control world | same seed, same box, biome modifier removed from the source tree |

### Denied mineral counts — suppressed world vs same-seed control

| Family | Control | Suppressed | Removed | Residual source |
| ------ | ------- | ---------- | ------- | --------------- |
| Coal | 34,426 | **0** | 100% | — |
| Gold | 6,673 | **0** | 100% | — |
| Redstone | 9,222 | **0** | 100% | — |
| Diamond | 6,388 | **0** | 100% | — |
| Lapis | 6,469 | **0** | 100% | — |
| Emerald | 0 | **0** | n/a — see below | — |
| Iron | 21,053 | **709** | 96.6% | `OreVeinifier` iron vein |
| Copper | 86,325 | **3,464** | 96.0% | `OreVeinifier` copper vein |
| **Total** | **170,556** | **4,173** | **97.6%** | |

Nothing is hidden: the two non-zero rows are real, and they are the finding above.

**Emerald caveat.** Emerald generates only in mountain biomes, and the audit box contains none — the control world also produced zero. Emerald suppression is therefore proven at registry level (`noOverworldBiomeStillRunsADeniedMineralFeature` walks every `#minecraft:is_overworld` biome) but **not** at block level by this sample. The same caveat applies to `ore_gold_extra`, the badlands-only gold distribution.

### Attribution of the residual

Every residual block is accounted for as vein output, not as a surviving feature:

| Evidence | Control | Suppressed |
| -------- | ------- | ---------- |
| `deepslate_copper_ore` (feature-only variant) | 5,627 | **0** |
| `iron_ore`, stone variant (feature-only) | 14,348 | **0** |
| `copper_ore` Y span | y−16 … y96 (feature range) | y0 … y50 (**vein range only**) |
| `deepslate_iron_ore` Y span | y−64 … y0 | y−64 … y−17 (**vein range only**) |
| `raw_copper_block` (vein-only block) | 51 | **51** — identical |
| `raw_iron_block` (vein-only block) | 17 | **17** — identical |

The deepslate copper and stone iron variants — which only the placed features produce — are gone entirely. The vein-only raw blocks are unchanged to the block. Both facts point the same way.

Per chunk, the residual is 13.5 copper ore and 2.8 deepslate iron ore, against 337.2 and 82.2 in the control.

### Ambiguous ore blocks from structures or fixtures

**None.** The scan lists every block whose id contains `ore` or `raw_`; outside the two vein families there were zero denied ore blocks from any source in 256 chunks — no structure-sourced, fixture-sourced or manually placed occurrences appeared in the sample. No `britannia_mod:*` ore block appeared either (see *Production rollout warning*).

### Allowed geology evidence (per chunk, suppressed world)

`stone` 13,355 · `deepslate` 13,547 · `andesite` 1,140 · `tuff` 1,125 · `diorite` 1,104 · `granite` 1,058 · `dirt` 1,019 · `gravel` 490 · `dripstone_block` 302 · `smooth_basalt` 24 · `calcite` 20 · `amethyst_block` 15 · `clay` 0.7 · `magma_block` 0.3

These millions of counted blocks are also the scan's control: the reader demonstrably sees blocks, so the zeros above are absences rather than a broken parser.

### Reproducibility

Two independently generated worlds from the same seed produced identical copper figures (1,672 over the same 54 chunks in the first pass), and the control world reproduced `raw_copper_block`, `raw_iron_block`, `calcite`, `amethyst_block` and `smooth_basalt` counts exactly. Terrain is deterministic from the seed as expected.

---

## Nether verification

**The first-pass Nether suppression set is empty. M5 removes nothing from the Nether.**

Four independent proofs:

1. **Policy.** All 11 Nether features are classified `out_of_scope`, each recording `underground_decoration` as its step. `theNetherSuppressionSetIsEmptyAndRecorded` asserts the intersection of the suppressed set with the Nether set is empty.
2. **Mechanism — biome scope.** The modifier targets `#minecraft:is_overworld`. Nether biomes are not in that tag.
3. **Mechanism — step scope.** The modifier is constrained to `underground_ores`. Every Nether ore is in `underground_decoration`, a step the modifier does not touch.
4. **Runtime.** `netherOreGenerationIsUnchanged` (GameTest) walks the live Nether biome registry and asserts the Nether ore features are still attached.

Empirically, over 64 fully generated Nether chunks in the suppressed world:

| Block | Total | Per chunk |
| ----- | ----- | --------- |
| `nether_quartz_ore` | 4,267 | 66.7 |
| `nether_gold_ore` | 1,292 | 20.2 |
| `ancient_debris` | 97 | 1.5 |
| `blackstone` | 11,476 | 179.3 |
| `magma_block` | 10,923 | 170.7 |
| `gravel` | 9,839 | 153.7 |
| `soul_sand` | 3,611 | 56.4 |
| `glowstone` | 218 | 3.4 |
| `netherrack` | 665,729 | 10,402.0 |

No Overworld ore blocks leaked into the Nether sample.

---

## Classification validation

The failure mode this guards against is silence: Minecraft adds an ore variant, or a mod adds an Overworld biome with a new mineral, and nothing notices because the removal list simply does not mention it.

Four detectors, at two different levels:

| Detector | Level | Catches |
| -------- | ----- | ------- |
| `everyVanillaOrePlacementOfThisVersionIsClassified` | JUnit, parses decompiled `OrePlacements.java` | A vanilla ore placement in this MC version that is in no bucket |
| `everyVanillaOrePlacementInTheLiveRegistryIsClassified` | GameTest, walks the live registry | The same, but including anything a mod or datapack registered at runtime; also catches a policy entry naming a feature this version no longer has |
| `noOverworldBiomeStillRunsADeniedMineralFeature` | GameTest, exhaustive over `#minecraft:is_overworld` × every decoration step | A denied feature that survives in any biome or slipped into a different step |
| `suppressionRemovedSomethingRatherThanFindingNothing` | GameTest | The trivial pass — a suppression that "succeeds" because it matched nothing |

The fourth category added by this milestone is validated too. `VanillaFeaturePolicy` refuses to load a policy where an unreachable source names a family that is not suppressed, names no blocks to look for, has an inverted height range, or collides with a classified feature id. And `theOverworldStillGeneratesTheNoiseOreVeinsWeCannotRemove` asserts the leak is **still open** — if a future Minecraft, NeoForge or datapack ever sets `ore_veins_enabled` to false, that test fails and sends whoever sees it to delete the now-stale policy entries, rather than leaving the mod carrying a documented gap that no longer exists.

---

## Changes

M5 is purely additive. No tracked file was modified.

| File | Change | Reason |
| ---- | ------ | ------ |
| `src/main/resources/data/britannia_mod/worldgen/vanilla_feature_policy.json` | New — 19 suppressed / 11 allowed / 11 out-of-scope / 2 unreachable, schema 1 | The auditable source of truth: what is removed, what is deliberately kept, what is deliberately undecided, what cannot be reached |
| `src/main/resources/data/britannia_mod/neoforge/biome_modifier/suppress_vanilla_overworld_ores.json` | New — `neoforge:remove_features`, `#minecraft:is_overworld`, `underground_ores` | The mechanism, derived from and pinned to the classification |
| `src/main/java/com/seggellion/britannia_mod/worldgen/VanillaFeaturePolicy.java` | New — classpath-JSON loader with fail-fast validation | Lets plain JUnit read the shipped policy without booting the game, in the established `MineableCatalog` pattern |
| `src/test/java/com/seggellion/britannia_mod/worldgen/VanillaFeaturePolicyTest.java` | New — 21 tests | Anti-drift, rock-family protection, Nether emptiness, loader rejection, version completeness, the unreachable category |
| `src/main/java/com/seggellion/britannia_mod/gametest/VanillaOreSuppressionGameTests.java` | New — 7 GameTests | Runtime proof against the live registry rather than against the data files |

---

## Tests

| Test / command | Result |
| -------------- | ------ |
| `gradlew test --tests *VanillaFeaturePolicyTest*` | **21 run, 0 failed, 0 skipped** |
| `gradlew test` (full JUnit) | **2,921 run, 5 failed, 0 errors, 17 skipped** |
| `gradlew runGameTestServer` (run 1) | **675 run, 3 failed** |
| `gradlew runGameTestServer` (run 2, `--rerun-tasks`) | **675 run, 2 failed** |
| `gradlew build -x test` | **BUILD SUCCESSFUL** |
| Jar content check | both M5 data files present in `build/libs/…-all.jar` (736 B and 6,995 B) |
| Dedicated server boot (×4, real world generation) | biome modifier loaded with **no parse or registry error**; empirically applied |

### Baseline comparison

| | Post-M4 baseline | M5 | Delta |
| --- | --- | --- | --- |
| JUnit run | 2,900 | 2,921 | **+21** (all M5, all passing) |
| JUnit failed | 5 | 5 | **0** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 674 | 675 | **+1** (M5) |
| GameTests failed | 2 | 2 (steady state) | **0** |

### Failure classification

**Known JUnit baseline failures — 5, unchanged, none in M5 code:**

- `RoutineSkillGainPresentationTest` (1)
- `structure.hardening.MilestoneEightContentReportTest` (1)
- `structure.render.CorrectiveMilestoneNineARenderAlignmentTest` (1)
- `structure.render.MonolithMilestoneSevenRenderingTest` (2)

**Known pre-existing GameTest failures — 2, unchanged:**

- `aprivateplotacceptsitsownerandrefuseseveryoneelse`
- `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`

Both appear in the M0 baseline log captured before any milestone work, and in every run since. *(Correction to earlier milestone reports, which described this pair as "the two GrapeArbor failures": only the legacy-vine test is GrapeArbor; the other is a private-plot test. The pair and its provenance are unchanged.)*

**Intermittent textile flake — observed, then cleared:**

- `spinningandweavingareexactandrejectspiderssilk` failed in run 1 and **passed in run 2**. It is the same known intermittent test, previously seen in the M4 runs and absent from the baseline, M1, M2 and M3 runs. No textile code was touched or examined. Not an M5 regression.

**New deterministic regressions: none.**

All 7 M5 GameTests and all 21 M5 JUnit tests passed in every run.

---

## Invariants now established

| Invariant | Status |
| --------- | ------ |
| Denied vanilla mineral **placed features** no longer participate in new Overworld chunk generation | ✅ Established — registry-exhaustive and empirical |
| Denied vanilla minerals no longer **reach the ground at all** in new Overworld chunks | ⚠️ **Six of eight families only.** Copper and iron retain ~4% and ~3% of their vanilla volume from `OreVeinifier`, which no biome modifier can reach |
| Allowed rock/geology features remain | ✅ Established — four guards plus block-level evidence |
| Existing chunks are untouched | ✅ Established — no code path in M5 reads or writes a chunk |
| Manual/structure ore blocks remain possible | ✅ Established — GameTest-proven |
| Nether generation is unchanged | ✅ Established — four proofs, including 64 generated Nether chunks |
| No post-generation ore scan exists | ✅ Confirmed — none added, none needed |
| No OreVein clear/cleanup operation is needed | ✅ Confirmed — `/populateores clear` and `/undoores` remain disabled from M1 |

---

## Production rollout warning

**Enabling the M5 suppression definitions alone on production would create immediate and severe resource gaps. M5 implementation completion is not deployment approval.**

The audit measured this directly rather than inferring it. In 256 freshly generated chunks with suppression active, the number of `britannia_mod:*` ore blocks generated was **zero**. That is not a sampling artefact: there is no worldgen path that produces them. `MaterializationService` is reachable only from `PopulateOresCommand` (an operator command) and `DepositRegistrar` (Rails- or admin-driven). The mod registers no `Registries.FEATURE` entry and no biome modifier that adds deposits, so **no managed deposit is ever placed automatically in a new chunk**.

The practical effect on any newly generated terrain after activation:

| Suppressed resource | Vanilla generation after activation | Controlled replacement generation | Gap |
| ------------------- | ----------------------------------- | --------------------------------- | --- |
| Coal | None | None automatic | **Total** |
| Gold | None | None automatic | **Total** |
| Redstone | None | None automatic | **Total** |
| Diamond | None | None automatic | **Total** |
| Lapis | None | None automatic | **Total** |
| Emerald | None | None automatic | **Total** |
| Iron | ~3.4% of vanilla volume, deepslate-only, y−60…−8 | None automatic | **Near-total** |
| Copper | ~4.0% of vanilla volume, stone-only, y0…50 | None automatic | **Near-total** |

Every one of the eight suppressed families currently lacks equivalent controlled natural generation. Redstone, diamond and lapis additionally have no managed resource identity at all in the current catalogue, so they cannot be replaced by placement even manually without new resource definitions.

Activation must therefore be gated behind a deliberate rollout in which controlled generation exists and is proven first. Shipping the jar with these data files present **is** activation for every chunk generated after the update — there is no runtime toggle in M5. If the owner wants the code merged before the economy is ready, the suppression data needs a gate (see *Playbook impact*).

---

## Later managed iron/gold cutover

Not implemented, as instructed. Recommendation for placement:

**Do the cutover in M8, alongside the existing-world retrofit, and not before.**

The reasoning:

- M2 gives it canonical resource definitions, M3 gives it deterministic geometry, M4 gives it stable identity and a ledger, and M5 gives it a classification that already names iron and gold as owned families. The infrastructure is complete; nothing is missing that would force it earlier.
- The cutover changes block identity for resources that **already exist in the live world in vanilla form**. That makes it fundamentally a migration problem, not a generation problem, and M8 is where the retrofit machinery and the operator command suite land. Doing it in M6 or M7 would mean building a one-off migration path and then rebuilding it in M8.
- It should be sequenced *after* the M5 activation gate, so the ordering is: controlled generation exists → suppression activates → block identity migrates. Migrating identity while vanilla iron is still generating would leave two iron identities in the same world with no way to tell which came from where.

One M5-specific input to that design: the `OreVeinifier` iron vein will keep producing `minecraft:deepslate_iron_ore` in new chunks regardless of the cutover. Whatever identity managed iron takes, vanilla deepslate iron ore will continue to appear at y−60…−8 unless the noise-vein decision below is taken first.

---

## Temporary debt

| Item | Origin | Status |
| ---- | ------ | ------ |
| **Noise ore-vein leak (copper, iron)** | **M5 — new** | Recorded in policy data, pinned by a GameTest that fails if it ever closes. Needs an owner decision; the only fixes touch the dimension's noise settings and would also remove the granite/tuff vein filler |
| **Emerald and badlands-gold not block-level verified** | **M5 — new** | Registry-level proof only; the audit box contains no mountain or badlands biome. A second audit box over those biomes would close it |
| **No managed replacement generation** | M3/M4, quantified by M5 | Measured at zero managed ore blocks per 256 generated chunks. This is the blocker for activation |
| **Redstone, diamond, lapis have no managed resource identity** | Pre-existing, surfaced by M5 | Cannot be replaced even by manual placement without new resource definitions |
| Rails synchronous fetch | M0/M4 | Unchanged — deferred to M8 |
| Agapite live radius correction | M3 | Unchanged — live Rails row still carries the old radius; not modified, per scope |
| Silver/tin balancing | M3/M4 | Unchanged — deferred to M9 |
| `ore_blackstone` misclassified in the M3 report | M3 | **Corrected in M5** — Nether-only, out of scope |
| `underwater_magma` shares the governed step | M5 | Resolved — classified `allowed`, not an ore |

---

## Playbook impact

**Yes — M6 through M9 need amendment.** Three changes, one of them structural.

### 1. M7/M8/M9 need a coordinated activation gate for M5 suppression — this is the important one

The playbook treats suppression as a milestone that completes. It does not: M5 produces data that takes effect the moment the jar ships, for every chunk generated afterwards, with no runtime switch. Meanwhile the resources it suppresses have no automatic replacement, and three of them have no managed identity at all.

Recommended amendment — add an explicit gate with a named owner decision point:

- **M6/M7**: keep the suppression data in the tree but **not active**. The cleanest mechanism is a config flag consulted at datapack-load time, or shipping the biome modifier behind a built-in datapack that is disabled by default. Either keeps the code path exercised by tests while leaving production generation untouched.
- **M7**: silica sedimentary-lens generation is the first controlled *natural* generation in the programme. It should be treated as the reference implementation for how every suppressed family eventually gets replaced, and its design reviewed with that in mind rather than as a one-off.
- **M8**: activation prerequisites — controlled generation for every suppressed family, managed identities for redstone/diamond/lapis, the existing-world retrofit, and the operator commands needed to fix a bad rollout. The iron/gold cutover belongs here.
- **M9**: activation itself, plus the balancing pass, plus a repeat of the M5 fixed-seed audit against the activated build. The audit harness built for M5 is reusable as-is.

### 2. M6 or M9 must take the noise ore-vein decision

The playbook has no milestone that can absorb it, because it did not know the mechanism existed. It is a small decision with a real trade-off, and it should be made explicitly rather than by default:

- **Accept the leak.** Copper and iron keep ~4% and ~3% trickle generation. Cheapest, and arguably a feature — a small vanilla trickle is not obviously bad for the economy.
- **Close it via the Overworld noise settings.** Requires shipping a full `minecraft:worldgen/noise_settings/overworld` override, which freezes vanilla's entire terrain noise router at 1.21.1 values and breaks on the next Minecraft update. High blast radius; not recommended.
- **Close it via the `ore_veininess` noise parameters.** Much smaller override, but it also removes the granite and tuff the veins deposit as filler, which changes underground geology — the thing M5 was explicitly told to preserve.

My recommendation is **accept the leak for now** and revisit at M9 balancing with real economy data, where the question is answerable numerically rather than architecturally.

### 3. M6 should note the audit-harness limitation

`GameTestServer` runs `WorldPresets.FLAT`, so no worldgen assertion about generated ore can ever be made in a GameTest. Any future milestone that wants to prove something about generated terrain needs the dedicated-server-plus-region-reader approach built here. Worth recording so it is not rediscovered.

---

## Git state

| | |
| --- | --- |
| M4 commit | `90e604df757bc6e515bcf558aa6202320f8d7211` |
| Current HEAD | `90e604df757bc6e515bcf558aa6202320f8d7211` — **unchanged; M5 is uncommitted** |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| Worktree | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |

```
$ git status --short
?? src/main/java/com/seggellion/britannia_mod/gametest/VanillaOreSuppressionGameTests.java
?? src/main/java/com/seggellion/britannia_mod/worldgen/
?? src/main/resources/data/britannia_mod/neoforge/biome_modifier/suppress_vanilla_overworld_ores.json
?? src/main/resources/data/britannia_mod/worldgen/
?? src/test/java/com/seggellion/britannia_mod/worldgen/
```

**M5 files changed:** 5 new files, 0 modified, 0 deleted.

1. `src/main/resources/data/britannia_mod/worldgen/vanilla_feature_policy.json`
2. `src/main/resources/data/britannia_mod/neoforge/biome_modifier/suppress_vanilla_overworld_ores.json`
3. `src/main/java/com/seggellion/britannia_mod/worldgen/VanillaFeaturePolicy.java`
4. `src/test/java/com/seggellion/britannia_mod/worldgen/VanillaFeaturePolicyTest.java`
5. `src/main/java/com/seggellion/britannia_mod/gametest/VanillaOreSuppressionGameTests.java`

**M5 remains uncommitted**, pending review. The stash is untouched, no unrelated files are staged, and the project documentation in the main checkout is outside this worktree. The audit worlds and the audit datapack live under `run/`, which is gitignored and can never be committed; the biome modifier temporarily removed for the control run was restored and verified byte-present.

---

## Recommendation

### Code development: safe to proceed to M6

The suppression mechanism does exactly what it claims for the features it can reach, it is proven at three levels (data, live registry, generated blocks), it is pinned against drift in both directions, and it introduced no regressions. The milestone is additive, reversible by deleting one file, and carries no runtime code that touches a chunk.

The one qualification is honest rather than blocking: **the milestone's goal is met for six of eight families, and ~96% met for the other two.** The shortfall is a Minecraft architecture constraint that no biome modifier can address, it is now documented in shipped data and pinned by a test, and closing it is an owner decision with a real geological cost. It should be decided, not inherited.

### Production activation: **not ready — do not deploy**

These are not the same answer, and here they diverge sharply.

Shipping this jar activates suppression for every chunk generated afterwards. There is no toggle. The audit measured zero managed ore blocks generated per 256 chunks, so eight resource families would go from full vanilla generation to essentially nothing, with redstone, diamond and lapis having no managed identity to fall back on even manually.

Activation should wait for, in order:

1. controlled natural generation for every suppressed family (M7 sets the pattern, M8 completes it);
2. managed resource identities for redstone, diamond and lapis;
3. the existing-world retrofit and operator command suite (M8);
4. a repeat of this fixed-seed audit against the activated build (M9).

Until then, the recommended immediate action is to add the activation gate described under *Playbook impact* so the code can merge without changing what production generates.

**Stopping after M5 as instructed. Milestone 6 not begun.**
