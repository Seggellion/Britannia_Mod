# Claude Kickoff Prompt - UltimaCraft OreVein Remediation Project

You are beginning a multi-milestone engineering project in the UltimaCraft / Britannia Mod NeoForge codebase.

A playbook named:

`ULTIMACRAFT_OREVEIN_REMEDIATION_PLAYBOOK.md`

has been placed in the repository root.

Read that playbook completely before making changes.

The playbook was derived from a detailed engineering audit of the current OreVein, Mining, world-generation, extraction, persistence, and restoration systems.

## Mission

The current repository does **not** yet have one authoritative OreVein platform.

The audit found three adjacent systems:

1. `/populateores` retrieves curated coordinates from Rails and directly writes ore shapes into the command source's dimension;
2. six imperative shape algorithms directly mutate the world and are selected through resource-name branching;
3. a newer data-driven Mining catalogue and per-block persistent restoration system manage part of the extraction lifecycle.

The long-term goal is to consolidate these into a safe, deterministic, data-driven geological resource platform that UltimaCraft can use for many future ores and minerals.

Silica sand is the first new resource we eventually need to add.

Silica is a pale/whiter geological sand resource used as glass feedstock. It must be a distinct managed deposit, not ordinary sand, and it must require a custom non-vanilla shovel-type extraction tool. The extraction rule should ultimately be tag/data-driven rather than hardcoded to one concrete item.

However:

**Do not start by implementing silica.**

The audit identified correctness and scalability problems that must be addressed first.

## Important audit findings to verify

Treat these as audit findings, not assumptions. Verify them in the current code.

The audit reported that:

- a sufficiently skilled player may be able to break a managed resource with the wrong tool, bypassing managed yield/restoration;
- vanilla ore generation is still active;
- vanilla iron/gold participate in the Mining catalogue while coal is placed by OreVein but not managed through the same Mining flow;
- several current shape implementations directly overwrite unsafe/arbitrary states;
- shape randomness comes from `ServerLevel#getRandom()`, making repeat placement non-deterministic;
- repeated `/populateores` calls are not idempotent;
- deposits have no persistent identity;
- restoration is stored per depleted block and the scheduler scans all pending records every server pre-tick;
- the current restoration delay is six real-world hours, not 24 hours;
- overdue records in unloaded chunks do not force-load those chunks, which is good, but they are still repeatedly scanned;
- piston/explosion/nonstandard mutation paths can bypass the managed extraction lifecycle;
- `/populateores clear` is potentially dangerous and performs a very large synchronous block scan;
- the static undo map is nonpersistent and dimension-unsafe;
- current shape algorithms contain correctness/design issues;
- the repository already has a stronger data-driven seam in `MineableCatalog` / `Mineables` that should be preserved rather than replaced casually.

## Desired destination

The playbook defines the target responsibilities in detail.

At a high level, we want:

```text
ResourceDefinition
    -> shape + generation policy
    -> extraction/tool/Mining policy
    -> regeneration policy

VeinShape
    -> pure deterministic planner

MaterializationService
    -> guarded host-safe world mutations

DepositInstance
    -> persistent stable identity and summary state

ExtractionService
    -> one authoritative extraction transaction

Chunk-indexed Depletion/Restoration
    -> due timestamps
    -> loaded-chunk scheduling
    -> no forced chunk loading
```

Future vanilla mineral generation should be controlled by suppressing explicitly selected vanilla `PlacedFeature`s through the appropriate NeoForge 1.21.1 biome modifier mechanism, not by scanning generated terrain and replacing every ore block afterward.

Existing generated chunks must not be silently rewritten.

## How you must work

This is a milestone project.

Do **not** attempt to complete the entire playbook in one pass.

Do **not** launch a giant rewrite.

Do **not** assume the audit is perfectly current.

Do **not** discard healthy Mining infrastructure.

Do **not** modify unrelated systems.

Do **not** destroy or reset existing world persistence data.

Do **not** use destructive Git commands.

Do **not** create a new worktree or switch branches unless explicitly instructed.

Before changing code:

1. print the current repository path;
2. print the current branch;
3. print concise Git status;
4. identify pre-existing unrelated changes;
5. read the entire playbook;
6. inspect the relevant source and tests;
7. re-verify the audit claims.

## Start with Milestone 0 only

Execute:

**Milestone 0 - Re-verify Audit and Freeze Architectural Decisions**

from the playbook.

Milestone 0 is primarily an investigation and architecture-decision milestone.

Do not proceed into M1 implementation unless I explicitly tell you to continue.

### Re-verify the active system

Trace the current execution paths around:

- `BritanniaMod`
- `OreVeinLoader`
- `PopulateOresCommand`
- `OreVeinFetcher`
- every active vein/shape implementation
- `MineableCatalog`
- `Mineables`
- Mining gate/event handlers
- custom managed break logic
- provenance storage
- broken-block/depletion storage
- restoration scheduler
- admin/debug commands
- biome modifiers
- configured/placed features
- worldgen registration
- relevant GameTests/unit tests

Show how these currently connect:

