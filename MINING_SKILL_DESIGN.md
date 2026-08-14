# UltimaCraft Mining Skill Progression — Design Document

## 1. Purpose

This project adds a complete Mining skill progression layer to UltimaCraft's existing renewable Mining system.

The implementation must extend the systems that already exist in the repository rather than creating a parallel mining, skill, persistence, networking, ore-generation, economy, or block-restoration architecture.

The central gameplay loop is:

1. A player finds a natural rock or ore that is managed by the Mining system.
2. The server identifies the material and its Mining requirement.
3. The server reads the player's authoritative Mining skill from the existing skill system.
4. If the player is below the requirement, the break is denied before the block, drops, tool durability, restoration state, or skill state are mutated.
5. If the player meets the requirement, the normal Mining break proceeds.
6. The existing Mining skill engine receives exactly one qualifying Mining activation.
7. The existing renewable Mining/restoration system restores the block according to its established rules.
8. As Mining rises, additional natural rock families and increasingly valuable metals become available.

The intended progression is recognizably Ultima Online while still behaving naturally inside Minecraft and UltimaCraft.

The target progression time is approximately **18,000 qualifying Mining activations from 0.0 to 100.0 Mining** for a player progressing through normal field use. This is an estimated balance target, not a requirement to hard-code an activation counter.

---

## 2. Owner-approved decisions

These are project requirements and must not be reopened unless repository evidence reveals a genuine technical contradiction.

### 2.1 Silver

Silver is required.

Silver is an important UltimaCraft economic metal and must exist as a real mineable resource, not merely as an economy/catalog entry.

The repository must be audited for all existing Silver concepts before adding anything:

- raw Silver commodity;
- Silver ingot/refined metal;
- item registrations;
- ore blocks;
- textures/models;
- loot;
- smelting/refining;
- trader/vendor mappings;
- crafting/material registries;
- quality/material metadata;
- world generation;
- tests.

Do not duplicate an existing Silver item or economic identity merely because the mineable block is missing.

### 2.2 Tin replaces Dull Copper

UltimaCraft uses **Tin** in the progression position occupied by **Dull Copper** in classic Ultima Online.

The canonical UO Dull Copper Mining requirement of 65.0 therefore maps to:

> **Tin — Mining 65.0**

Do not add Dull Copper as a second resource unless the owner explicitly requests it in the future.

### 2.3 Natural rock family

Mining applies to the broader natural Minecraft/UltimaCraft rock family, not only ore blocks.

The architecture must be data-driven/extensible so new rocks can be added later without adding a new event handler or switch statement for every block.

Regular Stone is an intentional Mining training resource.

### 2.4 Hard skill gate

If a player does not meet a mineable material's Mining requirement, the block must not be broken.

The denial must occur authoritatively on the server before:

- block removal;
- loot;
- XP or secondary drops;
- tool durability consumption;
- Mining skill awards;
- restoration scheduling;
- economy hooks;
- statistics or advancements added by UltimaCraft;
- sounds/particles that communicate a successful Mining action.

Player-facing feedback should include the actual threshold, for example:

> You need 75.0 Mining to mine Copper. Current Mining: 72.4.

Use repository-standard localization/components rather than hard-coded English in gameplay Java.

### 2.5 Existing skill engine

The existing UltimaCraft skill system is authoritative.

Do not create a new player capability, attachment, save file, scoreboard, NBT skill field, or separate Mining progression engine.

The Mining implementation must discover and reuse the same authoritative skill/persistence/network model used by the current project, including `SkillManager` or its current successor.

### 2.6 Progression duration

The field-use target is approximately:

> **18,000 qualifying Mining activations for 0.0 → 100.0 Mining**

If the skill engine advances in 0.1-point awards, 100.0 skill contains 1,000 such awards. An 18,000-activation target would therefore correspond to an overall average of roughly one successful 0.1 gain per 18 qualifying activations, or about 5.56%, across the complete journey.

That arithmetic is a calibration aid only. Claude must inspect the real skill engine before deciding how Mining difficulty is expressed.

Do not change global skill-gain behavior for every skill merely to hit the Mining target.

---

## 3. Reference sources and authority

### 3.1 Ultima Online behavioral references

Primary gameplay/code reference:

- RunUO repository: https://github.com/runuo/runuo
- RunUO Mining implementation:
  https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Mining.cs
- RunUO HarvestResource:
  https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Core/HarvestResource.cs

Secondary gameplay reference:

- UOGuide Mining: https://www.uoguide.com/Mining
- UOGuide Ingot/metal progression: https://www.uoguide.com/Ingot

RunUO is a behavioral reference. It is GPL-licensed code.

**Do not copy RunUO implementation code into UltimaCraft merely for convenience.** Reimplement the gameplay concepts using UltimaCraft's own architecture unless the repository's licensing policy explicitly permits otherwise.

### 3.2 Repository authority

For UltimaCraft implementation details, the current checked-out repository is authoritative over this document.

Claude must inspect the current project rather than assuming historic class names still exist.

Expected project root from prior work:

```text
C:\projects\britannia\mod\Britannia_Mod
```

Relevant external/raw asset area that may contain reusable project assets:

```text
C:\projects\britannia\raw fiels
```

Do not modify external raw assets during discovery.

---

## 4. What RunUO/UO contributes to the design

RunUO's Mining resource model distinguishes:

```text
required skill
minimum skill
maximum skill
```

For the canonical metal resources, RunUO uses:

| UO resource | Required | Min | Max |
|---|---:|---:|---:|
| Iron | 0.0 | 0.0 | 100.0 |
| Dull Copper | 65.0 | 25.0 | 105.0 |
| Shadow Iron | 70.0 | 30.0 | 110.0 |
| Copper | 75.0 | 35.0 | 115.0 |
| Bronze | 80.0 | 40.0 | 120.0 |
| Gold | 85.0 | 45.0 | 125.0 |
| Agapite | 90.0 | 50.0 | 130.0 |
| Verite | 95.0 | 55.0 | 135.0 |
| Valorite | 99.0 | 59.0 | 139.0 |

The important design lesson is not that UltimaCraft must reproduce RunUO's success formula byte-for-byte.

The important lesson is:

> **The minimum skill required to access a resource and the difficulty/value of that resource for skill progression are separate concepts.**

UltimaCraft should preserve that separation.

A mineable definition should therefore be capable of expressing at least:

```text
material identity
required Mining skill
skill-gain difficulty / challenge input
resource category
restoration eligibility
drop/resource mapping
worldgen/provenance metadata where required
```

The exact Java/data shape must match the repository architecture discovered by Claude.

---

## 5. Existing UltimaCraft context that must be reconciled

Prior project artifacts already show the economy recognizing the following raw ores:

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

and matching refined metals for those materials.

Prior project artifacts also show stone commodities including:

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

These are clues, not substitutes for a repository audit.

The project must trace each identity through actual registrations and runtime systems.

A commodity existing in Rails does not prove that a Minecraft block, item, ingot, loot table, recipe, material definition, or world-generation path exists.

Likewise, a block existing in Minecraft does not prove that Rails knows how to value it.

---

## 6. Mining progression philosophy

### 6.1 UO structure, Minecraft interaction

The metal unlock ladder should retain the recognizable UO structure:

```text
Iron at the beginning
large early training span
colored/special metals clustered from the mid/high skill range
Valorite immediately before Grandmaster
```

Minecraft's physical terrain provides something UO's tile-based resource banks do not: many distinct natural rocks.

UltimaCraft should use those rocks to make the long early training span feel intentional rather than empty.

### 6.2 Realistic experience does not mean strict real-world metallurgy everywhere

The experience should feel believable:

- common rock is easy;
- denser/deeper/specialized rock requires more experience;
- valuable ore is progressively harder to extract;
- high-end fantasy metals are rare and skill-gated;
- Silver occupies a meaningful midgame economic tier;
- the player learns Mining by actually mining.

At the same time, UO's fantasy metal hierarchy is an intentional gameplay abstraction.

Do not reorder Copper, Gold, Verite, Valorite, etc. purely because real geology would place them differently.

### 6.3 Bronze conflict rule

Classic UO treats Bronze as a mineable colored ore at Mining 80.0.

UltimaCraft also uses Tin, which creates a realistic possibility that Bronze is intended to be produced as a Copper + Tin alloy.

Therefore:

> **Bronze is a repository-audit decision, not an automatic new ore.**

During discovery, Claude must determine whether current blacksmithing/material/crafting systems establish Bronze as:

1. a mineable material;
2. an alloy;
3. both;
4. absent.

Preferred behavior:

- If Bronze is already an alloy, preserve that model and do not create a contradictory Bronze ore.
- If Bronze is already a mineable UO-style material, preserve it at the UO 80.0 tier.
- If Bronze is entirely absent, report the existing material model and implement the path that best matches established project conventions.
- Never silently create both a mineable Bronze ore and a Copper+Tin Bronze production path.

---

## 7. Proposed hard Mining requirements

These are the project baseline unless discovery finds an existing approved requirement that conflicts.

### 7.1 Metal progression

| Material | Mining requirement | Design role |
|---|---:|---|
| Iron | 0.0 | Canonical UO starter metal |
| Silver | 55.0 | UltimaCraft economic bridge tier |
| Tin | 65.0 | Replaces UO Dull Copper |
| Shadow Iron | 70.0 | Canonical UO special metal |
| Copper | 75.0 | Canonical UO progression position |
| Bronze | 80.0 | Conditional; only if repository establishes mineable Bronze |
| Gold | 85.0 | Canonical UO progression |
| Agapite | 90.0 | Canonical UO progression |
| Verite | 95.0 | Canonical UO progression |
| Valorite | 99.0 | Canonical UO endgame metal |
| Grandmaster Mining | 100.0 | Cap/mastery point; future expansion hook |

### 7.2 Silver skill window

Silver is an UltimaCraft extension rather than a canonical colored UO ore.

For systems that require a RunUO-style challenge window, the initial recommended reference is:

```text
required = 55.0
min      = 15.0
max      = 95.0
```

This follows the same ±40 pattern used by the UO colored-metal progression.

Do not force these values into the skill engine if it uses a different abstraction. Translate the intent instead.

### 7.3 Non-metal resources

Coal and other existing non-metal geological resources must be discovered and classified.

Do not delete, hide, or accidentally ungate existing mineables because they are not part of the UO metal table.

---

## 8. Proposed natural rock progression

The implementation must use a data-driven family/definition model.

The following is the initial balance baseline for known or likely Minecraft/UltimaCraft rock types.

Claude must reconcile exact block availability, worldgen, economy identity, and current hardness/tool rules before finalizing.

| Rock/resource | Initial Mining requirement | Notes |
|---|---:|---|
| Stone | 0.0 | Primary universal training material |
| Naturally generated Cobblestone | 0.0 | Only earns skill when provenance/system rules allow |
| Limestone | 5.0 | Common/soft sedimentary progression |
| Calcite | 5.0 | If present and intended as a mineable resource |
| Diorite | 10.0 | Early rock variety |
| Andesite | 15.0 | Early rock variety |
| Granite | 20.0 | More experienced quarrying target |
| Tuff | 25.0 | Transitional/deeper rock |
| Deepslate | 30.0 | Deeper material |
| Dripstone Block | 35.0 | If part of normal mining progression |
| Basalt / Smooth Basalt | 40.0 | Advanced volcanic rock |
| Blackstone | 45.0 | Advanced/dangerous-region rock |
| Natural quartz-bearing rock | 50.0 | Only if an actual mineable form exists |
| Obsidian | 60.0 | Advanced extraction; do not override tool rules |
| Future rocks | data-driven | Must not require new mining architecture |

These values intentionally create meaningful progression before Tin at 65.0 while allowing Silver at 55.0 to become the first major economic midgame unlock.

### 8.1 Minecraft Granite versus UO High Quality Granite

Do not conflate these.

Minecraft/UltimaCraft `granite` is an ordinary natural rock and may be available at a low-to-mid Mining skill.

UO "High Quality Granite" is a special masonry resource associated with Grandmaster Mining and a learned ability.

If UltimaCraft later adds UO-style High Quality Granite as a special crafting resource, it should be represented as a distinct resource/quality concept rather than making the ordinary Minecraft Granite block require 100.0 Mining.

---

## 9. Skill-gain model

### 9.1 Qualifying Mining activation

A **qualifying Mining activation** is one successful, server-authorized Mining action against a block recognized by the Mining progression system.

The exact hook depends on the existing implementation, but one normal successful mineable block break should produce no more than one Mining activation.

The following do **not** count:

- insufficient-skill attempts;
- invalid-tool attempts;
- protected-region denials;
- canceled breaks;
- client-only predicted breaks;
- Creative/admin bypass actions;
- fake-player/automation actions unless a future owner-approved skill owner exists;
- command removal;
- world generation;
- restoration itself;
- explosion/piston/fluid destruction unless the project explicitly defines these as Mining.

### 9.2 Stone remains useful

Regular Stone must be a valid Mining skill-gain source.

