# UltimaCraft Mining Skill Progression — Milestone Playbook

## Project objective

Implement a server-authoritative Mining skill progression system on top of UltimaCraft's existing Mining, renewable block-restoration, world-generation, skill, economy, and crafting systems.

The project must:

- gate mineable rocks and ores by Mining skill;
- keep under-skilled blocks physically intact;
- use regular Stone and other natural rocks for skill progression;
- preserve the UO special-metal ladder;
- use Tin instead of Dull Copper;
- add a real Silver mining path at Mining 55.0;
- target roughly 18,000 qualifying field Mining activations for 0.0 → 100.0;
- fill actual content/system gaps discovered in the repository;
- avoid duplicate systems and duplicate material identities.

Read first:

```text
MINING_SKILL_DESIGN.md
```

Reference:

```text
https://github.com/runuo/runuo
https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Mining.cs
https://github.com/runuo/runuo/blob/master/Scripts/Engines/Harvest/Core/HarvestResource.cs
https://www.uoguide.com/Mining
https://www.uoguide.com/Ingot
```

RunUO is behavioral reference material. Do not copy GPL implementation code into UltimaCraft unless repository licensing explicitly permits it.

---

# Global execution rules

## A. Repository first

Never implement from this playbook's guesses when the current repository can answer the question.

Trace actual call chains, registrations, tags, definitions, persistence, networking, assets, tests, and worldgen.

## B. Preserve existing architecture

Do not create parallel systems for:

- skill storage;
- skill persistence;
- client skill synchronization;
- Mining block restoration;
- ore generation;
- economy;
- trader supply;
- material metadata;
- smelting;
- block protection.

## C. Server authority

The client never decides Mining eligibility or awards skill.

## D. Side-effect-free denial

An insufficient Mining skill denial must happen before:

```text
block mutation
loot
durability
Mining award
restoration scheduling
success effects
```

## E. One milestone at a time

Complete the milestone, run its required checks, update its documentation, report, and stop.

Do not automatically continue into the next milestone unless explicitly instructed.

## F. No destructive Git behavior

Do not:

- hard reset;
- discard unrelated changes;
- clean untracked project work;
- rewrite history;
- delete worktrees;
- merge;
- push;
- force push;
- deploy.

Preserve unrelated dirty files.

## G. No global balance collateral

Do not change the global skill gain formula for every skill to make Mining hit 18,000 activations.

## H. Asset discipline

Before creating a new texture/model/item identity, search:

```text
repository assets/resources
C:\projects\britannia\raw fiels
```

Reuse established assets and conventions where appropriate.

Do not overwrite raw source assets during discovery.

---

# Approved progression baseline

## Metal hard requirements

```text
Iron         0.0
Silver      55.0
Tin         65.0   # UltimaCraft replacement for UO Dull Copper
Shadow Iron 70.0
Copper      75.0
Gold        85.0
Agapite     90.0
Verite      95.0
Valorite    99.0
GM Mining  100.0
```

## Initial rock hard-requirement proposal

```text
Stone                    0.0
natural Cobblestone      0.0
Limestone                5.0
Calcite                   5.0
Diorite                  10.0
Andesite                 15.0
Granite                  20.0
Tuff                     25.0
Deepslate                30.0
Dripstone Block          35.0
Basalt / Smooth Basalt   40.0
Blackstone               45.0
natural quartz resource  50.0
Obsidian                 60.0
```

The catalog milestone may remove entries that do not exist or are intentionally inaccessible, but it must not silently change the approved metal ladder.

---

# Milestone 0 — Preflight, safety, and source authority

## Goal

Establish a safe, reproducible baseline without modifying production code.

## Tasks

1. Confirm the actual repository root.
2. Read repository guidance:
   - `CLAUDE.md`;
   - `AGENTS.md`;
   - `.claude/`;
   - root design/playbook docs;
   - relevant mining/worldgen/skill/economy docs.
3. Record:
   ```bash
   git status --short
   git branch --show-current
   git rev-parse HEAD
   git worktree list --porcelain
   git remote -v
   ```
4. Identify dirty/untracked files and mark them as pre-existing.
5. Do not reset or clean them.
6. Record Minecraft, NeoForge, Java, GeckoLib, Gradle, and relevant dependency versions from the repository.
7. Review the supplied RunUO and UOGuide references.
8. Record source-authority order:
   1. owner decisions in `MINING_SKILL_DESIGN.md`;
   2. current repository behavior/data;
   3. current project documentation;
   4. RunUO/UOGuide behavioral reference;
   5. implementation inference.
