# Kickoff Prompt — Unified Stone Slate Roof Project

We are beginning a new implementation project in the UltimaCraft / Britannia Mod repository.

The project is:

**Unified Stone Slate Roof System**

The project will ultimately:

- repair the defective existing Slate Roof;
- add Sandstone Roof;
- add Limestone Roof;
- give all three roofs six persistent texture variations;
- randomly choose a variation on placement;
- allow the Interior Decorator Tool to cycle the six variations;
- extract Sandstone and Limestone artwork from the Illustrator master at `C:\projects\britannia\raw fiels\roof.ai`;
- preserve historical world compatibility;
- preserve the current Iris/Photon-safe adaptive roof rendering pipeline.

For this kickoff, begin the project carefully and complete **Milestone 0 only**.

Do not start the feature implementation yet.

## Important discoveries already established

The existing slate architecture is defective and split between unrelated systems.

Currently:

```text
britannia_mod:slate_roof
    = historical StairBlock

britannia_mod:slate_roof_flat
    = TopOnlySlabBlock

britannia_mod:slate_roof_1_flat
    = TopOnlySlabBlock

britannia_mod:slate_roof_2_flat
    = TopOnlySlabBlock
```

The three flat blocks are separate texture registrations rather than one block with variants.

That is the architecture we intend to replace player-facing.

The compatibility-safe canonical blocks for the new system are:

```text
britannia_mod:slate_roof_flat
britannia_mod:sandstone_roof
britannia_mod:limestone_roof
```

Their player-facing names will be:

```text
Slate Roof
Sandstone Roof
Limestone Roof
```

Do **not** repurpose `britannia_mod:slate_roof`.

It is already a stair block with historical `facing`, `half`, and stair `shape` states.

Changing that registry ID into a top-only roof would damage existing saved states.

Retain historical registrations:

```text
slate_roof_base
slate_roof
slate_roof_1_flat
slate_roof_2_flat
```

Compatibility registrations may eventually be hidden from the creative inventory, but they must not simply be deleted.

## Intended architecture

The planned reusable behavior is conceptually:

```text
VariantTopOnlySlabBlock
    extends TopOnlySlabBlock
    implements VariantCyclable
```

with:

```text
variation = 0..5
```

All three canonical roof materials should be instances of the same reusable implementation.

Do not create three duplicated Java block classes.

Future placement behavior will be:

```text
place canonical roof item
        ↓
server-authoritative random 0..5
        ↓
persist variation in BlockState
```

Future decorator behavior will be:

```text
0 → 1 → 2 → 3 → 4 → 5 → 0
```

Cycling must eventually preserve all unrelated properties and adaptive roof data.

Do not implement this during Milestone 0.

## Critical rendering constraint

The current shader-safe architecture is:

```text
AdaptiveRoofBlockEntity
        ↓ ModelData
AdaptiveRoofBakedModel
        ↓ FaceBakery / terrain quads
```

An older `AdaptiveRoofRenderer` once existed.

It was first changed to `Sheets.solidBlockSheet()` and was then removed entirely in favor of the stronger baked-terrain approach.

The current solution is the baked model.

Do not restore `AdaptiveRoofRenderer`.

Do not introduce a new `RenderType.solid()` block-entity renderer.

Preserving the terrain baked-model architecture is a release requirement.

## Illustrator source

The authoritative new artwork source is:

`C:\projects\britannia\raw fiels\roof.ai`

Do not modify the master file.

Previous inspection reported:

```text
sandstone:
    six 1254×1254 RGB images

limestone:
    six 1254×1254 RGB images
```

It also reported that two sandstone entries were byte-for-byte identical, meaning the source appeared to contain six sandstone slots but only five unique sandstone designs.

Do not fix or fabricate anything yet.

During Milestone 0, only verify that the source still exists and record the finding in the project scratchpad.

Extraction belongs to a later milestone.

## Milestone 0 tasks

First inspect the current repository and git state.

Report:

```text
current branch
HEAD
worktree status
relevant recent roof commits
```

Do not assume the code is identical to the earlier discovery if the branch has moved.

Create or update:

```text
docs/projects/stone-slate-roofs/PROJECT.md
```

This is the persistent project scratchpad.

It should contain:

```text
Project objective
Locked architectural decisions
Canonical registry IDs
Legacy compatibility IDs
Current roof architecture
Rendering constraints
Illustrator source
Known asset inventory
Known sandstone duplicate
Milestone checklist
Baseline test commands/results
Decisions made during implementation
Risks / blockers
Current status
```

Then re-verify the discovery against source.

Inspect at minimum:

```text
TopOnlySlabBlock
VariantCyclable
AdaptiveRoofBlockEntity
AdaptiveRoofBakedModel
AdaptiveRoofClientModels
InteriorDecoratorToolItem
BlockRegistry
ItemRegistry
CreativeTabRegistry
BlockEntityRegistry
existing slate blockstates/models/textures
existing roof tests
```

Confirm whether:

1. `slate_roof` is still a `StairBlock`.
2. The three historical flat slate IDs still exist.
3. `slate_roof_flat` is suitable to become the canonical repaired Slate Roof.
4. `TopOnlySlabBlock` still has `TYPE`, `WATERLOGGED`, and `SUPPORTS_LANTERN`.
5. Initial placement still forces waterlogging false or whether that has already changed.
6. The Interior Decorator Tool still conflicts with `TopOnlySlabBlock` acquired-bottom clearing.
7. `VariantCyclable` still provides the expected server-authoritative property cycling.
8. `AdaptiveRoofBakedModel` is still the active rendering solution.
9. `AdaptiveRoofRenderer` remains absent.
10. `AdaptiveRoofClientModels` still requires explicit/hard-coded roof IDs or whether that architecture has changed.
11. Existing flat slate art still consists of only three known variations.
12. `roof.ai` still contains the expected Sandstone and Limestone source assets.

Search git history only where it helps verify intent or compatibility.

Do not resurrect old implementations simply because they exist in history.

## Baseline testing

Before making feature changes, run the focused existing tests relevant to:

```text
TopOnlySlabBlock
adaptive roof model/model data
VariantCyclable
Flagstone variation
Sandstone paver variation
creative registry integrity
roof-related GameTests
```

Also determine the appropriate full-test command that later milestones should use.

Record exact commands and results in the scratchpad.

If a baseline test already fails, distinguish that clearly from a regression caused by this project.

## Milestone plan to record

Add these milestones to the scratchpad:

```text
M0  Project foundation and baseline
M1  Reusable six-variant top-only roof block
M2  TopOnlySlabBlock hardening
M3  Existing Slate Roof repair
M4  Illustrator texture extraction and validation
M5  Sandstone + Limestone block registration
M6  Models, loot, mining, localization, resource completion
M7  Legacy compatibility / migration acceptance
M8  Automated acceptance suite
M9  Dedicated-server + Iris/Photon visual acceptance
M10 Cleanup, documentation, release handoff
```

Mark only Milestone 0 as active.

Do not implement M1 or later work during this kickoff.

## Safety rules

Do not:

- delete legacy slate registry IDs;
- repurpose `slate_roof`;
- generate placeholder textures;
- modify `roof.ai`;
- add recipes;
- restore `AdaptiveRoofRenderer`;
- add renderer-time texture randomness;
- expose six separate inventory items per material;
- begin broad cleanup of unrelated roof code;
- change behavior merely because something looks architecturally untidy.

The repository and existing world compatibility are the authority.

## Milestone 0 completion report

When finished, return:

### 1. Git/worktree state

Branch, HEAD, cleanliness, and any pre-existing changes.

### 2. Discovery revalidation

What remains true, what has changed since discovery, and exact file references.

### 3. Registry contract

Canonical IDs and legacy IDs now frozen for this project.

### 4. Rendering contract

Confirm the active adaptive rendering path and shader-regression constraints.

### 5. Asset-source status

Confirm `roof.ai`, Sandstone/Limestone source counts if inspectable, and duplicate status.

Do not export the textures yet.

### 6. Baseline tests

Exact commands and results.

### 7. Project scratchpad

Give the path and summarize what was recorded.

### 8. Risks or blockers

Only report genuine blockers.

Do not turn implementation choices already resolved by this prompt back into questions.

### 9. Milestone 0 verdict

Explicitly state either:

```text
MILESTONE 0: PASS
```

or:

```text
MILESTONE 0: BLOCKED
```

and explain why.

### 10. Next milestone

End with the concrete scope for Milestone 1, but do not begin implementing it.

The goal of this kickoff is to give the project a trustworthy baseline and persistent context before any registry or blockstate changes are made.
