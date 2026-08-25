# Patch 18 Fertile Dirt Recovery — Milestone Plan

## Milestone 0 — Repository archaeology and design lock

### Goal

Map the existing systems and resolve the few product details that must be grounded in repository behavior before implementation.

### Investigate

Locate and document:

- the canonical `fertile_dirt` item and every current acquisition/use path;
- Earth Elemental fertile-dirt loot;
- current farming application/consumption logic;
- crop harvest completion logic;
- farm plot/block entity persistence;
- the `WildResource` base classes, registries, data definitions, spawn scheduler, support predicates, breaking, and drops;
- existing custom shovel classes and right-click/block-use patterns;
- Mining entry points, so dirt gathering can explicitly avoid them;
- per-player cooldown patterns;
- item interaction patterns involving main hand + offhand;
- custom containers/buckets/bowls if any;
- water-source detection;
- UltimaCraft water-well implementation;
- Adventure-mode/build-right checks;
- inventory-safe item replacement helpers;
- GameTest/JUnit conventions for each relevant system.

### Resolve and record

1. Final container return count for the last bowl step.
2. Concrete dirt-gather cooldown and where it is stored.
3. Exact canonical custom dirt item identity.
4. Whether only normal dirt, or dirt + coarse dirt, may be gathered with the shovel.
5. Where the five remaining fertile harvests should be persisted.
6. Which exact event constitutes a “successful harvest.”
7. Whether missing art assets block any implementation milestone.

### Deliverable

Create a repository report such as:

`PATCH18_FERTILE_DIRT_M0_REPORT.md`

Include file paths, class names, data IDs, risks, proposed architecture, and a milestone-by-milestone implementation map.

### Production changes

None, except harmless test probes or documentation if needed. Do not begin feature implementation in M0.

### Gate

Do not start M1 until the existing architecture is sufficiently mapped to avoid duplicate items, duplicate fertile-dirt state, or a second WildResource framework.

---

## Milestone 1 — Item and registry foundation

### Goal

Establish canonical item/block identities without yet implementing the full interaction chain.

### Implement

As needed based on M0:

- `empty_bowl`
- canonical custom `dirt`
- `bowl_of_dirt`
- `bowl_of_fertile_dirt`
- `bowl_of_water`
- `dung` block/item registration
- language entries
- models/blockstates/data-generation hooks
- creative-tab placement where appropriate

Reuse the existing fertile-dirt item. Do not create a duplicate.

### Tests

Verify:

- all intended registry IDs resolve;
- vanilla bowl is distinct from UltimaCraft empty bowl;
- serialization/registry lookup survives normal test bootstrap;
- dung block has one canonical dropped item identity.

### Gate

No bowl interaction logic yet unless registry architecture requires a minimal helper.

---

## Milestone 2 — Dung as a WildResource

### Goal

Make dung a first-class `WildResource`.

### Implement

- register dung through the existing WildResource mechanism;
- enforce support only on dirt/coarse dirt for natural spawning;
- use existing density/cap/respawn conventions;
- ensure invalid support prevents or removes natural placement according to WildResource precedent;
- breaking dung grants one dung item through the normal authoritative break/drop path.

### Explicit exclusions

- no mining integration;
- no managed deposits;
- no Rails authority;
- no fertile-dirt crafting yet.

### Tests

At minimum:

- allowed support: dirt;
- allowed support: coarse dirt;
- rejected support: grass;
- rejected support: stone;
- rejected support: sand;
- no airborne spawn;
- break gives exactly one dung;
- no double drop;
- existing WildResources continue to pass.

### Gate

Dung must be renewable through the existing WildResource lifecycle before bowl crafting consumes it.

---

## Milestone 3 — Custom shovel dirt gathering

### Goal

Allow deliberate renewable collection of the custom dirt ingredient without damaging terrain.

### Implement

When the correct UltimaCraft shovel is used on exact vanilla dirt or coarse dirt:

- right-click/use interaction grants the canonical custom dirt item;
- target block remains unchanged;
- no block-break event is required;
- no Mining skill, Mining XP, deposit, vein, or managed extraction code is called;
- apply the M0-approved player cooldown;
- enforce cooldown server-side;
- use existing protection/build-right checks when applicable;
- provide appropriate success/cooldown feedback using existing project conventions.

### Abuse cases

Test:

- spam click;
- hand swap;
- offhand tool use if unsupported;
- Adventure mode;
- Survival mode;
- Creative mode duplication;
- protected housing;
- client/server double firing.

### Tests

Prove that:

- one valid action grants one item;
- dirt remains dirt;
- immediate second action grants zero;
- action works again after cooldown;
- Mining progression remains unchanged;
- invalid tool grants nothing;
- invalid target grants nothing.

### Gate

Dirt acquisition must be balanced, authoritative, and independent of Mining.

---

## Milestone 4 — Custom bowl preparation chain

### Goal

Implement the two dry preparation steps.

### Step A

Main hand: `empty_bowl`

Offhand: custom `dirt`

Right-click -> `bowl_of_dirt`

### Step B

Main hand: `bowl_of_dirt`

Offhand: `dung`

Right-click -> `bowl_of_fertile_dirt`

### Requirements

- preserve exact hand-order contract;
- server-authoritative inventory mutation;
- exact one-for-one ingredient consumption;
- no vanilla bowl acceptance;
- no reversed-hand accidental execution;
- no double execution from both hands;
- safe output behavior when inventory conditions are unusual;
- no vanilla crafting-table recipe as a substitute.

### Tests

Cover exact inputs, wrong inputs, reversed hands, stacks >1, full inventory, Creative mode, multiplayer/server-side execution, and repeated click packets.

### Gate

The dry mixture chain must be deterministic before adding water behavior.

---

## Milestone 5 — Bowl of water and water/well filling

### Goal

Create the water-container acquisition path.

### Implement

Using `empty_bowl` on a valid water source or UltimaCraft water well:

- yields `bowl_of_water`;
- does not use a vanilla bowl;
- does not consume world water unless the existing well/water architecture explicitly requires it;
- follows M0’s source-vs-flowing-water decision;
- respects Adventure-mode interaction/protection conventions;
- is authoritative on the server.

### Water targets

Test at minimum:

- valid source water;
- flowing water;
- water well;
- non-water block;
- protected-area interaction;
- Creative mode.

### Gate

Water acquisition must work through both required sources before final fertile-dirt mixing is enabled.

---

## Milestone 6 — Final fertile-dirt mixing

### Goal

Complete the preparation loop.

### Interaction

Main hand: `bowl_of_fertile_dirt`

Offhand: `bowl_of_water`

Right-click -> canonical `fertile_dirt` + returned custom bowl container(s) according to M0.

### Requirements

- exact canonical fertile-dirt ID;
- no alternate duplicate fertile-dirt item;
- no item loss beyond the approved recipe;
- no bowl duplication;
- no vanilla bowl output;
- atomic server-side mutation;
- reliable stacked-item behavior.

### Tests

Prove exact before/after inventory counts for:

- one of each input;
- stacked inputs;
- full/near-full inventory;
- Creative mode;
- invalid offhand;
- reversed hands;
- repeated invocation;
- multiplayer execution.

### Gate

A player must now be able to craft fertile dirt end-to-end, although five-harvest persistence lands in M7.

---

## Milestone 7 — Five-harvest fertile dirt lifecycle

### Goal

Change fertile dirt from the current behavior to a five-successful-harvest resource.

### Implement

Using the existing farming architecture identified in M0:

- a fresh fertile-dirt application starts with 5 remaining successful harvests;
- decrement only on the authoritative successful-harvest event;
- persist remaining harvests;
- synchronize state to clients where visible/necessary;
- after the fifth successful harvest, transition to the existing exhausted/non-fertile state;
- fertile dirt obtained from Earth Elementals behaves identically to crafted fertile dirt.

