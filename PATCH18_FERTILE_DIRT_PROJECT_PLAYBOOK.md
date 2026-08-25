# Patch 18 Fertile Dirt Recovery Project Playbook

## Project purpose

Patch 18 exposed a critical progression problem in UltimaCraft farming: `fertile dirt` is too inaccessible when its only acquisition path is Earth Elemental loot.

This project adds a deliberate, time-gated, world-integrated crafting path for fertile dirt while preserving Earth Elementals as a valid source. The new path must feel like Ultima rather than a vanilla Minecraft recipe and must integrate with the existing `WildResource`, farming, custom-tool, water-well, Adventure-mode, and multiplayer/server-authoritative systems.

This is a multi-milestone project. Do not implement it as one broad patch.

---

## Canonical gameplay loop

The intended player loop is:

1. Find naturally spawned `dung` in the world.
2. Break the dung block to obtain a `dung` item.
3. Use the UltimaCraft custom shovel on a normal dirt or coarse dirt block to gather a `dirt` item.
   - The world dirt block is **not broken or replaced**.
   - This is **not Mining** and must not route through the managed mining/deposit system.
   - Gathering must have a meaningful cooldown/time cost so dirt cannot be spam-farmed.
4. Obtain an UltimaCraft `empty_bowl`.
   - Do **not** use the vanilla Minecraft bowl item.
5. Hold `empty_bowl` in the main hand and UltimaCraft `dirt` in the offhand, then right-click to produce `bowl_of_dirt`.
6. Hold `bowl_of_dirt` in the main hand and `dung` in the offhand, then right-click to produce `bowl_of_fertile_dirt`.
7. Obtain `bowl_of_water` by using an UltimaCraft empty bowl on:
   - a valid water source; or
   - an UltimaCraft water well block.
8. Hold `bowl_of_fertile_dirt` in the main hand and `bowl_of_water` in the offhand, then right-click to produce:
   - an UltimaCraft `fertile_dirt` item; and
   - the appropriate returned UltimaCraft bowl container(s), following the container-conservation decision established during Milestone 0.
9. Use fertile dirt through the existing farming system.
10. One fertile-dirt application must support **exactly five successful crop harvests** before fertility is exhausted.

Earth Elemental fertile-dirt loot remains valid. This project adds a second acquisition path; it does not remove the loot source.

---

## Product invariants

### Custom bowls only

Create and use UltimaCraft items for:

- `empty_bowl`
- `bowl_of_dirt`
- `bowl_of_fertile_dirt`
- `bowl_of_water`

Never silently substitute `minecraft:bowl`.

Existing vanilla bowls must not become valid inputs to this workflow unless a future product decision explicitly says so.

### Dirt gathering is not mining

Dirt gathering with the custom shovel must:

- work independently of Mining skill;
- not call managed-deposit extraction;
- not create or consume mining nodes;
- not break the target dirt block;
- not rely on vanilla block-break completion;
- not accidentally grant vanilla dirt through a second path;
- respect the project’s normal ownership/build-right protections where relevant.

### Dung is a WildResource

`dung` must be integrated through the existing `WildResource` architecture, not implemented as unrelated ad hoc worldgen.

Dung may spawn only on valid support blocks:

- `minecraft:dirt`
- `minecraft:coarse_dirt`

The implementation must prevent unsupported/airborne dung from persisting where the `WildResource` system normally enforces support validity.

Breaking dung must yield the intended `dung` item exactly once.

### Server authority

All inventory-changing interactions must be authoritative on the logical server.

Do not permit:

- client-only item creation;
- double consumption from both hands;
- duplicated outputs caused by interaction events firing twice;
- creative/adventure discrepancies that bypass the intended path;
- rapid packet spam bypassing the dirt-gather cooldown;
- hand swapping to bypass cooldowns;
- bowl filling from flowing water if the design resolves to source blocks only.

### Atomic transformations

Every bowl-chain step must behave as one atomic transformation:

- validate main-hand item;
- validate offhand ingredient;
- validate permission/context;
- consume the exact required inputs;
- grant the exact output(s);
- preserve or return containers according to the approved rule;
- play feedback once;
- synchronize inventory once.

If the output cannot fit in the inventory, follow the project’s established safe inventory/drop convention rather than deleting items or duplicating them.

### No vanilla crafting-table shortcut

The bowl chain is an interaction-driven Ultima-style preparation process. Do not replace it with ordinary vanilla crafting recipes unless a later milestone explicitly adds recipes as an additional path.

---

## Naming and registry expectations

Prefer clear registry IDs unless the repository already has a stricter convention:

- `dung`
- `empty_bowl`
- `dirt`
- `bowl_of_dirt`
- `bowl_of_fertile_dirt`
- `bowl_of_water`
- existing `fertile_dirt`

