# UltimaCraft OreVein Remediation and Expansion Playbook

**Project:** UltimaCraft / Britannia Mod  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.72  
**Source audit:** OreVein Engineering Audit, 2026-08-20  
**Purpose:** Convert the existing OreVein-adjacent systems into a safe, deterministic, scalable resource-generation platform, then add silica sand as the first new resource implemented on top of that foundation.

---

## 1. Project Mandate

This project is not a request to simply add another ore block.

The engineering audit established that the repository currently contains three adjacent systems rather than one authoritative OreVein platform:

1. an operator command that synchronously retrieves curated vein coordinates from Rails and writes blocks into the current dimension;
2. six static imperative shape algorithms selected by resource-name conditionals;
3. a newer data-driven Mining catalogue plus a persistent per-block restoration ledger.

The Mining catalogue is the strongest existing seam and should be preserved. The current placement and restoration architecture is not yet a safe foundation for a long-running controlled resource economy.

The project must incrementally consolidate these systems into an architecture where UltimaCraft controls:

- what geological resources exist;
- where they can appear;
- how their deposits are shaped;
- which blocks they can replace;
- which tools may extract them;
- which Mining requirements apply;
- what they yield;
- how depleted resource cells regenerate;
- how deposits are identified and inspected;
- how vanilla mineral generation is suppressed;
- how existing worlds are migrated safely.

Silica sand will be the first new resource built on the corrected platform.

The desired end-state is a reusable resource platform, not a growing collection of ore-specific `switch` statements.

---

## 2. Non-Negotiable Design Principles

### 2.1 Preserve healthy existing systems

Do not discard working infrastructure merely because the placement system needs redesign.

The audit specifically identified these as worth preserving or evolving:

- `MineableCatalog`
- `Mineables`
- server-authoritative Mining skill gating
- Mining economy/resource identity
- player-placement provenance
- the concept of persistent restoration state
- read-only Mining diagnostics

Refactor these only where needed to establish one canonical resource definition and one extraction transaction.

### 2.2 No giant rewrite

Work in explicit milestones.

Each milestone must:

1. inspect the current implementation before modifying it;
2. state the intended change;
3. add or strengthen tests;
4. implement only the milestone scope;
5. run focused tests;
6. run the appropriate broader regression suite;
7. report exact files changed;
8. report exact test results;
9. update this playbook's execution record or provide a milestone handoff;
10. stop at the milestone boundary unless explicitly instructed to continue.

### 2.3 No silent world rewriting

Existing generated chunks must never be globally scanned and rewritten simply because vanilla ores exist.

Future-generation control and legacy-world migration are separate concerns.

Any retrofit of existing terrain must eventually be:

- bounded;
- previewable;
- administrator initiated;
- idempotent;
- auditable;
- recoverable from backup.

### 2.4 Prevent generation rather than cleaning it afterward

For future chunks, suppress selected vanilla ore `PlacedFeature`s through NeoForge biome modifiers.

Do not use a post-generation "find every ore block and turn it into stone" world scan as the primary control mechanism.

### 2.5 Managed deposits must be distinguishable

UltimaCraft must be able to distinguish controlled resource deposits from:

- decorative ore in structures;
- manually placed blocks;
- legacy terrain;
- unrelated vanilla geology.

Where needed, use custom managed deposit blocks rather than vanilla ore identity.

### 2.6 Extraction is a transaction

A managed resource must have one authoritative extraction path.

A correct extraction should conceptually perform:

1. identify managed resource/deposit;
2. validate player/automation policy;
3. validate required tool/tag;
4. validate Mining requirement;
5. validate game-mode/protection rules;
6. mutate the block to the expected depleted state;
7. persist restoration state;
8. create configured yield;
9. damage the tool once;
10. update deposit summary;
11. award skill.

Wrong-tool or invalid extraction must be cancelled before world mutation.

### 2.7 Shape planners must not mutate the world

A geological shape should answer:

> Given a deterministic seed, origin, and configuration, which relative positions belong to this deposit?

It should not directly call `ServerLevel#setBlock`.

All actual placement must pass through one guarded materialization service.

### 2.8 Do not force-load chunks for regeneration

Regeneration should be lazy and persistent.

Offline time should count, but unloaded chunks should remain unloaded.

### 2.9 No resource-specific Java branching unless unavoidable

Adding a future mineral should approach:

1. register block/item if needed;
2. add resource definition;
3. choose/configure a shape;
4. choose/configure extraction and regeneration policy;
5. add content/assets/tests.

It should not require editing several resource-name `switch` statements.

---

## 3. Audit Findings That Drive This Project

