# UltimaCraft New Assets Project — Codex Milestone Playbook

## Project Identity

Project: UltimaCraft New Assets Import and Gameplay Expansion

Base branch: `patch-18`

Feature branch: `new-assets`

Primary repository working copy that must remain available to other agents:

`C:\projects\britannia\britannia_mod`

Required isolated worktree:

`C:\projects\britannia\new-assets`

Raw asset intake folder:

`C:\projects\britannia\raw fiels\models to import`

Authoritative project documents:

- `UltimaCraft_New_Assets_Design_Document.md`
- `UltimaCraft_New_Assets_Codex_Playbook.md`

---

# Global Codex Rules

## A. Worktree safety

Before editing:

1. Inspect `C:\projects\britannia\britannia_mod`.
2. Confirm the base branch `patch-18` exists.
3. Confirm whether branch `new-assets` already exists.
4. Confirm whether `C:\projects\britannia\new-assets` already exists.
5. Never delete an existing worktree or branch automatically.
6. If neither exists, create the isolated worktree from `patch-18`:

```powershell
cd C:\projects\britannia\britannia_mod
git worktree add -b new-assets C:\projects\britannia\new-assets patch-18
```

7. If the branch/worktree already exists, verify that the directory is attached to branch `new-assets` and continue only if safe.
8. Do all implementation work inside `C:\projects\britannia\new-assets`.
9. Do not modify the main `britannia_mod` worktree except for the Git command required to create/register the new worktree.

## B. No destructive Git operations

Do not:

- reset other work;
- clean untracked files globally;
- delete another worktree;
- rebase;
- merge;
- push;
- force-push;
- tag;
- alter unrelated branches.

## C. Owner-controlled milestone commits

At the end of each implementation milestone:

1. validate the milestone;
2. report exact changed files and test results;
3. stop;
4. request owner approval for an isolated local commit.

Do not create the milestone commit unless the owner has explicitly authorized it for that milestone.

If the owner authorizes a commit, create exactly one isolated local commit for that approved milestone and report the commit hash.

## D. Scope isolation

Never combine unrelated cleanup with a milestone.

If reconnaissance discovers a pre-existing defect that is not required to implement the current milestone, document it instead of silently fixing it.

## E. Source asset safety

Treat `C:\projects\britannia\raw fiels\models to import` as read-only.

Copy from it. Never rename, move, delete, or re-save original assets in place.

## F. Placeholder policy

Missing art is not a blocker unless gameplay cannot be implemented without geometry.

Create clearly marked placeholders using final registry IDs and log them in the manifest.

---

# Milestone 0 — Workspace Bootstrap and Repository Reconnaissance

## Objective

Create or verify the isolated `new-assets` workspace and understand the existing systems that this project must extend.

## Required work

- establish/verify worktree and branch;
- record starting HEAD and branch divergence;
- confirm working tree cleanliness/state;
- read the design document and playbook;
- inspect registration architecture;
- identify existing:
  - regional spawn/population code;
  - Jhelom region identifiers;
  - entity variant patterns;
  - GeckoLib/animation conventions;
  - multi-block structures;
  - container/menu implementations;
  - custom Adventure-mode placement rules;
  - axe harvest/tag patterns;
  - water items/interactions;
  - skill-gain APIs;
  - weapon-to-skill classification;
  - textile/crafting systems;
  - neighbor-aware blocks;
  - creative tab registration;
  - moongate implementation;
  - dedicated-server population/bootstrap integration.

## Deliverable

Create:

`docs/new-assets/IMPLEMENTATION_LOG.md`

Record recon findings and exact code precedents with file paths/classes.

## Gate

No gameplay asset implementation in Milestone 0.

Stop after reporting reconnaissance and any architectural risks.

---

# Milestone 1 — Raw Asset Inventory and Import Manifest

## Objective

Scan the raw source directory and map every requested asset to available source files.

## Required work

Recursively inspect:

`C:\projects\britannia\raw fiels\models to import`

Catalog candidate:

- `.png`
- `.json`
- `.bbmodel`
- `.obj`
- `.mtl`
- `.gltf`
- `.glb`
- other relevant model/resource formats discovered

Create:

`docs/new-assets/ASSET_IMPORT_MANIFEST.md`

Every requested asset must appear, even if missing.

## Matching rules

Use filename, folder, texture references, model metadata, dimensions, and visual/resource relationships.

Do not assume a file is correct based on filename alone.

## Special checks

Explicitly determine:

- whether white ibis model/texture exists;
- whether scarlet ibis texture exists;
- whether six merchant-cart colors exist;
- whether display-case state variants exist;
- whether crate sizes are separate geometries;
- whether loom/spinning wheel models are present;
- whether large multi-block dimensions match the requested dimensions.

## Gate

