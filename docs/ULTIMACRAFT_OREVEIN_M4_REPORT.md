# Milestone 4 - Deposit Identity and Scalable Regeneration

**Project:** UltimaCraft OreVein Remediation
**Date:** 2026-08-20
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**M3 commit:** `b4dd3a7ae5ce9d1b1afe1c76922285553c49805e`
**Status:** complete, uncommitted, awaiting review

---

## Objective

Give deposits persistent identity, and replace the flat every-tick restoration scan with a
per-dimension, chunk-indexed, loaded-chunk scheduler — without discarding a single existing debt.

---

## Findings

### 1. The old restoration cost was structural, and so is the fix

The previous scheduler walked every debt in every dimension on every server pre-tick. With ten
thousand outstanding debts that is two hundred thousand map visits a second to discover that none
of them are reachable. It is now zero: debts are grouped by `ChunkPos`, only loaded chunks are
watched, and the watch list is a due-time priority queue, so a pass with nothing ready costs one
comparison. **Measured, not asserted** — see Complexity evidence.

### 2. Registration had to be three-way, not two

The obvious ledger — "this id exists, skip" — would have been actively harmful. M3 made
materialisation budget-bounded and resumable, so a deposit can legitimately be half-written;
skipping on sight would strand every one of them permanently. The distinction between *the same
deposit again* (resume) and *a different deposit wearing the same id* (refuse) is the ledger's
whole job, and both are pinned.

### 3. A direct `storage.add` bypassed the scheduler — caught by an existing test

Two pre-existing GameTests (`aworkedbedisrecordedandcomesback`, `aquarriedsandstonefacecomesback`)
failed on the first full run. Two distinct causes, both real:

- **A debt added straight to the store was never watched.** Only `BrokenBlockTracker` notified the
  scheduler, so any other caller — including those tests — added a debt that nothing would look at
  until its chunk happened to cycle. Fixed at the source: `add()` now notifies, so *every* caller
  is correct rather than one.
- **The cadence outran the tests.** A pass runs once a second now, and those tests waited five
  ticks. That is a deliberate behaviour change, not a defect, so the tests were extended past the
  cadence rather than the cadence being bent to fit them.

### 4. My own test then caught the fix over-reaching

With `add()` notifying, adding a debt for an *unloaded* chunk marked that chunk active — which is a
lie, and would have quietly resurrected the cost this milestone exists to remove. The
force-load GameTest caught it immediately. `add()` now consults `getChunkNow`, which returns null
rather than loading, and only watches chunks that are genuinely there.

### 5. `destroyAllBrokenBlocks` would have silently done nothing

`/brokenblocks destroy_all` called `getBrokenBlocks().clear()`. That view is now a copy, so the
command would have reported success and cleared a temporary map. Found by reading the call sites
rather than by a test — there is none for it — and replaced with an explicit `clearAll()`.

### 6. A source-text test obstructed the change for the second time

`MiningRestorationPolicyTest.restorationKeepsASingleSchedulerAndNeverForceLoads` asserted that
`BlockRestoreHandler` contained exactly one `@SubscribeEvent` and the literal string
`level.isLoaded(data.pos)`. Both were true of the full scan and false of the scheduler that replaced
it. The invariant was never "one listener" — it was "one scheduler, and it never force-loads". M1
converted one method in this file for the same reason; this is the second. What remains static is
only the part a static check is good at: proving an API is *absent*.

### 7. The textile GameTest is flaky, and its rate moved

`spinningandweavingareexactandrejectspiderssilk` failed in two of four M4 GameTest runs and passed
in the other two, including the final one. It counts dropped item entities within two blocks of a
player — a proximity assertion over entities that move — and it is recorded in project memory as a
known flaky test. Nothing in M4 touches textile. The most likely mechanism for the changed rate is
timing: altering per-tick work shifts GameTest batch scheduling, which changes whether a
neighbouring test's dropped yarn drifts into range. **Reported as flaky rather than as a
regression, but the rate change is real and worth knowing.**

---

## Deposit identity model

One derivation, versioned and explicit:

```
source identity  ->  canonical encoding (v1|<source>|...)  ->  instanceId (FNV-1a 64)
instanceId       ->  plannerSeed
```

| Source | Stable ID inputs | Persisted? | Notes |
|---|---|---|---|
| **Natural** | world seed, dimension, resource id, owner grid cell, definition salt | Derived; also stored once registered | Rule defined and tested now; natural generation itself is not implemented. It inherits a contract instead of inventing one. |
| **Rails** | shard, dimension, resource id, origin, radius, rotation, region | Derived; stored once registered | The **fallback contract** — M0 established the payload has no stable row id. Consequence: editing a row's position, radius or rotation makes it a *different* deposit. A future Rails id replaces every component but the dimension. |
| **Admin** | a UUID minted once, plus dimension and resource | **Minted once, then persisted** | Nothing immutable to derive from — an operator may place the same thing twice and mean two. The same UUID always yields the same id, which is what makes persisting it sufficient. |
| **Retrofit** | migration batch id, dimension, resource id, origin | Derived (future) | Contract defined and tested; the framework is M8. Re-running a batch adopts the same terrain as the same deposits. |

**Canonical encoding.** Assembled in one place as `v1|<source>|<component>|…`, never an object's
`toString()` — a record's generated formatting is not a serialization contract, and reordering two
fields would silently change every id in every world. Components are normalised (trimmed,
lower-cased, null collapsed to empty) so one row cannot spell two identities. The exact string is
pinned by test. `DepositIdentity.VERSION` exists so a future change is deliberate and detectable.

**Zero is reserved** for "no deposit" and the hash refuses to produce it.

**Seed relationship.** `plannerSeed` is identical to the instance id today. They are kept as two
named concepts so they can diverge later — if a revision policy ever needs to re-roll geometry
without changing identity — without a redesign. M3's `DepositSeed` is **deleted**, not left beside
this: there is one derivation, not two that happen to agree.

---

## Deposit lifecycle

```
curated row / admin request
   -> canonical identity  -> instanceId + plannerSeed
   -> PlacementPlanner    -> deterministic PlannedDeposit (reads no blocks)
   -> DepositLedger.register(candidate)
        REGISTERED         new id; recorded; materialisation proceeds
        ALREADY_REGISTERED same id, same immutable metadata -> resume or no-op
        CONFLICT           same id, different metadata -> refused, nothing written
        REVISION_MISMATCH  same deposit, different definition revision -> refused, identity kept
   -> MaterializationService (budget-bounded, resumable, one guarded write path)
   -> DepositRegistrar.recordCompletePass  (only when the whole plan was examined)
   -> extraction -> debt with dueAt + resourceId + owning instanceId
   -> chunk-indexed store -> scheduler -> restore, or block and back off
```

**Repeat import versus collision.** A repeat re-derives the same identity from the same row, so
every piece of immutable metadata matches and the ledger says `ALREADY_REGISTERED` — materialisation
proceeds, finds correct cells already correct, and writes only what is missing. A collision is the
same id with different metadata, which should be impossible and means the derivation or the data is
wrong; it is refused with both descriptions and nothing is mutated.

**Registration is not materialisation.** They are deliberately separate state: a registered deposit
whose progress is `UNKNOWN_COUNT` has been recorded but not yet swept. A crash between the two
leaves a resumable deposit, not a poisoned one.

**Summary counts.** Only what can be made trustworthy:

- `plannedCells` — immutable, from the plan.
- `materializedCells` / `blockedCells` — written **only after a pass that examined the whole plan**.
  A budget-truncated pass describes a prefix, and recording it would turn "we have not finished
  looking" into "this much is blocked". After a complete pass they partition the plan exactly:
  `materialized + blocked == planned`, with already-correct cells counting as materialised.
- `depletedCells` — **not persisted.** Counted on demand from the debt store across the chunks the
  deposit's own bounds touch, which is bounded by the deposit rather than the world. A cached
  counter would need decrementing on extraction, restoration, admin removal and migration, and
  would drift the first time one was missed.

---

## Persistent schema

