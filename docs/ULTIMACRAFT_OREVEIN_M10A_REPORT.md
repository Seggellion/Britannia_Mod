# UltimaCraft OreVein — Milestone 10A Completion Report
## Controlled Natural Generation for Existing Managed Metals

**Date:** 2026-08-21
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**HEAD at time of writing:** `118db9d3` (M9). **M10A is uncommitted, as instructed.**
**Production rollout gate:** **BLOCKED** — unchanged, and correctly so. See §11.

---

## 1. Verdict

**M10A is complete.** Iron, gold and copper now generate naturally in the Overworld through
resource definition alone. No coal, redstone, diamond, lapis or emerald was added.

Two things are worth reading before anything else:

1. **The milestone shipped as predicted — data, tests and balancing evidence — with one
   exception, and that exception is a platform defect this milestone existed to expose.** The
   change is 2 production Java files (26 net lines), 1 data file, and 4 test files. There is no
   resource-name switch anywhere in it.
2. **The defect was a silent server hang, not a wrong number.** Adding iron reproducibly stopped
   world generation dead — no exception, no crash report, no log line. It was latent before M10A
   and would have reached production. Details in §3.

---

## 2. What was asked, and what was delivered

| Requirement | Outcome |
|---|---|
| Commit M9 | Done — `118db9d3` |
| Natural generation for iron, gold, copper only | Done |
| No coal / redstone / diamond / lapis / emerald | Confirmed — none added |
| Mostly resource data, tests, balancing evidence | Confirmed — see §12 |
| Substantial resource-specific Java ⇒ report a platform defect | **Triggered.** §3 |
| Copper must not simply inherit Cluster radius 22 | Done — measured, §5 |
| Natural vs Rails config must be independent | Verified, no coupling — §7 |
| Existing chunks untouched | Structurally unchanged — §8 |
| Independent salts per resource | Verified — §6 |
| Production gate stays blocked | Confirmed — §11 |

---

## 3. The platform defect: a silent server-thread self-deadlock

### 3.1 Symptom

With iron's natural configuration enabled, a fresh server never finished starting. It printed
`Preparing spawn area: 51%` and stopped there indefinitely. No exception, no crash report, no
watchdog trip, no further log output. Copper, gold and silica were unaffected.

### 3.2 How it was isolated

Rather than guess, each natural resource was enabled in isolation against a fixed seed
(`britannia-m5-audit`), booting a real dedicated server with a 300-second ceiling:

| Natural resources enabled | Result |
|---|---|
| silica | `Done (4.700s)` |
| silica + copper | `Done (4.523s)` |
| silica + copper + gold | `Done (4.486s)` |
| silica + copper + **iron** | **never finished** |
| **iron** alone | **never finished** |
| **iron** alone, radius reduced 24–32 to 8–12 | **never finished** |

The radius probe mattered: it eliminated deposit size, search-window size and planning cost as
causes, all of which had been plausible. Two further hypotheses were eliminated by inspection —
the shape (`Vertical`'s planning loop is bounded by radius) and the block identity (gold also
generates a vanilla id, `minecraft:gold_ore`, and gold works).

At that point guessing had run out, so a JVM thread dump was taken from the hung server.

### 3.3 Root cause

The dump showed no JVM deadlock. It showed the server thread parked against itself:

```
"Server thread" ... java.lang.Thread.State: TIMED_WAITING (parking)
  at ServerChunkCache$MainThreadExecutor.managedBlock
  at ServerChunkCache.getChunk
  at Level.getBlockState
  at NeighborUpdater.executeShapeUpdate
  at CollectingNeighborUpdater.runUpdates
  at BlockBehaviour$BlockStateBase.updateNeighbourShapes
  at Level.markAndNotifyBlock
  at Level.setBlock
  at MaterializationService.materialize            <-- our write
  at MaterializationService.materializeChunk
  at NaturalDepositService.populate
  at NaturalGenerationHandler.onChunkLoad
  at ChunkStatusTasks.lambda$full$2                <-- posting ChunkEvent.Load
```

Read it bottom-up. We write blocks from `ChunkEvent.Load`, which fires while the chunk is still
being promoted to full status by `ChunkStatusTasks.full`. Our `setBlock` used flag
`Block.UPDATE_CLIENTS` alone, on the stated belief — written into the source comment — that flag
2 meant "no neighbour updates".

