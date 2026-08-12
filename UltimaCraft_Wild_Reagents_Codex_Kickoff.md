# Codex Kickoff Prompt
## UltimaCraft Wild Reagents, Resource Spawning, and Swamp Gas

You are working on the UltimaCraft Minecraft mod.

This project follows the managed vegetation/regrowth feature.

Your task is to implement a second ecological/resource system that adds:

- Sulfurous Ash near lava,
- Black-lipped Oysters near water on limestone/calcite,
- Black Pearl harvesting from oysters,
- Blood Moss in swamp managed vegetation,
- a subtle green swamp-gas environmental hue,
- original placeholder assets for all new visuals that do not yet have finished artwork.

Before changing code, read these files in full:

1. `UltimaCraft_Wild_Reagents_Design.md`
2. `UltimaCraft_Wild_Reagents_Playbook.md`

Treat the design as the specification and the playbook as the milestone workflow.

---

# Core Requirements

## Sulfurous Ash

Sulfurous Ash resource blocks should randomly appear in eligible **loaded chunks**.

A candidate is valid only when:

- it is within 20 blocks of lava,
- the target position is safe/replaceable,
- it has a suitable supporting surface,
- placing it will not overwrite protected/unrelated world content.

Do not invent a special harvesting tool restriction.

Search for existing Sulfurous Ash content before adding anything.

If artwork/content is missing, create original placeholders using the project's normal asset conventions.

---

## Black-lipped Oyster

Black-lipped Oyster resource blocks should randomly appear in eligible loaded chunks.

A candidate is valid only when:

- it is placed on the UltimaCraft `limestone` block or vanilla `calcite`,
- it is within 20 blocks of water,
- the target position is safe and replaceable.

In Adventure mode:

- an oyster may only be harvested with a recognized **dagger**,
- the check is server-side,
- a non-dagger attempt must not destroy the oyster,
- successful harvest yields exactly **1 Black Pearl**.

Search for the existing dagger abstraction and Black Pearl item first.

Do not assume every sword is a dagger.

If Black Pearl does not exist, create a placeholder item and original placeholder texture.

Explicitly document Survival and Creative behavior after inspecting current project conventions.

---

## Blood Moss

Blood Moss belongs to the completed **managed vegetation system**, not the wild-resource scheduler.

In the default swamp vegetation profile:

```text
Grass family: 75%
Fern: 20%
Blood Moss: 5%
Normal random flower: 0%
```

This means the normal 5% flower slot becomes a 5% Blood Moss slot in swamp biomes.

Requirements:

- Blood Moss is available only in the centralized configured swamp-biome set,
- reuse managed vegetation node persistence,
- reuse managed vegetation timing/regrowth,
- reuse managed `SwordType` cutting by default,
- do not create a second vegetation manager,
- create original placeholder Blood Moss art,
- do not invent seven growth stages for Blood Moss.

Outside swamp biomes, preserve the normal vegetation profile.

---

## Swamp Gas

Players in configured swamp biomes should experience a subtle greenish environmental hue suggesting swamp gas.

Inspect existing client rendering infrastructure first.

Preferred implementation:

- biome-specific fog/color adjustment,
- configurable intensity,
- smooth enter/exit transition if practical,
- client-only code,
- dedicated-server safe,
- no texture recoloring,
- no heavy custom shader unless the project already uses one.

The same centralized swamp-biome rule should drive both:

- Blood Moss eligibility,
- swamp-gas rendering.

---

# Critical Performance Constraint

Do not implement resource spawning by scanning all blocks in all loaded chunks.

Use a bounded server-side loaded-chunk scheduler.

Expected model:

```text
loaded chunk
-> occasional scheduled spawn attempt
-> choose resource entry
-> sample bounded random candidate positions
-> validate environment
-> place if valid and under cap
-> apply cooldown
```

Each resource entry should define/own:

```text
id
spawn frequency/weight
max per chunk
cooldown
max probes
candidate strategy
environment validator
substrate restrictions
harvest behavior
```

Do not force-load chunks.

If a 20-block proximity query crosses into an unloaded chunk, fail/defer safely instead of loading it.

---

# Mandatory Discovery Before Feature Changes

Inspect and report:

```text
Repository root:
Current branch:
Baseline SHA:
Working tree status:
Existing worktrees:
Expected base branch:
Feature branch/worktree plan:

Managed vegetation manager:
Managed vegetation profiles:
Managed vegetation persistence:
Managed vegetation scheduler:
Managed vegetation cut event:
SwordType:
Dagger identity:
Adventure break hooks:
Limestone registry ID:
Calcite handling:
Existing Sulfurous Ash:
Existing Blood Moss:
Existing Black Pearl:
Existing oyster content:
Chunk lifecycle hooks:
World persistence:
Server scheduler:
Biome helpers:
Client fog/render hooks:
Creative Tab:
Asset conventions:
Relevant tests:
Potential conflicts:
Recommended architecture:
```

Do not make implementation changes before producing this discovery report.

---


# Wild Resource Scheduler Requirements

Sulfurous Ash and Black-lipped Oysters should use one generic scheduler.

The scheduler must:

