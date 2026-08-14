# Claude Code Initial Kickoff Prompt — UltimaCraft Mining Skill Progression

You are beginning a new UltimaCraft feature project: **Mining Skill Progression**.

Work from the current UltimaCraft repository, expected at:

```text
C:\projects\britannia\mod\Britannia_Mod
```

The owner has already approved the core gameplay direction.

Before doing anything else, read these two project documents in full:

```text
MINING_SKILL_DESIGN.md
MINING_SKILL_MILESTONE_PLAYBOOK.md
```

If they are in a different repository-relative location, find them and use their actual paths.

## Owner requirements

Implement Mining on top of the existing UltimaCraft Mining system where mined resource blocks are restored to the world.

Do not replace the existing restoration system.

Mining must become a real skill progression system in which natural rocks and ores have skill requirements.

A player who lacks the required Mining skill **must not be able to break the block**.

An insufficient-skill attempt must leave the block and all gameplay state unchanged. It must not create drops, consume tool durability, award Mining skill, or schedule a block restoration.

Regular Stone is an intentional Mining training resource.

The natural rock family must be extensible because more rock types will be added later.

The target field progression is approximately:

```text
18,000 qualifying Mining activations
for Mining 0.0 -> 100.0
```

Use the existing UltimaCraft skill engine to provide this progression. Do not create a separate Mining XP/capability/persistence system.

## Approved metal progression

Use this hard Mining requirement baseline:

```text
Iron         0.0
Silver      55.0
Tin         65.0
Shadow Iron 70.0
Copper      75.0
Gold        85.0
Agapite     90.0
Verite      95.0
Valorite    99.0
GM Mining  100.0
```

UltimaCraft uses **Tin instead of Dull Copper**.

Do not add Dull Copper.

Silver is an important part of the UltimaCraft economy and must become a complete real mineable resource while reusing existing Silver economy/material identities.

## Bronze rule

Do not blindly add Bronze ore.

Classic UO treats Bronze as a mineable ore, but UltimaCraft uses Tin and may intentionally treat Bronze as a realistic Copper+Tin alloy.

During discovery, prove whether the current project treats Bronze as:

```text
mineable ore
crafted alloy
both
absent
```

Preserve the established model.

Never create contradictory ore and alloy production routes.

## Natural rock baseline

Audit the actual project and use the design document as the starting proposal for:

```text
Stone
Cobblestone
Limestone
Calcite
Diorite
Andesite
Granite
Tuff
Deepslate
Dripstone
Basalt
Smooth Basalt
Blackstone
Quartz-bearing rock
Obsidian
all custom UltimaCraft rocks
```

The architecture must be data-driven. New rocks should generally be added by definition/registration/tag rather than new break-event logic.

## Ultima Online research

Use these sources for behavioral context:

```text
https://github.com/runuo/runuo
https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Mining.cs
https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Core/HarvestResource.cs
https://www.uoguide.com/Mining
https://www.uoguide.com/Ingot
```

Important UO reference ladder:

```text
Iron         0
Dull Copper 65
Shadow Iron 70
Copper      75
Gold        85
Agapite     90
Verite      95
Valorite    99
```

Map Dull Copper's position to Tin.

RunUO's `HarvestResource` separates required skill from its minimum/maximum success/gain range. Preserve that conceptual separation when mapping resource access and skill difficulty into UltimaCraft's existing skill engine.

RunUO is GPL code. Treat it as behavioral reference material. Do not copy its source implementation into UltimaCraft unless project licensing explicitly allows it.

## Known project context to verify, not assume

Prior project work indicates that the economy has recognized raw/refined identities for:

```text
Tin
Copper
Iron
Silver
Gold
Shadow Iron
Agapite
Verite
Valorite
```

and stone commodity identities including:

```text
Cobblestone
Stone
Andesite
Diorite
Granite
Tuff
Basalt
Blackstone
Limestone
Quartz
```

Verify the current repository and Rails project rather than trusting old documentation.

Also inspect the raw project asset area:

```text
C:\projects\britannia\raw fiels
```

for existing ore/rock/Silver assets before creating anything.

Do not modify raw assets during discovery.

# Your task in this kickoff

Perform **Milestone 0 and Milestone 1 only** from `MINING_SKILL_MILESTONE_PLAYBOOK.md`.

Do not implement Mining progression yet.

Do not add blocks.

Do not add Silver yet.

Do not change skill gain rates.

Do not modify production code unless a tiny documentation/tooling-only change is explicitly required by Milestone 0/1.

## Milestone 0 work

Establish and report:

```text
repository root
current branch
current HEAD
git status
worktrees
remotes
toolchain versions
repository guidance files
relevant project design documents
test infrastructure
```

