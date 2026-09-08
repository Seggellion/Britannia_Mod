# Unified Stone Slate Roof System — Implementation Report

Date: 2026-08-30 (America/Vancouver)

## Summary

The unified stone-roof implementation is technically complete. Slate Roof, Sandstone Roof,
and Limestone Roof are each exposed as one canonical player-facing block with six persistent
variation states. Placement selects a variation once on the logical server, the Interior
Decorator Tool cycles it, and the adaptive acquired-bottom texture continues to render through
the terrain baked-model path.

Dedicated-server restart testing and real-client Iris/Photon inspection passed. On 2026-08-30,
the user explicitly approved the documented release mapping: Slate's three unique flat designs
are mirrored across six states, and the Illustrator master's five unique Sandstone designs are
retained across six authored slots.

## Architecture

- `VariantTopOnlySlabBlock` is the shared implementation for all three canonical materials.
- It extends `TopOnlySlabBlock`, implements `VariantCyclable`, and persists `variation=0..5` in
  block state.
- The logical server selects placement variation. No renderer-time randomness is used.
- Decorator cycling changes only `variation`, preserving slab type, waterlogging,
  `supports_lantern`, and `AdaptiveRoofBlockEntity.BottomTexture` data.
- Adaptive lower faces remain in the Iris-safe terrain pipeline:
  `AdaptiveRoofBlockEntity -> ModelData -> AdaptiveRoofBakedModel -> FaceBakery quads`.
- `AdaptiveRoofRenderer` remains absent. Inventory models are not wrapped as terrain models.
- `AdaptiveRoofBlock` was reviewed but retained. It is not registered as a roof in this
  project, but `AdaptiveRoofBlockEntity` still contains compatibility handling for its
  `supports_lantern` property; removing both together is broader and riskier than M10 cleanup.

## Registry changes

Canonical player-facing IDs:

| Registry ID | Name | Implementation |
| --- | --- | --- |
| `britannia_mod:slate_roof_flat` | Slate Roof | `VariantTopOnlySlabBlock` |
| `britannia_mod:sandstone_roof` | Sandstone Roof | `VariantTopOnlySlabBlock` |
| `britannia_mod:limestone_roof` | Limestone Roof | `VariantTopOnlySlabBlock` |

Each canonical block has one block item and one normal creative-tab entry. The adaptive roof
block-entity type includes all three canonical blocks.

## Legacy compatibility

The following registrations remain intact and loadable:

- `britannia_mod:slate_roof_base`
- `britannia_mod:slate_roof` — retained as the historical `StairBlock`; never repurposed
- `britannia_mod:slate_roof_1_flat`
- `britannia_mod:slate_roof_2_flat`

Historical items remain registered while legacy choices are hidden from normal creative
exposure. State/item NBT round-trips and registered-world GameTests passed. No automatic
migration was needed or added.

## Texture extraction

The authoritative source is the read-only PDF-compatible Illustrator master
`C:\projects\britannia\raw fiels\roof.ai` (SHA-256
`9ae5a6a8b520d0710da961f0f637e246dcabefa949097df14eaeebb0354caf8a`).

`tools/roof_textures/extract_roof_textures.py` reproducibly:

1. validates the master hash, PDF structure, named layers, draw order, image dimensions, and
   color profile;
2. decodes the embedded RGB image XObjects without changing the master;
3. normalizes each full source square from 1254 x 1254 to 64 x 64 with LANCZOS;
4. writes deterministic PNGs in authored layer order; and
5. fails if provenance, ordering, dimensions, or expected duplicate relationships change.

## Asset mapping

- Slate variations `0,1,2` use the three shipped grey flat-slate designs. Variations `3,4,5`
  deterministically mirror `0,1,2`; no unsuitable historical or fabricated art was adopted.
- Sandstone filenames `sandstone_roof_1.png` through `sandstone_roof_6.png` preserve the six
  authored source positions. Positions 2 and 4 are identical in the Illustrator master.
- Limestone filenames `limestone_roof_1.png` through `limestone_roof_6.png` are six distinct
  authored designs.
- Shared parent models own top-half geometry; per-material/per-variation child models select
  texture identity. Inherited bottom/double states resolve to safe top-half fallback geometry.
- Canonical item models show authored variation 1 (`variation=0`). Loot tables and the pickaxe
  mining tag cover all three canonical blocks.

## Testing

