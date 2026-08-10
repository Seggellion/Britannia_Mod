# UltimaCraft House Farm Plot Farming Integration
## Design Document

**Project:** UltimaCraft  
**Feature:** House Farm Plot Farming Integration  
**Target runtime:** Minecraft 1.21.1, NeoForge 21.1.72, Java 21, GeckoLib 4.6.6  
**Primary existing block:** `HouseFarmPlotBlock`  
**Reset tool:** `FarmingHoeItem`  
**Status:** Implementation design  
**Date:** 2026-08-09

---

## 1. Feature Summary

UltimaCraft already has a `HouseFarmPlotBlock`, but the block is not currently integrated with the established crop farming and flower systems.

This feature makes `HouseFarmPlotBlock` a first-class farming surface while preserving its unique house-plot geometry and gameplay identity.

The implementation must reuse the existing farming system, crop definitions, crop growth logic, harvesting rules, farming skill behavior, flower system, flower models, crop models, item drops, and related shared utilities wherever possible.

The house farm plot adds one intentional behavior that ordinary farm plots do not have:

> A house farm plot permanently remembers what it grows. Harvesting does not change the assigned plant. The same crop or flower continues to grow on that plot until the player explicitly clears the assignment with the UltimaCraft `FarmingHoeItem`.

A newly created house farm plot is assigned to the existing red poppy by default.

This is an integration and extension of the existing farming architecture, not a second farming system.

---

## 2. Goals

The feature must:

1. Make `HouseFarmPlotBlock` compatible with the existing farming interaction pipeline.
2. Allow every plant type that is already valid for the ordinary UltimaCraft farming system to work on a house farm plot, subject to the same plant-specific rules.
3. Reuse existing crop and flower models rather than creating duplicate house-plot-specific plant models.
4. Correctly position those models on the different geometry of `HouseFarmPlotBlock`.
5. Assign the existing red poppy to a newly initialized house farm plot by default.
6. Persist the plot's assigned crop or flower across harvesting, chunk unloads, world saves, server restarts, and normal gameplay.
7. Allow the assigned plant to be changed only by first clearing the plot with `FarmingHoeItem`.
8. Preserve the normal farming system's growth, harvesting, drops, tools, skill progression, permissions, and multiplayer behavior.
9. Avoid changing the behavior of ordinary farming blocks.
10. Keep the implementation extensible so future crops and flowers automatically inherit house-plot compatibility whenever practical.

---

## 3. Non-Goals

This feature must not:

- Create a second crop growth engine.
- Duplicate crop definitions.
- Duplicate flower definitions.
- Duplicate seed or planting logic.
- Create a parallel harvesting system.
- Introduce house-plot-specific copies of every crop model.
- Change ordinary farm plot behavior unless a small shared refactor is required to expose reusable logic.
- Change crop growth timing.
- Change farming skill progression or mastery thresholds.
- Change existing harvest-tool requirements.
- Change the flower dye/color system.
- Change house ownership rules beyond honoring any existing protection hooks that already apply.
- Make previously invalid plant types legal merely because the surface is a house farm plot.
- Add new network packets unless the existing architecture genuinely requires them.

If the existing farming system does not support a particular plant on its normal farming surface, this feature does not need to add support for that plant.

---

## 4. Existing Systems to Reuse

The implementation agent must locate and reuse the actual project implementations for the following systems before writing feature code.

### 4.1 Farming surface / farming block

Locate the ordinary UltimaCraft farming block and determine:

- how it accepts seeds or plant items;
- how it transitions into a planted state;
- whether planting replaces the farming block with a crop/flower block;
- how soil state is represented;
- how growth is scheduled;
- how watering, fertility, skill, or other farming state is stored;
- how harvest reset behavior works;
- which helper methods or services can be extracted without changing gameplay.

### 4.2 Crop system

Reuse the existing:

- crop registry/data definitions;
- growth-stage definitions;
- models and textures;
- planting validation;
- growth ticks;
- crop-specific geometry;
- tall-crop handling;
- harvest tools;
- harvest drops;
- farming skill hooks;
- multiplayer synchronization.

Known UltimaCraft crop behavior includes seven-stage annual crops and special handling for tall crops such as corn and banana. The implementation must discover the current source of truth rather than coding from this document alone.

### 4.3 Flower system

Reuse the existing `FlowerBlock` pipeline, including:

- flower species definitions;
- red poppy content;
- growth stages;
- color/dye state where applicable;
- rendering;
- planting rules;
- harvest behavior;
- creative/admin protections.

