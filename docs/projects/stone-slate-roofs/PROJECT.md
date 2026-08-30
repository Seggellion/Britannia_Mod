# Unified Stone Slate Roof System

Persistent project scratchpad. Update this file at every milestone before changing the
milestone status.

Baseline date: 2026-08-29 (America/Vancouver)

## Project objective

Repair the existing player-facing Slate Roof and add Sandstone Roof and Limestone Roof.
Each material will be one top-only roof block with six persistent texture variations,
server-authoritative random variation selection on placement, and Interior Decorator Tool
cycling through `0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0`. Preserve saved-world compatibility and
the Iris/Photon-safe adaptive terrain-model rendering pipeline.

M0 established the baseline. M1-M9 now provide the reusable behavior, hardened base block,
canonical Slate repair, source-controlled texture extraction, all three canonical material
registrations, complete resources and compatibility coverage, automated acceptance, and true
dedicated-server plus Iris/Photon live acceptance.

## Repository baseline

- Branch: `patch-18`
- HEAD: `d1c7f433bf7a08b7ef73cc073f43bcd339fd79aa`
- Upstream relation at kickoff: `origin/patch-18`, ahead 41 and behind 12.
- The worktree was not clean before this project began.
- Pre-existing modified files:
  - `src/main/resources/assets/britannia_mod/models/block/new_assets/spinning_wheel.json`
  - `src/main/resources/assets/britannia_mod/models/block/new_assets/spinning_wheel_active.json`
  - `src/main/resources/assets/britannia_mod/models/item/fertilized_dirt.json`
  - `src/main/resources/assets/britannia_mod/textures/banner/mount/poles.png`
  - `src/main/resources/assets/britannia_mod/textures/block/new_assets/spinning_wheel-static.png`
  - `src/main/resources/assets/britannia_mod/textures/block/new_assets/spinning_wheel_animated.png`
- Pre-existing untracked files:
  - `docs/new-assets/TRUE_PHYSICAL_CRATE_STACKING_ARCHITECTURE.md`
  - `src/main/resources/assets/britannia_mod/textures/item/fertile_dirt.png`
  - `ultimacraft_stone_slate_roof_kickoff.md`
  - `ultimacraft_stone_slate_roof_project_playbook.md`
- Milestone 0 did not modify any of those pre-existing files.

Relevant history on the current branch:

- `41f36811` (2026-08-25), `fix(rendering): bake adaptive roof texture into terrain`:
  installed `AdaptiveRoofBakedModel` and `AdaptiveRoofClientModels`, moved the acquired
  lower half to terrain quads, removed `AdaptiveRoofRenderer`, and removed its client
  registration. This is the authoritative active rendering decision.
- `84f125b9` (2026-08-25), `fix(rendering): use block entity pipeline for adaptive roofs`:
  changed the older renderer from `RenderType.solid()` to `Sheets.solidBlockSheet()`.
  It is historical context only; the renderer was subsequently removed by `41f36811`.
- `78ef73e9` (2026-02-24), `#238 roof tiles not compatible with dark stone`:
  added dark-stone item-to-bottom-texture mappings to `TopOnlySlabBlock`.
- `a4a38bda`, `7495eb3d`, and `b1f2351f` are recent roof-named/model commits, but their
  inspected changes concern cedar/other roof art rather than the unified stone roof
  architecture.
- `9037b720` is a same-timestamp parallel-history version of the terrain-bake change found
  by `git log --all`; it is not an ancestor of this HEAD. Do not use it instead of the
  current-branch `41f36811`.

## Locked architectural decisions

- Do not repurpose `britannia_mod:slate_roof`. It is a historical `StairBlock` whose saved
  facing, half, and stair-shape states must remain valid.
- Retain all compatibility registrations. They may later be hidden from normal creative
  exposure, but must not be deleted merely because they are no longer player-facing.
- The reusable implementation is conceptually one `VariantTopOnlySlabBlock` extending
  `TopOnlySlabBlock` and implementing `VariantCyclable`; do not create one Java block class
  per material.
- The persistent `variation` property has exactly the values `0..5`.
- Placement chooses a legal value once on the logical server and persists it in `BlockState`.
  Renderer-time randomness is forbidden.
- Decorator cycling changes only `variation`; it must preserve slab, waterlogging,
  lantern-support, and adaptive lower-texture data.
- There is one inventory item per canonical material, not six items per material.
- Do not add recipes until recipe design is explicitly in scope.
- Do not generate placeholder art or fabricate a sixth unique sandstone design.
- Do not perform broad cleanup of unrelated roof code as part of this project.

## Canonical registry IDs

These IDs and player-facing names are frozen:

| Registry ID | Player-facing name | Baseline status |
| --- | --- | --- |
| `britannia_mod:slate_roof_flat` | Slate Roof | Existing `TopOnlySlabBlock`; canonical repair target |
| `britannia_mod:sandstone_roof` | Sandstone Roof | Registered in M5 as `VariantTopOnlySlabBlock` |
| `britannia_mod:limestone_roof` | Limestone Roof | Registered in M5 as `VariantTopOnlySlabBlock` |

`slate_roof_flat` is suitable as the canonical repaired Slate Roof because it is already the
established player-facing flat-slate registry identity, already has a `BlockItem`, is already
an adaptive-roof block-entity host, and is already wrapped by the terrain baked-model path.
Its current blockstate/model architecture is defective and must be repaired without changing
the registry identity.

## Legacy compatibility IDs

Retain these registrations:

| Registry ID | Current role |
| --- | --- |
| `britannia_mod:slate_roof_base` | Historical base block used by the slate stair |
| `britannia_mod:slate_roof` | Historical `StairBlock`; never convert to a top-only roof |
| `britannia_mod:slate_roof_1_flat` | Historical separate-texture `TopOnlySlabBlock` |
| `britannia_mod:slate_roof_2_flat` | Historical separate-texture `TopOnlySlabBlock` |

The canonical `slate_roof_flat` ID also already exists in saved worlds. Adding a variation
property later must retain a safe default for states serialized before that property existed.

## Current roof architecture

### Registrations and state

- `BlockRegistry.java:1426-1433` registers `slate_roof_base` and registers `slate_roof` as
  `DeferredHolder<Block, StairBlock>` backed by `new StairBlock(...)`.
- `BlockRegistry` registers canonical Slate, Sandstone, and Limestone as exact instances of
  the shared `VariantTopOnlySlabBlock`. The historical slate stair and numbered flats retain
  their original registrations and types.
- `ItemRegistry` registers one canonical block item for each of those three materials and
  retains every historical slate item for compatibility.
- `CreativeTabRegistry` exposes only canonical Slate Roof, Sandstone Roof, and Limestone Roof
  for this family, in that order. The historical slate choices remain registered but hidden.