It does not. **Flag 2 suppresses neighbour _block_ updates. Neighbour _shape_ updates are
suppressed only by `UPDATE_KNOWN_SHAPE` (flag 16).** So every cell written ran
`updateNeighbourShapes`, which reads all six neighbours. A cell on a chunk border therefore read
a block in the adjacent chunk; reading an unloaded chunk asks the chunk source to produce it; and
the thread issuing that request was the same server thread that owed the answer. It parked
waiting for itself.

### 3.4 Why iron, and why this is not an iron problem

Any cell on a chunk boundary triggers it. Iron is simply the densest resource configured — one
candidate cell per 8x8 chunks, 13.3 deposits per 1,000 chunks, roughly 4x copper and 13x gold —
so within a 441-chunk spawn area it lands a border cell every time. Copper, gold and silica were
getting away with it by rarity.

**This was latent in the platform from M7 onward and would have reached production**, presenting
as an unreproducible server freeze during ordinary exploration. Iron's density is what made it
deterministic enough to catch.

### 3.5 Fix

One flag, in the single write path, named and documented:

```java
public static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
```

`MaterializationService` line 173 now uses it. The misleading comment is replaced with the actual
mechanism, and the incorrect claim in `NaturalDepositService`'s class doc — that confining *write
positions* to the chunk was sufficient — is corrected: confining writes is necessary but not
sufficient, because an in-chunk write still *reads* across the border unless the shape update is
suppressed too.

**This is a platform fix, not a resource fix.** It applies to every managed write by every
resource, curated and natural alike. No resource-name branching was added anywhere in M10A.

### 3.6 Proof of fix

| Configuration | Before | After |
|---|---|---|
| all four natural resources incl. iron | never finished in 300s | **`Done (6.220s)`** |

### 3.7 Regression pin

`ManagedWriteFlagsTest` (3 cases) asserts the flags, and documents why it is a flag assertion
rather than a world test: the failure requires a border cell in a mid-promotion chunk whose
neighbour is absent. A GameTest runs inside an already-loaded neighbourhood, so the border read is
always satisfied from memory and **the hang cannot be reproduced there** — a world test would pass
whether or not the bug were present, which is worse than no test. The flags are the whole of the
fix, so the flags are what is pinned, including an explicit assertion that flag 2 alone is
rejected by name.

---

## 4. What shipped, per metal

All three: `dimension: minecraft:overworld`, `biomes: minecraft:is_overworld`, `chance: 0.85`,
host tag `britannia_mod:ore_hosts`. Measured over 5 seeds against the **real Overworld noise
generator** (biome and surface height queried without generating chunks).

| | iron | copper | gold |
|---|---|---|---|
| generates | `minecraft:iron_ore` | `britannia_mod:copper_ore` | `minecraft:gold_ore` |
| shape | Vertical | Cluster | Snake |
| cell | 8 chunks | 16 chunks | 28 chunks |
| radius | 24–32 | 8–10 | 32–40 |
| depth | 45 ± 15 | 32 ± 12 | 85 ± 20 |
| band | −52 … 120 | −52 … 120 | −52 … 90 |
| salt | 1041 | 3163 | 2079 |
| **density** | **13.30 / 1k chunks** (1 per 75) | **3.34 / 1k** (1 per 299) | **1.02 / 1k** (1 per 976) |
| cells min/median/max | 66 / **93** / 122 | 679 / **1051** / 1513 | 127 / **179** / 271 |
| altitude min/median/max | −43 / 17 / 115 | −21 / 29 / 115 | −52 / −20 / 87 |
| nearest neighbour min/median | 66 / **105** blocks | 140 / **211** | 245 / **374** |
| multi-chunk | 100% | 100% | 100% |
| distinct biomes | 46 | 44 | 49 |
| candidates → accepted | 835 → 834 | 837 → 837 | 833 → 787 |

The scarcity ordering is iron >> copper > gold on deposit frequency, and gold sits deepest with
the longest walk between finds (median 374 blocks). Copper is the least frequent-but-largest: a
third of iron's frequency carrying eleven times the cells, which is the intended "rare but worth
the trip" shape rather than "found every few chunks".

Gold rejects 46 of 833 candidates on altitude — its band tops out at y=90 while its depth is 85
below surface, so high terrain has no room. That is a tuning signal, not an error, and it is
visible because the selector deliberately tests biome before altitude.