9. Confirm the existing test commands and GameTest infrastructure.
10. Create/update a Mining implementation log with the baseline.

## Deliverable

```text
docs/mining/MINING_IMPLEMENTATION_LOG.md
```

or the repository-consistent equivalent.

## Acceptance criteria

- Current branch/HEAD recorded.
- Dirty work preserved.
- Repository guidance read.
- Toolchain recorded.
- RunUO references reviewed.
- No production code changed.

## Stop condition

Stop and report Milestone 0.

---

# Milestone 1 — Complete Mining and mineable-system discovery

## Goal

Produce a repository-grounded map of every system Mining will touch.

This milestone is discovery only.

## 1.1 Existing Mining flow

Trace end to end:

```text
player left-click/break
NeoForge/Minecraft break event or block callback
tool validation
protection/region checks
custom ore handling
loot
tool durability
skill award
block restoration registration
restoration persistence
restoration execution
world save/reload
```

Find the earliest safe server-authoritative location to deny a break before mutation.

Do not assume a single hook covers Survival, Adventure, custom blocks, and automation. Prove the routes.

## 1.2 Existing restoration system

Document:

- classes;
- persistent saved data;
- block-position keys;
- stored block states;
- timers;
- queue/scheduler;
- chunk behavior;
- conflict policy;
- player/vehicle/entity occupancy behavior;
- replacement-block behavior;
- server restart behavior;
- duplicate suppression;
- force-loading behavior;
- which blocks are currently eligible;
- how natural/player-placed provenance is determined;
- existing tests.

## 1.3 Existing skill system

Trace Mining/skill infrastructure:

- Mining skill ID if already registered;
- skill registry/definition;
- `SkillManager` or current equivalent;
- authoritative server lookup;
- Rails persistence;
- login loading states;
- client sync;
- precision;
- caps;
- skill gain/award API;
- difficulty/challenge inputs;
- cooldowns/anti-spam;
- events;
- trainer/Guildmaster integration;
- tests.

Compare with a mature existing skill such as Farming, but do not copy assumptions without tracing current code.

## 1.4 Mineable catalog

Inventory every registered/reachable mineable natural rock and geological resource.

Required audit fields:

```text
display_name
registry_id
vanilla_or_custom
category
block_class
drop_item
refined_item
material_identity
hard_tool_requirement
existing_mining_requirement
worldgen_source
vein_generator
dimensions
regions
biomes
depth
rarity/placement
restoration_eligible
restoration_path
economy_identity
smelting_path
crafting_consumers
model
texture
loot_table
tags
localization
tests
notes
```

At minimum inspect:

```text
Iron
Silver
Tin
Shadow Iron
Copper
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
all other custom rock/ore registrations
```

## 1.5 Economy audit

Trace raw and refined resource identities in Rails and Minecraft.

Known prior economic identities include:

```text
raw: Tin, Copper, Iron, Silver, Gold, Shadow Iron, Agapite, Verite, Valorite
refined: same set
stone: Cobblestone, Stone, Andesite, Diorite, Granite, Tuff, Basalt,
       Blackstone, Limestone, Quartz
```

Prove actual current state.

Identify missing commodity mappings and duplicates.

Do not add prices in discovery.

## 1.6 Blacksmithing/smelting audit

Determine:

- whether raw ore exists as items;
- whether blocks drop raw ore or themselves;
- refining/smelting recipes;
- which skill controls refining;
- whether Tin is consumed to make Bronze;
- how material metadata reaches tools/armor;
- whether Silver is already craftable/refinable;
- whether any missing material would break crafting tables.

## 1.7 Asset audit

Search both:

```text
C:\projects\britannia\mod\Britannia_Mod
C:\projects\britannia\raw fiels
```

for Silver and any missing UO metals/rocks.

Catalog:

- textures;
- item textures;
- block textures;
- Blockbench/JSON models;
- generated assets;
- source art;
- naming conventions.

Do not create or modify art in this milestone.

## 1.8 Deliverables

Create:

```text
docs/mining/MINING_SKILL_DISCOVERY_REPORT.md
docs/mining/MINING_MINEABLE_MASTER_CATALOG.md
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
```

The gap analysis must classify each desired resource as:

```text
COMPLETE
PARTIAL
MISSING
DUPLICATE/CONFLICT
NOT APPLICABLE
OWNER/ARCHITECTURE DECISION REQUIRED
```

## 1.9 Acceptance criteria