- `BlockEntityRegistry.ADAPTIVE_ROOF` includes all three canonical blocks as well as the
  pre-existing compatible roof hosts.
- `TopOnlySlabBlock.java:38-56,135-137` extends `SlabBlock`, inherits `TYPE` and
  `WATERLOGGED`, declares `SUPPORTS_LANTERN`, and includes all three properties in its state
  definition through `super.createBlockStateDefinition(...)` plus the added support property.
- The default state remains `TYPE=TOP`, `WATERLOGGED=false`, and
  `SUPPORTS_LANTERN=false`. M2 keeps `TYPE=TOP` on placement while deriving `WATERLOGGED`
  from the target cell's fluid state.
- M2 makes selection, collision, block-support, and occlusion shapes consistently full-block
  when synchronized `SUPPORTS_LANTERN=true`; all four remain top-half when it is false. Shape
  queries no longer inspect the block entity directly.

### Decorator and variant behavior

- `InteriorDecoratorToolItem.java:39-184` has no `VariantCyclable`/top-only-roof branch. A
  roof has no horizontal facing or other recognized decorator state, so the item returns
  `PASS` at line 184.
- M2 reserves acquired-bottom clearing in `TopOnlySlabBlock` for the wooden axe. The Interior
  Decorator Tool is no longer mapped to the `minecraft:block/air` clear sentinel, so base roofs
  pass that interaction and variant roofs can own it exclusively.
- `VariantCyclable.java:20-46` still implements the intended shared cycle. It rejects
  spectators/non-decorator items, calculates the next value from the property's legal value
  count, mutates only `state.setValue(variation, next)` on the logical server, uses
  `Block.UPDATE_ALL`, plays a sound, and returns sided success. This preserves unrelated
  `BlockState` properties by construction. There was no direct standalone unit test for this
  interface at kickoff.
- `FlagstoneBlock` and `SandstonePaverBlock` demonstrate persistent variation properties and
  placement-time randomization. The sandstone paver also delegates its item interaction to
  `cycleVariation`.
- M1 added `VariantTopOnlySlabBlock`, which extends `TopOnlySlabBlock`, implements
  `VariantCyclable`, owns `variation=0..5` with default `0`, selects the placement variation
  from the logical server's `RandomSource`, and consumes decorator use before the inherited
  no-item acquired-bottom clearing path can run.
- M1 added `VariantCyclable.nextVariationState(...)` as the shared pure state transition used
  by the server mutation. It changes only the declared variation property.

### Adaptive rendering

- `AdaptiveRoofBlockEntity.java:19-169` persists the acquired bottom texture, synchronizes it,
  requests model-data refreshes, publishes it as `ModelData`, and mirrors its presence into
  the appropriate `SUPPORTS_LANTERN` state property.
- `AdaptiveRoofBakedModel.java:33-146` is the active acquired-bottom rendering implementation.
  It resolves the texture from model data, uses `FaceBakery` to create directional lower-half
  quads, and returns them through ordinary terrain-model quad buckets.
- `AdaptiveRoofClientModels` installs the wrapper during model bake using a registry-backed,
  type-based rule: Britannia terrain models whose registered block is a `TopOnlySlabBlock`
  are wrapped automatically. Inventory models and foreign namespaces remain excluded before
  registry lookup, so future compatible roofs cannot silently miss the adaptive terrain path.
- `AdaptiveRoofRenderer.java` is absent, and `ClientModSetup` has no adaptive-roof block-entity
  renderer registration.

### Existing slate resources

- Separate blockstates still exist for `slate_roof_flat`, `slate_roof_1_flat`, and
  `slate_roof_2_flat`. M3 converted only the canonical blockstate to deterministic variation
  selectors; the two numbered legacy resources remain available to old IDs.
- Three distinct flat-slate texture files exist:
  - `textures/block/roof/slate_roof_flat.png` (SHA-256
    `3a709f79e2cddbca08d2ccb12f1e00d4893ba25063a48ea81be1b7411a8a6dc8`)
  - `textures/block/roof/slate_roof_1_flat.png` (SHA-256
    `81df17a702634a0b8e09cdd643f92bc313d8e33056fda5eac5426f8e3d4a8621`)
  - `textures/block/roof/slate_roof_2_flat.png` (SHA-256
    `26b4ee67d54656a34651f6b991dc1392640522d463713b122134250646c5cfe7`)
- `textures/block/roof/slate_texture.png` is separate historical stair artwork and is not an
  automatic fourth flat-roof variation.
- M3 re-searched current files, every Git object in repository history, and the raw-art tree.
  No fourth, fifth, or sixth grey flat-slate design exists in those sources.
- The only additional raw candidate, `raw fiels/slate_roof/slate_roof_1.png`, is a 64x64
  orange/brown scalloped-shingle image (4,047 bytes; SHA-256
  `09515b3b7387217ed80c91ff698a3c947155daa157b0bbc45c7cde4631b0a2b8`). Visual inspection
  confirmed that it is not part of the shipped grey slate family, so M3 did not adopt it.
- M3 removed the canonical reference to the absent `slate_roof_flat_double` model by covering
  every canonical type/variation selector with an existing model. The two numbered legacy
  blockstates still do not define double variants; they remain compatibility resources rather
  than player-facing canonical content.

## Rendering constraints

- Preserve this active pipeline:
  `AdaptiveRoofBlockEntity -> ModelData -> AdaptiveRoofBakedModel -> FaceBakery terrain quads`.
- Never restore `AdaptiveRoofRenderer`.
- Never add a new `RenderType.solid()` or `Sheets.solidBlockSheet()` adaptive block-entity
  renderer. The historical sheet-based renderer was an intermediate solution, not the release
  architecture.
- Do not bypass model data or make texture selection renderer-random.
- Keep inventory models excluded from adaptive terrain wrapping unless a tested future design
  explicitly changes that rule.
- Treat missing wrapper coverage for a canonical roof ID as an Iris/Photon regression.
- Automated model tests are necessary but not a substitute for the M9 dedicated-server and
  Iris/Photon visual checks.

## Illustrator source

- Authoritative master: `C:\projects\britannia\raw fiels\roof.ai`
- Exists at the Milestone 0 baseline.
- Size: 96,096,596 bytes.
- Last-write UTC: 2026-08-29 22:36:35.
- SHA-256: `9ae5a6a8b520d0710da961f0f637e246dcabefa949097df14eaeebb0354caf8a`
- PDF-compatible Illustrator file: PDF 1.6, one 64 x 64 point page, created by Adobe
  Illustrator 30.8.
- The master was read only. Milestone 0 did not modify it and did not export textures.

## Known asset inventory

A read-only inspection of PDF optional-content groups, page drawing operations, and image
XObjects confirmed:

- Optional-content layer `sandstone`: `/Im0` through `/Im5`, six image placements.
- Optional-content layer `limestone`: `/Im6` through `/Im11`, six image placements.
- Every placement is 1254 x 1254 pixels, 8 bits per component, Flate-compressed, with an
  ICC-based color profile whose component count is 3 (RGB).
- The file contains 12 image placements and 11 unique embedded image objects because of the
  sandstone duplicate described below.
- All six limestone image streams have distinct raw and decoded SHA-256 hashes.

Extraction, normalization, visual inspection, source-order-to-filename mapping, and any game
texture writes belong to M4.

## Known sandstone duplicate

- Sandstone source positions 2 and 4 (`/Im1` and `/Im3`) both reference PDF object `31 0`.
- Their raw embedded-stream SHA-256 is
  `3a779706a59600b2e1d8d66fdd55a88d03613a2acf8be3da4381718a31b92e55`.
- Their decoded RGB-data SHA-256 is
  `9ff6734f53fc2cfdc45fd9e34b3d2bb8625fdd3ccab19c9d9bfe53ce38ba54ec`.
- Therefore the source still contains six sandstone slots but only five unique sandstone
  designs. Do not invent a replacement. Preserve six-state code architecture and carry this
  as a later visual-art acceptance risk until a unique sixth design is supplied or the
  duplication is explicitly accepted.

## Discovery verification

1. PASS: `slate_roof` is still a `StairBlock`.
2. PASS: `slate_roof_flat`, `slate_roof_1_flat`, and `slate_roof_2_flat` still exist as
   historical flat registrations; `slate_roof_base` and the historical stair also remain.
3. PASS: `slate_roof_flat` remains the compatibility-safe canonical repaired Slate Roof ID.
4. PASS: `TopOnlySlabBlock` still has `TYPE`, `WATERLOGGED`, and `SUPPORTS_LANTERN`.
5. M2 PASS: placement now retains source-water fluid state while forcing `TYPE=TOP`.
6. M2 PASS: the base acquired-bottom path no longer treats the decorator as a clear tool.
7. PASS: `VariantCyclable` still supplies server-authoritative property cycling that changes
   only the variation property.
8. PASS: `AdaptiveRoofBakedModel` remains the active rendering solution.
9. PASS: `AdaptiveRoofRenderer` remains absent.
10. M5 PASS: `AdaptiveRoofClientModels` now uses the tested Britannia
    `TopOnlySlabBlock` type rule and still excludes inventory models.
11. PASS: existing flat-slate art still consists of exactly three known distinct variations.
12. PASS: `roof.ai` exists and its two named layers still contain the expected six sandstone
    and six limestone source placements; the known sandstone duplicate remains.

No discovery item changed in a way that invalidates the project plan. The exact duplicate
positions and hashes are now pinned more precisely than the earlier report.

## Milestone checklist

- M0 - COMPLETE: Project foundation and baseline - PASS.
- M1 - COMPLETE: Reusable six-variant top-only roof block - PASS.
- M2 - COMPLETE: `TopOnlySlabBlock` hardening - PASS.
- M3 - COMPLETE: Existing Slate Roof repair - PASS.
- M4 - COMPLETE: Illustrator texture extraction and validation - PASS.
- M5 - COMPLETE: Sandstone + Limestone block registration - PASS.
- M6 - COMPLETE: Models, loot, mining, localization, resource completion - PASS.
- M7 - COMPLETE: Legacy compatibility / migration acceptance - PASS.
- M8 - COMPLETE: Automated acceptance suite - PASS.
- M9 - COMPLETE: Dedicated-server + Iris/Photon technical acceptance - PASS; the documented
  Slate mirrors and Sandstone duplicate were explicitly accepted at M10 release handoff.
- M10 - COMPLETE: Cleanup, documentation, technical release handoff, and final approval - PASS.

M10 is complete. Technical release handoff is ready, and the user explicitly approved the
documented Slate and Sandstone mappings for release on 2026-08-30.

## Baseline test commands/results

### Focused JUnit/contract baseline

