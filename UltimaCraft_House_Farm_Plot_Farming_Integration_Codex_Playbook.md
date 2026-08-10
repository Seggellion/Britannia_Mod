# UltimaCraft House Farm Plot Farming Integration
## Codex Implementation Playbook

**Companion design:** `UltimaCraft_House_Farm_Plot_Farming_Integration_Design.md`  
**Feature:** Integrate `HouseFarmPlotBlock` with existing farming and flower systems  
**Reset tool:** `FarmingHoeItem`  
**Primary repository folder:** `britannia_mod`  
**Integration/base branch:** `patch-18`  
**Implementation style:** Dedicated feature branch + isolated Git worktree, milestone-driven, evidence-based, minimal duplication  
**Date:** 2026-08-09

---

# 1. Operating Rules

This playbook is intended for an implementation agent working on the UltimaCraft repository.

The primary local repository folder is:

```text
britannia_mod
```

The current integration/source-of-truth branch is:

```text
patch-18
```

The agent must **not** perform implementation work directly inside the primary `britannia_mod` working directory.

That folder must remain available so other agents can continue working independently.

Before any feature implementation begins, the agent must:

1. inspect the primary `britannia_mod` checkout;
2. confirm that `patch-18` is the correct base branch;
3. create a new dedicated feature branch from `patch-18`;
4. create a separate Git worktree for that feature branch;
5. perform all implementation, builds, tests, and commits inside the new worktree;
6. leave the primary `britannia_mod` folder otherwise untouched.

A recommended branch name is:

```text
feature/house-farm-plot-farming
```

A recommended sibling worktree folder is:

```text
britannia_mod-house-farm-plot
```

Use the repository's existing naming conventions if they clearly differ.

Do not merge the feature branch back into `patch-18`.

Do not push unless explicitly instructed.

---

# 2. Feature Architecture Rules

The agent must:

- read the companion design document before editing code;
- inspect the repository before deciding architecture;
- use exact existing class names and registry identifiers from source;
- reuse the existing farming, crop, flower, and tool systems;
- keep standard farming behavior unchanged;
- compile/test after each meaningful milestone;
- commit each completed milestone independently when practical;
- record evidence for each milestone.

Do not invent a parallel farming system because `HouseFarmPlotBlock` has different geometry.

The unique requirements are only:

1. house plot farming compatibility;
2. different visual/model anchor;
3. default red poppy assignment;
4. persistent plant assignment across harvests;
5. assignment reset exclusively through `FarmingHoeItem`.

Everything else should be existing farming behavior.

---

# 3. Git and Worktree Setup

## Objective

Create an isolated feature workspace based on `patch-18` before touching implementation code.

## 3.1 Inspect the primary checkout

Start in the existing primary repository:

```bash
cd britannia_mod
git status --short
git branch --show-current
git log -5 --oneline
git worktree list
```

Confirm the repository is the expected UltimaCraft project.

Confirm `patch-18` exists:

```bash
git branch --list patch-18
git branch -r --list "*/patch-18"
```

If the primary checkout is already on `patch-18`, do not make feature edits there.

If it is on another branch because another agent is actively using it, do not switch it merely for this feature.

The critical requirement is that the feature branch be based on the current local `patch-18` commit.

## 3.2 Preserve unrelated work

If `britannia_mod` has uncommitted changes:

- do not discard them;
- do not stash them unless explicitly instructed;
- do not reset them;
- do not checkout over them;
- do not clean the repository.

Those changes may belong to another agent.

Because implementation will happen in another worktree, unrelated changes in the primary checkout should normally be left alone.

## 3.3 Determine the exact `patch-18` base

Inspect:

```bash
git rev-parse patch-18
git log -1 --oneline patch-18
```

Record the base commit hash in the implementation notes.

If a remote exists and normal repository workflow permits fetching, it is acceptable to fetch before branch creation, but do not automatically merge or rebase `patch-18`.

Do not change the base branch unless explicitly instructed.

## 3.4 Create the feature branch

Preferred branch:

```text
feature/house-farm-plot-farming
```

Check whether it already exists:

```bash
git branch --list "feature/house-farm-plot-farming"
git branch -r --list "*/feature/house-farm-plot-farming"
```

If the branch does not exist, create it directly from `patch-18` as part of worktree creation.