Preserve every unrelated dirty/untracked file.

No reset, clean, rebase, merge, push, or destructive operation.

## Milestone 1 work

Create a complete evidence-based discovery of:

### Existing Mining break flow

Trace the actual path from a player's attempt to mine a block through:

```text
break/interaction event
tool checks
protection checks
block mutation
loot
durability
skill award
restoration scheduling
restoration persistence
restoration execution
```

Identify the earliest safe server-authoritative place to deny a block break before any mutation.

### Restoration system

Find and document:

```text
classes
saved data
timers
queues
block-state preservation
duplicate prevention
chunk behavior
restart behavior
occupied-position behavior
replacement-block behavior
force-loading behavior
eligible blocks
natural/player-placed provenance
tests
```

### Skill engine

Trace the current skill architecture, including:

```text
Mining skill registration if present
SkillManager/current equivalent
server-authoritative lookup
Rails persistence
client synchronization
precision
skill cap
award API
gain formula
difficulty inputs
cooldowns
training/Guildmaster integration
loading/unavailable behavior
tests
```

Use current Farming or another completed skill as comparative evidence where useful, but verify current code.

### Complete mineable catalog

Inventory every actual natural rock and geological resource that could be governed by Mining.

For each one record:

```text
display name
registry ID
vanilla/custom
category
block class
drop item
refined item
material identity
tool/tier requirement
existing Mining requirement
worldgen source
vein/generator type
dimension/region/biome/depth
restoration eligibility/path
economy identity
smelting/refining
crafting consumers
model
texture
loot
tags
localization
tests
notes
```

At minimum explicitly search for:

```text
Iron
Silver
Tin
Shadow Iron
Copper
Bronze
Gold
Agapite
Verite
Valorite
Coal
Stone
Cobblestone
Limestone
Calcite
Diorite
Andesite
Granite
Tuff
Deepslate
Dripstone
Basalt
Smooth Basalt
Blackstone
Quartz
Obsidian
```

Do not rely on filenames alone. Follow registrations and runtime call chains.

### Silver gap analysis

Find every existing Silver representation.

Determine exactly what is present or missing among:

```text
ore block
raw ore item
ingot
material
model
texture
loot
tags
worldgen
restoration
smelting
recipes
economy commodity
trader integration
tests
```

Do not create the missing pieces yet.



Inspect blacksmithing, recipes, material definitions, economy data, and assets.

### Economy/material flow

Trace Mining outputs through the current Rails/economy/trader and blacksmithing/refining systems.

Detect naming or identity mismatches.

### Assets

Search repository resources and:

```text
C:\projects\britannia\raw fiels
```

for reusable Silver, rock, ore, ingot, and material assets.

Catalog findings only.

# Required discovery documents

Create the repository-consistent equivalents of:

```text
docs/mining/MINING_SKILL_DISCOVERY_REPORT.md
docs/mining/MINING_MINEABLE_MASTER_CATALOG.md
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
docs/mining/MINING_IMPLEMENTATION_LOG.md
```

The master catalog must be complete and machine-reviewable.

The gap analysis must classify each desired resource/system as:

```text
COMPLETE
PARTIAL
MISSING
DUPLICATE/CONFLICT
NOT APPLICABLE
OWNER/ARCHITECTURE DECISION REQUIRED
```

Use `UNKNOWN` rather than guessing.

# Important constraints

Do not:

- create a new skill persistence system;
- create a new block-restoration scheduler;
- change global skill gain;
- add Dull Copper;
- create Bronze ore without proving the material model;
- duplicate Silver economy identities;
- rewrite world generation;
- copy RunUO GPL source code;
- implement future UO features such as Mining gloves, sand mining, ore elementals, or Gargoyle Pickaxes;
- stage/commit/push unless the playbook explicitly instructs it and the owner has authorized that action.

# End-of-kickoff response

When Milestones 0 and 1 are complete, stop and provide a concise closeout containing:

```text
Milestone:
Status:
Repository root:
Branch:
HEAD:
Working tree state:

Mining break entry point:
Restoration architecture:
Skill architecture:
Current Mining skill support:
Mineable count:
Ore count:
Rock count:

Silver status:
Silver missing pieces:
Bronze model:
Tin status:
Missing UO/UltimaCraft materials:

Existing economy identities:
Worldgen systems found:
Restoration scope:
Player-placed exploit risk:

Files/documents created:
Files inspected:
Tests/commands run:
Production code changes:
Unknowns:
Actual blockers:
Recommended Milestone 2 approach:
```

Do not ask the owner questions that have already been answered in the design documents.

Only surface questions that cannot be resolved from the current repository and that materially block the next milestone.

Then stop.