Treat these as the starting engineering hypotheses from the 2026-08-20 audit. Re-verify them against the current worktree before changing code.

### Critical findings

1. **Wrong-tool/high-skill destruction bypass**
   - The skill gate checks skill but not the extraction tool.
   - A sufficiently skilled player using a non-Britannia tool can reach vanilla breaking.
   - This can permanently destroy a managed resource without creating its managed yield or restoration debt.

2. **Vanilla ores remain uncontrolled**
   - Vanilla biome ore features are still active.
   - Vanilla iron and gold participate in managed Mining because their blocks are in the Mining catalogue.
   - Coal is placed by the OreVein command but is not managed by the Mining catalogue.

3. **Unsafe shape writes**
   - Several current shape classes overwrite arbitrary states.
   - Some can replace air, fluids, structure content, block entities, or nearly anything except bedrock.
   - Replacement policy is inconsistent between shapes.

4. **No deterministic deposit identity or duplicate prevention**
   - Shapes consume `ServerLevel#getRandom()`.
   - Re-running `/populateores` rerolls and reapplies a deposit.
   - There is no persistent deposit-instance ledger.

### High-priority findings

5. **Restoration is O(all pending blocks) every server pre-tick**
   - Every restoration debt is visited every tick, including unloaded overdue cells.

6. **`/populateores clear` is dangerous**
   - It scans an enormous area synchronously.
   - It can load/generate chunks.
   - It replaces recognized ores without reliable provenance.

7. **Explosion/piston/non-standard mutation paths bypass managed extraction**
   - Managed deposit blocks can be moved/destroyed outside the restoration transaction.

8. **Undo state is static, dimension-unsafe, and nonpersistent**
   - `BlockPos` alone is insufficient across dimensions.
   - Restart loses the undo ledger.

9. **Shape correctness defects exist**
   - `SnakeVein` can fail on small radii.
   - `LayeredVein` mixes air placement and solid replacement.
   - `VerticalLayeredVein` is air-only.
   - `GeodeVein` is not actually geode-shaped.
   - placement counts may include duplicate writes.

10. **Placement work and Rails fetches happen synchronously**
    - Network timeouts or large shapes can stall the server thread.

### Confirmed regeneration behavior from the audit

The current global delay is **six real-world hours**, not 24 hours.

The existing system uses:

- `System.currentTimeMillis()`;
- per-position restoration records;
- per-dimension `SavedData`;
- persistence across save/restart;
- no forced chunk load;
- restoration only after a due record's chunk becomes loaded.

Silica may use a new 24-hour policy if approved, but do not silently change all existing resources to 24 hours unless the project explicitly decides to do so.

---

## 4. Target Architecture

The implementation does not have to use these exact class names, but it should converge on these responsibilities.

```text
ResourceDefinition
    |
    +-- stable resource id
    +-- managed block / depleted state
    +-- output item / yield rules
    +-- Mining requirement
    +-- extraction item tag
    +-- geological shape id + shape config
    +-- biome / dimension / altitude policy
    +-- deterministic spacing / salt
    +-- host-block policy
    +-- regeneration policy
    +-- schema / revision

VeinShape
    |
    +-- pure deterministic planner
    +-- validated config / codec
    +-- no direct world mutation

PlacementPlanner
    |
    +-- derives instance seed / owner cell
    +-- computes planned candidate positions
    +-- supports chunk slicing

MaterializationService
    |
    +-- validates world bounds
    +-- validates host state
    +-- rejects protected/block-entity cells
    +-- rejects duplicate instance placement
    +-- enforces bounded work
    +-- performs actual mutations

DepositInstance
    |
    +-- stable instance id
    +-- resource id
    +-- dimension
    +-- origin
    +-- deterministic seed
    +-- bounds
    +-- definition revision
    +-- source: natural / Rails / admin / retrofit
    +-- summary state

ExtractionService
    |
    +-- authoritative tool/skill/policy validation
    +-- one commit path
    +-- yield
    +-- depletion
    +-- restoration scheduling
    +-- skill award

DepletionLedger
    |
    +-- persisted per dimension
    +-- indexed by chunk
    +-- dueAt epoch timestamps
    +-- loaded-chunk scheduling only
    +-- chunk-load catch-up
```

---

## 5. Git and Worktree Discipline

Before every milestone:

1. print the current repository path;
2. print the current branch;
3. print `git status --short`;
4. identify unrelated pre-existing changes;
5. do not overwrite or discard user work;
6. do not switch worktrees or branches unless explicitly instructed;
7. keep milestone changes narrowly scoped;
8. do not commit unless the user has asked Claude to commit;
9. if commits are authorized, use one or more clean milestone-focused commits and report hashes.