If the branch already exists locally and is clearly the intended branch for this feature, inspect it before reuse.

Do not overwrite an existing branch.

## 3.5 Create the isolated worktree

From the directory containing `britannia_mod`, create a sibling worktree.

Conceptual command:

```bash
git -C britannia_mod worktree add \
  -b feature/house-farm-plot-farming \
  britannia_mod-house-farm-plot \
  patch-18
```

If the feature branch already exists:

```bash
git -C britannia_mod worktree add \
  britannia_mod-house-farm-plot \
  feature/house-farm-plot-farming
```

Adjust paths to the actual local filesystem.

The expected layout is conceptually:

```text
<parent>/
├── britannia_mod/
└── britannia_mod-house-farm-plot/
```

## 3.6 Verify isolation

Enter the new worktree:

```bash
cd britannia_mod-house-farm-plot
git status --short
git branch --show-current
git log -1 --oneline
```

Verify:

```text
branch = feature/house-farm-plot-farming
base = patch-18
working tree = clean at feature start
```

Also verify the primary checkout remains available:

```bash
git worktree list
```

## 3.7 Worktree rules

After the worktree is created:

- all feature source edits happen in `britannia_mod-house-farm-plot`;
- all builds/tests for this feature run from that worktree;
- all feature commits are created on `feature/house-farm-plot-farming`;
- do not implement this feature inside `britannia_mod`;
- do not merge into `patch-18`;
- do not push unless instructed;
- do not remove the worktree at the end unless explicitly instructed.

The final agent report must include:

```text
Base branch: patch-18
Base commit: <hash>
Feature branch: <branch>
Feature worktree: <path>
```

---

# 4. Repository Safety Rules

Inside the feature worktree, before making changes:

```bash
git status --short
git branch --show-current
git log -5 --oneline
```

Do not discard unrelated changes.

Do not reset, clean, checkout over, or rewrite files unrelated to this feature.

Do not modify generated/build output unless the project intentionally tracks it.

If unexpected changes appear in the feature worktree, investigate before proceeding.

---

# 5. Milestone Overview

| Milestone | Goal |
|---|---|
| M0 | Worktree verification, repository discovery, and architecture map |
| M1 | Persistent house-plot assignment state |
| M2 | Default red poppy initialization |
| M3 | Shared planting compatibility |
| M4 | Crop/flower geometry and model anchoring |
| M5 | Harvest persistence and automatic same-crop regrowth |
| M6 | `FarmingHoeItem` reset and reassignment |
| M7 | Tall-crop and edge-case cleanup |
| M8 | Persistence, multiplayer, protection, and regression validation |
| M9 | Documentation, final test pass, and implementation evidence |

Do not skip M0.

---

# M0 - Worktree Verification, Repository Discovery, and Architecture Map

## Objective

Confirm the isolated Git workspace and understand how farming currently works before changing implementation code.

## Worktree evidence

Record:

```text
Primary checkout:
<actual britannia_mod path>

Base branch:
patch-18

Base commit:
<git rev-parse patch-18>

Feature branch:
<actual branch>

Feature worktree:
<actual path>
```

Verify the feature worktree is the active working directory before editing.

## Required searches

Locate the definitions and all meaningful call sites for:

```text
HouseFarmPlotBlock
FarmingHoeItem
ordinary farming block class
FlowerBlock
red poppy species/registry entry
crop base block/class
crop registry or crop data definitions
planting interaction
harvest interaction
growth tick logic
farming skill hooks
house/lot protection checks
crop renderer/model path
flower renderer/model path
tall crop handling
```

Useful commands may include:

```bash
rg -n "class HouseFarmPlotBlock|HouseFarmPlotBlock" src
rg -n "class FarmingHoeItem|FarmingHoeItem" src
rg -n "class .*Farming.*Block|FarmingBlock" src
rg -n "class FlowerBlock|FlowerBlock" src
rg -n "poppy|red_poppy|POPPY" src
rg -n "harvest|plant|seed|growth|grow|age|stage" src/main/java
rg -n "BlockEntity|saveAdditional|loadAdditional|CompoundTag" src/main/java
```

Adapt paths to the repository.

## Questions M0 must answer

Write down the answers before coding:

1. What is the exact ordinary farming surface class?
2. Does planting replace the farm block with the crop/flower block?
3. Where is the authoritative growth stage stored?
4. Where is crop identity stored?
5. How does `FlowerBlock` store species and color?
6. How does the ordinary farming system decide whether an item can be planted?
7. How does harvesting reset or remove the plant?
8. Where are harvest drops generated?
9. Where are farming skill/mastery hooks fired?
10. How does `FarmingHoeItem` currently interact with farm blocks?
11. Does `HouseFarmPlotBlock` already have a block entity?
12. What is the actual top planting-surface Y/shape of `HouseFarmPlotBlock`?
13. How are crop models positioned vertically?
14. How are tall crops represented and cleaned up?
15. What ownership/protection API should the house plot use?
16. What test framework and test conventions exist?

## Deliverable

Create a short implementation note in the agent's working summary containing:

```text
Current farming lifecycle:
<actual flow>

Current flower lifecycle:
<actual flow>

HouseFarmPlotBlock current implementation:
<actual details>

Chosen integration seam:
<actual class/helper/interface to extend>

Persistence strategy:
<actual strategy>

Rendering/anchor strategy:
<actual strategy>
```

## Gate

Do not start M1 until:

- the feature worktree is verified;
- the branch is confirmed to be based on `patch-18`;
- the actual farming lifecycle is understood.

---

# M1 - Persistent House-Plot Assignment State

## Objective

Give each `HouseFarmPlotBlock` durable state that can distinguish:

```text
UNINITIALIZED
ASSIGNED(<plant id>)
CLEARED
```

## Requirements

The state must survive:

- chunk unload;
- world save;
- server restart.

The assignment must not live only in a temporary crop/flower block.

## Preferred implementation order

Use whichever existing project pattern fits:

1. extend existing farming persistent data;
2. extend an existing block entity already owned by `HouseFarmPlotBlock`;
3. add a minimal `HouseFarmPlotBlockEntity`;
4. use another existing persistent component convention.

Avoid duplicating crop stage state.

Store only what is house-plot-specific.

## Suggested logical API

Names are illustrative:

```java
boolean isInitialized();
boolean isCleared();
Optional<ResourceLocation> getAssignedPlantId();

void assignPlant(ResourceLocation plantId);
void clearAssignedPlant();
void markInitialized();
```

If a single enum/state field plus nullable ID is cleaner, use that.

## Critical migration behavior

Legacy house plots created before this feature will have no new data.

Treat missing state as:

```text
UNINITIALIZED
```

Do not treat missing state as permanently cleared.

## Invalid ID behavior

If an assigned registry/data ID no longer exists:

- do not crash world load;
- use the project's standard missing-data behavior;
- preferably transition the plot to a safe cleared state and mark it initialized.

## Tests

Add focused serialization tests if supported.

At minimum verify:

```text
UNINITIALIZED save/load
ASSIGNED(red_poppy) save/load
ASSIGNED(other_crop) save/load
CLEARED save/load
```

## M1 Gate

Compile/tests pass before proceeding.

Commit suggestion:

```text
feat(farming): add persistent house farm plot assignment state
```

---

# M2 - Default Red Poppy Initialization

## Objective

A truly new or legacy-uninitialized house farm plot receives the existing red poppy assignment exactly once.

## Requirements

Use the existing flower definition/registry entry for red poppy.

Do not create:

```text
house_red_poppy
house_plot_poppy
special_poppy
```

unless one already exists as the canonical red poppy.

## Initialization behavior

Conceptually:

```java
if (plotState == UNINITIALIZED) {
    assign(existingRedPoppyId);
    activateExistingFlowerLifecycle();
    markInitialized();
}
```

The actual activation path must reuse the existing flower system.

## Important guard

A `CLEARED` plot must never be treated as `UNINITIALIZED`.

This must remain true after reload.

## Stage selection

Use the existing project's normal initialization semantics for a planted flower unless source code clearly establishes a special pre-populated landscaping convention.

Do not invent new flower growth timing.

## Validation

Test:

1. new house plot -> red poppy;
2. unload/reload -> still red poppy;
3. harvest -> red poppy remains assigned;
4. hoe-clear -> empty;
5. unload/reload -> still empty.

## M2 Gate

Compile/tests pass.

Commit suggestion:

```text
feat(farming): initialize house farm plots with red poppies
```

---

# M3 - Shared Planting Compatibility

## Objective

