# Unified Stone Slate Roof System — Implementation Report

Date: 2026-08-30 (America/Vancouver)

## Summary

The unified stone-roof implementation is technically complete. Slate Roof, Sandstone Roof,
and Limestone Roof are each exposed as one canonical player-facing block with six persistent
variation states. Placement selects a variation once on the logical server, the Interior
Decorator Tool cycles it, and the adaptive acquired-bottom texture continues to render through
the terrain baked-model path.

Dedicated-server restart testing and real-client Iris/Photon inspection passed. Final art
approval remains open because Slate has three unique flat designs mirrored across six states
and the Illustrator master supplies only five unique Sandstone designs for six authored slots.

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
  states. Unique designs 4, 5, and 6 require supplied art or explicit acceptance of the mirror
  mapping.
- Sandstone source positions 2 and 4 are identical, leaving five unique designs across six
  legal states. A unique sixth design requires an updated master or explicit acceptance.
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

## Release recommendation

Technical implementation: **GO**. The final M10 command completed green.

Final art release: **HOLD FOR ART DECISION**. Either supply unique Slate designs 4/5/6 and a
unique sixth Sandstone design, or explicitly accept the documented deterministic Slate mirrors
and Sandstone duplicate. No unresolved technical behavior, compatibility, resource, dedicated
server, or shader-rendering blocker is known.