| Store | Name | Schema | Notes |
|---|---|---|---|
| Deposit ledger | `britannia_deposits` | **1** (new) | Per dimension. Chunk index rebuilt in memory from stored bounds rather than persisted — it is derivable, and persisting it would be a second thing to keep in step. |
| Restoration debts | `broken_blocks` | **2** | Grouped by `ChunkPos`. Schema 1 was the flat list and carried no marker, so its *absence* is how it is recognised. |

**Legacy migration.** A file with no `schema` is read as the flat layout, regrouped by chunk, and
given the due time it already had. No debt is dropped, no chunk is loaded, and saving afterwards
writes only schema 2.

**The due-time calculation is deliberately not the six-hour constant.** M2 made the delay resolve
from the resource definition every time the old scheduler looked at a record, so a pending silica
bed in a legacy file was *already* owed at 24 hours — that was live behaviour. The migration
computes `brokenTime + resolveDelay(originalState)` once, which keeps every existing debt due at
exactly the moment it was due before. A block the catalogue no longer governs falls back to the
historical constant, which is also what the old scheduler did for it.

---

## Scheduler architecture

```
BrokenBlockDataStorage        Map<ChunkPos, Map<BlockPos, Debt>>   persistent, per dimension
        |
   ChunkEvent.Load  ---->  activate(chunk): read THAT chunk's debts, queue them
        |
   PriorityQueue<Entry> ordered by max(dueAt, retryAt)             in memory, loaded chunks only
        |
   ServerTickEvent.Pre every 20 ticks (1s), budget 64 per pass
        |
        +-- ready?  no  -> break (one comparison)
        +-- ready?  yes -> canRestoreInto(cell)?
                             yes -> setBlockAndUpdate, debt removed
                             no  -> back off, re-queue, debt kept
        |
   ChunkEvent.Unload ---->  deactivate(chunk): O(1), debts stay on disk
```

**Cadence: 20 ticks.** These are six- and twenty-four-hour economic timers; sub-second precision on
when a vein returns is worth nothing, and twenty passes a second was the entire problem. **Budget:
64 restorations per pass**, so a mass chunk-load drains over several passes rather than one spike.

**Stale entries.** Unload does not walk the queue — that would make unload cost the queue's size.
An entry whose chunk is no longer active is discarded when it surfaces. Bounded, because an entry
is only queued by an activation and rejecting one is O(1).

**No force loading.** Nothing in the restoration package calls `setChunkForced`, `forceLoad`,
`addRegionTicket`, `getChunkAt` or `getChunkFuture` — statically asserted. The one chunk-source
question asked is `getChunkNow`, which returns null rather than loading, and it is used to decide
whether a newly recorded debt is worth watching at all.

---

## Complexity evidence

`RestorationSchedulerTest.tenThousandUnloadedDebtsAreNeverInspected`, using the scheduler's own
`debtsInspected()` counter rather than wall-clock timing:

| Step | Result |
|---|---|
| 10,000 debts created across 10,000 chunks, none loaded | `totalCount() == 10_000` |
| **1,000 scheduler passes** with no chunk loaded | `debtsInspected() == 0`, `restored == 0` |
| Debts still owed afterwards | 10,000 — none lost |
| Then 25 chunks activated | `debtsInspected() == 25` — exactly one read per loaded chunk's debt |
| One pass | exactly those 25 restored; the other 9,975 untouched and unread |

Supporting measurements:

- `aPassWithNothingReadyInspectsNothing` — 500 watched-but-not-due debts, 100 passes, **zero** reads.
- `activatingAChunkReadsOnlyThatChunksDebts` — 50 debts in 50 chunks, activating one reads **one**.
- `manyPermanentlyBlockedDebtsDoNotDominateTheScheduler` — 200 blocked debts are attempted **200
  times total**, then 100 further passes attempt **zero**.
- `overdueWorkDrainsOverBoundedPasses` — 199 overdue debts drain in exactly 4 passes, none
  exceeding the budget.

The old scheduler would have visited all 10,000 debts on each of those 1,000 passes.