- operate only on normally loaded chunks,
- use bounded random probes,
- have centralized spawn frequency,
- support per-resource max-per-chunk caps,
- support spawn/respawn cooldowns,
- persist enough state to prevent trivial unload/reload/restart duplication,
- avoid full per-tick chunk scans,
- avoid forced chunk loading,
- remain server-authoritative.

The architecture must be ready for future reagents with different:

- biomes,
- substrates,
- nearby blocks/fluids,
- tools,
- heights,
- weather/time,
- loot.

Do not implement those future reagents now.

---

# Environmental Proximity Rules

Implement reusable safe proximity validation for a 20-block radius.

Sulfurous Ash:

```text
candidate within 20 blocks of lava
```

Oyster:

```text
candidate sits on limestone or calcite
candidate within 20 blocks of water
```

Document the distance metric used.

Use loaded world state only.

Do not force chunks to load just to satisfy the radius search.

---

# Placeholder Asset Requirements

No final art exists for these new assets.

Generate simple **original** placeholders locally.

Do not download external art.

Follow current project texture resolution/model style after repository discovery.

Required placeholder visuals, if absent:

```text
Sulfurous Ash block
Sulfurous Ash item icon, if separate item is required

Black-lipped Oyster block
Black Pearl item icon

Blood Moss block
Blood Moss item icon, if required by gameplay
```

Visual direction:

```text
Sulfurous Ash:
  pale yellow / yellow-gray low-profile ash/crystal pile

Black-lipped Oyster:
  dark black-purple shell with lighter lip
  low-profile against pale stone

Black Pearl:
  dark pearl with a small highlight

Blood Moss:
  crimson/blood-red low moss or ground plant
```

Keep registry/resource paths stable so final artwork can replace placeholders without code changes.

---

# Harvesting Authority

All harvest decisions happen on the server.

For oysters:

```text
Adventure + valid dagger -> harvest
Adventure + not dagger -> reject
authorized harvest -> exactly 1 Black Pearl
```

Two players attempting to harvest the same oyster must not duplicate loot.

For Sulfurous Ash:

- use project-standard harvesting,
- do not invent a new required tool.

For Blood Moss:

- reuse the managed vegetation `SwordType` rule unless existing project architecture provides a better established reagent strategy.

---

# Persistence and Reconciliation

The system must survive:

- server restart,
- chunk unload/reload,
- player disconnect/reconnect.

It must safely handle:

- lava/water source removed,
- oyster substrate removed,
- resource externally destroyed,
- player structure placed over former resource location,
- invalid persisted resource entry.

Never restore a reagent by overwriting an unrelated player block.

Do not force-load chunks during recovery.

---

# Future Profession Hook

Provide or reuse a clean event for successful wild-resource harvesting, conceptually:

```text
WildResourceHarvestEvent
```

Useful data:

```text
player
resource id
position
dimension
result item
tool category
```

Do not implement:

- payments,
- profession XP,
- quests,
- NPC jobs,
- Rails calls.

Blood Moss may continue using the existing managed vegetation cut event if that is the cleanest architecture.

---

# Required Working Method

Follow `UltimaCraft_Wild_Reagents_Playbook.md` milestone by milestone.

For every milestone:

1. state the objective,
2. inspect related existing code first,
3. implement the smallest compatible change,
4. add/update tests,
5. run focused tests,
6. report changed files and test results,
7. commit only if the project's current workflow safely allows it,
8. continue only after the milestone gate passes.

Do not collapse the entire feature into one large unreviewable patch.

---

# First Response Required From You

Before making feature changes, provide this concise discovery report:

```text
Repository root:
Current branch:
Baseline SHA:
Working tree status:
Existing worktrees:
Expected base branch:
Feature branch/worktree plan:

Managed vegetation manager:
Managed vegetation profiles:
Managed vegetation persistence:
Managed vegetation scheduler:
Managed vegetation cut event:
SwordType:
Dagger identity:
Adventure break hooks:
Limestone registry ID:
Existing reagent items:
Chunk lifecycle:
Persistence:
Server scheduler:
Biome helpers:
Client fog/render hooks:
Asset conventions:
Relevant tests:
Potential conflicts:
Recommended concrete architecture:
```

Then proceed with Milestone 0 unless you encounter a genuine stop condition in the playbook.

Do not ask broad questions that repository inspection can answer.

---

# Completion Standard

The project is complete only when all of these are true:

```text
Sulfurous Ash:
  loaded-chunk random spawning
  <=20 blocks from lava
  safe placement
  placeholder asset

Black-lipped Oyster:
  loaded-chunk random spawning
  on limestone/calcite
  <=20 blocks from water
  Adventure dagger-only harvest
  exactly one Black Pearl
  placeholder asset

Blood Moss:
  managed vegetation integration
  swamp-only
  exactly 5% default swamp slot
  normal 5% flower slot replaced in swamp
  managed regrowth retained
  placeholder asset

Swamp environment:
  subtle green hue
  centralized swamp biome rule
  client-only
  dedicated-server safe

Architecture:
  generic extensible resource entries
  bounded random probes
  centralized balance values
  max-per-chunk caps
  cooldowns
  persistence
  no forced chunk loads
  no full per-tick chunk scans
  server authority
  regression tests
  final implementation report
```

Build the feature into the systems UltimaCraft already has. Reuse the managed vegetation work where appropriate, but keep environmental resource spawning as its own clean lifecycle domain.