Milestone 8 established the complete focused and registered-world acceptance matrix:

- Full JUnit: 3,290 tests, 17 skipped, 0 failures, 0 errors.
- Registered-world GameTests: 936 required tests passed.
- Roof-specific coverage includes placement through real `BlockItem.place(...)`, exact
  variation range and sampling, cycling and wraparound, state preservation, waterlogging,
  geometry, block-entity persistence/update packets, resources, drops, registries, creative
  exposure, and legacy saved identities.

Final M10 validation command:

```powershell
.\gradlew.bat test runGameTestServer --no-configuration-cache --console=plain
```

Final result: `BUILD SUCCESSFUL` in 4m 13s. JUnit recorded 430 suites and 3,290
tests: 17 skipped, 0 failures, and 0 errors. The GameTest server reported 936 tests complete
in 54.21s and `All 936 required tests passed :)`.

The first sandboxed M10 invocation could not download Gradle because network access was
restricted; this was an execution-environment failure before project compilation, not a test
failure. The same command was immediately rerun with the required execution permission.

## Live acceptance

The M9 session used a true NeoForge 1.21.1 dedicated server and a separate remote game client.
The isolated world `m9-roof-acceptance` avoided the normal sandbox save. Server configuration
was restored after the session.

Verified live:

- random server-authoritative placement for Slate, Sandstone, and Limestone;
- Interior Decorator transitions on all three materials (`0 -> 1` observed live; all six
  transitions are covered by registered-world tests);
- Adventure mode allowed decorator use while the roof resisted breaking;
- canonical variation states `0` and `5` and acquired lower textures persisted before and
  after a clean dedicated-server stop/restart;
- all 18 material/variation combinations, canonical item models, top-half geometry,
  neighboring faces, selection outline, and lighting in the real client;
- baseline rendering and Iris 1.8.0 with Photon 1.1 rendering; no black geometry, bad normals,
  terrain/entity shader mismatch, renderer regression, or lighting discontinuity was visible.

Evidence:

| Check | Screenshot |
| --- | --- |
| Photon, all variants | [m9-photon-all-variants.png](evidence/m9-photon-all-variants.png) |
| Baseline, all variants | [m9-baseline-all-variants.png](evidence/m9-baseline-all-variants.png) |
| Decorator cycles | [m9-decorator-cycles.png](evidence/m9-decorator-cycles.png) |
| Random placement | [m9-random-placement.png](evidence/m9-random-placement.png) |
| Adventure behavior | [m9-adventure-mode.png](evidence/m9-adventure-mode.png) |
| Persistence before restart | [m9-pre-restart-persistence.png](evidence/m9-pre-restart-persistence.png) |
| Persistence after restart | [m9-post-restart-persistence.png](evidence/m9-post-restart-persistence.png) |

Photon loaded and rendered correctly despite non-fatal pack-option/preprocessor warnings and
Intel OpenGL debug noise in the client log. A second simultaneous client was not practical in
the shared development run directory; this check was explicitly optional where feasible. One
real remote client exercised synchronization against the dedicated server.

## Known limitations

- Slate currently contains three unique usable grey flat designs mirrored across six legal
  states. The mirror mapping is explicitly accepted for this release; future unique designs
  4, 5, and 6 remain an optional art improvement.
- Sandstone source positions 2 and 4 are identical, leaving five unique designs across six
  legal states. The duplicate is explicitly accepted for this release; a unique sixth design
  remains an optional future master-art update.
- Recipes were deliberately deferred; none were added without an approved recipe design.
- No registry migration was added because all historical identities remain loadable.
- The M9 test world, Iris/Sodium jars, and Photon pack remain under ignored `run/` paths for
  reproducibility and are not release artifacts.

## Commits/files changed

No commit was created. The project changes are intentionally left in the existing dirty
worktree for review. Relevant change groups are:

- behavior: `VariantCyclable.java`, `TopOnlySlabBlock.java`, and
  `VariantTopOnlySlabBlock.java`;
- registration/render integration: `BlockRegistry.java`, `ItemRegistry.java`,
  `CreativeTabRegistry.java`, `BlockEntityRegistry.java`, and
  `AdaptiveRoofClientModels.java`;
- resources: canonical blockstates, shared/child roof block models, item models, roof textures,
  loot tables, localization, and the pickaxe mining tag;
- tests: roof JUnit contracts plus `TopOnlySlabFoundationGameTests` and the four
  `StoneRoof*GameTests` classes;