```text
definition/config/source
    -> deposit placement
    -> player extraction
    -> managed yield
    -> depletion
    -> persistence
    -> regeneration
```

### Specifically verify

1. Whether `/populateores` is still the active placement entry point.
2. Whether Rails requests are synchronous and which thread runs them.
3. Whether `OreVeinLoader` data is actually consumed by placement.
4. Whether shapes still directly call world mutation APIs.
5. Whether shapes use shared level RNG.
6. Whether repeat placement is deterministic/idempotent.
7. Whether placement can overwrite fluids, block entities, structures, bedrock, or arbitrary blocks.
8. Whether deposit identity exists anywhere now.
9. Whether the skill gate validates tools.
10. Whether a high-skill player can break a managed block using a wrong/vanilla tool.
11. Whether piston/explosion paths can destroy/move deposits.
12. Which resources are in the Mining catalogue.
13. Which resources `/populateores` can actually generate.
14. Whether coal is still placed but not managed.
15. Whether vanilla ores still naturally generate.
16. What biome modifiers/worldgen data currently exist.
17. The actual restoration delay.
18. Whether restoration uses wall-clock epoch time.
19. Whether restoration persists through server restart.
20. Whether unloaded chunks remain unloaded.
21. Whether every pending debt is scanned every server tick.
22. Whether restoration can overwrite water/lava.
23. Whether `/populateores clear` can load/generate large chunk areas.
24. Whether undo state is persisted and dimension-aware.
25. What existing automated tests genuinely execute behavior versus merely inspect source text.

## Architecture decisions M0 must recommend

After re-verification, make explicit recommendations for:

### A. Canonical resource definition

Should the existing Mining definition be extended, or should a new `ResourceDefinition` compose/reference it?

We want one canonical resource id and no parallel third catalogue.

### B. Managed vanilla-looking resources

Should controlled iron/gold use custom managed deposit blocks rather than `minecraft:iron_ore` / `minecraft:gold_ore`?

Consider the need to distinguish:

- controlled deposits;
- legacy vanilla terrain;
- structures;
- manual decoration.

### C. Rails coexistence

Should Rails-curated vein coordinates remain supported?

If yes, explain how they should enter the same deterministic deposit-instance platform as natural/admin deposits without repeated rerolling.

### D. Restoration timing

The audit found a six-hour global delay.

Recommend whether:

- existing resources retain six hours;
- resource definitions receive per-resource timing;
- silica receives 24 real-world hours;
- any global default should remain.

Do not change timing during M0.

### E. Deposit identity

Recommend the smallest useful persistent `DepositInstance` record.

Explain stable identity for:

- natural deposits;
- Rails-curated deposits;
- admin-created deposits;
- retrofit deposits.

### F. Restoration storage

Compare:

1. per-dimension `SavedData` indexed by `ChunkPos`;
2. chunk attachments immediately.

Prefer the least disruptive option that removes the O(all-debts) tick scan while preserving restart/offline behavior and supporting migration of existing `broken_blocks` state.

### G. Vanilla ore suppression

Confirm the correct NeoForge 1.21.1 mechanism for future chunks.

Prefer explicit removal of selected vanilla placed features rather than block scanning.

Separate Overworld and Nether policy.

### H. Existing-world policy

Recommend a hybrid approach unless the current code reveals a better one:

- new chunks use controlled worldgen;
- existing chunks remain unchanged by default;
- existing curated deposits can be imported/registered;
- optional retrofit must be bounded, previewable, idempotent, and administrator initiated.

## M0 output format

Produce:

# Milestone 0 - Audit Re-verification and Architecture Decision Record

## 1. Repository / Git State

Include path, branch, HEAD, and concise status.

## 2. Verified Runtime Architecture

Use concrete classes/methods/files.

## 3. Audit Findings: Confirmed / Changed / Disproved

Use a table:

| Audit finding | Current verdict | Evidence |
|---|---|---|

## 4. Current Resource Matrix

Show the current resources, blocks, generation shapes, Mining handling, extraction tool behavior, and regeneration behavior.

## 5. Current Worldgen and Vanilla Ore Status

Explain exactly what happens today.

## 6. Current Restoration Lifecycle

Include timing, persistence, unloaded chunk behavior, and scheduler complexity.

## 7. Architecture Decisions

Cover A-H above.

## 8. M1-M4 Impact Map

List the likely classes/data/tests each milestone will touch.

Do not implement them yet.

## 9. Risks / Unknowns

Anything that still requires proof.

## 10. Milestone 0 Exit Criteria

State pass/fail for every M0 exit criterion in the playbook.

## 11. Recommendation

State whether the codebase is ready to proceed to M1.

Then stop.

## Evidence standard

Do not say something "looks good" without evidence.

For important conclusions, cite:

- concrete files;
- classes;
- methods;
- registrations;
- JSON/data files;
- event priorities;
- SavedData keys;
- test names;
- execution paths.

If the current code contradicts the original audit, update the project plan rather than forcing the old conclusion.

The purpose of M0 is to make the rest of the project safe to execute.