Never use destructive Git commands to "clean up" an unfamiliar worktree.

---

# Milestone 0 - Re-verify Audit and Freeze Architectural Decisions

## Objective

Reconcile the engineering audit with the current code before implementing anything.

The audit is a snapshot. Claude must prove the relevant findings still apply.

## Required investigation

Verify at minimum:

- `BritanniaMod`
- `OreVeinLoader`
- `PopulateOresCommand`
- `OreVeinFetcher`
- all active shape classes
- `MineableCatalog`
- `Mineables`
- Mining gate handlers
- custom break handler
- provenance storage
- broken-block/restoration storage
- restoration scheduler
- admin/debug commands
- biome modifiers
- configured/placed features
- GameTests and unit tests covering these systems

Trace:

```text
resource definition
    -> placement
    -> extraction
    -> depletion
    -> persistence
    -> regeneration
```

## Decisions to resolve

Document explicit recommendations for:

1. whether existing Rails-curated coordinates remain supported alongside deterministic natural generation;
2. whether controlled iron/gold should use custom managed blocks instead of vanilla ore blocks;
3. whether legacy vanilla ores become decorative/non-economic after cutover;
4. whether existing resources remain on their current six-hour restoration timing;
5. whether silica alone initially receives a 24-hour restoration delay;
6. how deposit ids are derived for natural, Rails, admin, and retrofit sources;
7. whether `ResourceDefinition` extends the Mining catalogue or composes/references it;
8. whether restoration remains per-dimension `SavedData` initially or immediately moves to chunk attachments.

## Deliverable

Produce a concise architecture decision record containing:

- verified current behavior;
- differences from the audit, if any;
- decisions;
- risks;
- implementation impact;
- exact files expected to change in M1-M4.

## Exit criteria

- Every critical audit claim has been re-verified or corrected.
- No implementation has started beyond harmless test scaffolding.
- The canonical resource-definition direction is selected.
- The managed block strategy is selected.
- Restoration timing policy is explicitly documented.
- Rails coexistence strategy is explicitly documented.

**Stop after M0 and report unless instructed to continue.**

---

# Milestone 1 - Correctness Containment

## Objective

Close mutation paths that can permanently destroy or corrupt managed resources before building new generation infrastructure.

## Required fixes

### 1. Wrong-tool extraction bypass

The resource gate must validate the extraction tool before vanilla mutation.

For a managed resource:

- correct tool/tag + sufficient skill may proceed through managed extraction;
- wrong tool must cancel before block mutation;
- no wrong-tool path may produce vanilla drops;
- no wrong-tool path may remove the managed block;
- no wrong-tool path may create a malformed restoration entry.

Add the missing regression case:

> sufficiently skilled player + wrong/vanilla tool must still be denied.

### 2. Piston behavior

Managed deposit blocks should not be movable away from their deposit location.

Use the appropriate NeoForge/Minecraft block policy rather than trying to repair provenance after the move.

### 3. Explosion policy

Define and enforce explicit behavior.

Default recommendation for managed economic deposits:

- explosions do not economically extract them;
- explosions do not permanently delete them;
- no accidental vanilla-drop route.

### 4. Restoration occupancy correctness

Do not treat all replaceable states as valid restoration targets.

In particular:

- water should block restoration;
- lava should block restoration;
- arbitrary replaceable state should not be overwritten unless explicitly part of policy.

### 5. Validate existing placement rows

Reject or safely skip:

- unknown resource ids;
- invalid radii;
- invalid rotations;
- invalid build-height positions;
- malformed coordinates;
- unsafe dimensions if not supported;
- shape configs that would throw.

### 6. Contain dangerous commands

Audit `/populateores clear` and `/undoores`.

At minimum:

- prevent accidental massive synchronous generation/loading;
- do not claim a bounded scan is global;
- do not erase unknown/manual/structure ores without provenance;
- remove or disable unsafe functionality if it cannot be made reliable within this milestone.

## Tests

Add focused tests for:

- high-skill wrong-tool denial;
- correct-tool success remains unchanged;
- piston protection;
- explosion policy;
- water/lava restoration blocking;
- malformed shape radius/rotation rejection;
- admin command safety boundaries where testable.

## Exit criteria

- No ordinary player or automation path can destroy a managed resource outside the authoritative extraction path.
- Invalid placement inputs cannot crash a shape algorithm.
- Restoration does not overwrite water/lava.
- Current managed extraction tests remain green.
- Dangerous clear/undo behavior is either safely bounded or intentionally disabled with a clear message.
- Focused tests pass.
- Appropriate full unit/GameTest regression suite has been run and exact results reported.