### Required sequence test

Assert:

- apply -> 5;
- harvest -> 4;
- harvest -> 3;
- harvest -> 2;
- harvest -> 1;
- harvest -> 0/exhausted.

Also verify:

- planting alone does not decrement;
- failed harvest does not decrement;
- unrelated block interaction does not decrement;
- chunk unload/reload retains count;
- server restart persistence retains count if supported by the existing test harness;
- two players cannot double-decrement one harvest;
- existing crop regrowth/replant semantics remain intact.

### Gate

The five-harvest rule is the authoritative farming behavior for all fertile dirt.

---

## Milestone 8 — Integration, exploitation audit, and regression

### Goal

Prove the feature as one complete gameplay system.

### Full scenario

Automate as much as practical:

1. Dung spawns on valid terrain.
2. Player obtains dung.
3. Player gathers dirt with custom shovel.
4. Player obtains/fills custom bowl.
5. Player creates bowl of dirt.
6. Player creates bowl of fertile dirt.
7. Player obtains bowl of water.
8. Player creates fertile dirt.
9. Player applies fertile dirt.
10. Player completes five successful harvests.
11. Fertility exhausts.

### Exploit audit

Specifically attempt:

- spam clicking;
- packet double execution;
- main/offhand event duplication;
- stack underflow/overflow;
- Creative mode duplication;
- relog cooldown bypass;
- changing held item to bypass cooldown;
- using vanilla bowls;
- using vanilla dirt if custom dirt is required;
- gathering dirt through Mining pathways;
- placing dung on invalid support;
- breaking/replacing dung to multiply drops;
- two-player simultaneous interaction;
- chunk unload during fertility use.

### Regression suites

Run:

- focused new tests;
- farming tests;
- WildResource tests;
- water/well tests;
- protection/Adventure-mode tests;
- Mining tests to prove non-regression;
- the repository’s broader JUnit suite;
- the broader GameTest suite.

Classify only genuinely pre-existing failures as baseline, with evidence.

### Final report

Create:

`PATCH18_FERTILE_DIRT_FINAL_REPORT.md`

Include:

- final gameplay behavior;
- exact registry IDs;
- balance/cooldown value;
- five-harvest storage mechanism;
- tests and results;
- known limitations;
- art/assets still needed;
- live-client checks still required;
- branch divergence/dirty-worktree status.

Do not merge or integrate branches unless explicitly instructed.

---

# Acceptance matrix

| # | Acceptance condition |
| --- | --- |
| 1 | Dung is implemented through `WildResource`. |
| 2 | Naturally spawned dung only uses dirt/coarse dirt support. |
| 3 | Breaking dung yields one dung item. |
| 4 | UltimaCraft has a custom empty bowl; vanilla bowl is not accepted. |
| 5 | Custom shovel gathers custom dirt without breaking the dirt block. |
| 6 | Dirt gathering does not use Mining. |
| 7 | Dirt gathering has a server-authoritative anti-spam cooldown. |
| 8 | Empty bowl + dirt creates bowl of dirt using the requested hand order. |
| 9 | Bowl of dirt + dung creates bowl of fertile dirt. |
| 10 | Empty bowl fills from valid water. |
| 11 | Empty bowl fills from the UltimaCraft water well. |
| 12 | Bowl of fertile dirt + bowl of water creates canonical fertile dirt. |
| 13 | Returned bowl count follows the explicit M0 container rule. |
| 14 | Earth Elemental fertile-dirt loot remains valid. |
| 15 | Every fertile-dirt application supports exactly five successful harvests. |
| 16 | Fertility decrements only on successful harvest. |
| 17 | Fertility state persists correctly. |
| 18 | Multiplayer cannot duplicate inputs/outputs or double-decrement harvests. |
| 19 | Adventure-mode and protection behavior remains correct. |
| 20 | Existing WildResource, farming, Mining, and relevant regression suites remain healthy. |