---

## Blocked restoration behaviour

Two distinct times, and the distinction is the point:

- **`dueAt` — economic.** When the player is owed the node back. Resolved once at extraction from
  the resource's own policy. **Never moved by a block.** Moving it would quietly extend a flooded
  deposit's timer for as long as the puddle lasted.
- **`retryAt` — operational.** When it is worth looking again. Doubling backoff from 30 s, capped at
  30 minutes, persisted alongside `retryCount` so a restart does not resume hot-looping.

Blocked by: water, lava, block entity, non-replaceable occupancy, or an entity in the cell — the M1
occupancy rules, unchanged. `canBeReplaced()` is **not** reinstated as a broad permission. A blocked
debt is never dropped and the blocker is never overwritten.

Diagnostics (`/mining restorations`) now report per dimension: owed, overdue, backed-off, unowned,
how many are being watched in how many loaded chunks, when the next is due, whether the dimension's
file was migrated — plus the five most-retried blocked cells by name, position, attempt count, next
retry and owning deposit. Then every registered deposit with its planned/materialised/blocked/
depleted counts and revision. `/manageddeposit inspect` reports the stored due time (not a
recomputed constant), the backoff, and the owning deposit.

---

## Changes

| File | Change | Reason |
|---|---|---|
| `resource/deposit/DepositIdentity.java` **(new)** | Versioned canonical encoding + FNV-1a for all four sources; `plannerSeed` | One derivation from identity to seed. Replaces `DepositSeed`. |
| `resource/deposit/DepositSource.java` **(new)** | NATURAL / RAILS / ADMIN / RETROFIT | Part of the immutable metadata; keeps two sources describing one place apart. |
| `resource/deposit/DepositInstance.java` **(new)** | The record, with `sameDepositAs`, bounds, chunk footprint, NBT | Stores what cannot be recomputed; not one standing cell. |
| `resource/deposit/DepositLedger.java` **(new)** | Per-dimension `SavedData`, three-way registration, chunk-indexed ownership | Registration is not "skip if seen". |
| `resource/deposit/DepositRegistrar.java` **(new)** | Plan → instance; complete-pass progress; on-demand depleted count | The join between planning and the ledger, owning no policy. |
| `resource/placement/DepositSeed.java` | **Deleted** | Folded into `DepositIdentity`; two seed derivations would be exactly what the milestone forbade. |
| `resource/placement/PlacementPlanner.java` | `planCuratedVein` derives through `DepositIdentity`; shard/region overload | One contract for id and seed. |
| `block/blockrestore/BrokenBlockData.java` | Added `dueAt`, `instanceId`, `resourceId`, `retryAt`, `retryCount`; `blockedUntil`, `withDueAt` | Resolve once at extraction instead of recomputing on every visit; record ownership honestly. |
| `block/blockrestore/BrokenBlockDataStorage.java` | Chunk-indexed, schema 2, legacy migration, `debtsIn`/`hasDebtsIn`/`debtAt`, maintained `totalCount`, explicit `clearAll`, `add()` notifies the scheduler for loaded chunks only | The index is what makes the event-driven scheduler possible. |
| `block/blockrestore/RestorationScheduler.java` **(new)** | Due-time queue, chunk activation, bounded pass, backoff, instrumentation | Takes a storage rather than a level, so ordering/budget/backoff are testable without a world. |
| `block/blockrestore/BlockRestoreHandler.java` | Full scan → chunk events + cadenced pass; occupancy rules untouched | The milestone's central change. |
| `block/blockrestore/BrokenBlockTracker.java` | Resolves due time and owning deposit at extraction | Answers "which deposit is missing a piece". |
| `commands/PopulateOresCommand.java` | Registers before materialising; refuses on conflict/revision; records complete passes; reports resumed deposits | Durable identity on the compatibility path. |
| `commands/MiningDebugCommand.java` | Rewritten restoration report + deposit listing | Its old numbers described a system that no longer exists. |
| `commands/ManagedDepositCommands.java` | Uses stored `dueAt`, reports backoff and owner | Recomputing from a constant reported six hours for a silica bed owed at 24. |
| `commands/BlockCommands.java` | `destroy_all` uses `clearAll()` | It would otherwise have cleared a copy and reported success. |
| `BritanniaMod.java` | `RestorationScheduler.clearAll()` on server stop | The queues are rebuilt from disk as chunks load. |
| `gametest/ManagedClayDepositGameTests.java`, `HousingMaterialSupplyGameTests.java` | Restoration waits 30 ticks | The pass is cadenced now; 5 ticks outran it. |
| `test/…/MiningRestorationPolicyTest.java` | Force-load test rewritten; scheduler-shape test added | It pinned the old architecture's spelling. |
| `test/…/PlacementPlannerTest.java` | Seed cases moved to `DepositIdentityTest` | They belong with identity now. |
| 5 new test classes | 46 unit + 8 GameTests | |

