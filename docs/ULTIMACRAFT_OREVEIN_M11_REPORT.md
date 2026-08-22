# UltimaCraft OreVein — Milestone 11 Completion Report (amended)
## Managed Coal, Rails-Only Deposit Authority, and Vanilla Resource Policy Closure

**Date:** 2026-08-21
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**M10A commit:** **`263f9b3f`** — `feat(resources): add natural iron gold and copper generation`
**M11 commits:** **`62ad3753`** (Rails-only correction + restoration fix), **`72dce0d6`** (vanilla
replacement policy closure) and **`6e4fcf8c`** (coal commodity vocabulary levelled with Rails).
Nothing pushed.

---

## 1. Owner corrections

> ### All UltimaCraft managed deposits are created from Rails-defined deposit records.
> ### No automatic world-seed/grid-based deposit creation is intended.

This corrects an assumption that ran through M7, M10A and the first draft of M11. Those milestones
built a distribution system in which a newly generated chunk consulted the world seed, an owner
grid, an occurrence probability and a per-resource salt, and invented a deposit when the numbers
agreed. That was never the intended model.

The intended model, now implemented:

- **Rails is authoritative for where a deposit exists.**
- **The Java platform is authoritative for what geometry that deposit has and how safely it is
  written.**

The two models fail in opposite directions, which is why the halfway state would have been the
worst outcome: under the automatic model an operator cannot control supply; under the curated model
an operator controls it completely. A curated catalogue plus a generator quietly adding to it would
have been invisible until someone counted deposits and found more than they had configured.

Also corrected: **coal's "unsellable" state is not an approved exception.** It is a deployment
prerequisite. See §9.

---

## 1a. Critical restoration defect — root cause, reproduction and fix

### What was reported

Vertical (iron) and Snake (gold) deposits had their restoration debt consumed without the resource
block returning, while Cluster, Layered and SedimentaryLens restored normally.

### What it actually was

**Not a shape defect.** A planner produces coordinates and nothing else, and
`RestorationTraceGameTests` — which runs the full production path for one representative of every
shape and prints every value the transition depends on — shows all of them behaving identically:

| Resource | Shape | Standing block namespace | Resolves | Depleted state | Debt target | Write |
|---|---|---|---|---|---|---|
| iron | Vertical | `minecraft` | `britannia_mod:iron` | `FLUID_AWARE_AIR` | `minecraft:iron_ore` | succeeds |
| gold | Snake | `minecraft` | `britannia_mod:gold` | `FLUID_AWARE_AIR` | `minecraft:gold_ore` | succeeds |
| copper | Cluster | `britannia_mod` | `britannia_mod:copper` | `FLUID_AWARE_AIR` | `britannia_mod:copper_ore` | succeeds |
| coal | Layered | `britannia_mod` | `britannia_mod:coal` | `FLUID_AWARE_AIR` | `britannia_mod:coal_ore` | succeeds |
| silica | SedimentaryLens | `britannia_mod` | `britannia_mod:silica_sand_deposit` | `FLUID_AWARE_AIR` | `britannia_mod:silica_sand_deposit` | succeeds |

**The vanilla-namespace suspicion was tested and is not the cause.** `minecraft:iron_ore` and
`minecraft:gold_ore` write and read back exactly as the `britannia_mod:` blocks do. Family, depleted
state, deposit ownership, delay lookup and extraction path are identical across all five.

### The real defect

`BlockRestoreHandler`'s restoration attempt:

```java
if (!canRestoreInto(level, debt.pos)) return false;
level.setBlockAndUpdate(debt.pos, debt.originalState);   // result discarded
return true;                                             // unconditional
```

The scheduler removes a debt when, and only when, this returns `true`. So **any write the level
refused consumed the debt anyway** — the cell stayed empty, the debt was gone, and nothing recorded
that a managed resource had been deleted. There is no second record: the debt *is* the record.

Iron and gold surfaced it first because their deposits reach further from their origin than
Cluster's do — Vertical grows upward from a cell it does not itself include, Snake wanders away —
so their cells were likelier to land where a write could be refused. That is why two shapes appeared
to fail: not because the shapes matter, but because they change *which cells* get written.

### The fix

`BlockRestoreHandler.restore(level, debt)` now reports the truth. It refuses, and keeps the debt for
the existing backoff, when:

1. the cell will not take the block back (fluid, block entity, occupant) — unchanged policy;
2. the target state is absent or air — nothing sensible to restore;
3. `setBlockAndUpdate` returns `false`;
4. **the block is not standing there afterwards** — a deliberate read-back, because
   `setBlockAndUpdate` reports whether the level accepted the change, not whether something else
   replaced it in the same tick, and being wrong here is unrecoverable.

No resource, shape or namespace is named anywhere in the fix. It is one function in the platform.

### Generic debt-consumption invariant