---

## 5. The copper radius decision (measured, not assumed)

The instruction was explicit: copper must not simply inherit the existing Cluster radius of 22.
It was measured across five candidate configurations rather than reasoned about:

| radius | cells min / **median** / mean / max | multi-chunk |
|---|---|---|
| 6–7 | 277 / **463** / 410 / 555 | 96% |
| **8–10 (shipped)** | 679 / **1051** / 1076 / 1513 | 100% |
| 12–14 | 2384 / **3150** / 3182 / 4049 | 100% |
| 16–18 | 5696 / **7012** / 7056 / 8474 | 100% |
| 22 (curated reference) | 14847 / **15155** / 15153 / 15428 | 100% |

Radius 22 would have made a single copper deposit **15,155 cells** — over 14x the shipped size and
roughly 163x a single iron deposit. That is not a deposit, it is terrain.

**8–10 was chosen** as the smallest configuration that is reliably multi-chunk (100%, where 6–7
drops to 96% and would sometimes produce a single-chunk deposit) while staying an order of
magnitude below the curated body. Median 1,051 cells.

---

## 6. Determinism, independence and salts

- **Salts are distinct and asserted to be**: iron 1041, copper 3163, gold 2079, silica 7311. The
  test fails if two resources ever share one, because retuning either would silently reroll the
  other.
- **Independence measured, not assumed**: co-occurrence of iron and copper owner cells came out at
  **0.721 against 0.719 predicted by chance**, and **0 of 2,596 shared cells** placed the two
  deposits at the same origin. The distributions do not correlate.
- **Chunk-order independence** and **restart identity** are covered end-to-end per metal in
  `NaturalMetalLifecycleGameTests` (plan → register → materialize → mine → debt → 6h restore),
  along with M6 actor/Creative policy regression on naturally generated cells.

---

## 7. Rails / curated coupling — checked, no defect

The milestone required stopping and reporting if natural generation could not use different shape
values from Rails-curated deposits without changing shared behaviour. It can:

> `copper: curated r=22 plans 15297 cells, natural r=10 plans 1460 — the two are independent configuration`

`naturalTuningDoesNotResizeCuratedDeposits` pins this. Natural tuning is read from the `natural`
block; curated deposits continue to read the resource's own generation block. **No architectural
coupling was found, so no portion of the milestone was stopped.**

---

## 8. Existing worlds

`NaturalGenerationHandler`'s `isNewChunk()` guard is **untouched by M10A** — the diff shows no
change to that file's logic. The no-retro-population guarantee from M7 is structurally unchanged,
and its existing coverage passes inside the 738-test GameTest run. M10A adds no migration, no
retroactive scan, and no force-loading.

---

## 9. Generated-world proof

A real dedicated server, fixed seed `britannia-m5-audit`, all four natural resources enabled, then
the region files read off disk with the Anvil scanner (GameTest worlds are always FLAT, so this is
the only way to see true Overworld generation).

**54 fully-written chunks, 88 distinct block types, 0 unreadable.**

| Finding | Result |
|---|---|
| Managed iron present | **`minecraft:iron_ore` x 103** |
| Every other `*ore*` id | **absent** |
| All 18 denied vanilla ore / raw-metal ids | **absent** (coal, redstone, diamond, lapis, emerald, gold, copper, deepslate variants, `raw_iron_block`, `raw_copper_block`, `raw_gold_block`) |
| Ambient rock | intact — stone 793,886 · deepslate 737,539 · granite 63,390 · andesite 60,912 · diorite 59,933 · tuff 58,777 · gravel 27,078 · dirt 54,595 · clay 1,371 |

103 iron blocks is almost exactly one deposit (median 93 cells) against 0.72 expected in 54
chunks — the generated world agrees with the modelled density.

**Stated honestly:** copper and gold did not appear in this sample, and should not have — their
expected counts in 54 chunks are 0.18 and 0.055 deposits respectively. Their placement is proven
by the lifecycle GameTests and the 5-seed audit, **not** by this world.

**A precision note that now matters:** because iron and gold generate vanilla block ids, the
presence of `minecraft:iron_ore` no longer distinguishes managed from vanilla by block identity
alone. The distinguishing evidence is the deposit ledger. The claim supported here is the M9
formulation: *no denied vanilla economic ore is produced through any generation route currently
identified and covered by the project.*