No mass registry implementation yet.

The milestone ends with a complete manifest classified as:

- FOUND
- PARTIAL
- MISSING

and a proposal for placeholder needs.

---

# Milestone 2 — Asset Import Infrastructure and Low-Risk Decorative Assets

## Objective

Prove the import pipeline using low-risk assets before complex gameplay systems.

## Candidate scope

- Globe
- Fern
- Folded cloth
- Bolt of cloth
- Pewter mug
- Kettle
- Plates and silverware
- Moonglow bushes
- Sandstone brick walls

## Required work

- normalize resource paths;
- import found assets;
- create placeholders for missing ones;
- register blocks/items using existing conventions;
- add localization;
- add creative-tab entries;
- add block items where appropriate;
- add loot tables/tags;
- configure transparent render types for foliage;
- use proper wall-block behavior for sandstone walls.

## Validation

- build;
- resource/model load;
- in-game placement;
- missing-texture check;
- collision check;
- inventory icon check.

## Gate

All assets in this milestone must be individually accounted for in the manifest.

---

# Milestone 3 — Multi-Block Decorative Structures and Merchant Carts

## Objective

Add non-container, non-skill multi-block structures and six merchant cart variants.

## Candidate scope

- Merchant carts, six colors
- Fountain, 2x2x3
- Scarecrow, 2 high
- Dress form, 2 high
- Loom, 2 wide x 3 high, structural/visual behavior only unless recon proves an existing compatible loom mechanic

## Required work

- reuse existing multi-block placement architecture if available;
- atomic placement;
- blocked-space checks;
- correct teardown;
- single drop;
- deliberate voxel/collision shapes;
- six cart variants preferably sharing geometry.

## Validation

Test:

- every orientation;
- partial obstruction;
- breaking root and child parts;
- save/reload;
- creative pick/place;
- client and dedicated server.

---

# Milestone 4 — Crate Container Family

## Objective

Implement small, medium, and large crates as real containers.

## Required work

- determine exact model footprint from source;
- use existing container/block entity/menu architecture;
- choose slot counts from project precedent;
- persistent NBT/component storage;
- server-authoritative opening;
- multiplayer-safe interaction;
- correct drops;
- no duplication if any crate is multi-block.

## Validation

- insert/extract;
- save/reload;
- break while containing items;
- simultaneous multiplayer access if existing container architecture allows it;
- dedicated server.

---

# Milestone 5 — Water Well and Adventure Ladder

## Objective

Implement the two utility structures with special interaction rules.

## Water well

- 2 blocks high;
- obtain water using existing water-container items;
- no invented parallel water system;
- server-authoritative item conversion/fill behavior.

## Ladder

- 3 blocks high;
- double-sided;
- climbable from both sides;
- placeable on the ground by Adventure-mode players;
- axe-destructible;
- narrow stair/ladder-like geometry;
- atomic multi-block placement.

## Safety requirement

Adventure-mode placement permission must be narrowly scoped to this ladder item/block and must not relax global Adventure-mode rules.

## Validation

- Survival;
- Creative;
- Adventure;
- both climb directions;
- blocked placement;
- axe vs non-axe destruction behavior;
- multiplayer;
- save/reload.

---

# Milestone 6 — Ibis Entity, Variants, and Jhelom Population

## Objective

Add the white/scarlet ibis and integrate it with Jhelom-only regional spawning.

## Required work

- import ibis model and white texture if present;
- generate scarlet texture from white when absent;
- use one persistent/synchronized variant model unless recon requires otherwise;
- register entity;
- integrate with the existing regional spawn/population service;
- Jhelom only;
- maximum 15 ibis total in Jhelom;
- prevent global biome spawning.

## Scarlet texture rule

Generated texture must preserve alpha/UV/dark detail and be logged as generated.

If automated recoloring is visually unsafe, use a clearly marked scarlet placeholder and keep the white original untouched.

## Validation

- spawn both variants;
- persistence after save/reload;
- server restart;
- verify no natural ibis outside Jhelom;
- verify cap does not exceed 15;
- two-client render test.

---

# Milestone 7 — Training Dummy Skill Trainer

## Objective

Implement the complete UO-inspired training dummy.

## Dimensions

- 3 blocks high
- 2 blocks wide

## Skill behavior

Can train up to 25.0:

- Swordsmanship
- Mace Fighting
- Fencing

May occasionally train:

- Tactics

Must never train:

- Wrestling
- Anatomy
- Lumberjacking

Cooldown:

- 3 seconds per player after a valid training strike

Durability:

- weapon durability must not decrease

## Required implementation decisions

Recon must identify:

- skill service/API;
- skill decimal/internal scaling;
- weapon category mapping;
- skill-gain function;
- Tactics gain convention;
- hit event interception;
- safe way to suppress durability damage only for the dummy interaction;
- synchronized animation path.