**Stop after M1 and report unless instructed to continue.**

---

# Milestone 2 - Canonical Data-Driven Resource Definitions

## Objective

Remove duplicated resource knowledge and establish one canonical definition seam that can support 25-50 resources.

## Required work

Design a codec-backed resource definition using an appropriate NeoForge 1.21.1 data-driven mechanism.

Prefer a custom datapack registry if it cleanly fits the codebase.

The definition should be capable of representing:

- resource id;
- managed block/depleted state;
- raw output item/economy identity;
- Mining requirement/challenge;
- extraction tool item tag;
- yield/enchantment policy;
- shape id;
- shape configuration;
- allowed dimensions;
- biome tags;
- altitude constraints;
- spacing/separation/salt;
- host-block tag;
- regeneration delay and mode;
- schema/revision.

Do not invent fields merely because they may be useful someday. Implement the smallest coherent schema needed for existing resources plus silica and near-term expansion.

## Migration

Convert existing resource-specific generation knowledge away from:

- `ORE_TYPES` block maps;
- ore-name shape switches;
- hardcoded tool identity where tags are more appropriate.

Do not silently change approved Mining levels, economy identifiers, purity behavior, or current yields.

## Tool tags

Establish generalized extraction tags, including the future silica tag pattern:

```text
britannia_mod:tools/extracts/<resource-or-family>
```

Choose exact naming based on the repository's existing tag conventions.

## Validation

Definitions must fail fast or produce precise startup errors for:

- unknown shape;
- missing block/item;
- unresolved required fields;
- invalid range relationships;
- negative/zero dimensions where invalid;
- unsafe regeneration values;
- malformed tags;
- unsupported dimension/biome constraints.

## Tests

- codec round-trip;
- malformed definitions fail cleanly;
- unknown shape fails;
- existing resources load;
- existing Mining requirements remain identical;
- no generation resource-name `switch` is required to add a test resource;
- extraction tag lookup works.

## Exit criteria

- A new test resource can use an existing shape and extraction rule without adding another resource-name `switch`.
- One canonical id links generation and Mining/extraction behavior.
- Existing resource behavior remains pinned by tests.
- Definitions are data-driven and validated.

**Stop after M2 and report unless instructed to continue.**

---

# Milestone 3 - Deterministic Pure Shape Planners and Guarded Placement

## Objective

Replace direct-mutating shape algorithms with deterministic pure planners and one safe world-mutation boundary.

## Shape contract

A shape should conceptually provide:

```text
plan(origin, deterministicSeed, config) -> unique candidate positions
```

Requirements:

- no `ServerLevel#getRandom()`;
- no `setBlock`;
- deterministic for identical inputs;
- validated configuration;
- unique planned positions;
- predictable bounds;
- testable without a running world where practical.

## Existing shapes

Review all existing archetypes individually:

- Cluster
- Vertical
- Snake
- Geode
- Layered
- VerticalLayered

Do not preserve bugs merely for compatibility.

For each:

1. document intended geology;
2. document current observed algorithm;
3. decide whether to preserve, correct, rename, or deprecate;
4. create deterministic tests.

Known audit concerns to address:

- small-radius Snake failure;
- "Geode" not actually being a geode;
- Layered placing into air in some phases;
- VerticalLayered being air-only;
- duplicate placement counts;
- inconsistent rotation behavior;
- misleading comments/names.

## Materialization service

All placement should pass through one service that validates:

- world build height;
- target dimension;
- biome policy if applicable;
- allowed host-block tag;
- block entities/containers;
- fluids;
- bedrock/protected blocks;
- already-owned deposit cells;
- operation budget;
- chunk ownership/slicing.

No shape may overwrite arbitrary content simply because it planned a position.

## Chunk behavior

Design generation so a deposit larger than one chunk does not force neighboring chunks to load.

Preferred strategy:

- deterministic owner cell / deposit origin;
- recompute deterministic nearby candidate instances;
- materialize only the slice belonging to the currently generating chunk;
- ensure generation order does not alter the final union.

## Idempotence

Running the same deterministic instance twice must not duplicate or reroll it.

## Tests

- same seed => same positions;
- different resource/salt => independent positions;
- positions unique;
- positions stay inside declared bounds;
- chunk-slice union equals full shape;
- chunk order does not alter output;
- host predicate protects air/fluids/block entities/bedrock/non-hosts;
- repeated materialization is idempotent;
- invalid config cannot reach runtime generation.

## Exit criteria

- No active shape directly mutates `ServerLevel`.
- No active shape consumes shared runtime RNG.
- Placement is host-safe and bounded.
- Repeated placement is deterministic and idempotent.
- Cross-chunk generation does not force-load neighboring chunks.
- Existing approved resources can be represented through the new planner system.