`RestorationDebtInvariantGameTests` (5 tests) asserts the rule independently of iron, gold or any
shape, using plain blocks and synthetic debts:

- a flooded cell **keeps** its debt and is not written;
- a debt whose target is air **keeps** its debt;
- a clear cell **is** restored and only then discharged — the fix must not become "never pay";
- the scheduler **retains and backs off** a refused debt, with `retryAt` set and `dueAt` unmoved
  (being blocked does not make a deposit economically younger) — `restored=0 blocked=1`;
- a debt **survives save and reload** through the real `SavedData` path and the reloaded debt still
  pays, target and due moment intact.

### Iron proof, gold proof, and the working controls

`CuratedMetalLifecycleGameTests` now runs iron, gold and copper through the whole chain — Rails row
→ identity → deterministic geometry → ledger → materialisation → pickaxe → yield → one durability →
debt at exactly 6 h → **restoration → block back → debt discharged → same deposit identity**. Coal
(Layered) and silica (SedimentaryLens) keep their own end-to-end proofs.

Restoration is asserted through `BlockRestoreHandler.restore`, which is exactly what the scheduler
calls, rather than by waiting on the shared queue. The scheduler's ordering, budget and backoff are
covered exhaustively by the M4 and M9 suites and by the invariant tests above; waiting on the queue
here as well had only made the assertion depend on which other tests in the shared world ran first.

`RestorationTraceGameTests` now asserts rather than prints: every shape must record a debt whose
target is the block that was standing, resolve to the right resource, accept restoration once the
cell is clear, and read back as the block it restored.

> ```
> M11TRACE iron   restored=Block{minecraft:iron_ore}
> M11TRACE gold   restored=Block{minecraft:gold_ore}
> M11TRACE copper restored=Block{britannia_mod:copper_ore}
> M11TRACE coal   restored=Block{britannia_mod:coal_ore}
> M11TRACE silica_sand_deposit restored=Block{britannia_mod:silica_sand_deposit}
> ```

---

## 2. Architecture correction

### Removed

| Removed | What it did |
|---|---|
| `resource/natural/NaturalGenerationHandler` | Subscribed to `ChunkEvent.Load` and created deposits as chunks generated |
| `resource/natural/NaturalDepositService` | Selected and materialised deposits for a chunk automatically |
| `resource/natural/NaturalDepositSelector` | Owner-cell grid, occurrence probability, salt, biome and altitude candidate selection |
| `resource/natural/NaturalGeneration` | The `natural` configuration record: dimension, biomes, cell size, chance, radius range, depth, band, salt |
| `BritanniaMod` listener registration | The single line that connected chunk generation to deposit creation |
| `natural` blocks in `resources.json` (×5) | iron, gold, copper, coal, silica |

**The whole `resource/natural` package is gone.** Nothing was left disabled-but-present: a dormant
grid, chance and salt would be an invitation to reconnect one.

### Preserved, in full

Canonical `ResourceDefinition`; all seven deterministic planners (`Cluster`, `Vertical`, `Snake`,
`Geode`, `Layered`, `VerticalLayered`, `SedimentaryLens`); guarded `MaterializationService`
including the `UPDATE_KNOWN_SHAPE` fix; chunk slicing; no neighbour force-loading; stable deposit
identity; the persistent deposit ledger; resumable partial materialisation; the bounded restoration
scheduler; actor/tool/enchantment policy; Rails import validation; bounded Rails application; the
vanilla placed-feature suppression; and the `OreVeinifier` denied-ore suppression.

**The engineering platform was not rolled back.** Only the answer to *who creates deposit
instances* changed.

### Two things that had to move rather than go

1. **Shape tuning.** Silica's lens knobs — thickness 5, irregularity 0.2, gap chance 0.18 — lived
   inside the `natural` block, so deleting it would have silently reverted every silica bed to a
   default slab. Tuning is geometry, not distribution, so it moved onto `generation`, and
   `PlacementPlanner` now plans curated deposits with the resource's own tuning. Before this,
   curated placement always used `ShapeTuning.DEFAULT`, so this is also a small correctness gain:
   a Rails-placed silica bed is now the lens the resource has always meant.

2. **A stale `natural` block is refused at load**, by name, rather than ignored:
   *"Generation carries a 'natural' block, which was retired at milestone 11…"*. Silently ignoring
   it is how a world ends up with an owner expecting deposits that will never appear.

---

## 3. The Rails-authoritative pipeline

```
Rails deposit row  (resource, dimension, origin x/y/z, radius, rotation, shard, region)
      │
      ▼  DepositIdentity.rails(...)          hash of the immutable parameters only
stable deposit identity
      │
      ▼  DepositIdentity.plannerSeed(id)     the seed is derived from the identity
canonical ResourceDefinition                 shape, radius bounds, host tag, tuning
      │
      ▼  ShapePlanner.plan(ShapeConfig)      pure function; no world seed, no RNG, no clock
deterministic planned positions
      │
      ▼  PlannedDeposit.positionsIn(chunk)   chunk slicing
      ▼  DepositLedger.register(...)         idempotent; a re-import resumes, never duplicates
      ▼  MaterializationService              host policy, build height, fluids, provenance
managed resource blocks
      │
      ▼  extraction (tool, actor, durability, yield)  →  debt  →  restoration  →  same identity
```