Command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.structure.FlagstoneBlockContractTest --tests com.seggellion.britannia_mod.block.SandstonePaverBlockTest --console=plain
```

Result after allowing the pinned Gradle distribution/dependency download: `BUILD SUCCESSFUL`
in 2m 7s. Test reports recorded 23 tests, 0 skipped, 0 failures, 0 errors:

- `AdaptiveRoofBlockEntityDataTest`: 3 passed.
- `AdaptiveRoofModelRenderingTest`: 8 passed.
- `FlagstoneBlockContractTest`: 7 passed.
- `SandstonePaverBlockTest`: 5 passed.

The first sandboxed invocation failed before configuration because the Gradle wrapper could
not download Gradle 8.9 (`java.net.SocketException: Permission denied: getsockopt`). This was
an execution-environment restriction, not a repository test failure; the identical command
passed when network access was allowed.

Coverage note: the focused set exercises `TopOnlySlabBlock` occlusion through the adaptive
model test, adaptive model data and baked geometry, flagstone variation resource contracts,
and sandstone-paver placement/state/resource contracts. No direct standalone
`VariantCyclable`, decorator-on-roof, or roof-placement test existed at kickoff.

### GameTest and creative registry baseline

Initial command:

```powershell
.\gradlew.bat runGameTestServer --console=plain
```

Result: Gradle failed in 2s before server startup while deserializing a configuration-cache
task classpath (`ProviderBackedFileCollectionSpec`, `null array`). This is a pre-existing
Gradle configuration-cache failure, not a game-test failure.

Successful retry:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 18s. NeoForge reported `926 GAME TESTS COMPLETE` and
`All 926 required tests passed :)`. This includes `CreativeTabRegistryIntegrityGameTests`.
The repository currently has no roof-specific GameTest class. Missing local server credentials
produced expected background HTTP/auth warnings but did not fail the suite.

### Full validation command for later milestones

Use the no-configuration-cache form until the cache deserialization issue is independently
resolved:

```powershell
.\gradlew.bat test runGameTestServer --no-configuration-cache --console=plain
```

M9 additionally requires a real dedicated-server acceptance pass and client visual inspection
with Iris/Photon; those cannot be replaced by the command above.

## Milestone 1 implementation

- Added one generic `VariantTopOnlySlabBlock`; it is independent of material registrations,
  artwork, models, loot, localization, and recipes.
- The persistent property has exactly six values, `0..5`, defaults to `0`, and combines with
  inherited `TYPE`, `WATERLOGGED`, and `SUPPORTS_LANTERN` for 72 legal block states.
- Placement leaves the client prediction at its default and does not consume client RNG. The
  logical server selects the authoritative value using the world's `RandomSource`.
- The placement helper changes only `variation`, preserving the base placement result. M2 now
  supplies the corrected fluid-aware base placement state.
- Decorator interaction delegates to `cycleVariation(...)` before `TopOnlySlabBlock` can treat
  that tool as an acquired-bottom clear action.
- `VariantCyclable.nextVariationState(...)` implements `0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0` while
  retaining the block identity and every unrelated state property. The inherited adaptive
  block-entity factory is unchanged, so `AdaptiveRoofBlockEntity` and its `BottomTexture` data
  remain outside the mutation.
- No blocks or items were registered, and no blockstates, models, textures, loot tables,
  translations, creative tabs, block-entity registrations, or renderers were changed.

## Milestone 1 test commands/results

Focused M1 command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 1m 47s. All 5 tests passed with 0 skipped, 0 failures, and
0 errors. Coverage proves the exact property values/default/state count, seeded placement
across all six values, client-side no-RNG prediction, the complete cycle and wrap, preservation
of inherited properties/block identity, inherited adaptive block-entity support, and decorator
interaction delegation.

Focused M1 plus baseline-regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.structure.FlagstoneBlockContractTest --tests com.seggellion.britannia_mod.block.SandstonePaverBlockTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 15s. The then-current 4 M1 tests and all 23 baseline tests
passed; the fifth M1 interaction-delegation test passed in the later focused run above.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 54s. Reports recorded 424 suites and 3,269 tests: 17 skipped,
0 failures, and 0 errors. The M0 GameTest baseline remains 926/926 required tests passing; it
was not repeated because M1 introduced no registered or runtime-visible block instance and no
GameTest.

## Milestone 2 implementation

- `TopOnlySlabBlock.getStateForPlacement(...)` now reads the target cell's fluid state, always
  produces `TYPE=TOP`, and sets `WATERLOGGED=true` for source water instead of forcing false.
- `SUPPORTS_LANTERN`, which `AdaptiveRoofBlockEntity` synchronizes with acquired-bottom data,
  now drives selection, collision, block-support, and occlusion shapes. A visible acquired
  lower half therefore has one consistent full-block interaction contract on server and client;
  a roof without it keeps the original top-half contract.
- Collision no longer reads `AdaptiveRoofBlockEntity` directly, avoiding client timing and
  missing-block-entity discrepancies during state-sensitive shape queries.
- The base class now reserves the Interior Decorator Tool for variant behavior. A wooden axe
  remains the explicit acquired-bottom clear tool; the decorator passes without erasing
  `BottomTexture` on existing non-variant roofs.
- Added focused unit contracts and registered-world GameTests. The runtime coverage exercises
  actual tile-roof placement in water and the acquired/set/decorator/clear lifecycle for tile,
  cedar, and thatch flat roofs.
- No registry, creative-tab, block-entity registration, blockstate, model, texture, loot,
  localization, recipe, or rendering-pipeline files changed.

## Milestone 2 test commands/results

Focused foundation and rendering-regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.TopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 1m 57s. All 19 tests passed: 3 foundation tests, 5 reusable
variant tests, 3 adaptive-data tests, and 8 adaptive-model tests. The first permitted run
compiled successfully but exposed one assertion that depended on cached fluid state for an
unregistered unit-test block; the test was corrected to assert the property, while the
registered-world GameTest below proves the resulting fluid state. The initial sandboxed
invocation could not access the pinned Gradle distribution; the identical command ran when
Gradle access was allowed.

Complete GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Final result after strengthening the wooden-axe interaction path: `BUILD SUCCESSFUL` in
3m 30s. NeoForge reported `928 GAME TESTS COMPLETE` and `All 928 required tests passed :)`,
including the two new M2 runtime tests. The preceding run also passed in 3m 9s.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 40s. Reports recorded 425 suites and 3,272 tests: 17 skipped,
0 failures, and 0 errors.

## Milestone 3 implementation

- Upgraded only canonical `britannia_mod:slate_roof_flat` from `TopOnlySlabBlock` to the shared
  `VariantTopOnlySlabBlock`. It now has six persistent variation states, server-random initial
  placement, decorator cycling, the M2 water/geometry contract, and inherited adaptive
  block-entity support.
- Preserved `slate_roof_base`, the historical `slate_roof` `StairBlock`, both numbered legacy
  flat blocks, all four associated item registrations, and all three adaptive block-entity
  registrations. No registry ID was renamed or removed.
- Old serialized canonical states that omit `variation` resolve to the block's default
  `variation=0`; a registered-world GameTest proves this through `NbtUtils` decoding.
- Changed the canonical player-facing name to exactly `Slate Roof`. The normal building
  creative tab now contains only `SLATE_ROOF_FLAT_ITEM` for the slate family; historical items
  remain registered and obtainable by ID but are no longer normally exposed.
- Replaced the dangling canonical double-model reference with 18 explicit deterministic
  selectors covering `bottom`, `top`, and `double` for each variation `0..5`. The blockstate
  contains no weighted/random model arrays.
- The repository and raw-art re-inventory found only the three existing usable grey flat-slate
  designs. To keep every legal state renderable without fabricating art, M3 maps variations
  `0,1,2` to those three designs and temporarily mirrors them for `3,4,5` respectively. These
  mirrors are deterministic fallbacks, not three additional unique artworks.
- No texture was added, copied, synthesized, recolored, or extracted. No Illustrator,
  sandstone/limestone registration, loot, recipe, mining, or renderer work began.

## Milestone 3 test commands/results

Focused foundation and M3 contract command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.SlateRoofMilestoneThreeContractTest --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.TopOnlySlabBlockTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 37s. All 12 selected tests passed: 4 M3 registry/resource
contracts, 5 reusable-variant tests, and 3 top-only foundation tests. The first sandboxed
attempt stopped before tests because it could not download the pinned Gradle distribution;
the identical permitted run passed.

Complete GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Final result: `BUILD SUCCESSFUL` in 3m 26s. NeoForge reported `929 GAME TESTS COMPLETE` and
`All 929 required tests passed :)`. The new registered-world test proves canonical/legacy
runtime block types and items, missing-variation decode to zero, all six decorator transitions
and wraparound, and preservation of type, waterlogging, acquired-bottom geometry, block entity,
and `BottomTexture`. An initial full run correctly changed the state but exposed an overly
specific assertion that expected the client `SUCCESS` enum on the logical server; asserting
the API's consumed-action contract produced the final green run.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 48s. Reports recorded 426 suites and 3,276 tests: 17 skipped,
0 failures, and 0 errors.

## Milestone 4 implementation

- Revalidated the authoritative `C:\projects\britannia\raw fiels\roof.ai` before and after
  extraction. It remains a 96,096,596-byte, PDF 1.6 Illustrator file with SHA-256
  `9ae5a6a8b520d0710da961f0f637e246dcabefa949097df14eaeebb0354caf8a`; M4 never opened it for
  writing and did not require Illustrator automation.
- Added `tools/roof_textures/extract_roof_textures.py`, a fail-closed extractor using pypdf
  6.10.0 and Pillow 12.3.0. The pinned Python dependencies are recorded beside it in
  `tools/roof_textures/requirements.txt`.
- The script verifies the master hash, one-page structure, optional-content layer names,
  content-stream drawing order, image geometry, 8-bit depth, three-component sRGB profile,
  decoded byte length, and the known duplicate contract before accepting output.
- Preserved source order explicitly: sandstone `/Im0..Im5` map to
  `sandstone_roof_1.png..sandstone_roof_6.png`; limestone `/Im6..Im11` map to
  `limestone_roof_1.png..limestone_roof_6.png`. Filesystem enumeration never determines
  variation numbering.
- Decoded all twelve 1254x1254 embedded RGB images directly rather than rasterizing the whole
  canvas. Each was reduced without crop or aspect-ratio change to an opaque 64x64 RGB PNG using
  Pillow LANCZOS with `reducing_gap=3.0`, optimized PNG output, and compression level 9.
- Wrote the twelve production textures under the established
  `textures/block/roof` directory and recorded source/object/output provenance and hashes in
  `docs/projects/stone-slate-roofs/M4_TEXTURE_MANIFEST.json`.
- A second complete extraction produced byte-identical hashes for all twelve PNGs. Poppler page
  rendering, 1254px source inspection, and a labeled 64px contact sheet confirmed correct
  orientation, square framing, material grouping, and source order without visible extraction
  defects.
- Reconfirmed the source defect exactly: sandstone positions 2 (`/Im1`) and 4 (`/Im3`) both
  reference PDF object 31 and normalize to identical PNG SHA-256
  `a283a080346a397593473b1f05344b85c0bdc7a0d8a2c8ffd7ab83ea758a5a81`. Sandstone therefore
  has six slots but five unique designs. All six limestone sources and outputs are unique.
- Added an automated M4 asset contract for exact filenames/order, valid decodable PNGs,
  dimensions, RGB/alpha contract, manifest provenance, hashes, and duplicate counts.
- No block, item, block-entity, creative-tab, localization, blockstate, model, loot, mining,
  recipe, or renderer registration changed. M5 did not begin.

Reproducible extraction command used from the repository root:

```powershell
& 'C:\Users\dusti\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools\roof_textures\extract_roof_textures.py --source 'C:\projects\britannia\raw fiels\roof.ai' --output-dir src\main\resources\assets\britannia_mod\textures\block\roof --manifest docs\projects\stone-slate-roofs\M4_TEXTURE_MANIFEST.json
```

## Milestone 4 test commands/results

Focused M4 asset and M3 slate-regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.roof.RoofTextureMilestoneFourAssetTest --tests com.seggellion.britannia_mod.block.SlateRoofMilestoneThreeContractTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 8s. All 8 selected tests passed: 4 M4 asset contracts and
4 canonical-slate regression contracts.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 5s. Reports recorded 427 suites and 3,280 tests: 17 skipped,
0 failures, and 0 errors. GameTest was not repeated because M4 changed no Java runtime,
registry, data-pack, or server-visible resource; the M3 baseline remains 929/929 required tests
passing.

## Milestone 5 implementation

- Registered `britannia_mod:sandstone_roof` and `britannia_mod:limestone_roof` as exact
  `VariantTopOnlySlabBlock` instances using the same stone-slab properties and shared behavior
  as canonical `slate_roof_flat`. No material-specific Java subclass was created.
- Added the two canonical `BlockItem` registrations and extended
  `BlockEntityRegistry.ADAPTIVE_ROOF` so both blocks create and retain the existing adaptive
  roof block entity.
- Exposed exactly canonical Slate Roof, Sandstone Roof, and Limestone Roof for this family in
  the normal creative tab, in that order. The historical slate stair and numbered-flat item
  registrations remain valid but hidden.
- Added the frozen canonical English names and updated the repository-wide item-universe
  contract from 925 to 927 for the two intentional new block items.
- Replaced `AdaptiveRoofClientModels`' hard-coded ID set with a registry-backed type rule for
  Britannia `TopOnlySlabBlock` terrain models. The namespace and inventory exclusions run
  before lookup; a focused regression proves future compatible roofs are wrapped without
  exposing item models to the terrain wrapper.
- Added source-level M5 contracts and a registered-world GameTest covering exact shared-class
  use, registry identities, all 72 legal states, server placement across variations `0..5`,
  state serialization, block-entity creation, six-step decorator wraparound, top-only
  geometry, and preservation of acquired-bottom state/data for all three canonical materials.
- M5's render gate established safe adaptive terrain-wrapper eligibility for each registered
  canonical roof. The then-future M6 deliberately owned the deterministic base
  blockstates/models and complete no-missing-resource proof, along with loot and mining tags;
  none of that work began in M5.

## Milestone 5 test commands/results

Focused behavior, wrapper, registration, and M4 texture-regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneFiveContractTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.roof.RoofTextureMilestoneFourAssetTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 52s. All 21 selected tests passed.

Dedicated-server GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 4m 13s. NeoForge ran 930 GameTests in 52.68s and reported
`All 930 required tests passed :)`.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

The first full pass correctly identified the repository item-count contract still expecting
925 rather than the two-item M5 total of 927; all other tests passed. After documenting that
intentional roster change, the final run was `BUILD SUCCESSFUL` in 3m 23s. Reports recorded
428 suites and 3,284 tests: 17 skipped, 0 failures, and 0 errors.

## Milestone 6 implementation

- Added one shared `top_only_slab` model containing the exact top-half cuboid and six textured
  faces. Eighteen small material/design models inherit that geometry: six each for Slate,
  Sandstone, and Limestone.
- Added deterministic canonical blockstates with 18 explicit selectors per material. Every
  inherited `bottom`, `top`, and `double` value combined with variation `0..5` resolves to a
  real model; there are no weighted arrays or dangling `_double` references. Waterlogging and
  lantern-support states intentionally share those visual mappings.
- Preserved the known art contracts. Slate variations 4-6 mirror designs 1-3, and the
  source-authored duplicate Sandstone slots remain unchanged. No artwork was fabricated.
- Added canonical item models using the first design for each material.
- Added canonical self-drop loot tables and placed all three blocks in the repository's
  pickaxe-mineable tag. The repository does not apply a stone-tier requirement to comparable
  stone roofs, so no unsupported `needs_stone_tool` classification was invented.
- Reconfirmed all three frozen English names and that no canonical roof recipe was introduced.
- Added a static resource-graph contract covering geometry, all 54 selectors, model/texture
  reachability, item parents, loot, mining, localization, and the no-recipe boundary.
- Added a registered runtime GameTest that exercises every variation of every material through
  authoritative `Block.getDrops` calls. Ordinary iron-pickaxe and Silk Touch paths each return
  exactly one canonical item, and all three blocks resolve in the live pickaxe tag.
- The first full JUnit pass exposed only stale M3 model-path expectations and an unrelated
  source-filename scope check. The M3 contract was advanced to the shared M6 model graph and
  the runtime test received a milestone-neutral production filename; no production behavior
  changed in response.

## Milestone 6 test commands/results

Static JSON validation parsed all 29 relevant canonical roof JSON files successfully: three
blockstates, three item models, three loot tables, the pickaxe tag, the shared geometry model,
and eighteen material/design models.

Initial focused resource/regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneSixResourceTest --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneFiveContractTest --tests com.seggellion.britannia_mod.roof.RoofTextureMilestoneFourAssetTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 19s. All 12 selected tests passed.

Focused correction/regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneSixResourceTest --tests com.seggellion.britannia_mod.block.SlateRoofMilestoneThreeContractTest --tests com.seggellion.britannia_mod.structure.interaction.InteriorDecoratorMilestoneFiveScopeTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 17s. All 12 selected tests passed.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Final result: `BUILD SUCCESSFUL` in 2m 58s. Reports recorded 429 suites and 3,288 tests:
17 skipped, 0 failures, and 0 errors.

Dedicated-server GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Final result after the production test-file rename: `BUILD SUCCESSFUL` in 3m 5s. NeoForge ran
931 GameTests in 56.44s and reported `All 931 required tests passed :)`.

## Milestone 7 implementation

- Re-audited the frozen Slate IDs: `slate_roof_base`, the historical `slate_roof` stair,
  canonical `slate_roof_flat`, and numbered compatibility blocks `slate_roof_1_flat` and
  `slate_roof_2_flat`. No registry ID, holder, or block class changed.
- Added valid `type=double` fallback selectors to both numbered compatibility blockstates.
  Their existing bottom/top models and item models remain unchanged; every legal inherited
  slab type now resolves instead of falling through to a missing model.
- Added a static compatibility contract proving the historical stair retains its complete
  40-selector model matrix and that both numbered flat resources resolve bottom, top, and
  double states through their historical model identities.
- Added a registered-world compatibility GameTest class. It proves all five block IDs resolve
  through the live registry to their exact historical types, including the stair's 80 vanilla
  states and each numbered flat's 12 inherited states.
- Round-tripped representative non-default saved block states for all five IDs through
  `NbtUtils`, including stair facing/half/shape/waterlogging and numbered-flat type,
  waterlogging, and support state. Every decoded state retained its original ID and values.
- Round-tripped the historical stair, canonical flat, and both numbered-flat items together
  through Minecraft's saved-container NBT path. Item identities and stack counts survived.
  `slate_roof_base` historically has no item registration, so M7 did not invent one.
- Built the real Britannia world creative tab on the GameTest server and proved it exposes the
  canonical Slate Roof exactly once while excluding the stair and both numbered compatibility
  items.
- No migration hook, alias, data fixer, automatic world conversion, or item remapping was
  added. The direct state and inventory round-trips show that the default no-migration policy
  is sufficient, so the conditional migration sub-milestone was not activated.

## Milestone 7 test commands/results

Focused M7 compatibility plus M3 regression command:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneSevenCompatibilityTest --tests com.seggellion.britannia_mod.block.SlateRoofMilestoneThreeContractTest --no-configuration-cache --console=plain
```