**Stop after M3 and report unless instructed to continue.**

---

# Milestone 4 - Deposit Identity and Scalable Regeneration

## Objective

Create enough persistent deposit identity to support duplicate prevention, diagnostics, regeneration ownership, migrations, and future economic control without storing one database record for every standing resource block.

## Deposit instance ledger

Create a compact per-dimension persistent ledger.

Recommended fields:

- stable instance id;
- resource definition id;
- definition revision/fingerprint;
- dimension;
- origin;
- deterministic seed;
- bounds;
- creation source:
  - natural
  - Rails
  - admin
  - retrofit
- creation/materialization version;
- planned/materialized/depleted/blocked summary counts where useful.

Standing block coordinates should normally be reproducible from the shape plan instead of fully persisted.

## Stable ids

Define deterministic id rules.

Examples of input material:

### Natural

```text
world seed
+ dimension id
+ resource id
+ owner/grid cell
+ definition salt
```

### Rails

Prefer a stable Rails vein identity if the API provides one. Otherwise derive a stable id from immutable curated row identity fields rather than command execution time.

### Admin

Persist a generated id at creation.

### Retrofit

Derive/persist an id from migration batch + deterministic candidate identity.

## Regeneration redesign

Replace the flat every-tick scan.

Recommended first implementation:

```text
per-dimension persistent depletion data
    -> grouped/indexed by ChunkPos
    -> each entry stores dueAtEpochMillis
    -> loaded chunks contribute to an in-memory due-time queue
    -> process due work on a bounded cadence
    -> chunk load immediately catches up overdue entries
    -> chunk unload removes them from active scheduling
```

Requirements:

- offline time counts;
- restart persists timing;
- no forced chunk loads;
- cost must not scale with all unloaded historical debts;
- process at a bounded cadence, not a full scan 20 times/second;
- blocked restoration must use backoff or otherwise avoid pathological hot retry;
- persist schema/data version;
- restore only when the expected depleted state is present;
- water/lava/occupied cells remain blocked;
- operator diagnostics expose blocked entries.

## Legacy restoration migration

Preserve existing pending `broken_blocks` data where possible.

Do not simply drop current restoration debts.

Implement a versioned migration and test it.

## Tests

- persistence round-trip;
- migration from legacy broken-block data;
- restart/offline due behavior;
- unloaded chunks remain unloaded;
- due entries catch up when chunk loads;
- 10k+ synthetic unloaded debts do not create O(n) per-tick scans;
- occupied block/entity/fluid behavior;
- blocked retry/backoff;
- deposit summary updates;
- duplicate instance rejection.

## Exit criteria

- Deposits have persistent stable identity.
- Duplicate generation can be detected.
- Regeneration survives restart/offline time.
- Unloaded debts are not scanned every tick.
- Regeneration does not force-load chunks.
- Legacy pending restoration state is retained through migration.
- Admin/debug code can identify the parent deposit/resource for a managed depleted cell where applicable.

**Stop after M4 and report unless instructed to continue.**

---

# Milestone 5 - Controlled Vanilla Ore Suppression

## Objective

Make UltimaCraft authoritative over future natural mineral generation.

## Strategy

Use NeoForge `neoforge:remove_features` biome modifiers to remove explicitly selected vanilla ore `PlacedFeature`s from future biome generation.

Do not remove the entire `underground_ores` generation step.

Do not use a block-replacement world scan as the primary mechanism.

## Initial Overworld suppression audit

Verify the exact Minecraft 1.21.1 keys before creating data files.

The audit identified the following families to classify:

- coal upper/lower;
- iron upper/middle/small;
- gold;
- lower gold;
- extra badlands gold;
- redstone;
- lower redstone;
- diamond variants;
- lapis variants;
- emerald;
- copper;
- large copper.

Create an explicit UltimaCraft-owned classification:

```text
suppressed
allowed
```

Prefer a tag or auditable definition list.

Add CI/startup diagnostics for ore-producing vanilla features that are neither explicitly allowed nor suppressed if practical.

## Nether policy

Do not remove Nether resources incidentally.

Evaluate separately:

- Nether gold;
- quartz;
- ancient debris;
- magma;
- soul sand;
- blackstone;
- gravel.

Only suppress the resources the design explicitly chooses to replace.

## Existing chunks

This milestone controls only new generation.

Do not alter existing chunks.

## Structure/manual block safety

Prove that feature removal prevents biome decoration ore features while preserving:

- structure template blocks;
- manually placed ore blocks;
- already generated chunks.

## Tests