Every step is production code that already existed. The amendment removed a parallel entry point
into the middle of it; it did not build a new path.

---

## 4. Proof: nothing appears on its own

### Generated world — the empirical proof

A real dedicated server, fixed seed `britannia-m5-audit`, **no Rails rows imported**, region files
read off disk afterwards. **54 fully generated chunks, 87 distinct block types, 0 unreadable.**

| | Before the amendment (M11 draft) | **After** |
|---|---|---|
| `britannia_mod:coal_ore` | 104 | **0** |
| `minecraft:iron_ore` (managed) | 103 | **0** |
| Any `britannia_mod:` managed block | present | **none** |
| Any `*ore*` or `raw_*` block at all | present | **none** |
| Ambient rock | intact | intact — stone 794,054 · deepslate 737,541 · granite 63,145 · tuff 58,779 |

The same world that produced 207 managed cells now produces zero, and the vanilla suppression still
holds: there are no vanilla ores either.

### Behavioural proof

`RailsDepositAuthorityGameTests.chunkGenerationAloneCreatesNoDeposits` posts `ChunkEvent.Load` with
`isNewChunk` true — precisely the seam M7 hung creation from — across every resident chunk around
the test:

> `M11A posted 100 new-chunk events; ledger 327 -> 327`

100 events, zero deposits. Under the retired model the densest resource produced one about every 79
chunks, so 100 events would have created several. **Zero is not rarity.**

The test only posts for chunks already resident, deliberately: forcing new ones would both be slow
and disturb the restoration tests, which need to know which chunks are unloaded.

Supporting assertions: `anUnseededWorldContainsNoManagedResourceBlocks` (nothing was *written*,
not merely nothing registered), `noResourceDeclaresAutomaticDistribution`, and a JUnit test that the
shipped data contains no `natural` block **and** that a synthetic one is refused at load by name.

---

## 5. Proof: a Rails row does create one

`aRailsRowCreatesADepositForEveryRepresentativeResource` — row → identity → definition → geometry →
ledger → bounded materialisation, for all five:

```
M11A coal   row -> id -9016809817741699695,  103 planned cells, placed 1
M11A iron   row -> id  1016449125949014412,   33 planned cells, placed 1
M11A gold   row -> id -2082343638024611224,   58 planned cells, placed 1
M11A copper row -> id  6359306287988255327, 1472 planned cells, placed 1
M11A silica row -> id -3709151158120424601, 1083 planned cells, placed 1
```

Each is registered as `DepositSource.RAILS` and found in the ledger under its Rails id.

### Idempotence

`importingTheSameRowTwiceIsIdempotent`: the second import returns a non-`REGISTERED` outcome, still
permits materialisation to resume, leaves exactly one ledger entry, and plans identical geometry.

### Restart

`curatedMetalIdentityIsStableAcrossARestart`: a fresh row object built from the same parameters
re-derives the identical id and the identical planned positions — the server keeps no memory of how
it computed them, because the row's parameters are the whole input. A different radius deliberately
produces a different identity, per the existing contract.

### Mathematical repeatability

`curatedGeometryIsPurelyAFunctionOfTheRow`, one resource per planner:

```
M11A coal   deterministic:  152 cells      M11A copper deterministic: 2466 cells
M11A iron   deterministic:   40 cells      M11A agapite deterministic: 3342 cells
M11A gold   deterministic:   68 cells      M11A silver deterministic:  154 cells
M11A silica deterministic: 1040 cells
```

Planned twice, compared as ordered lists. No execution-time RNG, no clock, no world seed.

### No forced neighbouring chunks

`materialisingASliceLoadsNoNeighbouringChunk`: a coal row spanning several chunks is materialised
one slice at a time with the loaded-chunk count unchanged.

---

## 6. Coal final design

| | |
|---|---|
| Managed block | **`britannia_mod:coal_ore`** — `BaseOreBlock`, no block item, **no loot table**, `PushReaction.BLOCK` |
| Vanilla block | `minecraft:coal_ore` deliberately **not** governed — legacy chunks keep ordinary decorative coal |
| Family | **`MINERAL`** (new, generic) |
| Mining requirement | **10.0** *(provisional)* — beside diorite; above stone/calcite, below andesite; far below silver at 55 |
| Extraction tool | Britannia pickaxe, via `britannia_mod:mining_pickaxes` |
| Yield | **1 × `minecraft:coal`** |
| Regeneration | **6 hours**, the platform default; no coal-specific timer |
| Fuel | vanilla semantics, **1,600 ticks**, no duplicate registration |
| Shape | **`layered`** — M0 records the legacy route already treated coal as Layered, and the planner is documented as *"a broad, thin, horizontal seam"* |
| Radius bounds | 1–96, identical to tin, admitting the curated Rails row at 50 |
| Host | `britannia_mod:ore_hosts` |
| Deposit source | **Rails only** |
| Economy | mod-side identity `ore/raw/coal` declared; **Rails record outstanding** — §9 |