The house plot's default red poppy must refer to the existing red poppy definition. Do not add a second "house poppy."

### 4.4 Farming hoe

The reset tool is the existing UltimaCraft `FarmingHoeItem`.

The feature must integrate with the actual existing item class and interaction flow rather than creating a new reset item.

### 4.5 House / lot protections

Locate any existing house, lot, ownership, or region protection hooks that affect `HouseFarmPlotBlock`.

The farming integration must not become a bypass around existing interaction restrictions.

---

## 5. Core Gameplay Rules

### 5.1 Default assignment

A newly initialized `HouseFarmPlotBlock` must have the existing red poppy as its persistent plant assignment.

"Default red poppy" means:

- the plot is logically assigned to the existing red poppy species;
- the red poppy is visible through the existing flower rendering/model pipeline;
- normal flower growth behavior is used unless the existing project already defines a more appropriate initialization stage for pre-populated plots.

Do not hardcode a duplicate model or duplicate flower behavior.

### 5.2 Persistent crop assignment

Each house farm plot has a persistent assignment:

```text
assignedPlant = <existing crop or flower identifier>
```

Once assigned, ordinary harvesting must never clear this value.

The assignment survives:

- growth stage transitions;
- harvests;
- crop reset/regrowth;
- chunk unload/reload;
- server save/load;
- server restart;
- client reconnect.

The persistent assignment belongs to the house plot, not to a temporary visual crop block that can disappear during a lifecycle transition.

### 5.3 Harvest and regrowth

When the assigned crop or flower reaches a harvestable state and is harvested:

1. Use the existing harvest validation.
2. Use the existing tool checks.
3. Use the existing drops.
4. Use the existing farming skill / mastery hooks.
5. Preserve the house plot's `assignedPlant`.
6. Reset the plant using the existing post-harvest or replanted growth state.
7. Allow the same plant to grow again.

Repeated harvests must therefore produce this loop:

```text
assigned crop
    -> grows
    -> matures
    -> harvested
    -> same assigned crop remains
    -> regrows
    -> matures
    -> harvested
    -> ...
```

The house plot should feel permanently planted until explicitly cleared.

### 5.4 Clearing with `FarmingHoeItem`

Using `FarmingHoeItem` on a planted or assigned house farm plot must:

1. validate that the player is allowed to interact with the plot;
2. remove the currently active crop/flower presentation;
3. clear the persistent plant assignment;
4. leave `HouseFarmPlotBlock` itself intact;
5. leave the plot in a valid empty/plantable state;
6. sync the new state to clients;
7. persist the cleared state.

The hoe action is the intentional "change what this plot grows" operation.

The action must not destroy the house farm plot.

### 5.5 Cleared state must persist

A critical distinction is required between:

- a brand-new/uninitialized plot, which defaults to red poppy; and
- a plot that a player intentionally cleared with `FarmingHoeItem`.

After a player hoes the plot, the red poppy must **not** automatically reappear after:

- a block update;
- chunk reload;
- save/load;
- server restart.

The implementation therefore needs enough persisted state to distinguish "not initialized yet" from "intentionally empty."

A conceptual state model is:

```text
UNINITIALIZED
    -> initialize
    -> ASSIGNED(red_poppy)

ASSIGNED(any_valid_plant)
    -> FarmingHoeItem
    -> CLEARED

CLEARED
    -> plant valid seed/flower
    -> ASSIGNED(selected_plant)
```

The exact storage representation should match existing project conventions.

### 5.6 Reassignment

While a plot has an assignment, attempting to plant a different crop must not silently replace it.

Expected workflow:

```text
existing assigned plant
    -> use FarmingHoeItem
    -> empty house farm plot
    -> plant desired valid crop/flower
    -> new persistent assignment
```

If the player tries to plant something different before clearing the plot, use the existing interaction conventions for a rejected planting attempt.

Avoid destructive item consumption on a rejected interaction.

---

## 6. State and Persistence Design

The exact implementation must be selected after repository inspection.

### 6.1 Required logical state

At minimum, each house farm plot needs to represent:

- whether initialization has occurred;
- whether the plot is intentionally cleared;
- the assigned plant identifier when assigned.

Additional data should be stored only if the existing farming system requires it.

Do not duplicate growth data in the house plot if growth stage already has a canonical location.

### 6.2 Preferred persistence rule

Prefer the lightest solution that follows existing UltimaCraft conventions.

Potential options, in order of preference:

1. Extend an existing farming block entity/state abstraction if one already exists.
2. Reuse an existing persistent plant/crop data component.
3. Add a small `HouseFarmPlotBlockEntity` containing only house-plot-specific persistent assignment state.
4. Use block state only if the supported plant set and persistence semantics make that practical and maintainable.

Do not create a large block entity that reimplements the crop system.

### 6.3 Identifier storage

Store the canonical project identifier for the assigned plant, not a display name and not a class name.

Examples conceptually:

```text
ultimacraft:red_poppy
ultimacraft:corn
ultimacraft:<crop>
```

Use the actual registry/data system in the repository.

When loading invalid or removed IDs, fail safely. Prefer leaving the plot cleared or applying the project's established data-migration strategy rather than crashing the world.

---

## 7. Planting Integration

### 7.1 House plot as a valid farming surface

Where possible, the ordinary planting code should recognize both:

```text
ordinary farming surface
house farm plot surface
```

through a shared capability, interface, helper, tag, predicate, or other existing project abstraction.

Avoid scattered checks such as:

```java
if (block instanceof FarmingBlock || block instanceof HouseFarmPlotBlock)
```

throughout many crop classes.

If a small shared abstraction does not exist, introduce the smallest sensible one.

Conceptual examples:

```text
FarmableSurface
PlantingContext
canPlantOn(...)
getPlantAnchor(...)
onPlantAssigned(...)
```

These names are illustrative only. Use project naming conventions.

### 7.2 Assignment on planting

When the plot is intentionally cleared and a valid crop or flower is planted:

1. run existing planting validation;
2. consume the item using existing rules;
3. create/activate the existing crop or flower representation;
4. save that plant's canonical identifier as the house plot assignment;
5. start at the existing normal planted growth stage;
6. sync/persist state.

### 7.3 Unsupported plants

A house farm plot must follow the same supported/unsupported decision as the normal farming system.

Do not make a house plot a universal planter for arbitrary vanilla or modded blocks unless the ordinary farming system already allows them.

---

## 8. Geometry and Rendering Compatibility

`HouseFarmPlotBlock` has geometry that differs from the ordinary farming block.

This is one of the primary implementation risks.

### 8.1 Requirement

Every existing crop or flower that is already legal on normal UltimaCraft farmland must render correctly when grown from a house farm plot.

Correct means:

- plant base visually meets the soil/planting surface;
- no obvious floating;
- no obvious sinking into the plot;
- no clipping caused by assuming the ordinary farmland top Y;
- horizontal centering remains correct;
- stage changes do not alter the anchor unexpectedly;
- tall crops preserve their expected multi-block alignment;
- collision/selection behavior remains consistent with the existing plant where possible.

### 8.2 Do not duplicate plant models

Do not create files like:

```text
corn_house_plot_stage_1.json
corn_house_plot_stage_2.json
...
poppy_house_plot_stage_7.json
```

unless there is no technically reasonable alternative.

The preferred design is to reuse the existing plant model and adjust its placement/anchor relative to the farming surface.

### 8.3 Surface anchor abstraction

During repository discovery, determine how existing crop models are positioned.

If crop placement currently assumes a fixed ordinary-farm-block height, introduce a reusable surface anchor/offset mechanism at the narrowest shared layer.

Conceptually:

```text
ordinary farm block -> crop anchor Y = existing value
house farm plot     -> crop anchor Y = house plot planting surface
```

The actual implementation may be:

- renderer translation;
- model transform;
- block-entity renderer transform;
- voxel/model parent transform;
- placement offset;
- an existing model-data mechanism.

Choose the approach that reuses models and keeps standard farm rendering unchanged.

### 8.4 Geometry source of truth

Do not guess the house plot's planting height from screenshots or assumptions.

Inspect the actual:

- block model JSON;
- voxel shape;
- renderer/model;
- blockstate;
- related textures;
- existing in-game placement.

The render anchor should be derived from the real geometry.

### 8.5 Tall crops

Special attention is required for existing tall crops.

If a crop uses multiple block positions or renderer segments, verify:

- its root is anchored to the house plot planting surface;
- upper segments remain aligned;
- harvest removes/resets the correct segments;
- hoe-clearing removes all transient plant segments without damaging neighboring blocks;
- chunk reload does not leave orphaned upper segments.

Do not add a second tall-crop implementation.

---

## 9. Block Lifecycle

### 9.1 Placement / initialization

On initial creation of a house farm plot:

```text
if truly uninitialized:
    assign existing red poppy
    activate existing flower representation
    mark initialized
```