---

## Tests

| Test / command | Result |
|---|---|
| `./gradlew test --tests "…resource.deposit.*"` | **23 run, 0 failed** |
| `./gradlew test --tests "…blockrestore.*"` | **24 run, 0 failed** |
| `./gradlew test` (full) | **2900 run, 5 failed, 17 skipped** |
| `runGameTestServer --no-configuration-cache` (final) | **668 run, 666 passed, 2 failed** |

### Regression accounting

| | M3 baseline | After M4 | Delta |
|---|---|---|---|
| JUnit run | 2854 | 2900 | **+46 new** |
| JUnit failed | 5 | 5 | **0 new** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 660 | 668 | **+8 new** |
| GameTests failed | 2 | 2 | **0 new** |

New unit tests: `DepositIdentityTest` 12, `DepositLedgerTest` 11, `RestorationSchedulerTest` 15,
`RestorationStorageMigrationTest` 9, `MiningRestorationPolicyTest` +1, `PlacementPlannerTest` −2
(moved) = **46**. New GameTests: `DepositLifecycleGameTests` **8**; repo total 668.

**Known baseline failures**, unchanged and not investigated: `RoutineSkillGainPresentationTest`,
`MilestoneEightContentReportTest`, `CorrectiveMilestoneNineARenderAlignmentTest`, two
`MonolithMilestoneSevenRenderingTest` asset-hash pins (JUnit); the two `GrapeArborGameTests`
failures (GameTest).

**Environmental / flaky:** `spinningandweavingareexactandrejectspiderssilk` — see Finding 7. Passed
in the final run and in one earlier M4 run; failed in two. A proximity-based entity count, recorded
in project memory as flaky, untouched by M4.

**New regressions: none.** Three failures appeared mid-implementation
(`aworkedbedisrecordedandcomesback`, `aquarriedsandstonefacecomesback`,
`anunloadedchunkisneverloadedtorestoreintoit`); all three were real defects in the M4 work, all
three were fixed at the source, and all three pass.

---

## Invariants now established

| Invariant | Status |
|---|---|
| Deposits have stable persistent identity | **Yes** — `DepositLedger`, schema 1, per dimension |
| The same deterministic deposit survives restart with the same identity | **Yes** — `aDepositSurvivesRestartWithItsIdentityAndStillResumes` |
| A deposit can resume partial materialisation | **Yes** — `aDepositRegistersMaterializesInSlicesAndResumes` |
| A duplicate import does not create another deposit | **Yes** — `ALREADY_REGISTERED`, ledger size unchanged |
| An identity conflict is refused | **Yes** — loudly, with both descriptions, nothing mutated |
| Legacy restoration data survives migration | **Yes** — every debt, its block, its timestamp, its due moment |
| Offline time counts | **Yes** — wall-clock epoch throughout, unchanged |
| Unloaded debts are not scanned continuously | **Yes** — 1,000 passes over 10,000 unloaded debts inspect zero |
| Unloaded chunks are never force-loaded for restoration | **Yes** — statically (no force APIs) and behaviourally (GameTest) |
| Overdue entries catch up on legitimate chunk load | **Yes** — `anOverdueDebtCatchesUpWhenItsChunkLoadsAgain` |
| Blocked entries do not hot-loop | **Yes** — attempted once, then backed off; capped at 30 min |
| Fluids and block entities are never overwritten | **Yes** — M1 rules unchanged and re-pinned |
| Deposit/resource parent identity is available for new debts | **Yes**, where a deposit is registered; honestly absent otherwise |