- Every actual mineable registration path traced.
- Existing Mining restoration traced.
- Skill award path traced.
- Economy identities traced.
- Silver gaps enumerated.
- Natural rock set enumerated.
- No production implementation made.

## Hard stop

Report all findings and stop.

Do not proceed to Milestone 2 automatically.

---

# Milestone 2 — Progression definition and Mining policy layer

## Goal

Convert the approved progression into repository-native definitions without yet changing actual block-breaking behavior.

## Tasks

1. Choose the smallest existing definition/tag/registry pattern that can express Mining policy.
2. Avoid giant block-ID switch statements.
3. Add or extend definitions for:
   - recognized mineable;
   - category;
   - hard Mining requirement;
   - skill-gain difficulty/challenge;
   - restoration relationship;
   - resource/material identity.
4. Encode approved metal requirements exactly.
5. Encode the rock baseline, adjusted only where discovery proves an entry does not exist or conflicts with established design.
6. Add Silver at 55.0.
7. Encode Tin as the Dull Copper analogue at 65.0.
9. Ensure new future rocks can be registered without changing core Mining logic.
10. Add validation for:
    - duplicate definitions;
    - out-of-range skills;
    - missing resource IDs;
    - impossible category combinations;
    - unresolved tags.
11. Add localization keys for generic Mining requirement messaging, but do not wire break denial yet.

## Tests

- Exact progression values.
- Duplicate/invalid definition detection.
- Every discovered managed mineable resolves exactly once.
- Non-mineable blocks resolve `NOT_APPLICABLE`.
- Future definition extensibility.

## Deliverables

Update:

```text
MINING_MINEABLE_MASTER_CATALOG.md
MINING_PROGRESSION_GAP_ANALYSIS.md
MINING_IMPLEMENTATION_LOG.md
```

## Stop condition

Stop and report Milestone 2.

---

# Milestone 3 — Server-authoritative Mining break gate

## Goal

Prevent under-skilled players from breaking gated rocks and ores.

## Tasks

1. Integrate the policy at the earliest safe server-authoritative break point discovered in Milestone 1.
2. Read current Mining skill from the existing server skill system on every relevant attempt.
3. Never trust a client-supplied skill.
4. Eligibility:
   ```text
   current Mining >= required Mining
   ```
5. On insufficient skill:
   - cancel break;
   - preserve block/state;
   - produce zero drops;
   - consume zero durability;
   - award zero Mining skill;
   - schedule zero restoration;
   - emit only denied feedback;
   - do not emit successful break effects from UltimaCraft.
6. Feedback includes:
   - material display name;
   - current skill;
   - required skill.
7. Preserve existing protection/tool ordering where required.
8. Creative/operator:
   - follow existing skill-gate policy;
   - bypass threshold only;
   - receive no Mining gain from bypass.
9. Fake players/automation:
   - follow current skill ownership policy;
   - default deny if no authoritative human skill owner exists.
10. Commands/worldgen/restoration must not accidentally traverse the player Mining gate.

## Tests

For every metal tier plus representative rocks:

```text
requirement - 0.1
requirement
requirement + 0.1
```

Assert side effects.

Test two players with different Mining skills.

## Stop condition

Stop and report Milestone 3.

---

# Milestone 4 — Mining skill awards and 18,000-activation calibration

## Goal

Connect successful Mining to the existing skill engine exactly once and calibrate progression.

## Tasks

1. Identify the repository's canonical skill-award API.
2. Add a Mining-specific award call after an authorized Mining action reaches the correct success boundary.
3. Never award on:
   - denied break;
   - invalid tool;
   - protection denial;
   - fake client prediction;
   - restoration;
   - Creative/admin bypass;
   - command/worldgen destruction.
4. Ensure one block break cannot award multiple Mining activations because multiple callbacks fire.
5. Feed mineable challenge/difficulty through the existing skill engine.
6. Regular Stone must remain a valid training source.
7. Higher difficulty resources should form the natural progression path.
8. Build a deterministic expected-value calculator or seeded simulation that uses the same production gain logic or directly derives from it.
9. Report expected actions per progression band.
10. Calibrate Mining-specific parameters toward:
    ```text
    17,100–18,900 total qualifying activations from 0.0 to 100.0
    ```
11. Do not modify global skill probabilities shared by unrelated skills without explicit owner approval.
12. Verify Guildmaster training still works if Mining is trainable.

## Required report

```text
docs/mining/MINING_SKILL_CALIBRATION_REPORT.md
```

Include:

- gain unit;
- chance formula;
- material difficulty inputs;
- expected actions by band;
- total;
- stone-only estimate;
- recommended-path estimate;
- effect of legitimate NPC training where relevant.