- fixed seed/new chunk contains zero denied vanilla ore feature output in audited areas;
- allowed geology remains;
- structure/manual ore blocks remain;
- suppression data loads without registry errors;
- deepslate variants are covered through the appropriate configured feature path.

## Exit criteria

- New chunks no longer generate denied vanilla mineral features.
- No post-generation ore scan is required.
- Existing chunks are unchanged.
- Structures/manual blocks are not rewritten.
- Nether policy is explicit rather than accidental.

**Stop after M5 and report unless instructed to continue.**

---

# Milestone 6 - Silica Sand Resource and Custom Extraction Tool

## Objective

Add silica as the first new resource using the improved definition, extraction, identity, and regeneration platform.

Do not generate silica deposits yet if the sedimentary shape is scheduled for M7.

## Silica identity

Silica sand is not ordinary Minecraft/UltimaCraft sand.

It is a managed geological raw material used as glass feedstock.

Add the appropriate:

- managed silica deposit block;
- texture/model/blockstate;
- raw silica sand item if separate;
- localization;
- registration;
- tags;
- resource definition;
- economy/refining identity if the codebase already requires one.

The visual design is a slightly whiter/paler version of the existing UltimaCraft sand, but the engineering work must keep the identities distinct.

## Falling behavior

Do not automatically inherit vanilla falling-sand behavior.

A managed geological deposit should remain bound to its deposit coordinates unless the architecture explicitly supports moving deposit cells.

Default recommendation: silica deposit block is visually sand-like but not gravity-movable.

## Silica extraction tool

Add a custom non-vanilla shovel-type item.

Use a generalized item tag, for example:

```text
britannia_mod:tools/extracts/silica
```

The exact namespace/path should follow repository conventions.

The resource definition should reference the tag, not the concrete item class.

### Required behavior

Correct tagged shovel + sufficient Mining skill:

- enters managed extraction transaction;
- creates configured silica yield;
- damages tool once;
- creates depletion/restoration debt;
- updates deposit summary;
- awards skill according to configured rules.

Ordinary vanilla shovel:

- cannot extract silica;
- does not remove the managed deposit;
- produces no vanilla drop;
- produces no restoration record.

Wrong tools/fake players/automation:

- follow explicit policy;
- default to no extraction and no mutation.

Creative/operator:

- may have an explicit administrative bypass;
- should not accidentally produce economic yield.

Fortune/Silk Touch:

- define and test explicit behavior;
- default recommendation is to ignore/reject economic amplification unless resource policy opts in.

## Restoration timing

Silica should support a data-driven regeneration duration.

The current global legacy duration discovered in the audit is six real-world hours.

If the approved silica design is 24 real-world hours, configure silica as 24 hours without changing all existing resources.

## Adventure/survival-zone compatibility

Audit any tool checks such as `instanceof QualityToolItem` that could prevent a shovel-family extraction tool from reaching the intended path.

Generalize them appropriately without weakening city/protection rules.

## Tests

- silica definition loads;
- ordinary sand is not silica;
- ordinary shovel denied;
- silica-tagged shovel succeeds;
- wrong-tool/high-skill remains denied;
- yield identity/quantity pinned;
- tool durability exactly once;
- Fortune/Silk Touch policy pinned;
- fake player/automation policy pinned;
- creative/operator policy pinned;
- regeneration delay resolves to the configured silica value.

## Exit criteria

- Silica is a fully defined managed resource.
- Extraction is tag-driven, not hardcoded item equality.
- Vanilla shovel cannot destroy or extract it.
- Regeneration timing is resource-specific.
- Existing Mining resources remain unaffected.

**Stop after M6 and report unless instructed to continue.**

---

# Milestone 7 - Sedimentary Lens Geological Shape and Silica Distribution

## Objective

Add a reusable geological shape suitable for silica and populate silica through the deterministic placement platform.

## Shape

Create a genuine shallow sedimentary lens, not a renamed `LayeredVein`.

Desired properties:

- broad horizontal footprint;
- elliptical/irregular lens boundary;
- relatively thin vertical thickness;
- deterministic low-frequency variation;
- optional small internal gaps;
- no arbitrary floating placement;
- host-rock/sediment replacement only;
- no block entities;
- no fluids;
- no protected/structure blocks unless explicitly allowed;
- chunk-slice safe.

Potential shape id:

```text
britannia_mod:sedimentary_lens
```

Use repository naming conventions if a better id exists.

## Silica distribution

Keep distribution data-driven.

Initial policy should be treated as a balancing configuration, not permanent hardcoded numbers.

Support:

- Overworld restriction;
- curated silica biome tag;
- approved host-block tag;
- shallow/subsurface altitude policy;
- minimum spacing/separation;
- deterministic salt;
- lens radius/thickness;
- regeneration policy;
- optional Rails/admin curated placement using the same shape planner.

Do not make "all normal sand" valuable silica.

## Statistical validation

For fixed seeds, gather distribution statistics such as:

- deposits per region/chunk area;
- average/min/max block count;
- altitude distribution;
- biome distribution;
- nearest-neighbor spacing;
- chunk-border behavior;
- host rejection percentage.

Avoid balancing by visual intuition alone.

## Tests

- deterministic geometry;
- same seed/definition => same lens;
- chunk order independence;
- no floating silica;
- no fluid/block-entity overwrite;
- thickness/radius bounds;
- spacing/min-separation;
- biome/altitude restrictions;
- fixed-seed distribution snapshots/statistics.

## Exit criteria

- Silica deposits generate deterministically and only in approved contexts.
- Deposit geometry is recognizably broad/shallow.
- No neighboring chunk force-load is required.
- Distribution can be tuned through data.
- Silica is the first successful proof that a new resource can be added without resource-name generation code.

**Stop after M7 and report unless instructed to continue.**

---

# Milestone 8 - Admin, Diagnostics, Rails Import, and Existing-World Migration

## Objective

Give operators enough visibility and migration control to run an authoritative resource economy safely.

## Minimum admin diagnostics

Implement or extend commands conceptually equivalent to:

```text
/orevein inspect
/orevein list [resource]
/orevein locate <resource>
/orevein stats
/orevein regenerate <id>
```

Exact syntax should follow existing command conventions.

Operators should be able to answer:

- how many deposits exist;
- where a specific deposit is;
- which resource definition it uses;
- its instance id;
- its source;
- its bounds;
- its materialized/depleted/blocked counts;
- when depleted cells are next due;
- whether a duplicate instance was rejected.

Do not build arbitrary create/delete commands until integrity constraints are proven.

## Rails curated vein import

Retain Rails support only through the same deterministic deposit platform.

Requirements:

- stable instance identity;
- validation before placement;
- idempotent repeated imports;
- no reroll on repeated command;
- dimension explicit or safely resolved;
- no synchronous giant mutation after a slow network response;
- clear failure reporting;
- no duplicate application.

If the Rails API lacks needed stable fields, document the smallest API contract improvement instead of guessing.

## Existing-world policy

Default:

- existing chunks remain unchanged;
- new chunks use suppressed vanilla ore generation and managed deposits;
- curated existing Rails deposits can be registered/imported deliberately.

## Retrofit framework

If a retrofit is implemented, require:

1. bounded target region/chunk selection;
2. dry-run preview;
3. candidate deposit list;
4. host-block impact count;
5. protected/conflicting cell count;
6. estimated work;
7. explicit apply;
8. durable migration ledger;
9. idempotent re-run;
10. backup/rollback instructions.

Never silently scan all existing world ore blocks and "fix" them.

## Exit criteria

- Operators can inspect and count persistent deposit instances.
- Rails import is deterministic/idempotent.
- Existing chunks are untouched by default.
- Any retrofit is bounded, previewable, and durable.
- Legacy dangerous clear behavior is no longer needed.

**Stop after M8 and report unless instructed to continue.**

---

# Milestone 9 - Performance, Regression, Live-World QA, and Rollout Readiness

## Objective

Prove the new resource platform is safe enough for a persistent multiplayer world and future mineral expansion.

## Performance validation

Measure or reasonably instrument:

- generation cost per chunk;
- materialization cost per deposit;
- shape planning cost;
- restoration scheduling cost;
- loaded due-queue size;
- pending depletion storage size;
- save serialization cost;
- blocked-restoration behavior;
- Rails/admin import work budgets.

Test scenarios should include:

- thousands of deposits;
- 10,000+ depleted cells;
- mostly unloaded debts;
- many overdue cells loaded at once;
- restart with pending restoration;
- blocked restoration;
- chunk generation near deposit boundaries.

## Full regression

Run the project's established:

- unit tests;
- focused Mining tests;
- GameTests;
- data validation;
- build/package checks.

Document known baseline failures separately from new regressions.

Do not call the milestone complete if new failures remain unexplained.

## Fixed-seed world audit

Generate controlled test regions and inspect:

- zero denied vanilla natural ores;
- expected managed deposit counts;
- no duplicate deposit ids;
- no protected block overwrites;
- deterministic regeneration behavior;
- silica distribution;
- cross-chunk consistency.

## Rollout rehearsal

Before production:

1. back up a representative world copy;
2. load/import existing curated deposit state;
3. verify legacy restoration migration;
4. enable new-chunk vanilla suppression;
5. generate new chunks;
6. mine/regenerate representative resources;
7. restart;
8. confirm timers/state;
9. profile server behavior;
10. rehearse rollback.

## Exit criteria

- No new correctness regressions.
- Restoration cost is not proportional to all unloaded debts every tick.
- Controlled deposit generation is deterministic.
- Duplicate protection works.
- Denied vanilla ores do not naturally generate in new chunks.
- Existing chunks are not silently rewritten.
- Silica extraction/regeneration works after restart.
- Performance is acceptable at projected long-lived multiplayer scale.
- Rollback procedure is documented.

**Stop and provide final project report.**

---

# 6. Deferred / Future Enhancements

Do not pull these into early milestones unless required by a discovered correctness issue.

Potential future work:

- deposit-wide regeneration cycles;
- geological surveying/discovery gameplay;
- player maps showing discovered deposits;
- resource exhaustion analytics;
- Rails synchronization of deposit summaries;
- market balancing based on extraction volume;
- chunk attachments if the per-dimension chunk-indexed ledger outgrows practical limits;
- additional geological archetypes;
- generation policies tied to UltimaCraft regions/cities;
- resource ownership or territorial control;
- administrative deposit creation/deletion after integrity invariants are proven.

---

# 7. Testing Philosophy

Prefer deterministic automated tests over manual visual verification.

Every fixed defect should gain a regression test when practical.

Avoid source-text tests that merely assert that a Java file contains a string if executable behavior can be tested.

For worldgen:

- fixed seeds;
- stable salts;
- deterministic expected geometry/statistics;
- chunk-order tests.

For extraction:

- test full state transition, not only permission functions.

For regeneration:

- test persistence and due-time behavior;
- test unloaded chunks;
- test blocked cells;
- test scale characteristics.

For migrations:

- preserve fixtures representing old serialized data.

---

# 8. Performance Guardrails

The final platform should avoid:

```text
for every pending depletion:
    inspect it every server tick
```

It should avoid:

```text
generate neighboring chunk just to finish a deposit
```

It should avoid:

```text
scan millions of blocks to remove vanilla ores
```

It should avoid:

```text
make a synchronous network request in the middle of world generation
```

It should avoid:

```text
one BlockEntity per ore block
```

It should avoid:

```text
store every standing deposit cell if it can be deterministically recomputed
```

Use bounded, indexed, event-driven, or lazy work wherever practical.

---

# 9. Milestone Completion Template

At the end of every milestone Claude must report:

## Milestone N Completion

### Objective

What the milestone was intended to achieve.

### Findings

Anything discovered that materially changed the plan.

### Changes

Table:

| File | Change | Reason |
|---|---|---|

### Tests

Table:

| Test / command | Result |
|---|---|

Include exact counts where available.

### Invariants now established

State the correctness guarantees that are now pinned by code/tests.

### Remaining risks

Anything still intentionally deferred.

### Playbook impact

Whether any later milestone should be amended.

### Git state

- branch;
- worktree status;
- commit hash(es), if commits were authorized;
- unrelated pre-existing changes left untouched.

### Recommendation

State whether it is safe to begin the next milestone.

Then stop unless explicitly instructed to continue.

---

# 10. Definition of Project Completion

This project is complete when all of the following are true:

1. managed resources cannot be permanently destroyed through wrong tools, pistons, explosions, or accidental vanilla break paths;
2. resource generation is data-driven rather than resource-name switch-driven;
3. geological shapes are deterministic pure planners;
4. placement is centralized, guarded, and host-safe;
5. deposits have stable persistent identity;
6. repeated generation/import is idempotent;
7. regeneration persists across restarts and offline time;
8. regeneration never force-loads chunks;
9. unloaded historical debts are not scanned every tick;
10. vanilla mineral generation is explicitly suppressed for selected resources in new chunks;
11. existing chunks are not silently rewritten;
12. silica sand exists as a distinct managed resource;
13. silica requires a custom tagged shovel/extraction tool;
14. vanilla shovels cannot extract or destroy silica deposits;
15. silica uses a genuine deterministic sedimentary-lens deposit shape;
16. silica's distribution and regeneration are configurable;
17. operators can inspect and diagnose deposits;
18. Rails-curated deposits, if retained, enter through the same idempotent deposit platform;
19. tests cover deterministic generation, extraction, persistence, regeneration, vanilla suppression, chunk boundaries, and migration;
20. the architecture can add future resources primarily through registration + definitions + existing reusable shapes/policies.

The final result should make Resource #25 or Resource #50 routine to add, rather than progressively making the codebase more fragile.
