# UltimaCraft Project: Unified Stone Slate Roof System

## Project objective

Rebuild the existing defective slate-roof implementation around the established `TopOnlySlabBlock` / adaptive baked-model architecture, then add two new matching roof materials:

- Slate Roof
- Sandstone Roof
- Limestone Roof

All three player-facing roofs must:

- use the same reusable `TopOnlySlabBlock`-based implementation;
- expose one canonical block/item per material;
- contain exactly six persistent visual variations;
- choose a variation randomly when placed;
- allow the Interior Decorator Tool to cycle through all six variations;
- preserve unrelated block state and adaptive lower-texture data when cycled;
- render through the existing shader-safe `AdaptiveRoofBakedModel` terrain pipeline;
- behave correctly on dedicated servers;
- remain safe for existing worlds containing historical slate IDs.

The project must also extract the sandstone and limestone artwork from:

`C:\projects\britannia\raw fiels\roof.ai`

The Illustrator file is the artwork master and must not be modified.

---

# Locked architectural decisions

These decisions should be treated as the project baseline unless implementation evidence proves one impossible.

### Canonical player-facing blocks

Use:

```text
britannia_mod:slate_roof_flat
britannia_mod:sandstone_roof
britannia_mod:limestone_roof
```

Player-facing names:

```text
Slate Roof
Sandstone Roof
Limestone Roof
```

`slate_roof_flat` remains the internal canonical ID for the repaired slate material because `britannia_mod:slate_roof` is already historically registered as a `StairBlock`.

Do not repurpose `britannia_mod:slate_roof`.

### Legacy slate registrations

Retain these registrations for world compatibility:

```text
slate_roof_base
slate_roof
slate_roof_1_flat
slate_roof_2_flat
```

The old `slate_roof` stair remains loadable.

The old `_1_flat` and `_2_flat` blocks/items remain loadable but cease being normal player-facing choices.

Do not delete old registry IDs.

Do not automatically migrate them unless a later milestone proves an automatic conversion is both useful and safe.

### Variant representation

Use one persistent integer blockstate property:

```text
variation = 0..5
```

Mapping:

```text
0 -> texture 1
1 -> texture 2
2 -> texture 3
3 -> texture 4
4 -> texture 5
5 -> texture 6
```

Do not use weighted JSON randomness or renderer-time randomness.

Placement selects the state once. That state then persists until deliberately changed.

### Item behavior

There is one inventory item per material.

Pick Block, normal drops, and Silk Touch should return the canonical material item rather than preserving an exact texture variant.

Placing that item chooses a new random variation.

### Rendering

Preserve:

```text
AdaptiveRoofBlockEntity
        ↓ ModelData
AdaptiveRoofBakedModel
        ↓
ordinary terrain/chunk quads
```

Do not restore `AdaptiveRoofRenderer`.

Do not introduce a new block-entity `RenderType.solid()` renderer.

### Recipes

Crafting recipes are outside this project unless an existing canonical roof recipe convention is discovered during implementation.

Loot tables and mining tags are not optional.

---

# Milestone 0 — Project foundation and safety baseline

## Goal

Establish a clean implementation workspace and freeze the compatibility contract before modifying registrations.

## Work

Create a project scratchpad, preferably:

```text
docs/projects/stone-slate-roofs/PROJECT.md
```

Record:

- project objective;
- milestone checklist;
- current branch and HEAD;
- baseline test results;
- legacy IDs;
- canonical IDs;
- rendering constraints;
- asset-source path;
- unresolved asset issues;
- test commands;
- implementation decisions made during later milestones.

Inspect the current source again before editing because the discovery report is a snapshot and the branch may have moved.

Verify the important discoveries:

```text
slate_roof              = historical StairBlock
slate_roof_flat         = TopOnlySlabBlock
slate_roof_1_flat       = TopOnlySlabBlock
slate_roof_2_flat       = TopOnlySlabBlock
```

Confirm the current implementations of:

- `TopOnlySlabBlock`
- `VariantCyclable`
- `AdaptiveRoofBlockEntity`
- `AdaptiveRoofBakedModel`
- `AdaptiveRoofClientModels`
- Interior Decorator Tool
- block/item/creative registrations

Run the relevant existing focused JUnit and GameTest suites before changes.

Record the exact baseline.

## Gate

Milestone 0 passes when:

- the working tree is understood;
- legacy IDs are frozen;
- canonical IDs are documented;
- baseline tests pass or pre-existing failures are explicitly documented;
- no feature implementation has begun accidentally.

---

# Milestone 1 — Reusable six-variant top-only roof block

## Goal

Build the generic behavior required by all three materials.

## Architecture

Prefer a reusable class such as:

```java
VariantTopOnlySlabBlock
    extends TopOnlySlabBlock
    implements VariantCyclable
```

The exact class name may follow repository naming conventions, but it must not contain material-specific behavior.

It should be instantiated for Slate, Sandstone, and Limestone rather than copied into three classes.

## Required behavior

Add:

```text
IntegerProperty variation = 0..5
```

Default:

```text
variation=0
```

Normal placement must:

- retain the intended top-only slab state;
- correctly preserve waterlogging semantics;
- choose `0..5` through the authoritative world/server random source;
- store that value in blockstate.

The selected variation must not change because of:

- chunk rebuild;
- client reload;
- neighboring updates;
- rendering;
- relighting;
- shader state.

Implement deterministic helper logic where useful so placement randomness can be unit tested with seeded `RandomSource`.

## Interior Decorator integration

Implement `VariantCyclable`.

Decorator use must cycle:

```text
0 → 1 → 2 → 3 → 4 → 5 → 0
```

The interaction must mutate the existing `BlockState`, not rebuild from `defaultBlockState()`.

Cycling must preserve:

- `TYPE`
- `WATERLOGGED`
- `SUPPORTS_LANTERN`
- all future unrelated properties
- the existing `AdaptiveRoofBlockEntity`
- `BottomTexture`

The roof-specific `useItemOn()` must consume the decorator interaction so it does not fall through to the old `TopOnlySlabBlock` behavior that clears the acquired bottom texture.

## Tests

Add ordinary tests covering:

- exact allowed values `{0,1,2,3,4,5}`;
- default state;
- seeded placement variation;
- full cycling sequence;
- wraparound;
- preservation of all unrelated properties.

## Gate

Milestone 1 passes when the generic class works independently of any particular roof artwork and its behavior is covered by tests.

---

# Milestone 2 — Harden the `TopOnlySlabBlock` foundation

## Goal

Fix foundation defects uncovered during discovery without destabilizing existing cedar, thatch, or tile roofs.

## Work

Review and correct initial waterlogging.

The current implementation forces:

```text
waterlogged=false
```

even when placed into water.

Make placement use correct world/fluid state while continuing to force the intended top slab geometry.

Review the acquired-bottom geometry contract.

The current behavior can produce:

```text
rendered full block
collision full block
support/occlusion full block
selection outline top half
```

Determine the intended selection behavior from the visual/interaction contract and make it internally consistent.

Prefer synchronized blockstate such as `SUPPORTS_LANTERN` for state-sensitive shapes rather than client-unsuitable direct block-entity inspection where appropriate.

Do not broaden this into an unrelated roof rewrite.

Test existing:

- tile roof;
- cedar roof;
- thatch roof;
- adaptive lower-texture behavior.

## Important non-goal

Do not restore the abandoned `AdaptiveRoofBlock`.

Do not resurrect obsolete renderer architecture.

## Gate

All existing top-only roofs retain their expected rendering and acquired-bottom behavior while the foundation defects are covered by regression tests.

---

# Milestone 3 — Repair the existing Slate Roof

## Goal

Replace the defective player-facing three-block texture system with one six-variation canonical slate roof.

## Canonical implementation

Upgrade:

```text
britannia_mod:slate_roof_flat
```

to the new six-variant roof class.

Display name:

```text
Slate Roof
```

Old states lacking the `variation` property should naturally load into the default variation where Minecraft's normal blockstate decoding permits it.