The player must not become unable to train simply because a particular ore tier is scarce.

However, the existing skill engine should be configured so harder/more appropriate materials are generally a better progression path than repeatedly mining only the easiest resource.

Do not create a separate per-block XP bar.

### 9.3 18,000-activation calibration

Claude must first inspect how the existing skill engine calculates gains.

Then create a Mining-specific calibration/report that estimates expected activations for:

```text
0.0 -> 25.0
25.0 -> 50.0
50.0 -> 65.0
65.0 -> 80.0
80.0 -> 90.0
90.0 -> 99.0
99.0 -> 100.0
0.0 -> 100.0 total
```

Primary acceptance target:

```text
17,100 to 18,900 expected qualifying activations
```

for a reasonable natural progression route, which is ±5% around 18,000.

If the current engine cannot be tuned per skill without changing other skills, do not alter global rates blindly. Document the constraint and add the smallest Mining-specific difficulty input compatible with the existing engine.

### 9.4 Guildmaster training

If the current Service NPC/Guildmaster system already supports Mining training, reuse it.

Do not create a second trainer.

The 18,000-activation target describes field progression from 0.0 to 100.0 and does not require players who purchase legitimate trainer skill to still perform the full 18,000 actions.

---

## 10. Break authorization

### 10.1 Server authority

All hard gates must be decided from authoritative server state.

Never trust a client-supplied skill value.

The gate must resolve:

```text
actor
mineable definition
current Mining skill
required Mining skill
game mode/admin policy
tool validity
protection/region validity
current block state/provenance
```

before the success mutation path.

### 10.2 Inclusive threshold

Eligibility is inclusive:

```text
current Mining >= required Mining
```

Test exact decimal boundaries.

### 10.3 Denied break invariants

An insufficient-skill attempt must leave:

```text
same block
same block state
same block entity, if any
same inventory
same tool durability
zero drops
zero skill award
zero restoration record/timer
zero successful-mining economy side effects
```

### 10.4 Creative/admin policy

For consistency with existing UltimaCraft skill-gated systems, the preferred policy is:

- Creative players may bypass the Mining threshold.
- Real server operators with the repository-standard permission level may bypass the threshold.
- Bypass does not grant Mining skill.
- All other non-skill restrictions still apply unless the existing admin system explicitly bypasses them.
- Fake players/automation do not inherit a human player's Mining skill by default.

Claude must confirm current project conventions before implementation.

---

## 11. Tool interaction

Mining skill and Minecraft tool requirements are separate gates.

Meeting Mining 99.0 must not let a player punch Valorite or bypass a required pickaxe tier.

Likewise, possessing a strong pickaxe must not bypass Mining skill.

The final break decision is conceptually:

```text
recognized mineable
AND valid actor
AND allowed by protection/world rules
AND valid tool/tool tier
AND Mining skill >= material requirement
```

Use the repository's current ordering where it has side-effect or compatibility significance.

Do not globally rewrite vanilla mining speed or hardness as part of this project unless required for an existing custom ore.

---

## 12. Renewable block restoration

UltimaCraft already restores mined resource blocks.

This project must integrate with that system rather than replacing it with RunUO resource banks.

### 12.1 Required behavior

For a successful authorized Mining break:

1. preserve whatever original block/state data the existing restoration system requires;
2. allow exactly one normal resource/drop transaction;
3. register exactly one restoration operation;
4. restore according to the existing timing/conflict policy;
5. preserve world-save/restart behavior already guaranteed by the Mining system.

For an insufficient-skill attempt:

```text
do not register restoration
```

because the block never left the world.

### 12.2 Rock restoration scope

Do not blindly make every Stone block in every dimension globally renewable before auditing the current Mining/restoration design.

Claude must determine:

- how a block becomes managed by the renewable Mining system;
- whether provenance is worldgen-based, tag-based, region-based, saved-data-based, or implicit;
- whether player-placed blocks are distinguishable;
- how restoration avoids overwriting players, entities, containers, buildings, or later player changes;
- whether restoration waits when the target cell is occupied;
- whether chunks are force-loaded;
- how duplicate restoration records are prevented.

Extend the established model to the natural rock family.

If the existing architecture intentionally restores all recognized mineables globally, preserve that behavior.

If it only restores managed/world-generated nodes, keep that boundary.

### 12.3 RunUO respawn timing

RunUO uses replenishing resource banks with roughly 10–20 minute respawn windows.