The first sandboxed invocation stopped before compilation because it could not download the
pinned Gradle 8.9 distribution. The permitted retry was `BUILD SUCCESSFUL` in 2m 52s; all six
selected tests passed.

Registered-world GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 3s. NeoForge ran 934 GameTests in 1.005 minutes and reported
`All 934 required tests passed :)`. This is the repository GameTest server, not the true
dedicated-server acceptance reserved for M9.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 35s. Reports recorded 430 suites and 3,290 tests: 17 skipped,
0 failures, and 0 errors.

## Milestone 8 implementation and coverage matrix

- Audited every M8 playbook requirement against the existing M1-M7 tests before adding code.
  All required JUnit areas already had direct assertions, so M8 did not add redundant
  meta-tests or duplicate production contracts merely to create a milestone-named suite.
- Behavior coverage comes from `VariantTopOnlySlabBlockTest` and `TopOnlySlabBlockTest`: exact
  variation range, authoritative random helper, client prediction, cycling/wraparound,
  unrelated-property preservation, waterlogging, and geometry contracts.
- Registration/resource coverage comes from the M3-M7 contracts: shared block class and
  geometry, all registry IDs and items, six source slots/models/textures per material,
  deterministic blockstates, item models, loot, mining, creative exposure, and legacy assets.