## Stop condition

Stop and report Milestone 4.

---

# Milestone 5 — Silver vertical slice

## Goal

Make Silver a complete real Mining resource connected to the existing economy.

## Tasks

Using the Milestone 1 gap report, add only missing Silver pieces.

Possible scope includes:

```text
block
item/raw ore
ingot
registrations
models
textures
loot
tags
tool requirements
worldgen
restoration
smelting
material metadata
recipes
economy/trader mapping
localization
tests
```

Do not duplicate already-existing raw/refined Silver identities.

## Worldgen

Prefer an existing narrow vertical or vertically layered vein implementation if compatible.

Silver should be:

- Mining 55.0;
- valuable but not endgame;
- rarer than common starter ore;
- more available than top fantasy metals;
- consistent with current worldgen depth/region conventions.

Do not replace the ore-generation framework.

## Economy

Reuse the existing Silver commodity identity.

Do not invent a second Silver commodity or arbitrary price.

## Tests

- generation/placement;
- Mining 54.9 denial;
- Mining 55.0 success;
- correct drop;
- correct refining;
- correct material identity;
- restoration;
- economy mapping;
- save/load.

## Stop condition

Stop and report Milestone 5.

---

# Milestone 6 — Missing ore and rock gap completion

## Goal

Fill actual content gaps identified in Milestone 1.

## Tasks

For every `PARTIAL` or `MISSING` desired mineable:

1. prove it is required by the approved progression;
2. prove no equivalent already exists;
3. add the smallest missing vertical slice;
4. preserve existing naming/material conventions;
5. reuse project asset conventions;
6. add registrations, resources, loot, worldgen, restoration, economy, and refining only where applicable.

Special rules:

### Tin

Must occupy Mining 65.0.

Do not add Dull Copper.


### Shadow Iron, Agapite, Verite, Valorite

Preserve canonical requirements:

```text
70.0
90.0
95.0
99.0
```

### Rocks

Extend the definition system to all confirmed natural rocks.

Do not treat decorative crafted variants such as polished stairs/slabs as natural training resources unless the repository explicitly defines them as mineable resource nodes.

## Stop condition

Stop and report Milestone 6.

---

# Milestone 7 — Renewable restoration integration

## Goal

Prove all skill-managed natural resource nodes cooperate with the existing Mining restoration system.

## Tasks

1. Add missing resource types to the existing restoration eligibility mechanism.
2. Do not create a second scheduler.
3. Ensure insufficient-skill denial never schedules restoration.
4. Ensure a successful break schedules at most one restore.
5. Verify original block state/resource type returns correctly.
6. Verify skill thresholds after restoration are unchanged.
7. Verify:
   - chunk unload;
   - server restart;
   - target occupied by player/entity;
   - target replaced with another block;
   - duplicate schedule;
   - neighboring simultaneous mines;
   - two players;
   - old save compatibility.
8. Explicitly validate Stone/rock restoration scope.
9. Prevent place-break skill exploits using the existing provenance model.
10. Do not introduce global force-loading.

## Stop condition

Stop and report Milestone 7.

---

# Milestone 8 — Economy, refining, crafting, and material compatibility

## Goal

Ensure mined resources flow into existing UltimaCraft gameplay without identity duplication.

## Tasks

1. Audit all final raw ore identities against Rails commodities.
2. Audit refined metals.
3. Ensure Trader/economy supply receives expected resource IDs.
4. Ensure Silver participates in existing commodity/refining/crafting paths.

6. Validate blacksmithing/material metadata.
7. Preserve existing recipes and material variants.
8. Do not redesign product pricing.
9. Do not invent new commodity prices without existing formula/data authority.
10. Document UO smelting parity:
    - if Mining already gates/refines colored ore, align thresholds;
    - otherwise record as deferred future parity unless local architecture clearly calls for it.

## Stop condition

Stop and report Milestone 8.

---

# Milestone 9 — Feedback, UI synchronization, and administration

## Goal

Make Mining progression understandable without moving authority to the client.

## Tasks

1. Add localized insufficient-skill messages.
2. Include current and required Mining values.
3. Ensure skill gains crossing a threshold take effect immediately.
4. Verify no reconnect is required.
5. If client UI displays Mining skill, reuse existing sync.
6. Do not store mineable eligibility on ItemStacks.
7. Confirm Creative/operator behavior.
8. Confirm debug/admin tooling can inspect:
   - player's Mining skill;
   - block Mining requirement;
   - resolved resource;
   - restoration status;
   without changing normal gameplay.