---

## 10. Regression baseline

| Suite | Post-M9 baseline | M10A | Delta |
|---|---|---|---|
| JUnit | 2,986 / 5 failed / 17 skipped | **2,989 / 5 / 17** | **+3 tests** (the new flag pin), **same 5 failures** |
| GameTest | 724 / 2 failed | **738 / 2** | **+14 tests**, **same 2 failures** |
| `build` | success | **success** | jars packaged (33.8 MB shaded) |

The 5 JUnit failures are the pre-existing asset-hash and report-hash tests
(`RoutineSkillGainPresentationTest`, `MilestoneEightContentReportTest`,
`CorrectiveMilestoneNineARenderAlignmentTest`, `MonolithMilestoneSevenRenderingTest` x2). The 2
GameTest failures are the known `aprivateplotacceptsitsownerandrefuseseveryoneelse` and
`alegacyvineconvertsonceandkeepsitsvarietyandmaturity`. **No new failure was introduced**, and per
standing instruction none of these were investigated.

---

## 11. Replacement completeness matrix

M5/M8.5 suppress **19 vanilla features across 9 families**. Managed replacement now stands at:

| Family | Vanilla features suppressed | Managed identity | Natural generation | Replaced? |
|---|---|---|---|---|
| iron | 3 | yes | **yes (M10A)** | **YES** |
| copper | 2 | yes | **yes (M10A)** | **YES** |
| gold | 2 (+1 badlands) | yes | **yes (M10A)** | **YES** |
| coal | 2 | **no** | no | **NO** |
| redstone | 2 | **no** | no | **NO** |
| diamond | 4 | **no** | no | **NO** |
| lapis | 2 | **no** | no | **NO** |
| emerald | 1 | **no** | no | **NO** |

**Production rollout gate: BLOCKED.** Five families would be suppressed with nothing in their
place. M10A moved the count of replaced families from 0 to 3 of 8; it did not and could not
unblock the gate.

---

## 12. Change inventory

```
 M  gametest/NaturalSilicaGameTests.java              33 ++++---   (assertion widened to the 4 intended resources)
 M  resource/natural/NaturalDepositService.java        8 ++-       (doc corrected — comment only)
 M  resource/placement/MaterializationService.java    24 ++++--    (THE FIX: WRITE_FLAGS + docs)
 M  data/britannia_mod/resources/resources.json       45 ++++++--  (three `natural` blocks)
 ?? gametest/MetalDistributionAuditGameTests.java     404 lines
 ?? gametest/NaturalMetalLifecycleGameTests.java      419 lines
 ?? gametest/NaturalGenerationCostGameTests.java      173 lines
 ?? test/…/placement/ManagedWriteFlagsTest.java        68 lines
```

Production Java touched: **2 files, +26/−7**, of which one is comment-only. Everything else is
data and tests — the shape the milestone predicted.

### Cost budget (structural, machine-independent)

| resource | reach | cell | owner cells asked per chunk | chunks planning one deposit |
|---|---|---|---|---|
| iron | 32 | 8 | 4 | 25 |
| copper | 10 | 16 | 4 | 4 |
| gold | 40 | 28 | 4 | 36 |
| silica | 13 | 10 | 4 | 4 |
| **total** | | | **16 cells per generated chunk** | |

`NaturalGenerationCostGameTests` asserts both bounds. A related sharp edge is documented rather
than hidden: `horizontalReach()` is the configured maximum radius, but for `Vertical` the radius
is a *column height*, not horizontal extent — iron reaches 32 while planning cells only 12 blocks
out (overshoot 20). The search window is therefore generous rather than wrong, and the test
asserts the correctness direction (reach ≥ furthest planned cell, so no deposit is silently
truncated) while reporting the overshoot. A generic fix using planner bounds was attempted, proved
a no-op for every current shape, and was reverted rather than left in as dead abstraction.

---

## 13. Next-phase reconnaissance — the five remaining families

**Principal finding: the five families have no managed identity at all.** They are absent from the
28-resource catalogue entirely. This is materially harder than M10A was.