## Exploit resistance

Validate:

- spam click;
- dual wield/off-hand;
- multiple players;
- weapon swapping during cooldown;
- client packet repetition;
- skill already at 25.0;
- unsupported weapons;
- unarmed hits.

## Animation

A successful/accepted strike should trigger the dummy animation even if no random skill gain occurs, but rejected cooldown spam should not retrigger training effects.

## Gate

No milestone approval until the skill cap, cooldown, and durability behavior are proven in-game.

---

# Milestone 8 — Spinning Wheel and Textile Outputs

## Objective

Add spinning-wheel processing while reusing existing textile items.

## Required conversions

- wool -> ball of yarn
- cotton -> spool of thread
- flax -> spool of thread
- silk -> spool of thread

## Required work

- locate existing input/output registry IDs;
- add missing output items only when necessary;
- import spinning-wheel model;
- determine physical footprint from art;
- reuse existing processing/crafting UI architecture where suitable;
- prefer data-driven recipes;
- prevent duplication;
- synchronize output server-side.

## Loom boundary

Do not invent loom recipes in this milestone unless the owner separately authorizes them.

## Validation

Test all four inputs, invalid input, full output/inventory conditions, save/reload if machine state persists, and multiplayer interaction.

---

# Milestone 9 — Neighbor-Aware Display Cases

## Objective

Implement independently placeable display cases that combine visually with neighbors.

## Dimensions

- 1 block footprint per segment
- 2 blocks high

## Minimum visual states

- single/independent
- edge/end
- middle/straight
- corner/turn

## Required work

- inspect imported display-case state models;
- determine orientation requirements;
- implement north/east/south/west neighbor detection;
- recompute on placement/removal;
- synchronize upper/lower structure;
- use blockstate/multipart or bounded connection-state architecture;
- avoid separate Java classes for every combination.

## Validation matrix

Test:

- one case;
- two in a line;
- three in a line;
- L corner;
- U shape;
- 2x2 cluster;
- remove a middle segment;
- rotate/orient from all horizontal facings if facing matters;
- save/reload;
- multiplayer neighbor updates.

No storage/item-display feature is added unless separately authorized.

---

# Milestone 10 — Moongate Integration

## Objective

Integrate the imported moongate asset without duplicating existing gate systems.

## Required work

- inspect existing moongate/Dungeon Shame implementation identified in Milestone 0;
- determine whether this asset replaces, extends, or adds a model to that system;
- preserve current destinations/configuration;
- import visual asset;
- add placeholder-only registration if no behavior contract exists.

## Gate

If no authoritative behavior can be inferred from existing code, stop at visual registration and document the gameplay question. Do not invent a new teleport network.

---

# Milestone 11 — Cross-System Polish and Creative/Data Audit

## Objective

Ensure all requested assets are consistently integrated.

## Audit

For every asset verify:

- registry ID;
- block item;
- creative tab;
- localization;
- loot;
- tags;
- render layer;
- collision;
- sound/material;
- recipe/processing data if required;
- placeholder status;
- manifest entry.

Review server bootstrap/population implications.

Run full relevant test suite and clean builds.

---

# Milestone 12 — Owner Live Validation and Project Closure

## Objective

Collect live-game evidence and close the project only after owner validation.

Create/update:

`docs/new-assets/LIVE_TEST_CHECKLIST.md`

## Required live checks

At minimum:

- all decorative assets render correctly;
- merchant cart six colors;
- fountain footprint/height;
- crate storage/persistence;
- well water interaction;
- ladder Adventure placement + climbing + axe destruction;
- white/scarlet ibis in Jhelom only and cap behavior;
- training dummy skill cap/cooldown/durability/animation;
- spinning wheel conversions;
- display-case single/edge/middle/corner recomputation;
- moongate integration does not break existing gates;
- client and dedicated server launch cleanly.

## Closure gate

Project closes only after:

- manifest has no unexplained missing entries;
- placeholders are explicitly accepted or replaced;
- all functional requirements pass;
- owner approves closure;
- final isolated commit is authorized;
- no merge/push is performed unless separately authorized.

---

# Standard Milestone Completion Report

Every milestone response should use this structure:

```text
Milestone:
Branch:
Worktree:
Starting HEAD:
Current HEAD:

Scope completed:
- ...

Assets affected:
- ...

Files changed:
- ...

Validation performed:
- ...

Validation result:
- PASS / BLOCKED / PARTIAL

Placeholders created:
- ...

Manifest updates:
- ...

Known issues / deferred work:
- ...

Commit status:
- Not committed; awaiting owner authorization.
```

If blocked, include the exact blocker and the safest next action. Do not continue into the next milestone automatically.