This initialization must occur exactly once for the lifetime of that placed plot unless the block is actually broken and replaced.

### 9.2 Normal growth

Growth is delegated to the existing crop/flower system.

The house plot must not maintain a parallel timer.

### 9.3 Harvest

Harvest delegates to existing crop/flower behavior, then preserves/restores the persistent assignment.

### 9.4 Hoe clear

`FarmingHoeItem` clears the assignment and removes the active plant representation.

### 9.5 Replant

The next valid plant interaction on a cleared plot establishes the new persistent assignment.

### 9.6 Plot destruction

If the house farm plot itself is legitimately broken, moved, or removed under existing game rules, clean up any associated crop state or upper segments.

Do not leave orphaned plant blocks or block entities.

---

## 10. Interaction Priority

The implementation should preserve intuitive right-click/use behavior.

Recommended priority for a `HouseFarmPlotBlock`:

```text
1. permission/protection checks
2. FarmingHoeItem clear action
3. existing valid planting action when plot is CLEARED
4. existing crop/flower interaction if applicable
5. normal fallback behavior
```

The agent must adapt this ordering to the actual event architecture in the project.

The key guarantee is that `FarmingHoeItem` has a reliable way to clear an assigned plot.

---

## 11. Permissions and Game Modes

The feature must honor existing project behavior for:

- house/lot ownership;
- Adventure mode;
- Creative/admin behavior;
- server authority;
- multiplayer interaction.

Do not trust client-only state for assignment changes.

Any state-changing operation must be authoritative on the server.

If existing flower/crop systems contain special creative/admin protections, preserve them.

---

## 12. Drops and Exploit Prevention

Harvesting a house plot must not allow duplicate drops compared with ordinary farming.

Specific risks to test:

- harvest callback plus block-break callback both awarding drops;
- hoe clear awarding harvest drops when it should only clear;
- upper segments of tall crops dropping independently;
- reconnect or chunk reload duplicating mature harvest state;
- rejected replant attempts consuming items;
- client/server double processing.

Unless the current `FarmingHoeItem` normally yields an item from clearing a crop, the house-plot reset action should not be treated as a harvest.

---

## 13. Compatibility Strategy

### 13.1 Future crops

The implementation should aim for future crops to work automatically when they are added to the existing farming registry.

Preferred:

```text
house plot accepts anything the standard farming system says is plantable
```

Avoid maintaining a second hardcoded list.

### 13.2 Future flowers

Use the existing flower registry/species data so new valid flower species inherit house-plot support.

### 13.3 Existing worlds

Existing placed `HouseFarmPlotBlock` instances must load safely.

A migration/default rule is required:

```text
legacy plot with no new persistent data
    -> treat as UNINITIALIZED
    -> initialize once with red poppy
```

After a user intentionally hoes that migrated plot, the CLEARED state must persist.

### 13.4 Standard farmland regression safety

Ordinary farm blocks must retain current behavior.

Shared refactors must be covered by regression tests.

---

## 14. Suggested Architecture

The implementation agent must first discover the actual codebase, but the desired architectural shape is:

```text
Existing planting/harvest/growth system
                |
                v
       shared farmable-surface logic
          /                  \
         /                    \
ordinary FarmingBlock     HouseFarmPlotBlock
                               |
                               +-- persistent assignment
                               +-- default red poppy
                               +-- FarmingHoeItem reset
                               +-- custom crop render anchor
```

House-specific code should contain only behavior that is genuinely unique to the house farm plot.

Everything else should delegate to shared farming behavior.

---

## 15. Acceptance Criteria

The feature is complete when all of the following are true.

### Core integration

- `HouseFarmPlotBlock` can participate in the same farming pipeline as the ordinary farm block.
- Existing valid crops can be planted after the plot is cleared.
- Existing valid flowers can be planted after the plot is cleared.
- Invalid plants are rejected exactly as the ordinary farming system rejects them.

### Default behavior

- A newly initialized house farm plot is assigned to the existing red poppy.
- The plot visually displays the existing red poppy model through the established flower system.
- The default assignment is not implemented as a duplicate flower definition.

### Persistence

- The assigned plant survives chunk unload/reload.
- The assigned plant survives save/reload.
- The assigned plant survives server restart.
- Harvesting does not clear the assignment.
- Repeated harvest/regrow cycles keep producing the same assigned crop.
- A hoe-cleared plot stays cleared after reload and does not spontaneously restore the default poppy.

### Reset/reassignment