M10A was cheap precisely because iron, gold and copper already had everything — block identity,
mining requirement, commodity value, extraction tooling, host policy, a shape — and lacked only a
`natural` block. **Coal, redstone, diamond, lapis and emerald have none of that.** Each needs a
full resource definition before natural distribution is even a question.

Every cell below is an **OWNER DECISION**. Per instruction, no mining requirement or economy value
has been invented.

| Family | Needs | Blocking owner decisions |
|---|---|---|
| coal | full definition | block identity (vanilla `minecraft:coal_ore` or a `britannia_mod:` block); **Mining requirement**; **commodity + price**; extraction tool tag; shape; 2 features to replace |
| redstone | full definition | as above; **is redstone an economic commodity at all in Britannia, or purely utility?** |
| diamond | full definition | as above; **4** features to replace — vanilla's most stratified family (`buried`, `large`, `medium`, base) |
| lapis | full definition | as above; **is lapis economic or enchanting-only?** |
| emerald | full definition | as above; vanilla emerald is mountain-biome-exclusive — **does Britannia keep a biome exclusivity?** |

### A better next phase than the five

Six families already have managed identity **and** generation, and lack only a `natural` block —
exactly the M10A shape, at roughly M10A cost:

**`silver`, `tin`, `shadow_iron`, `agapite`, `verite`, `valorite`.**

These are cheap, carry no new owner decisions about identity or economy, and enrich the world
without touching the production gate. They do **not** unblock rollout — only the five vanilla
families do that — but they are the highest value per unit of risk currently available.

---

## 14. Recommended milestone decomposition

| Milestone | Scope | Cost | Unblocks gate? |
|---|---|---|---|
| **M10B** | Natural generation for the six already-defined ores (silver, tin, shadow_iron, agapite, verite, valorite) | Low — data + tests, M10A shape | No |
| **M10C** | **OWNER DECISION session**: identity, Mining requirement, commodity and price for coal, redstone, diamond, lapis, emerald. No code. | Owner time only | No — prerequisite |
| **M11** | Implement the 5 families' resource definitions from M10C's answers | Medium | No |
| **M12** | Natural generation for the 5 families; **replacement completeness reaches 8 of 8** | Medium | **Prerequisite met** |
| **M13** | Enable production suppression; live QA; rollout | Medium | **YES** |

M10C is deliberately a decision milestone with no code. It is the actual critical path: M11 cannot
start without it, and nothing in M11–M13 can be guessed at safely.

---

## 15. Git state

- Branch `claude/ultimacraft-orevein-remediation-70bfeb`, HEAD **`118db9d3`** (M9).
- **M10A is uncommitted**, per instruction. 4 modified tracked files, 4 untracked test files.
- Nothing pushed. No upstream. `patch-18` remains at `c2bc44f3` and was not merged into.
- No destructive git operations; no history rewriting.
- The working tree contains **only** M10A's own files — no unrelated files, no stash, no
  documentation from the main checkout.

---

## 16. Carried-forward actions

1. **The `Vertical` reach overshoot** (§12) is documented and bounded, not fixed. If more
   `Vertical` resources are configured, revisit whether reach should derive from planner bounds
   per shape.
2. **Gold's 5.5% altitude rejection** (§4) is a tuning signal. Raising `max_y` above 90 or
   reducing depth below 85 would recover those candidates, if the owner wants gold slightly more
   common.
3. **Ledger flush during automated audits**: Gradle's `runServer` does not forward stdin, so
   `save-all` / `stop` could not be delivered and the deposit ledger was never flushed to disk for
   offline inspection in §9. The region-file evidence stands on its own and answers the question
   asked, but a future audit wanting *ledger* contents should deliver the command through a world
   datapack `minecraft:load` function — the established workaround in this repo — rather than
   stdin or RCON.
4. The 5 JUnit and 2 GameTest pre-existing failures remain uninvestigated, per standing
   instruction.

---

## 17. Recommendation

**Accept M10A and commit it**, including the platform fix, as one milestone commit.

Suggested message:

```
feat(worldgen): generate iron, gold and copper as managed natural deposits
```

The fix in §3 is the part worth weighing. It is small, but it corrects a comment that was
confidently wrong and a hang that produced no diagnostic of any kind. It is worth committing on
its own merits regardless of what happens to the three metals.

Then **M10C (owner decisions) is the critical path** — not more engineering. Nothing between here
and production rollout can proceed without the five families' identity and economy answers.