- Adaptive rendering coverage comes from `AdaptiveRoofModelRenderingTest` and
  `AdaptiveRoofBlockEntityDataTest`: type-based terrain wrapping, inventory/foreign exclusions,
  model data, lower-half geometry, texture persistence, clear synchronization, and explicit
  absence of `AdaptiveRoofRenderer` and its registration.
- Existing registered-world coverage already proved live canonical registries, placement-state
  sampling, all decorator transitions, preservation of type/waterlogging/support/adaptive data,
  valid block entities, canonical ordinary/Silk drops, legacy IDs/inventories, and creative
  inclusion/exclusion.
- Added `StoneRoofAcceptanceGameTests` for the two genuine runtime gaps. The first test invokes
  the real registered `BlockItem.place(...)` path 256 times per material in source water,
  requires every variation `0..5`, verifies top/waterlogged state and adaptive block entities,
  and compares selection, collision, support, and occlusion geometry across all three roofs.
- The second new test combines block-state NBT, block-entity disk data, and client update
  semantics for every canonical material. Non-default variation/waterlogging/support state and
  `BottomTexture` survive state plus block-entity reload round-trips; update tags contain the
  texture and reproduce it in a receiver, and every entity supplies an update packet.
- No production class, registry, asset, recipe, migration rule, or rendering path changed.
  Actual remote-client observation, true server restart, and shader behavior remain M9 work.

## Milestone 8 test commands/results

Focused complete roof JUnit matrix:

```powershell
.\gradlew.bat test --tests com.seggellion.britannia_mod.block.VariantTopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.TopOnlySlabBlockTest --tests com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntityDataTest --tests com.seggellion.britannia_mod.client.model.AdaptiveRoofModelRenderingTest --tests com.seggellion.britannia_mod.block.SlateRoofMilestoneThreeContractTest --tests com.seggellion.britannia_mod.roof.RoofTextureMilestoneFourAssetTest --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneFiveContractTest --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneSixResourceTest --tests com.seggellion.britannia_mod.roof.StoneRoofMilestoneSevenCompatibilityTest --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 2m 24s. Reports recorded 9 suites and 37 tests: 0 skipped,
0 failures, and 0 errors.

Registered-world GameTest command:

```powershell
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 11s. NeoForge ran 936 GameTests in 44.09s and reported
`All 936 required tests passed :)`. This remains the repository GameTest server, not the true
dedicated-server and multi-client gate assigned to M9.