Allow a cleared `HouseFarmPlotBlock` to accept the same plant set as ordinary UltimaCraft farming.

## Rule

The ordinary farming system remains the authority on whether something is plantable.

Do not add a second list like:

```java
HOUSE_PLOT_SUPPORTED_CROPS
```

if the normal farming system already has a registry/predicate.

## Implementation strategy

Find the narrowest reusable planting seam.

Preferred outcomes include:

```text
shared farmable-surface predicate
shared planting context
shared plant resolver
shared plant operation
```

If current planting code is tightly coupled to the ordinary farming block, extract only enough shared logic to support both surfaces.

## Planting on CLEARED plot

Flow:

```text
player uses valid plant item
    -> existing permission check
    -> existing plant resolution
    -> existing plant validation
    -> existing item consumption
    -> existing crop/flower activation
    -> save canonical plant ID as assignment
    -> begin normal growth
```

## Planting while ASSIGNED

A different seed/plant item must not silently replace the assignment.

Expected:

```text
ASSIGNED + different planting item -> reject/no-op using existing conventions
```

Do not consume the player's item.

## Regression

Ordinary farmland must behave identically before and after this refactor.

## Tests

At minimum:

- ordinary crop on cleared house plot;
- flower on cleared house plot;
- rejected invalid item;
- rejected replacement while assigned;
- ordinary farm block existing tests still pass.

## M3 Gate

Compile/tests pass.

Commit suggestion:

```text
feat(farming): allow house farm plots to use shared planting flow
```

---

# M4 - Crop and Flower Geometry / Model Anchoring

## Objective

Reuse existing crop/flower models while making them visually fit `HouseFarmPlotBlock`.

## First step: measure actual geometry

Inspect:

- `HouseFarmPlotBlock` voxel shape;
- block model JSON;
- renderer/model;
- ordinary farm block geometry;
- crop renderer/model assumptions.

Record actual planting-surface height.

Do not guess.

## Preferred architecture

Introduce one reusable surface anchor/transform seam.

Conceptually:

```text
crop renderer
    asks farming surface for planting anchor
```

or:

```text
planting/render context contains surface model offset
```

The exact design depends on current code.

## Do not create per-crop duplicate models

Avoid copying all crop JSON/models for the house plot.

If the entire plant can be translated consistently, do that at a shared layer.

## Validation families

Test at least one representative from every geometry family present in the project:

```text
standard single-block annual crop
leafy crop
root crop
flower
tall crop
any special crop geometry currently supported
```

If fruit trees use the same farm surface and are currently legal, test them too. If ordinary farmland rejects them, the house plot should also reject them.

## Visual requirements

At every relevant growth stage:

- no floating;
- no soil clipping;
- horizontally centered;
- no stage-dependent jump caused by incorrect origin;
- correct selection/collision behavior where applicable.

## Tall-crop requirement

Upper segments must remain aligned with the translated root.

Do not translate only the lower model if the crop uses a multi-block structure.

## M4 Gate

Perform an in-game visual pass in addition to automated tests/build.

Commit suggestion:

```text
fix(farming): anchor crop models to house farm plot geometry
```

---

# M5 - Harvest Persistence and Same-Crop Regrowth

## Objective

Harvesting a house farm plot must preserve its assignment and restart the same existing plant lifecycle.

## Core invariant

```text
harvest != unassign
```

For a house farm plot:

```text
assignedPlant before harvest == assignedPlant after harvest
```

## Implementation

Reuse the existing harvest flow:

- tool validation;
- mature-stage validation;
- drops;
- XP/skill/mastery;
- sound/particles;
- stage reset or plant reset.

Add the smallest house-plot-specific hook required to prevent assignment loss and restore/reactivate the same plant.

## Do not duplicate harvest rewards

Watch for event double-processing.

There should be exactly one normal harvest payout.

## Repeated-cycle test

For at least:

- red poppy;
- one standard crop;
- one special/tall crop if legal;

perform:

```text
grow -> harvest -> regrow
grow -> harvest -> regrow
grow -> harvest -> regrow
```

Confirm the assigned ID never changes.

## Persistence mid-cycle

Also test save/reload while:

- growing;
- mature;
- just harvested/reset.

## M5 Gate

Compile/tests and repeated in-game harvest validation pass.

Commit suggestion:

```text
feat(farming): preserve house plot crop assignment across harvests
```

---