Do not change the historical `slate_roof` stair into this block.

## Legacy handling

Keep:

```text
slate_roof
slate_roof_base
slate_roof_1_flat
slate_roof_2_flat
```

registered.

Remove obsolete slate variants from normal creative-tab exposure.

Do not remove their item registrations if existing inventories could contain them.

The player-facing creative inventory should ultimately contain only one Slate Roof item.

## Slate artwork

Inventory all current slate artwork again.

Known discovery state:

```text
slate_roof_flat.png
slate_roof_1_flat.png
slate_roof_2_flat.png
```

represent three distinct flat artworks.

Search the repository and raw-art directories for the intended remaining three slate variants before declaring them missing.

Do not treat `slate_texture.png` from the historical stair geometry as an automatic fourth variation.

Do not fabricate replacement art.

If only three usable slate variations exist, complete the code architecture and document the missing three assets as the sole asset gate for final slate visual acceptance.

## Models

The canonical Slate Roof must resolve deterministically:

```text
variation=0 -> slate texture 1
...
variation=5 -> slate texture 6
```

No weighted model arrays.

## Gate

Slate Roof exists as one canonical item/block with six-state architecture, legacy IDs remain valid, and the creative inventory no longer presents the old texture variants as separate roof choices.

---

# Milestone 4 — Extract and normalize Illustrator roof artwork

## Goal

Create production-ready Sandstone and Limestone texture sets from the authoritative Illustrator source.

## Source

Use:

`C:\projects\britannia\raw fiels\roof.ai`

Do not modify or overwrite this file.

Before extraction, create a reproducible inventory of the Illustrator content.

The discovery indicates:

```text
sandstone layer: 6 images, 1254×1254 RGB
limestone layer: 6 images, 1254×1254 RGB
```

Verify this against the actual file at implementation time.

## Extraction strategy

Use the safest available local extraction path.

Prefer a reproducible scripted/export workflow where possible.

If the `.ai` file is PDF-compatible and its embedded assets can be extracted losslessly, use that capability.

If Illustrator automation or another installed local conversion tool is required, document exactly what is used.

Do not rasterize the entire Illustrator canvas blindly if individual artwork objects can be exported directly.

Do not make visual changes to the artwork during extraction.

## Ordering

Preserve and document source layer/object ordering.

Map explicitly:

```text
sandstone source 1 -> sandstone_roof_1.png
...
sandstone source 6 -> sandstone_roof_6.png

limestone source 1 -> limestone_roof_1.png
...
limestone source 6 -> limestone_roof_6.png
```

Do not let filesystem ordering accidentally determine variation numbering.

## Game normalization

Recommended production target:

```text
64×64 PNG
RGB/RGBA as appropriate
square
```

Use a high-quality reduction method appropriate for authored Minecraft textures.

Do not stretch the artwork.

Do not crop differently between variants.

Preserve seamless edge behavior.

Store normalized files under the repository's established roof texture directory.

## Duplicate sandstone gate

Discovery found that two of the six sandstone source images were byte-for-byte identical.

Re-test this from the extracted originals.

If the duplicate still exists:

- identify exactly which source positions are duplicated;
- do not silently invent a replacement;
- document the problem prominently in the scratchpad;
- preserve the six-slot architecture;
- use the duplicate temporarily only if necessary for compilation;
- mark final sandstone visual acceptance blocked until a sixth unique artwork is supplied or the duplication is explicitly accepted.

The project must never pretend five unique textures are six unique textures.

## Automated asset contract

Validate:

- six filenames per material;
- valid PNG files;
- identical dimensions;
- expected color mode;
- no zero-byte/corrupt files;
- no accidental duplicates;
- expected numeric order.

## Gate

Milestone 4 passes technically when both six-file texture sets can be generated reproducibly and validated.

Final visual acceptance remains blocked if sandstone still has only five unique designs.

---

# Milestone 5 — Add Sandstone Roof and Limestone Roof

## Goal

Register the two new canonical blocks using exactly the same behavior as the repaired Slate Roof.

## Registry IDs

Create:

```text
britannia_mod:sandstone_roof
britannia_mod:limestone_roof
```

Display names:

```text
Sandstone Roof
Limestone Roof
```

Both must be instances of the same reusable variant top-only roof class used by Slate Roof.

Do not create material-specific Java subclasses merely to select textures.

## Registration

Integrate with:

- `BlockRegistry`
- `ItemRegistry`
- `CreativeTabRegistry`
- `BlockEntityRegistry.ADAPTIVE_ROOF`
- adaptive client-model wrapping
- localization

The creative tab should expose exactly:

```text
Slate Roof
Sandstone Roof
Limestone Roof
```

for this stone-slate roof family.

## Adaptive model wrapping

Investigate replacing the hard-coded `AdaptiveRoofClientModels` ID set with a safe type-based rule covering `TopOnlySlabBlock` instances.

If implemented, exclude inventory models where required and add regression tests.

The goal is that future compatible roof blocks cannot silently forget to opt into shader-safe adaptive rendering.

## Gate

All three canonical materials register, place, render, randomize, cycle, persist, and create valid adaptive block entities.

---

# Milestone 6 — Resource, loot, mining, and model completion

## Goal

Finish the game-data contract for all three canonical roofs.

## Models

Prefer one shared top-only slab geometry parent with small material/variation models selecting individual textures.

Each material requires deterministic mappings for all six variations.

The item model should display variation 1 consistently.

Ensure no state points to nonexistent:

```text
bottom
top
double
```

models.

If `TopOnlySlabBlock` necessarily inherits impossible slab states, provide valid fallback model coverage or otherwise resolve the asset contract cleanly.

Do not leave dangling `_double` model references.

## Localization

Provide canonical names.

Legacy names may remain for loadability but should not present themselves as normal new creative choices.

## Loot

All three canonical roofs must drop their canonical item correctly.

Test ordinary break and Silk Touch behavior.

Exact variation does not need to survive itemization.

## Mining

Add the appropriate canonical roof blocks to the expected pickaxe mining tags and any other required stone-material tags consistent with repository behavior.

## Recipes

Do not invent recipes as part of this milestone.

If recipes become desired, treat them as a separately specified gameplay decision.

## Gate

No missing models, textures, localization, loot tables, or required mining tags remain for the canonical blocks.

---

# Milestone 7 — Compatibility and migration acceptance

## Goal

Prove that fixing the slate system does not damage historical worlds or inventories.

## Required legacy contract

Verify all historical registrations still resolve:

```text
slate_roof_base
slate_roof
slate_roof_flat
slate_roof_1_flat
slate_roof_2_flat
```

Verify the historical stair preserves its stair properties and remains a `StairBlock`.

Verify legacy `_1_flat` and `_2_flat` instances remain loadable.

Verify their historical items remain valid if present in saved inventories.

Verify they are not exposed as normal creative choices.

## Automatic migration

Default policy for this project is no aggressive automatic conversion.

If implementation uncovers a compelling reason to migrate `_1_flat` and `_2_flat` in-world, design that as an explicit sub-milestone with tests proving preservation of:

- bottom texture NBT;
- waterlogging;
- support state;
- correct variation mapping;
- inventories;
- chunk reload.

Never delete the old IDs even after an automatic migration unless a future release strategy deliberately introduces a data-fix lifecycle.

## Gate

Existing IDs load safely and the new canonical Slate Roof does not redefine the historical stair contract.

---

# Milestone 8 — Automated acceptance suite

## Goal

Create enough coverage that this architecture cannot regress silently.

## JUnit coverage

Cover at least:

- variant property exact range;
- random-placement helper;
- cycling;
- wraparound;
- unrelated state preservation;
- geometry parity among Slate/Sandstone/Limestone;
- registration contract;
- asset existence;
- six models per material;
- six textures per material;
- deterministic blockstate mapping;
- item-model validity;
- creative exposure;
- loot-table existence;
- mining-tag coverage;
- adaptive-model wrapping;
- legacy registry contract;
- absence of the removed `AdaptiveRoofRenderer`.