**Not claimed:** natural generation, retrofit adoption of existing terrain, or any operator command
beyond read-only diagnostics.

---

## Legacy / unowned behaviour

Three distinct states, kept distinct:

1. **Migrated legacy debts** — from a pre-M4 file. `instanceId = 0`, `resourceId = ""`. The format
   never recorded either, so neither is invented. `/mining restorations` says the dimension was
   migrated and counts them as unowned.
2. **Unowned new debts** — a resource block extracted today with no registered deposit covering it,
   which is every resource block in an existing world. `instanceId = 0`, but `resourceId` **is**
   known, because the block still resolves through the catalogue.
3. **Owned debts** — inside a registered deposit's bounds, matching its resource.

No fake deterministic id is ever derived from insufficient information. Adopting existing terrain is
M8's retrofit.

---

## Revision behaviour

A stored instance whose `definitionRevision` differs from the current catalogue is
**`REVISION_MISMATCH`**: not a collision — it is the same deposit, and a definition file changing is
expected — but materialisation is refused, because continuing would write a deposit half in one
shape and half in another. Its identity and original revision are kept, the mismatch is reported by
name in the command output and in `/mining restorations`, and geometry is never silently
regenerated. **Full reconciliation is explicitly deferred to M8.** Pinned by
`aRevisionChangeIsDetectedRatherThanSilentlyRegenerating`.

---

## Temporary adapters / debt

| Item | Status | Removal |
|---|---|---|
| Rails id fallback | **In use.** Identity derived from immutable row content because the payload has no stable id. Known consequence: editing a row's position/radius/rotation makes it a different deposit. | **M8**, when Rails supplies a row id |
| Synchronous Rails fetch | **Unchanged**, still on the server thread, bounded ~15 s. Not needed for identity or scheduling. | **M8** |
| `VeinPlacementValidation` | **Unchanged** since M3 — external-input checks for a curated row only | **M8**, with the importer |
| Purity / graded-stone producers | **Unchanged.** Inspected: they call `BrokenBlockTracker.recordBrokenBlock`, which resolves the resource and owner itself, so no change was required. Deliberately not touched. | **M6 or a later extraction pass** |
| Unowned legacy debts | **Representable and visible.** Not adopted. | **M8** retrofit |
| `RestorationScheduler` in-memory queue | Rebuilt from the chunk-indexed store on chunk load; cleared on server stop. Not a temporary adapter — it is the design. | — |
| Chunk index in `DepositLedger` | Rebuilt in memory on load rather than persisted | — |

---

## Balancing notes (carried forward, unchanged by M4)

- **Agapite:** the live Rails `ore_veins` row still needs its radius corrected from the legacy `55`
  to the approved provisional **`8`**. `55` was a count multiplier for the old scatter algorithm and
  is refused by name, with the resource and row identified in the skip message. **Not changed here;
  the live Rails database was not touched.**
- **Silver / tin:** M3's corrected geometry is accepted and untouched. Silver at radius 50 plans
  ~2,510 cells where the defective shape placed effectively zero in solid rock; tin at 35 plans
  ~1,180 against ~210. **Required pre-production validation item for M9 or an explicit balancing
  pass.** The lever is resource configuration and radius, not the geometry.

---

## Playbook impact

**M5 — unchanged.**

**M6 — unchanged.** Confirmed that the purity/graded-stone producers did not need touching for
deposit identity, so no M4 work leaked into it.

**M7 — unchanged.** Silica's lens is a planner plus a host tag; the ledger takes it without change.

**M8 — materially thinner, as anticipated.** The importer no longer needs to invent idempotence:
stable identity, three-way registration, resumable materialisation and conflict refusal all exist
and are tested. What is genuinely left for M8 is the **asynchronous fetch**, the **Rails row-id
contract**, and the **retrofit framework** that adopts unowned terrain — plus the operator command
suite. The identity rules for retrofit are already defined and tested as a forward contract, so M8
implements a framework against an existing contract rather than designing one.