Do not create a second fertile-dirt identity if one already exists. Reuse the existing canonical fertile-dirt item and migrate code toward one authoritative ID.

Before registering `dirt`, verify whether UltimaCraft already has a custom dirt commodity/item. Do not create a duplicate merely because vanilla `minecraft:dirt` exists.

---

## Important Milestone 0 decisions

Milestone 0 must establish these from repository evidence before production implementation begins.

### 1. Bowl-container conservation

The final interaction consumes both `bowl_of_fertile_dirt` and `bowl_of_water`.

The product description explicitly requires fertile dirt plus an `empty_bowl`, but two custom bowls entered the interaction. Determine the project-consistent behavior by inspecting existing container transformations.

Preferred rule if no stronger repository precedent exists:

- conserve both reusable containers;
- final output is `fertile_dirt` plus **two `empty_bowl` items**.

If repository conventions or existing design clearly require only one returned bowl, document that evidence before implementing it. Do not allow one bowl to disappear accidentally because the interaction was coded as a simple item replacement.

### 2. Dirt-gather cooldown

Do not choose a cooldown arbitrarily without inspecting existing gathering/action cooldown conventions and farming consumption rates.

The selected cooldown must make dirt gathering a deliberate time investment while avoiding tedious repetition. It must be enforced server-side and should be configurable/data-driven if the existing architecture makes that practical.

Milestone 0 should recommend a concrete default and explain why.

### 3. Five-harvest fertility ownership

Determine where fertility state belongs in the current farming architecture.

The desired semantic is:

> One application/unit of fertile dirt enables five successful crop harvests.

A successful harvest decrements the remaining fertility by exactly one. Merely planting, interacting, failed harvesting, or breaking unrelated blocks must not decrement it.

Prefer storing fertility in the existing authoritative farm-plot/block-entity/state model rather than adding an unrelated global tracker.

When the fifth successful harvest completes, transition the affected farming substrate/plot to the existing non-fertile/exhausted state.

---

## Water behavior

`bowl_of_water` must be fillable from ordinary world water and the existing water-well block.

Milestone 0 must inspect how the mod currently identifies:

- water-source blocks;
- wells;
- protected-house interactions;
- Adventure-mode use interactions.

Unless repository precedent says otherwise, use these rules:

- fill from a true water source, not arbitrary flowing water;
- do not remove the world water source;
- well filling must not consume or alter the well unless the well already has a finite-water mechanic;
- the action should replace one `empty_bowl` with one `bowl_of_water`;
- creative mode must not create duplication paths.

---

## Interaction priority and input contract

The requested hand order is part of the gameplay contract:

| Main hand | Offhand | Action | Result |
| --- | --- | --- | --- |
| `empty_bowl` | custom `dirt` | Right-click | `bowl_of_dirt` |
| `bowl_of_dirt` | `dung` | Right-click | `bowl_of_fertile_dirt` |
| `bowl_of_fertile_dirt` | `bowl_of_water` | Right-click | `fertile_dirt` + returned bowl container(s) |
| `empty_bowl` | n/a | Right-click valid water/well | `bowl_of_water` |

Do not make reversed-hand combinations silently work unless the repository has a strong universal dual-hand convention and the product behavior remains deterministic.

Interaction code must avoid a main-hand handler and offhand handler each independently completing the same transformation.

---

## Dung WildResource behavior

Dung should feel like a renewable ambient resource, not an ore deposit.

Milestone 0 must inspect the existing `WildResource` spawning rules and identify the closest precedent for:

- surface-only resources;
- support-block predicates;
- respawn cadence;
- chunk loading/unloading;
- density/caps;
- biome or region constraints;
- manual placement;
- break/drop lifecycle.

The implementation should reuse that architecture.

Minimum product rule:

> A naturally generated dung block may only occupy a location whose supporting block is dirt or coarse dirt.

Do not broaden this to grass, farmland, mud, podzol, sand, stone, or custom fertile dirt unless explicitly approved later.

---

## Dirt gathering behavior

The custom shovel interaction should be implemented as a gathering action, not a block-break hack.

Required behavior:

- target block: exact vanilla dirt or coarse dirt;
- tool: the project’s intended custom shovel;
- result: one canonical UltimaCraft dirt item per successful gather unless repository balance conventions justify another fixed amount;
- target block remains dirt;
- no vanilla dirt drop occurs;
- no Mining XP or Mining progression occurs;
- cooldown is player-scoped and server authoritative;
- repeated clicks during cooldown produce no additional dirt;
- hand swapping, reconnecting, or client packet repetition must not trivially bypass it;
- respect any relevant house/build rights before granting resources.

Live Patch 18 testing confirmed that coarse dirt is also an intended gathering target. Keep the rule closed to exact vanilla dirt and coarse dirt.