9. Avoid chat spam during held-click repeated denial. Reuse existing cooldown/action-bar conventions if available.

## Stop condition

Stop and report Milestone 9.

---

# Milestone 10 — Automated regression, runtime QA, and closeout

## Goal

Prove the feature as an integrated system.

## Automated suites

Run the repository's actual required equivalents of:

```text
unit/JUnit tests
compileJava
build
GameTests
dedicated server/common bootstrap
data/resource validation
git diff --check
```

Do not invent command names if Gradle tasks differ.

## Mandatory GameTest/integration scenarios

### Skill gates

- Stone at 0.
- Silver 54.9/55.0.
- Tin 64.9/65.0.
- Shadow Iron 69.9/70.0.
- Copper 74.9/75.0.
- Gold 84.9/85.0.
- Agapite 89.9/90.0.
- Verite 94.9/95.0.
- Valorite 98.9/99.0.
- Exact threshold inclusivity.

### Side effects

Denied break:

```text
block unchanged
drop count 0
durability delta 0
Mining award 0
restore record delta 0
```

Successful break:

```text
one expected break
one expected loot transaction
at most one Mining activation
at most one restore registration
correct eventual restoration
```

### Persistence

- restart with pending restore;
- chunk unload/reload;
- Mining skill reload;
- reconnect;
- dimension change.

### Multiplayer

Use two clients/players with different Mining skills.

### Worldgen/assets

Validate newly added blocks actually appear where expected and have no missing-model/texture errors.

### Economy/crafting

Validate resource identities through their established production chain.

## Live/manual acceptance run

Perform a fresh runtime test with:

```text
LowMiner
HighMiner
```

Suggested sequence:

1. Set LowMiner immediately below Silver 55.
2. Set HighMiner to 55.
3. Attempt separate Silver nodes.
4. LowMiner must fail without block mutation.
5. HighMiner must mine successfully.
6. Verify one drop/award/restore.
7. Raise LowMiner to 55 through authoritative skill data.
8. Retry without reconnecting.
9. LowMiner now succeeds.
10. Repeat representative checks at Tin, Gold, Verite, and Valorite.
11. Verify regular Stone awards Mining.
12. Verify restoration after restart.

## Progression validation

Regenerate the skill calibration report using final production values.

Final recommended route must estimate approximately:

```text
18,000 activations ±5%
```

If it misses the target, fix Mining-specific balance inputs and rerun.

## Final documentation

Finalize:

```text
docs/mining/MINING_SKILL_DISCOVERY_REPORT.md
docs/mining/MINING_MINEABLE_MASTER_CATALOG.md
docs/mining/MINING_PROGRESSION_GAP_ANALYSIS.md
docs/mining/MINING_SKILL_CALIBRATION_REPORT.md
docs/mining/MINING_SKILL_IMPLEMENTATION_STATUS.md
docs/mining/MINING_SKILL_TEST_MATRIX.md
docs/mining/MINING_IMPLEMENTATION_LOG.md
```

## Final acceptance criteria

- Silver complete.
- Tin at 65.
- UO metal thresholds enforced.
- Rock family extensible.
- Stone grants Mining progression.
- Under-skilled blocks do not break.
- Denial side-effect free.
- Skill engine reused.
- Restoration reused.
- 18k target met within tolerance.
- Economy/crafting identities coherent.
- Dedicated server passes.
- Multiplayer passes.
- Save/restart passes.
- No unrelated regressions.

## Stop condition

Report final status, evidence, known limitations, and suggested commit message.

Do not push, merge, release, or deploy unless explicitly instructed.

---

# Standard milestone closeout format

End every milestone with:

```text
Milestone:
Status:

Repository root:
Branch:
HEAD before:
HEAD after:
Working tree before:
Working tree after:

Owner decisions applied:
Guidance reviewed:
Files inspected:
Files added:
Files modified:
Files removed:
Unrelated changes preserved:

Mineables audited:
Skill architecture findings:
Restoration architecture findings:
Economy findings:
Silver finding:

Tests/commands run:
Automated results:
GameTest result:
Dedicated-server result:
Client result:
Multiplayer result:

Progression values changed:
Expected activation count:
Side-effect-free denial proven:
Server authority proven:
Restoration proven:

Defects found:
Corrections made:
Risks:
Known limitations:
Deferred work:
Owner questions genuinely blocking next milestone:

Suggested commit message:
```

Do not claim a test passed unless it was actually run and its output was observed.