# M6 - `FarmingHoeItem` Reset and Reassignment

## Objective

Make `FarmingHoeItem` the exclusive intentional reset mechanism for the persistent assignment.

## Required behavior

Using `FarmingHoeItem` on an assigned house plot:

```text
permission check
    -> remove active crop/flower presentation
    -> clear persistent assignment
    -> preserve HouseFarmPlotBlock
    -> mark plot CLEARED
    -> sync/persist
```

## Important

Do not implement the reset as:

```text
destroy HouseFarmPlotBlock
place new HouseFarmPlotBlock
```

That risks:

- re-triggering red poppy initialization;
- losing house/lot metadata;
- duplicate drops;
- block update side effects.

Clear state in place.

## Tall crop cleanup

If active plant uses upper segments, the hoe reset must safely remove those segments.

Only remove segments that belong to this crop instance.

Do not damage neighboring player blocks.

## Tool behavior

Reuse current `FarmingHoeItem` durability/use/sound conventions.

Do not silently change the hoe's behavior on ordinary farm blocks.

## Reassignment validation

Test:

```text
red poppy assigned
-> hoe
-> cleared
-> plant crop A
-> crop A persists through harvest
-> hoe
-> cleared
-> plant flower B
-> flower B persists
```

## Critical reload test

```text
hoe -> CLEARED
save/restart
plot must still be CLEARED
```

If red poppy returns, the milestone fails.

## M6 Gate

Compile/tests pass.

Commit suggestion:

```text
feat(farming): reset house farm plots with farming hoe
```

---

# M7 - Tall Crops and Edge-Case Cleanup

## Objective

Handle crop structures that are not simple single-block plants.

## Inspect

Identify all existing special placement/cleanup systems, including known tall crops such as corn and banana.

For every special plant type that ordinary farmland supports, verify:

- initial placement;
- stage growth;
- upper/lower segment synchronization;
- house plot anchor;
- harvest;
- persistent regrowth;
- hoe clear;
- chunk reload;
- block removal cleanup.

## Neighbor safety

A house plot cleanup must not remove blocks merely because they occupy expected upper positions.

Use the existing crop ownership/segment identity rules.

## Plot removal

Legitimately removing the `HouseFarmPlotBlock` must clean up any plant representation it owns.

## M7 Gate

No orphan blocks after:

- harvest;
- hoe clear;
- plot destruction;
- reload.

Commit suggestion:

```text
fix(farming): handle persistent house plots for multi-block crops
```

---

# M8 - Persistence, Multiplayer, Protection, and Regression Validation

## Objective

Validate the feature as a real server-side gameplay system.

## Persistence matrix

Test:

```text
ASSIGNED growing -> save/reload
ASSIGNED mature -> save/reload
ASSIGNED post-harvest -> save/reload
CLEARED -> save/reload
CLEARED -> server restart
reassigned crop -> server restart
```

## Multiplayer matrix

With at least two clients:

- client A hoes plot;
- client B sees cleared state;
- client A replants;
- client B sees correct plant;
- crop growth stages synchronize;
- harvest result synchronizes;
- reconnecting client sees canonical assignment.

State changes must be server-authoritative.

## Ownership / protection

Verify current house/lot protections still apply.

A player who cannot normally modify the house plot must not gain access through:

- planting;
- harvesting;
- hoe reset.

Use existing protection APIs.

## Game modes

Test existing expected behavior in:

- Adventure;
- Creative/admin.

Do not add new mode-specific policy unless required to preserve current farming rules.

## Ordinary farming regression

Run all existing farming and flower tests.

Manually spot-check ordinary farmland:

```text
plant
grow
harvest
replant
hoe behavior
flower behavior
```

## M8 Gate

Full relevant test suite passes.

Commit suggestion:

```text
test(farming): cover house farm plot persistence and regressions
```

---

# M9 - Documentation, Final Validation, and Evidence

## Objective

Leave the feature complete, understandable, reproducible, and isolated on its feature branch.

## Code cleanup

Remove:

- temporary debug logs;
- commented-out experiments;
- duplicate helper code;
- unused imports;
- stale TODOs.

## Documentation

Add concise comments where behavior is intentionally different:

```text
House farm plots retain their assigned plant across harvests.
Only FarmingHoeItem clears the persistent assignment.
```

Update existing farming architecture docs if appropriate.