That is useful gameplay context but **not** a mandate to replace UltimaCraft's existing restoration timer.

Preserve the current UltimaCraft cadence unless the owner separately approves a balance change.

---

## 13. Player-placed block and exploit policy

The project must explicitly test the place-break loop.

A player must not be able to manufacture unlimited fast Mining gains by:

```text
place Stone
break Stone
place same Stone
break again
```

unless the current Mining design intentionally considers player-created renewable mining nodes valid.

Preferred behavior is that skill gains come from natural or Mining-managed renewable resources.

Use the existing provenance/restoration model if it already solves this.

Do not invent a large new global block-provenance database without first proving it is necessary.

---

## 14. Silver implementation requirements

If the Silver mineable is absent, complete it vertically across all systems that need it.

Audit and add only missing pieces among:

```text
silver ore block
deepslate/custom variant if project uses variants
raw silver drop/item
silver ingot/refined item
block/item registrations
creative tab
blockstate/model/item model
texture
loot table
tags
pickaxe/tool tier tags
smelting/refining
material metadata
world generation
renewable restoration
Mining definition
economy/trader mapping
recipes/blacksmithing integrations
localization
tests
```

### 14.1 Silver world-generation intent

Silver should feel like a valuable midgame vein metal.

Preferred geological presentation, if compatible with the existing ore-generation framework:

```text
narrow vertical or vertically layered veins
rarer than common Iron
more accessible/common than endgame fantasy metals
```

Reuse an existing vein generator/composition if possible.

Do not introduce a bespoke worldgen engine only for Silver.

The final generator, depth, abundance, and biome/region rules must be based on the current ore system and existing world balance.

---

## 15. Missing material gap analysis

Claude must produce a complete mineable catalog before adding content.

For every candidate resource, record:

```text
display name
registry ID
vanilla/custom
resource category
block class
item/drop ID
ingot/refined ID if applicable
material/quality identity
current hard tool tier
Mining requirement
skill-gain difficulty input
worldgen source
vein/generator type
generation dimensions/regions/biomes
restoration eligibility
restoration source
economy commodity identity
trader/vendor integration
smelting/refining path
crafting consumers
texture/model assets
localization
tests
notes
```

Use `UNKNOWN` when not proven.

### 15.1 Required metal audit

At minimum audit:

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
Coal and other existing geological resources
```

### 15.2 Required rock audit

At minimum audit all existing/reachable forms of:

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
Quartz-bearing natural blocks
Obsidian
any UltimaCraft custom rock
```

Also inspect actual tags/registries for additional modded and vanilla natural rocks.

---

## 16. Economy integration

Mining is a supply-side gameplay system and must not create a second economy authority.

Where current resource drops already map into Rails/Trader commodity systems, preserve those mappings.

Known prior economy context includes raw/refined identities for:

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

Silver should reuse its established economic identity rather than creating `silver_ore_2`, `mined_silver`, or another duplicate commodity.

If a new material such as Bronze must be added, update economy data only if the final gameplay/material model actually requires it.

Do not assign arbitrary new prices without deriving them from existing economy conventions or obtaining owner approval.

---

## 17. Smelting and crafting boundary

UO uses Mining skill during ore refining/smelting and uses the same colored-metal requirements as ore acquisition.

That is useful parity context.

For this project:

1. audit existing smelting/refining and blacksmithing;
2. determine whether Mining skill already participates;
3. preserve existing behavior;
4. ensure newly added Silver can flow through the established refining/crafting pipeline;
5. do not redesign Blacksmithing as part of Mining.

If no Mining requirement currently exists on smelting, document a future parity hook rather than silently expanding scope unless the existing architecture clearly expects the same skill gate.

---

## 18. Future UO parity hooks

These are not required for the initial Mining progression unless already implemented:

- High Quality Granite at Grandmaster Mining;
- Sand mining;
- Mining gloves above 100 skill;
- Gargoyle pickaxe ore-tier mutation;
- ore elementals;
- UO-style random resource-bank mutation;
- mounted-mining restrictions;
- Blacksmith Bulk Order reward interactions.

The architecture should not make these impossible.

Do not implement them merely because RunUO contains them.

---

## 19. Data-driven architecture

Avoid a giant event-handler switch such as:

```java
if (block == IRON_ORE) ...
else if (block == TIN_ORE) ...
else if (block == VERITE_ORE) ...
```

Prefer the project's existing definition/registry/tag pattern.

The Mining definition layer should be capable of answering:

```text
Is this block governed by Mining?
What resource is it?
What skill is required?
What challenge/difficulty does it provide?
What restoration policy applies?
What economic/material identity does it resolve to?
```

Future rocks should usually require data/definition registration, not a new gameplay subsystem.

---

## 20. Compatibility and safety

Mining changes touch fundamental block breaking and must be narrow.

Do not regress:

- Adventure/Survival/Creative behavior;
- region or spawn protection;
- claims/protected structures;
- custom tool durability;
- Fortune/Silk Touch behavior where intentionally supported;
- custom loot;
- XP drops;
- block entities;
- worldgen;
- existing restoration;
- chunk save/load;
- dedicated server startup;
- skill sync;
- Rails skill persistence;
- NPC skill training;
- economy/traders;
- blacksmithing/refining;
- existing worlds.

Never force-load chunks merely to restore a mining block unless the current restoration system already does so intentionally.

---

## 21. Required automated testing

At minimum cover:

### 21.1 Threshold boundaries

For every metal tier and representative rock tiers:

```text
requirement - 0.1 -> denied
requirement       -> allowed
requirement + 0.1 -> allowed
```

Use the actual skill precision supported by the repository.

### 21.2 Side-effect-free denial

Prove denied mining produces:

```text
0 block mutation
0 drops
0 durability damage
0 skill gains
0 restoration schedules
0 success sounds/particles from UltimaCraft
```

### 21.3 Successful transaction

Prove a valid mine produces:

```text
1 block break
expected drop transaction
1 Mining activation maximum
1 restoration record maximum
expected tool durability
correct restored block/state later
```

### 21.4 Restoration

Test:

- restart before restoration;
- chunk unload/reload;
- occupied restoration position;
- player-placed replacement at restoration time;
- duplicate scheduling;
- multiple players mining neighboring nodes;
- simultaneous break attempts;
- high latency if testable;
- world save.

### 21.5 Skill authority

Test:

- low/high players on same server;
- skill gain crossing an unlock threshold;
- reconnect;
- respawn;
- dimension change;
- server restart;
- skill service not loaded/loading/unavailable according to established policy;
- no client-authoritative override.

### 21.6 Modes and actors

Test:

- Survival;
- Adventure where Mining is allowed;
- Creative;
- operator/admin;
- fake player;
- automation;
- command removal;
- explosion/piston behavior where applicable.

### 21.7 Progression calibration

Add a deterministic expected-value calculator or seeded simulation based on the real Mining skill-gain path.

Report expected activations per band and total.

Do not make a flaky Monte Carlo test the only balance proof.

---

## 22. Runtime/manual QA

Perform at least one real dedicated-server or integrated multiplayer validation with two players:

- Player A below an ore threshold.
- Player B at/above the threshold.
- Both target the same material type in separate nodes.
- A cannot break it and receives correct feedback.
- B breaks it and receives the correct drop.
- B receives at most one Mining activation.
- The block restores once.
- A can mine it later after authoritative skill gain crosses the threshold.
- No client restart is required for the new skill value to take effect.

Manually validate visual assets for any newly added Silver or missing material.

---

## 23. Deliverables produced by implementation

The Mining project should ultimately leave repository documentation including:

```text
MINING_SKILL_DISCOVERY_REPORT.md
MINING_MINEABLE_MASTER_CATALOG.md
MINING_PROGRESSION_GAP_ANALYSIS.md
MINING_SKILL_IMPLEMENTATION_STATUS.md
MINING_SKILL_TEST_MATRIX.md
```

Use current repository documentation conventions if equivalent files already exist.

---

## 24. Definition of done

The project is complete only when:

- the complete mineable catalog has been audited;
- Silver is truly mineable and connected to its existing economy identity;
- Tin occupies the Dull Copper-equivalent 65.0 Mining tier;
- the UO-derived special-metal ladder is enforced;
- Bronze has one coherent, evidence-based production model;
- the natural rock family is data-driven and extensible;
- Stone reliably trains Mining;
- under-skilled players cannot break gated material blocks;
- denied breaks are side-effect free;
- successful breaks use the existing restoration system;
- Mining gains use the existing skill engine;
- normal progression estimates approximately 18,000 activations to 100.0;
- worldgen and restoration remain stable;
- economy/crafting integrations do not duplicate material identities;
- dedicated server, save/reload, multiplayer, and boundary tests pass;
- no unrelated skill or block-breaking behavior regresses.