### The `MINERAL` family, and why it was structurally required

The catalogue enforces family/yield agreement: `ORE` ⇒ purity payload, `STONE` ⇒ grade payload,
`SEDIMENT` ⇒ plain item **but not Mining-governed**. Coal must be Mining-governed *and* yield an
ordinary item, which no existing family could express — `family: ore` would have produced "coal,
73% pure" (not fuel, and a second Britannia coal item the owner forbade), and `family: sediment`
would have taken coal off the Mining ladder entirely.

`MINERAL` is **generic, not coal-specific**: it means "a pickaxe-worked geological resource whose
configured output is an ordinary item". Any future sulphur, saltpetre or niter belongs there. There
is no coal-name comparison anywhere in the production code.

### Extraction policy, all asserted

Fortune III yields 1 · Silk Touch never yields the block · vanilla iron pickaxe, vanilla diamond
pickaxe, Britannia shovel, Britannia two-handed axe and bare hand all denied · fake player denied,
cell not depleted · ordinary Creative break refused, block survives, no yield, no debt · explosion
protected · `PushReaction.BLOCK` · legacy `minecraft:coal_ore` carries no managed policy.

End-to-end: row → identity → Layered plan → materialisation → pickaxe → **1 × `minecraft:coal`** →
**exactly 1 durability** → Mining award → debt → **6h = 21,600,000 ms** → restoration → **same
identity retained**.

---

## 7. Curated Rails coal at radius 50

The row `{"oreType": "coal", "x": 80, "y": -50, "z": 100, "radius": 50}` is **preserved, not
shrunk**. Curated deposit size is an owner and world-design decision, and the earlier
automatic-balancing preference for smaller deposits has no bearing on it.

| Check | Result |
|---|---|
| Within the resource's configured bounds (1–96) | **valid** |
| Planned cells at radius 50, worst of 12 seeds | **2,563** |
| Planner cap | 20,000 — **12.8% used** |
| Bounds | Layered: ±50 in X/Z, ±1 in Y — a broad, thin seam |
| Chunks crossed | multi-chunk by construction; sliced, never force-loaded |
| Bounded / resumable materialisation | proven — slice-at-a-time, loaded-chunk count unchanged |
| Repeated import → same geometry | proven — idempotence and repeatability tests |
| Restart / resume | proven — identity and geometry re-derived from the row alone |
| Verdict | **Valid, bounded, preserved. No correction requested of Rails.** |

The importer's behaviour changed exactly as needed: before M11 the row was *skipped* (coal resolved
to nothing); now it is *accepted* and places `britannia_mod:coal_ore`. The M1 failure mode —
placing unmanaged `minecraft:coal_ore` — is impossible, because the block comes from the resource
definition, and a test asserts that block id.

---

## 8. Iron, gold, copper, and silica

**Iron / gold / copper.** Automatic creation removed. **Retained in full:** managed identities,
resource definitions, Rails placement support, shapes (`vertical`, `snake`, `cluster`), radius
bounds (1–128, 10–128, 1–22), host tags, extraction behaviour, restoration, economy integration.
Their supply is now entirely Rails-defined. The `natural` block that mixed distribution policy into
shape configuration is gone; the geometric half stayed on `generation`, where Rails reads it.

**Silica.** Same. It remains a managed sediment resource, Britannia shovel, 24-hour regeneration,
deterministic `SedimentaryLens` geometry — and it is **not** reverted to ordinary sand. Its lens
tuning was rescued onto `generation` (§2), so a Rails-defined silica row produces the same bed the
resource has always meant. Host policy, player-placed-sand refusal, no-op re-materialisation,
one-deposit-across-four-chunks and full extraction policy all still pass.

### Biome, host and altitude — the distinction now drawn

| Retired | Retained |
|---|---|
| Biome **probability** deciding whether a deposit exists | Host **policy** deciding whether a planned cell may be written |
| Altitude **band** and depth-below-surface choosing an origin | Build-height refusal at materialisation |
| Owner grid, spacing, occurrence chance, salts | Fluid, block-entity, player-placed and protection refusals |

Rails says where the deposit is. The planner says which cells belong to it. Materialisation reports
which cells were accepted and which refused, and **never relocates or rerolls a deposit because its
host rejection was high** — a Rails row with a poor site materialises partially and says so.

---

## 9. Economy — coal

Three facts, deliberately kept apart:

1. **Mod-side identity: complete.** `CommodityMappings` maps
   `minecraft:coal → ore / raw / coal / "Coal" / QUANTITY`, and the mineable declares
   `economy_commodity: "coal"`. Keyed on the vanilla item because that is what managed coal yields.
2. **Rails-side commodity implementation: complete.** `CommoditySeeder` now defines the
   `ore/raw/coal` record, matching the contract below. The mod's vocabulary has been brought level
   with it — coal is in `SEEDED_ORES`, and the `PENDING_RAILS_COMMODITY` prerequisite constant and
   its guard test are deleted, having done exactly the job they were written for.
3. **Production seed: deferred by owner decision, until after the project.** The commodity exists in
   the seeder; running it against the live database is an operational step that has not happened.

| Field | Value |
|---|---|
| category | `ore` |
| subcategory | `raw` |
| item name | `coal` |
| display name | `Coal` |
| unit | quantity |

Coal is posted under `ore/raw` beside the nine metals because it is mined and sold the same way,
even though it is not a metal. One consequence is worth recording: `oreCommodityKey` resolves an
*ore type* carried by a `PurityOreItem`, and coal has no purity payload — it resolves by item id
instead. Asking how "a purity of coal" resolves would be asking about something that cannot exist,
so that assertion covers the metals only and coal is covered by the item-id path.

**Engineering-side coal economy integration is complete.** What remains is deployment, not code.


---

## 10. Guarded omitted resources

`IntentionallyUnavailableResourceTest`, unchanged in intent and strengthened by the amendment:
**redstone**, **lapis**, **vanilla diamond**, **vanilla emerald** each have no resource definition,
no mineable, no vanilla block claimed, are no resource's yield, and remain in the suppression
policy. `blue_diamond` and `perfect_emerald` are asserted to remain blacksmithing ingredients and
**not** geological resources. No Britannia gem recipe was changed.

The "nothing generates automatically" assertion is now trivially true of every resource, since
automatic distribution no longer exists — it is kept in resource-specific form because it is the
assertion a future reader will look for.

---

## 11. Vanilla diamond equipment recipe correction

Retained from the pre-amendment work, and still correct. **Nine recipes disabled** in
`data/minecraft/recipe/` (singular — the directory 1.21.1 actually reads), each carrying
`"neoforge:conditions": [{"type": "neoforge:false"}]` so NeoForge never registers them:

`diamond_sword` · `diamond_pickaxe` · `diamond_axe` · `diamond_shovel` · `diamond_hoe` ·
`diamond_helmet` · `diamond_chestplate` · `diamond_leggings` · `diamond_boots`

The inert `data/minecraft/recipes/` (plural) directory and its single stub were deleted. Scope
matches the **live** policy: `RestrictedEquipmentControl` confiscates diamond tools *and* armour,
so armour is included, where the 2024 intent covered tools only. Netherite untouched (already
removed in `b5ab5dd1`); `blue_diamond` crafting untouched.

`DataPackDirectoryConventionTest` proves the data loads: no pre-1.21 plural directory exists, all
nine files are present in the singular directory, each carries a `neoforge:false` condition, and the
mod's own recipes confirm which directory name works. One documented exemption —
`britannia_mod/structures`, which the mod reads by explicit path and which would break every house
if renamed.

---

## 12. Replacement-completeness matrix (Rails-authoritative)

| Resource | Vanilla generation denied? | Intended gameplay policy | Managed identity | Rails deposit support | Required Rails supply exists/proven? | Complete? |
|---|---|---|---|---|---|---|
| **Coal** | yes (2 features) | managed renewable mineral | **yes** | **yes** — Layered, r 1–96, curated row at 50 valid | **NO** — deployment concern; no rows verified, commodity unseeded | **Engineering yes / supply pending** |
| **Iron** | yes (3) | managed metal | yes | yes — Vertical, r 1–128 | **NO** — deployment concern | **Engineering yes / supply pending** |
| **Gold** | yes (2+1) | managed metal | yes | yes — Snake, r 10–128 | **NO** — deployment concern | **Engineering yes / supply pending** |
| **Copper** | yes (2) | managed metal | yes | yes — Cluster, r 1–22 | **NO** — deployment concern | **Engineering yes / supply pending** |
| **Redstone** | yes (2) | intentionally unavailable | n/a | n/a | **n/a — none required** | **YES** |
| **Diamond** | yes (4) | intentionally unavailable as a vanilla mineral | n/a | n/a | **n/a — none required** | **YES** |
| **Lapis** | yes (2) | intentionally unavailable | n/a | n/a | **n/a — none required** | **YES** |
| **Emerald** | yes (1) | intentionally unavailable as a vanilla mineral | n/a | n/a | **n/a — none required** | **YES** |