**M9** gains one explicit item: silver/tin distribution and economy validation (above).

---

## Git state

| Item | Value |
|---|---|
| Repository | `C:\projects\britannia\mod\Britannia_Mod` |
| Worktree | `.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| **M3 commit** | **`b4dd3a7ae5ce9d1b1afe1c76922285553c49805e`** — `refactor(oreveins): add deterministic planners and guarded placement` (40 files; worktree verified clean immediately after) |
| Current HEAD | `b4dd3a7ae5ce9d1b1afe1c76922285553c49805e` — unchanged |
| **M4 committed?** | **No.** All M4 work is uncommitted. |

`git status --short`:

```
 M src/main/java/com/seggellion/britannia_mod/BritanniaMod.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BlockRestoreHandler.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BrokenBlockData.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BrokenBlockDataStorage.java
 M src/main/java/com/seggellion/britannia_mod/block/blockrestore/BrokenBlockTracker.java
 M src/main/java/com/seggellion/britannia_mod/commands/BlockCommands.java
 M src/main/java/com/seggellion/britannia_mod/commands/ManagedDepositCommands.java
 M src/main/java/com/seggellion/britannia_mod/commands/MiningDebugCommand.java
 M src/main/java/com/seggellion/britannia_mod/commands/PopulateOresCommand.java
 M src/main/java/com/seggellion/britannia_mod/gametest/HousingMaterialSupplyGameTests.java
 M src/main/java/com/seggellion/britannia_mod/gametest/ManagedClayDepositGameTests.java
D  src/main/java/com/seggellion/britannia_mod/resource/placement/DepositSeed.java
 M src/main/java/com/seggellion/britannia_mod/resource/placement/PlacementPlanner.java
 M src/test/java/com/seggellion/britannia_mod/mining/MiningRestorationPolicyTest.java
 M src/test/java/com/seggellion/britannia_mod/resource/placement/PlacementPlannerTest.java
?? src/main/java/com/seggellion/britannia_mod/block/blockrestore/RestorationScheduler.java
?? src/main/java/com/seggellion/britannia_mod/gametest/DepositLifecycleGameTests.java
?? src/main/java/com/seggellion/britannia_mod/resource/deposit/
?? src/test/java/com/seggellion/britannia_mod/blockrestore/
?? src/test/java/com/seggellion/britannia_mod/resource/deposit/
```

15 tracked files changed (+717 / −303, including the 76-line `DepositSeed` deletion), plus 10 new
files: 6 source, 1 GameTest class, 3 unit test classes.

Pre-existing unrelated work, untouched: `stash@{0}` (`On shrines-monoliths`, 2026-08-02), and the
untracked `docs/` project files in the main checkout, excluded from all four commits.

---

## Recommendation

**Safe to proceed to M5.**

Deposits have durable identity that survives a restart, resumes rather than duplicates, and refuses
to be overwritten by a different deposit. Every legacy restoration debt migrates with its block, its
timestamp and its exact due moment. The every-tick full scan is gone, replaced by a chunk-indexed,
loaded-chunk, budget-bounded scheduler whose cost is measured at **zero reads for 10,000 unloaded
debts over 1,000 passes**. Blocked cells back off instead of hot-looping, fluids and block entities
are still never overwritten, and no chunk is ever force-loaded. Both suites ran with baselines held
separate and **zero new regressions**.

Two items for your awareness, neither blocking:

1. **The textile GameTest's flake rate changed** (Finding 7) — same known-flaky proximity assertion,
   unrelated to M4, but failing more often than in earlier milestones. Worth a separate look if it
   starts reddening CI regularly.
2. **The agapite Rails row correction and the silver/tin balancing validation** remain outstanding
   owner/data items, carried forward unchanged.

**Stopping after M4 as instructed. Milestone 5 has not been started, and M4 remains uncommitted.**