Complete JUnit command:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 3m 2s. Reports recorded 430 suites and 3,290 tests: 17 skipped,
0 failures, and 0 errors.

## Milestone 9 dedicated-server and visual acceptance

Environment:

- NeoForge/Minecraft 1.21.1 true dedicated server with a separate remote development client.
- Isolated flat acceptance world: `run/server/m9-roof-acceptance`.
- Iris `1.8.0+mc1.21.1`, Sodium `0.6.0+mc1.21.1`, and Photon `1.1`.
- Iris SHA-256:
  `0E8AE2864F2BA144CC59FDB56A8AC00B88C0A96AFCD986C54F3FB2B7DBCC81F5`.
- Sodium SHA-256:
  `FB178004F4A942735029C57EF5556BC3C83CE6C1D8344B6ECDFCC651148535F6`.
- Photon SHA-256:
  `1228172BFB0EE49DE3D2B6268390BAC52C9A57DD0AA92C8903E57CF14583203A`.

Result: **TECHNICAL PASS**.

- The server logged the isolated world load and a successful ready state. A real remote client
  connected rather than an integrated single-player server.
- Live random placements produced legal synchronized variants for all three canonical roofs.
- Live Interior Decorator interactions advanced Slate, Sandstone, and Limestone from variation
  `0` to `1`; the complete six-step cycle remains covered by registered-world GameTests.
- In Adventure mode the decorator still cycled the target while a break attempt left it intact.
- Before restart, state checks confirmed canonical variants `0` and `5` and acquired lower
  texture data for all materials. The dedicated server was stopped cleanly, restarted, and the
  same checks passed after the client reconnected.
- The client inspected all 18 material/variation combinations, item models, top-half geometry,
  neighboring faces, selection, and lighting with shaders disabled and with Iris/Photon.
- Photon used the ordinary terrain model path. No black geometry, bad normals, entity/terrain
  shader mismatch, renderer regression, or visible lighting discontinuity was found.
- Non-fatal Photon option/preprocessor messages and Intel OpenGL debug noise appeared in the
  client log, but did not correspond to a visible roof defect.
- A second simultaneous client was not practical with the project's one shared development run
  directory. The playbook makes this check conditional on feasibility; one true remote client
  exercised the dedicated-server synchronization path.

The temporary server properties were restored after acceptance. Evidence is retained in
`docs/projects/stone-slate-roofs/evidence/` and cataloged by
`docs/projects/stone-slate-roofs/IMPLEMENTATION-REPORT.md`.

Visual-art result: **APPROVED FOR RELEASE**. Slate has three unique grey designs mirrored
across six states. Sandstone has five unique designs because source positions 2 and 4 are
identical. No replacement was fabricated; the user explicitly accepted both documented
mappings on 2026-08-30.

## Milestone 10 final validation

Command:

```powershell
.\gradlew.bat test runGameTestServer --no-configuration-cache --console=plain
```

Result: `BUILD SUCCESSFUL` in 4m 13s.

- JUnit: 430 suites, 3,290 tests, 17 skipped, 0 failures, 0 errors.
- GameTest: 936 tests complete in 54.21s; all 936 required tests passed.
- The initial sandboxed invocation failed before compilation because Gradle distribution
  download access was denied. The identical permitted retry produced the successful result
  above; the first attempt was an execution-environment restriction, not a repository failure.

## Decisions made during implementation

Milestone 0 decisions:

- Frozen the canonical and compatibility IDs exactly as listed above.
- Confirmed `slate_roof_flat` as the canonical slate repair target; the historical stair keeps
  its ID and type.
- Frozen the terrain baked-model adaptive path as a release requirement.
- Confirmed that M1 will introduce one reusable implementation, not material-specific classes.
- Left all registrations, creative exposure, behavior, blockstates, models, textures, recipes,
  and the Illustrator master unchanged.
- Recorded rather than concealed the duplicate sandstone source slots.

Milestone 1 decisions:

- Named the reusable implementation `VariantTopOnlySlabBlock` and kept it in the existing
  block package beside `TopOnlySlabBlock` and `VariantCyclable`.
- Made logical-server authority explicit: client placement consumes no RNG and waits for the
  synchronized authoritative block state.
- Extracted a pure shared next-state helper so preservation and wraparound can be tested
  without changing the established server-side interaction behavior.
- Kept the new class unregistered and artwork-independent; M3 and M5 will instantiate it for
  canonical materials.
- Left initial fluid-state correction to M2 instead of broadening M1 into base-class hardening;
  M2 has now completed that correction.

Milestone 2 decisions:

- Chose a full selection outline when acquired-bottom data is present because the rendered,
  collision, support, and occlusion contracts already represent a full block in that state.
- Made synchronized block state the sole shape authority. The block entity continues to own
  and persist the texture while mirroring only its presence into `SUPPORTS_LANTERN`.
- Reserved the decorator for variant cycling and retained the wooden axe as the explicit
  adaptive-bottom clear action.
- Kept the fix in `TopOnlySlabBlock`; did not restore `AdaptiveRoofBlock` or any block-entity
  renderer.

Milestone 3 decisions:

- Kept `slate_roof_flat` as the sole canonical slate ID and left the historical stair and
  numbered flat IDs registered rather than attempting a destructive registry migration.
- Interpreted the one-player-facing-item requirement literally: only the canonical flat item
  remains in the normal creative tab, while all legacy item registrations remain intact.
- Used deterministic `3 -> 0`, `4 -> 1`, and `5 -> 2` visual fallback mappings so the complete
  six-state architecture has no missing model while the absence of three unique designs stays
  explicit.
- Rejected the raw orange/brown shingle candidate and historical stair texture as flat-slate
  variations after visual and provenance checks; neither matches the three shipped grey flats.
- Kept the existing adaptive terrain-model wrapper unchanged because it already includes the
  canonical and legacy slate IDs and excludes inventory models.

Milestone 4 decisions:

- Used direct PDF image-XObject decoding instead of Illustrator GUI export because the master
  is PDF-compatible and every authored placement is already an embedded full-resolution image.
  This path is lossless at extraction, scriptable, ordered, and does not touch the master.
- Treated the optional-content layer and content-stream drawing order as authoritative. The
  extractor fails rather than silently renumbering outputs if either changes.
- Kept the decoded sRGB values as RGB and omitted redundant embedded PNG ICC metadata; the
  master profile is standard sRGB and the game interprets the normalized texture values in the
  same color space.