- tooling/docs: `tools/roof_textures/`, this report, the project scratchpad, and M9 evidence.

Pre-existing unrelated user changes listed in `PROJECT.md` were preserved.

## Roof-bottom repair follow-up (2026-09-08)

Worktree/repository: `C:/projects/britannia/mod/Britannia_Mod` (NeoForge 21.1.72, Minecraft
1.21.1), branch `patch-18`, starting HEAD `9111a8ac0f1776f22d5fa2636c79fb8ac08022ed`.
Initial status was ahead 119/behind 19, with four unrelated untracked root planning documents
and `docs/projects/gameplay-bugfixes/`. No applicable `AGENTS.md` was found in the worktree or
its parent directories. The existing README, build workflow, roof playbooks/handoff, and
testing records were inspected; unrelated project work and the Fabric sibling were preserved.

This follow-up supersedes the original report's unspecified-hand decorator cycling behavior.
The owner selected **main-hand right-click to clear the bottom** and **offhand right-click to
cycle the top**. No sneak modifier is required. Offhand use on a roof without top variations
is a consumed no-op. The existing wooden-axe bottom clear remains available.

The regression came from `5a9b7469`: it removed the decorator's original acquired-bottom clear
mapping while adding unconditional decorator top cycling. The item itself had no roof dispatch.
Sandstone Brick was separately absent from `TopOnlySlabBlock.getTextureFromItem` and therefore
fell through to ordinary block-item placement.

The repair dispatches every registered `TopOnlySlabBlock` through the decorator item before
generic rotation or nudging. Main-hand use calls the existing `setBottomTexture(null)` only
when a bottom or stale derived support flag exists. Client prediction returns success without
writing data; the server returns `CONSUME`, including for an already-clear roof, so the gesture
does not fall through to offhand cycling. Clearing makes no sound and consumes no durability
or items, matching the original clear action. Offhand cycling reuses `VariantCyclable` and its
existing sound, update flags, and six-state cycle. Its item fallback also handles sneak bypass.

`AdaptiveRoofBlockEntity` remains the single storage and synchronization path. The setter marks
the entity/chunk dirty, clears the derived `supports_lantern` flag, requests model data, and
updates clients/neighbors. Disk data omits `BottomTexture`; update packets contain the existing
`minecraft:block/air` clear sentinel, decoded to `null` and empty model data. The block, entity,
slab type, waterlogging, top variation, and unrelated persistent entity data remain intact.
These slabs have no facing, slope, corner, or connection properties. Historical stair roofs
have those properties but do not support acquired bottoms and retain their existing behavior.
The unregistered `AdaptiveRoofBlock` is not another live implementation path.

`britannia_mod:custom_sandstone_brick` now maps to
`britannia_mod:block/structure/sandstone/custom_sandstone_brick_0`, exactly matching its registered
item model. Existing `custom_sandstone_brick_0..3` and `custom_sandstone_brick_top_0..3` artwork is
unchanged. The item has no selected placed-block variation; applying it uses canonical variant
zero, as other bottom materials use their canonical item mapping. There is no material enum,
ordinal codec, tag, UI palette, or roof datagen path to update. Existing translation, item,
blockstate, model, texture atlas, and terrain-model wrapping resources already supply this ID.

Automated coverage adds real `ServerPlayerGameMode.useItemOn` dispatch for all eight roofs and
all 138 combinations of slab type, waterlogging, and top variation without a bottom. It covers
stone and sandstone application, mutation counts, repeated free clears, both tools held,
sneak bypass, offhand cycling, spectators, non-roof cycling/rotation, no drops, entity identity,
NBT byte serialization, dirty state, update packets, and stale observing-client model data.
The renderer test decodes all eight existing sandstone PNGs, checks their model references,
and bakes the requested sprites onto the five lower faces; clearing removes those faces even
when their quads were cached. This does not substitute for real-client visual acceptance.

Verification commands and results for this follow-up:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.TopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.structure.interaction.InteriorDecoratorMilestoneFiveScopeTest --no-configuration-cache --console=plain
```

Before edits: `BUILD SUCCESSFUL in 1m 28s`; 23 tests, no skips/failures/errors.

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain *> build/roof-bottom-red.log
```