- `FarmingHoeItem` clears the assigned plant.
- The house farm plot block itself remains in place.
- The cleared plot accepts a new valid crop/flower.
- The newly planted species becomes the new persistent assignment.
- A different plant cannot silently replace an assignment without first using the farming hoe.

### Rendering

- Existing crop models are reused.
- Existing flower models are reused.
- Plants sit correctly on the house plot's actual planting surface.
- No visible floating/sinking occurs across growth stages.
- Tall crops, if valid on normal farmland, remain aligned and clean up correctly.

### Gameplay regression

- Ordinary farming blocks behave exactly as before.
- Crop growth timing remains unchanged.
- Harvest tools and drops remain unchanged.
- Farming skill behavior remains unchanged.
- Existing flower behavior remains unchanged.
- Multiplayer synchronization is correct.
- Existing protection/ownership rules are respected.

### Quality

- No duplicate crop registry/list is introduced.
- No duplicate crop growth engine is introduced.
- No duplicate per-house-plot model set is introduced.
- Feature-specific code is documented where the persistent-assignment behavior differs from normal farming.

---

## 16. Required Manual Test Matrix

At minimum, test the following in a real development world.

| Test | Expected Result |
|---|---|
| Place/newly initialize house farm plot | Existing red poppy appears and is assigned |
| Let default poppy grow | Existing flower growth logic is used |
| Harvest default poppy | Correct harvest behavior; poppy assignment remains |
| Wait for regrowth | Red poppy grows again |
| Repeat harvest 3 times | Assignment never changes |
| Use `FarmingHoeItem` | Plant is cleared; plot remains |
| Reload chunk | Plot remains cleared |
| Restart server | Plot remains cleared |
| Plant another flower | New flower becomes persistent assignment |
| Harvest/reload | New flower remains assigned |
| Hoe and plant normal crop | Crop becomes persistent assignment |
| Harvest crop repeatedly | Same crop regrows |
| Test each crop geometry family | Model anchor is correct |
| Test a tall crop | Root and upper segments align and clean up |
| Attempt plant replacement without hoe | Rejected; no item loss |
| Test ordinary farmland | No behavior regression |
| Test Adventure mode | Existing rules preserved |
| Test Creative/admin | Existing rules preserved |
| Test second multiplayer client | State and visuals sync |
| Break/remove plot legitimately | No orphan crop state remains |

---

## 17. Automated Test Expectations

Follow the project's existing test style.

Where practical, add tests for:

- serialization/deserialization of assignment state;
- UNINITIALIZED -> default red poppy initialization;
- ASSIGNED -> CLEARED via farming hoe;
- CLEARED -> ASSIGNED via valid planting;
- harvest preserving assignment;
- invalid replacement attempts;
- ordinary farming regression around any shared code refactor;
- invalid/missing assigned plant ID load behavior.

Do not create brittle render tests if the project does not already have infrastructure for them. Geometry must still be manually validated in-game.

---

## 18. Logging / Debugging

Avoid permanent noisy logging.

During development, focused debug output may help verify:

- plot persistent state;
- assigned plant identifier;
- plant activation after load;
- farming hoe reset;
- model anchor values.

Remove or gate temporary diagnostics before final completion.

---

## 19. Documentation Requirements

Implementation should leave concise code comments around the non-obvious rule:

> The house farm plot stores a persistent plant assignment that survives harvest and is cleared only by `FarmingHoeItem`.

Update any existing farming architecture documentation if the project has a canonical system document.

Do not leave stale TODOs or temporary migration notes.

---

## 20. Implementation Decision Rules for the Agent

When the repository differs from assumptions in this document:

1. Prefer the repository's existing architecture over invented abstractions.
2. Preserve the gameplay contract in this document.
3. Reuse shared code instead of duplicating behavior.
4. Make the smallest refactor that yields a clean integration.
5. Do not modify unrelated systems.
6. Do not guess class names, registry IDs, model paths, or network APIs.
7. Record any important architecture decision in the implementation summary.

---

## 21. Definition of Done

This feature is done when `HouseFarmPlotBlock` behaves as a visually compatible, persistent-assignment version of the normal UltimaCraft farming surface:

```text
new plot
  -> red poppy by default

harvest
  -> same plant regrows indefinitely

FarmingHoeItem
  -> clears assignment

next valid planting
  -> becomes the new permanent crop

all ordinary farming behavior
  -> reused, not duplicated
```

The final implementation must be clean enough that the house farm plot feels like part of the original farming system rather than a special-case subsystem.