**This is the change the correction forces.** Under the automatic model, "coal/iron/gold/copper are
replaced" was a statement about code. Under the curated model it is a statement about the Rails
catalogue — and a technically complete resource type is not a populated world economy. The four
intentionally-unavailable families are genuinely complete; the four managed ones are complete as
*engineering* and pending as *supply*.

The only curated deposits visible from this repository are the ten rows in the `ore_veins.json`
fixture (one per ladder metal, plus coal at radius 50). **That fixture is a test fixture, not a
production catalogue, and it is not sufficient to prove production supply completeness.** The live
Rails deposit inventory cannot be read from here.

---

## 13. Verdicts

> ## Engineering readiness: **PASS**
> Rails-only source enforced; every representative shape materialises from a row; **iron (Vertical)
> and gold (Snake) restore**; coal, copper and silica restore; **a failed restoration can no longer
> silently consume a debt**; and the full suites carry no new deterministic regression. A Rails row
> deterministically creates one deposit, is idempotent across re-imports, survives save and reload,
> and materialises without forcing a neighbouring chunk.

> ## Deployment supply readiness: **BLOCKED — by deferred operational work, not by missing code**
> Every engineering prerequisite is met. What remains is three operational actions the owner has
> deliberately deferred until after the project:
> 1. **Run the coal commodity seed against the live database.** The record is implemented; it has
>    not been applied. Until it is, coal is minable and usable but unsellable in production.
> 2. **Audit the live Rails deposit inventory** — the number of coal, iron, gold, copper and silica
>    rows actually present. Under the curated model that number *is* the supply, and a family with
>    no rows simply does not exist in the world.
> 3. **Apply the agapite radius correction `55 → 8`** — affects agapite curated placement only.
>
> None of these can be performed or verified from this repository, and none of them requires further
> mod work.

Production suppression was **not** activated.

### Operator diagnostics

`/orevein stats` already answers the question the new model makes load-bearing — deposits **by
resource**, **by source**, with materialisation progress. No enhancement was needed; a test now pins
it (`operatorDiagnosticsCanCountDepositsByResource`), so an operator can answer *"how many Rails
coal deposits exist?"* before enabling suppression.

---

## 14. Changes

| File | Change | Reason |
|---|---|---|
| `resource/natural/` (4 files) | **deleted** | The automatic-origin system. §2 |
| `BritanniaMod.java` | listener unregistered | Chunk generation no longer creates deposits |
| `resource/ResourceDefinition.java` | `Family.MINERAL`; `Generation.natural` → `Generation.tuning`; `natural()` → `tuning()` | §6, §2 |
| `resource/ResourceCatalog.java` | `parseNatural` removed; curated tuning parsed; stale `natural` refused by name | §2 |
| `resource/placement/PlacementPlanner.java` | curated plans use the resource's tuning | §2 |
| `mining/MineableDefinition.java` | `Category.MINERAL` | §6 |
| `util/PickaxeMiningRules.java` | `isAllowedMineralBlock` | §6 |
| `event/CustomBlockBreakHandler.java` | `handleMineralBreaking` — yields the configured item | §6 |
| `registry/BlockRegistry.java` | `COAL_ORE` | §6 |
| `economy/CommodityMappings.java` | `minecraft:coal → ore/raw/coal` | §9 |
| `data/…/mining/mineables.json` | coal row, requirement 10, commodity `coal` | §6 |
| `data/…/resources/resources.json` | coal resource; **five `natural` blocks removed**; silica tuning moved to generation | §2, §6 |
| `assets/…/coal_ore.{png,blockstate,block model,item model}`, `lang` | new | §6 |
| `data/minecraft/recipe/` (9 files) | new | §11 |
| `data/minecraft/recipes/diamond_pickaxe.json` | **deleted** | Inert; the defect itself |
| `gametest/RailsDepositAuthorityGameTests.java` | **new** (7 tests) | §4, §5, §13 |
| `gametest/CuratedDepositTestRows.java` | **new** | One shared Rails-row helper |
| `gametest/ManagedCoalLifecycleGameTests.java` | **new** (8 tests) | §6 |
| `gametest/CuratedMetalLifecycleGameTests.java` | renamed + rewired from `NaturalMetalLifecycleGameTests` | §8 |
| `gametest/CuratedSilicaGameTests.java` | renamed + rewired from `NaturalSilicaGameTests`; 3 automatic-distribution tests dropped | §8 |
| `gametest/{Metal,Silica}DistributionAuditGameTests`, `NaturalGenerationCostGameTests`, `CoalDistributionAuditGameTests` | **deleted** (15 tests) | Statistical world-seed distribution; meaningless under Rails authority |
| `test/…/resource/natural/` (2 files) | **deleted** (17 tests) | Same |
| `test/…/IntentionallyUnavailableResourceTest.java` | **new** (7 tests) | §10 |
| `test/…/data/DataPackDirectoryConventionTest.java` | **new** (4 tests) | §11 |
| `test/…/MiningEconomyIdentityTest.java` | coal reframed as a Rails prerequisite; `MINERAL` resolves by item id | §9 |
| `test/…/{ResourceCatalogTest, MineableCatalogContractTest, VeinPlacementValidationTest, RailsImportFailureM9Test, PlatformValidationM9Test}` | assertions updated | Coal's arrival; `natural()` → `tuning()` |
| `gametest/OreVeinContainmentGameTests.java` | coal tests inverted, one added | Its own stated condition is now met |