After adding regression tests, before production edits: 1,176 GameTests, exactly three new
required failures (main-hand clear, sandstone application, and offhand control), all 1,172
existing tests and the new non-roof/spectator test passed. `BUILD FAILED in 3m 56s`. The sandstone
case returned ordinary block-placement `CONSUME` instead of the texture handler's `SUCCESS`.

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.TopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.structure.interaction.InteriorDecoratorMilestoneFiveScopeTest runGameTestServer --no-configuration-cache --console=plain *> build/roof-bottom-green.log
```

After repair: `BUILD SUCCESSFUL in 3m 56s`; 25 focused JUnit/resource tests, no
skips/failures/errors, and `All 1176 required tests passed :)` (1.716 minutes of GameTests).
No roof datagen task/source exists; the authored resource graph is checked by JUnit and normal
resource processing.

```powershell
.\gradlew.bat build --no-configuration-cache --console=plain *> build/roof-bottom-build.log
```

Complete build gate: `BUILD SUCCESSFUL in 2m 19s`; 488 JUnit suites, 3,916 tests,
3,893 passed, 23 skipped, zero failures/errors. `jar` and `jarJar` both ran. ZIP inspection
confirmed both built JARs contain the canonical sandstone-brick model/PNG and exclude all
GameTest classes. `git diff --check` passed. Generated outputs/JARs/logs are ignored and are
not included in the fix commit. No unexplained or order-dependent test failures occurred.

Two initial attempts at `test --tests '*Roof*' --tests '*TopOnlySlab*' --tests '*InteriorDecorator*'
with the same wrapper and `--no-configuration-cache --console=plain` stopped before tests:
the sandbox denied Gradle's network/cache access, then Windows expanded a wildcard into the
roof playbook filename. Explicit fully qualified test names and approved Gradle access resolved
those execution issues. Existing compiler deprecation warnings and GameTest background
backend/authentication warnings appeared in both red and green runs; they did not fail tests.

Manual acceptance (pending a real client and observing client):

1. Obtain materials with `/give @s britannia_mod:interior_decorator_tool`,
   `/give @s britannia_mod:tile_roof_flat`, `/give @s minecraft:stone 8`, and
   `/give @s britannia_mod:custom_sandstone_brick 8`.
2. Place the tile roof and record its position, top-half shape, and dry/waterlogged state using
   F3. Right-click it with stone in the main hand; its lower half should acquire stone.
3. Hold the decorator in the main hand and right-click once. The lower half must disappear
   immediately for both clients, leaving the roof intact and its top unchanged. Click again:
   there must be no rotation, top cycle, drop, tool break, or inventory charge.
4. Repeat application/clear with Sandstone Brick. Before clearing, inspect the underside and
   lower side faces for the existing sandstone-brick art, with no missing or unrelated texture.
   An operator can confirm the applied identifier with `/data get block X Y Z BottomTexture`;
   after clearing that key should be absent.
5. Repeat dry and waterlogged placements with `/give @s britannia_mod:cedar_roof_flat`,
   `thatch_roof_flat`, `slate_roof_1_flat`, `slate_roof_2_flat`, `slate_roof_flat`,
   `sandstone_roof`, and `limestone_roof` (use the `britannia_mod:` prefix for each).
   The last three have independent top variations. Empty the main hand, put the decorator in
   the offhand (default swap key F), and right-click six times: the top must cycle and wrap,
   while its applied bottom remains unchanged. Swap back to the main hand to clear the bottom.
6. Repeat once while sneaking to check the item fallback. Save both an applied specimen and a
   cleared specimen, leave beyond server view/simulation distance with no player or chunk
   ticket keeping them loaded, and return. Verify both states and their top/waterlogged values.
7. Relog both clients, then cleanly stop/restart the test server. Recheck the applied identifier,
   absent cleared key, geometry, and appearance. Repeat the visual check with the usual shader
   setup; a second observing client must see application and removal without a resource reload.

Only the NeoForge worktree was changed. The Fabric sibling at
`C:/projects/atrevion/Britannia_Mod` was inspected read-only and contains no corresponding
adaptive-roof implementation; adding that feature remains separate loader-parity work.

## Release recommendation

Technical implementation: **GO**. The final M10 command completed green.

Final art release: **GO WITH APPROVED EXISTING MAPPINGS**. On 2026-08-30, the user explicitly
accepted the documented deterministic Slate mirrors and Sandstone duplicate. No unresolved
technical behavior, compatibility, resource, dedicated-server, shader-rendering, or art-
approval blocker remains.