---

## Fertile dirt: five-harvest rule

The existing farming lifecycle is authoritative. Do not redesign crop growth.

Add only the smallest durable concept needed to represent remaining fertile harvests.

Required semantics:

- fresh application starts at 5 harvests remaining;
- successful harvest #1 -> 4;
- #2 -> 3;
- #3 -> 2;
- #4 -> 1;
- #5 -> 0 and fertility is exhausted;
- unsuccessful interaction -> unchanged;
- planting -> unchanged unless the current farming design already consumes a harvest at a different authoritative event, in which case Milestone 0 must surface the conflict before implementation;
- state persists across chunk unload/reload and server restart;
- multiplayer sees the same authoritative count/state;
- existing fertile dirt obtained from Earth Elementals uses the same five-harvest behavior.

Do not introduce five separate fertile-dirt item variants unless the farming architecture truly stores fertility on the item itself. Prefer state on the farmable substrate/plot after application.

---

## Compatibility requirements

Preserve existing behavior for:

- Earth Elemental loot;
- existing fertile-dirt item identity;
- crop growth stages;
- farming tools;
- House Farm Plot behavior;
- WildResource systems unrelated to dung;
- Mining and managed deposits;
- Adventure-mode protection;
- multiplayer synchronization;
- existing water-well behavior except the new bowl-fill interaction.

Do not broaden scope into ore/mining refactors, commodity pricing, Rails policy, or unrelated farming balance.

---

## Asset and data requirements

The project may require:

- item registrations;
- block registration for dung;
- block/item models;
- blockstates;
- language entries;
- loot tables;
- creative-tab exposure if appropriate;
- tags;
- WildResource definition/data;
- recipe-like interaction definitions if the project has a data-driven interaction system;
- tests/data fixtures.

Before creating new textures, search for existing suitable UltimaCraft assets. If an authored dung texture or bowl artwork is missing, identify the asset gap explicitly. Do not silently ship an unrelated vanilla texture as final art.

Functional milestones should not be blocked from code/test progress by missing final artwork if the repository has an accepted placeholder/dev-asset convention, but placeholders must be clearly documented and must not be misrepresented as finished assets.

---

## Testing philosophy

Every milestone must add focused automated coverage where the repository supports it.

The final project should cover at minimum:

- WildResource support predicate;
- dung spawn rejection on invalid support;
- dung break -> one dung item;
- custom shovel dirt gather;
- target dirt remains intact;
- Mining system is not invoked;
- cooldown blocks repeated grants;
- cooldown cannot be bypassed through alternate hand;
- empty bowl is custom, not vanilla;
- water-source filling;
- water-well filling;
- no fill from invalid blocks;
- each bowl transformation;
- exact consumption/output counts;
- multiplayer/server authority;
- no duplicate interaction execution;
- five-harvest decrement;
- persistence of fertility count;
- exhaustion after fifth successful harvest;
- Earth Elemental fertile dirt uses same five-harvest rule;
- regression tests for existing farming and WildResource behavior.

Use the project’s existing GameTest/JUnit conventions instead of inventing a parallel test framework.

---

## Milestone execution rules

For every milestone:

1. Inspect before editing.
2. State the relevant existing architecture and exact files/classes/data definitions involved.
3. Keep the patch scoped to that milestone.
4. Add or update focused tests.
5. Run the narrowest meaningful test set first.
6. Run the broader relevant suite before declaring the milestone complete.
7. Report:
   - files changed;
   - behavior implemented;
   - tests run and exact results;
   - unresolved risks;
   - whether the next milestone is safe to start.
8. Do not merge branches, rebase, delete user work, or perform unrelated cleanup.
9. Preserve dirty/unrelated worktrees.
10. If the branch moves underneath the work, report divergence and continue safely without overwriting unrelated changes.

---

## Definition of done

This project is complete only when a player can perform the full loop in a real multiplayer/Adventure-mode environment:

`Wild dung -> dung item`

`Custom shovel + dirt block -> custom dirt item without breaking dirt`

`Empty bowl + dirt -> bowl of dirt`

`Bowl of dirt + dung -> bowl of fertile dirt`

`Empty bowl + water/well -> bowl of water`

`Bowl of fertile dirt + bowl of water -> fertile dirt + returned custom bowl container(s)`

`Fertile dirt -> exactly five successful harvests`

…and all of the following remain true:

- Earth Elemental fertile dirt still works.
- Vanilla bowls are not accepted as a substitute.
- Dirt gathering is not Mining.
- Dung only naturally spawns on dirt/coarse dirt.
- No inventory duplication or double-consumption path exists.
- State survives restart/reload.
- Existing farming, WildResource, protection, and managed-mining tests remain healthy.