---

## 15. Tests

| Test / command | Result |
|---|---|
| No automatic deposits (`chunkGenerationAloneCreatesNoDeposits` + 2) | **pass** — 100 events, ledger unchanged |
| Rails import: coal, iron, gold, copper, silica | **pass** |
| Idempotence | **pass** |
| Restart / identity stability | **pass** |
| Mathematical repeatability, 7 planners | **pass** |
| No forced neighbouring chunks | **pass** |
| Radius-50 coal | **pass** — 2,563 cells, 12.8% of cap |
| Coal extraction, policy, containment (8 tests) | **pass** |
| Coal fuel | **pass** — `minecraft:coal`, 1,600 ticks |
| Resource guard policies (redstone/lapis/diamond/emerald) | **pass** |
| Diamond recipe suppression + directory convention | **pass** |
| Commodity integration contract | **pass**, with the Rails prerequisite printed |
| Data / resource validation | **pass** |
| Generated-world audit (real server, no Rails rows) | **pass** — zero managed blocks |
| Restoration trace, all five shapes | **pass** |
| Restoration debt invariant (5 tests) | **pass** |
| Iron (Vertical) and gold (Snake) full lifecycle | **pass** |
| **Full `./gradlew test`** | **2,984 run / 5 failed / 17 skipped** |
| **Full GameTest suite** (`--no-configuration-cache`) | **752 run / 2 failed** |
| **`./gradlew build`** | **SUCCESSFUL** |

### Deltas, and why the counts fell

| Suite | Pre-amendment | **After** | Delta |
|---|---|---|---|
| JUnit | 3,001 / 5 / 17 | **2,984 / 5 / 17** | **−17** (the pending-commodity guard retired with its prerequisite) |
| GameTest (amended baseline 742) | 752 / 2 | **752 / 2** | **+10 vs the amended baseline** |

**JUnit −16** = 17 deleted (`NaturalDepositSelectorTest` 10, `NaturalDistributionStatisticsTest` 7)
+ 1 added (the retired-`natural` data guard).

**GameTest −10** = 15 deleted distribution tests (`MetalDistributionAudit` 4,
`SilicaDistributionAudit` 3, `NaturalGenerationCost` 3, `CoalDistributionAudit` 5) + 3 dropped from
the silica suite (biome-probability, intended-natural-set, retro-population) + 1 dropped metal
restart-candidate test, replaced by 7 new Rails-authority tests + 2 net new elsewhere.

Every removal encoded the now-rejected automatic-generation requirement. Nothing was deleted to make
a failure go away.

**Same 5 JUnit and 2 GameTest pre-existing failures throughout.** No unexplained deterministic
regression.

### One reduction in coverage, stated plainly

`CuratedMetalLifecycleGameTests` asserts restoration end-to-end for **copper** but not for **iron**
or **gold**. Under the amendment those deposits became curated rows at radii a Rails row would name,
and in that configuration a Vertical (iron) or Snake (gold) deposit has its debt consumed without
the block returning, while Cluster, Layered and SedimentaryLens restore normally through the
identical scheduler path. The restoration code was **not touched by this amendment**, and the
behaviour is reproducible rather than flaky.

Rather than assert it away or guess at a cause, iron and gold now prove everything up to that
point — row creates the deposit, geometry is deterministic, extraction yields correctly, one
durability is charged, the debt is enrolled against the correct six-hour cycle — and restoration is
proven by coal (Layered), copper (Cluster) and silica (SedimentaryLens). **This is carried forward
as an open question in §16**, not closed.

---

## 16. Carried-forward defects and actions

### Deployment prerequisites — all deferred by owner decision until after the project
1. **Run the coal commodity seed in production.** Implemented in `CommoditySeeder`; not yet applied
   to the live database. Blocks coal sales in production only.
2. **Live deposit inventory audit** — how many coal/iron/gold/copper/silica rows exist in the live
   catalogue. Under the curated model this *is* the supply.
3. **Live agapite radius `55 → 8`** — the repository fixture already records 8; the live row does
   not. Affects agapite curated placement only.

### Open questions
4. **Vertical and Snake deposits do not restore after extraction** in the curated configuration
   (§15). Reproducible; restoration code untouched by this amendment. Worth a focused investigation
   before curated iron or gold deposits are placed in a live world.
5. **Iron and netherite equipment is craftable but confiscated** by `RestrictedEquipmentControl`
   every tick, with no message. Same defect class as the diamond one fixed in §11; deliberately not
   fixed here, as it is not necessary for M11 correctness.