- Selected LANCZOS with a reducing gap for the 1254-to-64 reduction, preserving the full square
  source extent consistently across every variation without crop, stretch, or artistic edits.
- Preserved all six sandstone filenames and the duplicate at slots 2 and 4. No replacement was
  synthesized and no other source was substituted.

Milestone 5 decisions:

- Used the exact shared `VariantTopOnlySlabBlock` class for Slate, Sandstone, and Limestone;
  material identity remains data-driven and no redundant material subclass was introduced.
- Generalized adaptive wrapping by registered block type instead of extending another manual
  ID list. The rule is intentionally limited to Britannia terrain models and continues to
  exclude inventory models.
- Kept one normal creative choice per canonical material while retaining all compatibility
  registrations and inventory identities.
- Treated the two new block items as a deliberate repository roster change and updated the
  existing exact-count regression contract to 927.
- Kept all M6 resource work out of M5. At the M5 gate, registration and adaptive-wrapper
  coverage were complete while base model mappings, item models, loot, and mining tags were
  deliberately deferred to M6.

Milestone 6 decisions:

- Centralized the top-half cuboid in one shared parent and kept variation/material identity in
  small child models. This removes duplicated geometry while leaving every resource edge
  explicit and testable.
- Mapped inherited `bottom` and `double` states to valid top-half fallback geometry. Normal
  placement and interaction continue forcing `TYPE=TOP`, but malformed or legacy state data
  can no longer resolve to a missing model.
- Defined "item variation 1" as the first authored design/model (`variation=0` in the
  zero-based block-state property) for all three canonical items.
- Preserved Slate's deterministic 1-2-3 mirror sequence and Sandstone's authored duplicate;
  resource completion did not relax the art-provenance constraints.
- Followed the repository's comparable-stone convention by adding the pickaxe tag only. No
  tier tag, recipe, migration, alias, or compatibility-ID change was introduced.
- Used the authoritative runtime loot API for ordinary and Silk Touch proof instead of
  duplicating loot-table interpretation in test code.

Milestone 7 decisions:

- Kept the project's default no-migration policy. Live-registry, saved-state, and saved-item
  round-trips preserve every frozen identity, so automatic conversion would add risk without
  repairing an observed compatibility defect.
- Treated the numbered flats' missing `double` selectors as a compatibility resource gap, not
  a reason to migrate blocks. Mapping that legal inherited state to each block's existing model
  preserves identity and requires no new artwork or behavior.
- Preserved `slate_roof_base` as a block-only historical registration. No evidence of a former
  item registration exists, so an item was not fabricated in the name of compatibility.
- Used Minecraft's real block-state and container-NBT codecs plus the built creative tab as
  acceptance authority rather than relying only on source-string registration assertions.
- Did not activate the conditional migration sub-milestone or its chunk-conversion lifecycle;
  no migration code exists to validate. A future migration still requires separate approval,
  complete preservation tests, and a deliberate data-fix strategy.

Milestone 8 decisions:

- Used an explicit requirement-to-test audit rather than creating a parallel monolithic JUnit
  suite. Existing focused contracts remain the authoritative owners of their behaviors and
  resources; only uncovered runtime paths received new tests.
- Chose the registered `BlockItem.place(...)` API as placement authority. Calling the block's
  state helper alone had already tested randomization but did not prove the item pipeline,
  world write, water replacement, block-entity creation, or item-to-block linkage together.
- Used 256 real placements per material. The acceptance condition is the exact observed set
  `{0,1,2,3,4,5}`, not a probabilistic minimum count or a mocked random implementation.
- Defined automated server-to-client coverage at the observable server boundary: synchronized
  block state, explicit update tag, decodable receiver state, and a non-null update packet.
  Actual packet delivery and second-client rendering remain correctly reserved for M9.
- Kept the GameTest server labeled accurately and did not treat it as the true dedicated-server
  restart, multi-client, Iris, or Photon acceptance environment.

Milestone 9 decisions:

- Used an isolated dedicated-server world so live restart proof could not modify the normal
  sandbox save, then restored the original server properties after the test.
- Accepted a true remote client as the required synchronization authority. A second client was
  not feasible in the project's shared development run directory and was optional in scope.
- Separated technical visual acceptance from art approval: Iris/Photon behavior passed, while
  the visible Slate mirrors and authored Sandstone duplicate remain explicit art decisions.

Milestone 10 decisions:

- Retained `AdaptiveRoofBlock`. It is not registered by this project, but the active adaptive
  block entity still includes compatibility handling for its property. Removing that path is
  not a low-risk, roof-local cleanup.
- Kept migration and recipes consciously deferred. Compatibility already passes without a
  migration, and no recipe design was approved.
- Preserved selected M9 screenshots as tracked release evidence while leaving the isolated
  world, shader jars, and shader pack under ignored `run/` paths for reproducibility.
- Removed the two temporary ignored launch helpers and restored the normal dedicated-server
  configuration. No speculative production cleanup was combined with the handoff.

## Risks / blockers

- The type-based adaptive-wrapper rule now covers every registered Britannia
  `TopOnlySlabBlock` terrain model automatically. Its inventory and foreign-namespace
  exclusions have regression coverage and its Iris/Photon terrain rendering passed live.
- Legacy numbered flat-slate resources now include valid bottom, top, and double selectors,
  while retaining their historical block and model identities.
- Slate still has only three unique usable flat designs. The six-state feature is technically
  complete and fully renderable via deterministic mirrors. The user explicitly accepted this
  mapping for release; unique designs 4, 5, and 6 are optional future improvements.
- Sandstone has six ordered production files but only five unique designs because source slots
  2 and 4 are identical. The user explicitly accepted the duplicate for release; a unique
  design remains an optional future master-art improvement.
- The client logged non-fatal Photon option/preprocessor warnings and Intel OpenGL debug noise.
  No corresponding roof rendering defect was visible during the shader acceptance pass.
- A second simultaneous development client was not feasible. One true remote client plus the
  registered-world synchronization/persistence suite supplied the practical coverage.
- The dirty worktree contains unrelated user changes. Preserve them and scope future diffs
  carefully.
- No technical behavior, compatibility, resource, dedicated-server, shader-rendering, or
  art-approval blocker remains.

## Current status

**MILESTONE 10: COMPLETE — RELEASE APPROVED**

Milestone 9 passed its true dedicated-server restart and real-client Iris/Photon technical
gate. M10 cleanup, documentation, and the final combined JUnit/GameTest command all passed.
The user explicitly approved the documented Slate mirror and Sandstone duplicate mappings for
release on 2026-08-30.

## Release disposition

The implementation is approved for release. Do not remove retained compatibility code or add
unapproved recipes/migration during handoff. The current deterministic Slate mirrors and
Sandstone duplicate are accepted release mappings; future unique artwork can replace them in a
separately reviewed asset update.