## Final commands

Use the repository's actual commands. Examples only:

```bash
./gradlew compileJava
./gradlew test
./gradlew build
```

If the project has focused GameTests or dedicated farming test tasks, run them too.

## Git validation

Before reporting completion:

```bash
git status --short
git branch --show-current
git log --oneline patch-18..HEAD
git diff --stat patch-18...HEAD
git worktree list
```

Confirm:

- current branch is the feature branch;
- implementation commits are not on `patch-18`;
- no merge into `patch-18` occurred;
- primary `britannia_mod` checkout remains present;
- worktree remains available for review/testing.

Do not push.

Do not merge.

Do not delete the feature worktree.

## Final evidence report

The agent's final response must include:

```text
1. Base branch and base commit
2. Feature branch
3. Feature worktree path
4. What changed
5. Key architecture decisions
6. Files changed
7. Tests/build commands run
8. Results
9. Manual in-game tests performed
10. Any known limitations
11. Commit hashes created
12. Confirmation that patch-18 was not merged into or modified by this feature workflow
```

If any acceptance criterion is not verified, state that explicitly.

## Final acceptance checklist

### Git/worktree

- [ ] Base branch is `patch-18`
- [ ] Base commit recorded
- [ ] Dedicated feature branch created
- [ ] Dedicated feature worktree created
- [ ] Feature work performed only in the dedicated worktree
- [ ] Primary `britannia_mod` checkout left available for other agents
- [ ] No merge into `patch-18`
- [ ] No push performed unless explicitly instructed
- [ ] Worktree left intact for review

### Feature

- [ ] Existing `HouseFarmPlotBlock` integrated, not replaced by a parallel block
- [ ] Existing `FarmingHoeItem` used as reset tool
- [ ] New/uninitialized plot defaults to existing red poppy
- [ ] Hoe-cleared plot stays cleared across reload
- [ ] Existing crop/flower planting logic reused
- [ ] Existing growth logic reused
- [ ] Existing harvest logic reused
- [ ] Existing drops and skill hooks reused
- [ ] Persistent assignment survives harvest
- [ ] Same plant regrows indefinitely until hoed
- [ ] New plant can be assigned after hoe reset
- [ ] Existing crop/flower models reused
- [ ] House plot geometry anchor correct
- [ ] Tall crop behavior validated where applicable
- [ ] House/lot protections preserved
- [ ] Multiplayer synchronization validated
- [ ] Ordinary farming behavior unchanged
- [ ] Focused tests pass
- [ ] Full relevant tests/build pass
- [ ] No unrelated files modified

Commit suggestion:

```text
docs(farming): document house farm plot integration
```

---

# 6. Stop Conditions

Stop and investigate before proceeding if any of the following occurs:

- the agent is about to implement inside the primary `britannia_mod` checkout;
- the feature branch is not based on `patch-18`;
- creating the worktree would overwrite an existing directory or branch;
- supporting the house plot appears to require copying the crop system;
- a proposed change alters ordinary farm behavior unintentionally;
- persistent assignment is stored only in a transient crop block;
- hoe-cleared plots respawn red poppies after reload;
- crop models require per-species duplication solely for vertical offset;
- harvest awards duplicate drops;
- tall crop cleanup removes unrelated blocks;
- client-only code becomes authoritative for assignment state;
- a class/registry ID is being guessed rather than confirmed from source.

Resolve the architecture or repository-state problem before continuing.

---

# 7. Preferred Final Shape

The completed code should conceptually look like:

```text
patch-18
    |
    +-- feature/house-farm-plot-farming
            |
            +-- dedicated Git worktree
            |
            +-- existing farming system
                    |
                    +-- ordinary farming surface
                    |
                    +-- HouseFarmPlotBlock
                           |
                           +-- persistent assigned plant ID
                           +-- initialized/cleared state
                           +-- default existing red poppy
                           +-- house-specific render anchor
                           +-- FarmingHoeItem clear hook
```

Not:

```text
britannia_mod primary checkout
    -> direct feature edits

or

house farming system
    +-- copied crop registry
    +-- copied growth ticks
    +-- copied harvest code
    +-- copied crop models
```

The feature succeeds when the house farm plot is mostly an adapter around existing farming behavior and the entire implementation remains isolated from the shared primary working folder until it is ready to be deliberately integrated later.