## GameTests

Cover at least:

- all three canonical blocks in live registries;
- actual `BlockItem` placement;
- legal random variation after placement;
- repeated placement reaching multiple variants;
- Interior Decorator full six-step cycle;
- preservation of `TYPE`;
- preservation of `WATERLOGGED`;
- preservation of `SUPPORTS_LANTERN`;
- preservation of `BottomTexture`;
- server-to-client state update semantics where testable;
- serialization/reload persistence;
- valid adaptive block entity;
- canonical block drops;
- legacy IDs remain valid;
- creative tab contains canonical entries and excludes compatibility variants.

Do not confuse `gameTestServer` with a true dedicated server when documenting acceptance.

## Gate

All targeted and full project test suites pass with no unexplained regressions.

---

# Milestone 9 — Live client, dedicated-server, and shader acceptance

## Goal

Validate what automated tests cannot fully prove.

## Dedicated-server smoke test

Verify on an actual dedicated server:

- Slate, Sandstone, and Limestone Roof placement;
- random variation synchronization;
- Interior Decorator cycling;
- second-client visibility where feasible;
- persistence after server restart;
- acquired lower texture preservation;
- Adventure/build-permission behavior remains consistent with existing decorator systems.

## Client visual acceptance

Inspect all six variations for each material in a controlled test roof.

Check:

- texture ordering;
- scale;
- seams;
- variation diversity;
- item model;
- top slab geometry;
- acquired-bottom rendering;
- neighboring block faces;
- selection/collision behavior.

## Iris + Photon regression test

Test with the shader configuration that previously exposed the adaptive-roof rendering defect.

Acceptance requires normal terrain-like lighting.

There must be no:

- near-black roof geometry;
- broken normals;
- incompatible terrain/entity shader path;
- restored block-entity renderer;
- lighting mismatch between top and adaptive bottom geometry.

## Sandstone asset gate

Explicitly verify whether all six sandstone textures are visually unique.

If the Illustrator source still supplies only five unique images, mark the feature technically complete but visual-art acceptance blocked until the sixth artwork is resolved.

## Gate

The three roofs behave and render correctly in the real gameplay environments that previously exposed roof defects.

---

# Milestone 10 — Cleanup, documentation, and release handoff

## Goal

Leave the repository understandable and release-ready.

Update the project scratchpad with:

- final architecture;
- canonical IDs;
- retained legacy IDs;
- texture mapping;
- Illustrator extraction procedure;
- test commands;
- dedicated-server results;
- Iris/Photon results;
- any consciously deferred migration;
- any consciously deferred recipe design;
- sandstone duplicate resolution.

Review dead roof code such as `AdaptiveRoofBlock` separately.

Remove it only if repository analysis proves it is genuinely unused and its cleanup is low-risk.

Do not combine speculative cleanup with functional roof changes merely to make the diff prettier.

Run the complete relevant test suite.

Produce a final implementation report containing:

```text
Summary
Architecture
Registry changes
Legacy compatibility
Texture extraction
Asset mapping
Testing
Live acceptance
Known limitations
Commits/files changed
Release recommendation
```

## Final project acceptance

The project is complete when:

1. Slate Roof is one corrected six-variation player-facing block.
2. Sandstone Roof is one six-variation block.
3. Limestone Roof is one six-variation block.
4. Placement randomly selects a persistent variation.
5. Interior Decorator cycles all six variations.
6. Cycling preserves all unrelated adaptive roof state.
7. All three use the established TopOnlySlab/adaptive baked-model architecture.
8. Historical slate IDs remain loadable.
9. The historical `slate_roof` stair has not been repurposed.
10. Creative inventory contains only the intended canonical roof choices.
11. Sandstone and Limestone artwork comes reproducibly from `roof.ai`.
12. All asset/model/loot/mining contracts are complete.
13. Automated tests pass.
14. Dedicated-server testing passes.
15. Iris/Photon testing passes.
16. No obsolete block-entity roof renderer has been reintroduced.
17. Any unresolved sixth sandstone artwork is explicitly documented rather than hidden.