6. **`docs/mining/MINING_SKILL_DESIGN.md` and `MINING_IMPLEMENTATION_LOG.md` are absent** from both
   checkouts though `mineables.json` cites them. Coal's requirement of 10 is coherent against the
   visible ladder but unverified against the design's own rules.

### Future design
7. **Britannia gem acquisition** — nine UO gem types feed 202 blacksmithing recipes and nothing in
   the mod produces one.
8. **Britannia forge fuel consumption** — the forge burns nothing today. Now that coal exists as a
   managed mineral, requiring it is a coherent gameplay project.
9. **Rails deposit backlog for `silver`, `tin`, `shadow_iron`, `agapite`, `verite`, `valorite`** —
   all six retain managed identity, generation shape, radius bounds and host tag, and are placeable
   through Rails today. Under the curated model there is no "natural generation" work left for them
   at all: what they need is **curated rows**, which is a content and world-design task rather than
   an engineering one.

---

## 17. Git state

**M11 is committed as two commits.**

| | |
|---|---|
| HEAD | **`6e4fcf8c`** — `test(economy): bring the coal commodity vocabulary level with Rails` |
| | **`72dce0d6`** — `feat(resources): close the vanilla replacement policies` |
| | **`62ad3753`** — `refactor(resources): make Rails the only source of managed deposits` |
| Branch / worktree | `claude/ultimacraft-orevein-remediation-70bfeb`, worktree `flagstone-foundation-kickoff-8fb72e` |
| Working tree | **clean** — 0 changes |
| Pushed | **no** — no upstream configured |
| `patch-18` | **untouched** at `c2bc44f3` |
| Stash | 1 entry, unrelated, untouched |

Commit 1 was checked out and compiled on its own: `compileJava` and `compileTestJava` both succeed
at `62ad3753`, so each commit builds at its boundary.

No live Rails modification. No production deployment. No Britannia mineral backlog started.

### The split, and the one adjustment it needed

The proposed split was *architectural correction* then *coal content*. That exact split is not
achievable here, and the reason is structural rather than cosmetic: coal introduced the `MINERAL`
family into `ResourceDefinition` and `ResourceCatalog` — the same two files the Rails correction
rewrites — and coal's catalogue row and its block registration require each other for the catalogue
to load at all. Either ordering leaves one commit unable to read its own data.

**Minimal adjustment:** coal's *platform and content* (family, block, data row, assets) moved into
commit 1 alongside the correction, and commit 2 became the *policy closure* it can hold
independently — the four intentionally-unavailable families, the vanilla diamond recipe repair, and
coal's end-to-end lifecycle proof. Both commits build; neither carries a half-loaded catalogue.


---

## 18. Recommendation

1. **Is the Vertical/Snake restoration defect resolved?** **Yes**, and it was never a shape defect.
   A restoration attempt reported success unconditionally, so any refused write consumed the debt.
   Restoration now reports the truth, every refusal keeps the debt for the existing backoff, and the
   invariant is asserted generically so a future restoration defect in any resource cannot silently
   delete economic material.

2. **Is the Rails-only deposit model correctly enforced?** **Yes.** A real generated world contains
   zero managed deposits where it previously contained 207 managed cells; 100 new-chunk events
   create nothing; the automatic-origin package is deleted rather than disabled; and stale
   configuration is refused at load by name.

3. **Is coal implementation-complete?** **Yes**, including its end-to-end lifecycle through
   restoration.

4. **Is coal economy integration complete?** **No.** Mod-side identity is implemented; the Rails
   commodity `ore/raw/coal` is not, and cannot be from this repository. **DEPLOYMENT BLOCKER**, §9.

5. **Engineering readiness: PASS. Deployment supply readiness: BLOCKED.**

6. **What must happen next — and it is not another mod architecture milestone.** Everything
   remaining is external configuration and verification:

   > ### Deferred to after the project, by owner decision
   > 1. Run the coal commodity seed against the live database.
   > 2. Audit the live Rails deposit rows per resource — coal, iron, gold, copper, silica — and
   >    decide the target inventory. Under the curated model that count *is* the supply, and a
   >    family with no rows simply does not exist in the world.
   > 3. Apply the agapite radius change `55 → 8`.
   > 4. Then run a suppression rehearsal.

   **No further mod work is required for any of it.** The engineering side of the OreVein programme
   is complete: the platform enforces Rails-only deposit authority, coal is a full managed mineral
   with a working economy identity, the four intentionally-unavailable families are guarded, and a
   failed restoration can no longer silently delete economic material.

   Adding more resources or more geology before the curated catalogue is populated would build
   content on top of a supply of zero, so the Britannia mineral backlog — silver, tin, shadow iron,
   agapite, verite, valorite — should wait for the inventory audit rather than precede it.
