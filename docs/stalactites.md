# Ceiling-Mounted Stalactites

## Worktree record

- Feature branch: `codex/stalactites`
- Worktree: `C:\projects\britannia\mod\Britannia_Mod_stalactites`
- Starting `patch-18` commit: `40fa27d27d4b29f37e9cd7d4a981ef4160a6a518`
- The original `patch-18` worktree was left untouched, including its pre-existing tracked and untracked changes.

## Existing architecture and scope

The existing family consists of seven numbered blocks, `stalagmite_1` through
`stalagmite_7`. `BlockRegistry` and `ItemRegistry` create the family in numbered
loops, the Decorative creative tab enumerates the registered blocks, and every
variant uses `TallDecorativeBlock`. That class supplies horizontal facing and a
two-block-tall `0..2` voxel shape; it does not impose floor support or react to
support removal.

The four horizontal blockstate variants point to one Blockbench-style JSON model
per numbered variant. Those models use geometry from Y=0 through as much as Y=32,
and collectively reuse three existing stalagmite textures. The repository has no
stalagmite loot tables, recipes, tags, datagen registrations, natural-generation
references, or admin/decorator utilities to mirror. Accordingly, this change adds
a complete registered and player-placeable counterpart without inventing cave
world generation or unrelated data systems.

## Variant parity

| Existing stalagmite | New stalactite |
| --- | --- |
| `stalagmite_1` | `stalactite_1` |
| `stalagmite_2` | `stalactite_2` |
| `stalagmite_3` | `stalactite_3` |
| `stalagmite_4` | `stalactite_4` |
| `stalagmite_5` | `stalactite_5` |
| `stalagmite_6` | `stalactite_6` |
| `stalagmite_7` | `stalactite_7` |

## Implementation

`StalactiteBlock` is a focused subclass of `TallDecorativeBlock`, so the new
family preserves the existing horizontal-facing behavior without changing the
stalagmite implementation. A stalactite survives only when
`Block.canSupportCenter` accepts the block directly above it on its lower face.
Removing that upper neighbor replaces the unsupported decorative block with air,
matching the repository's existing unsupported-decoration convention. Normal
`BlockItem` placement is retained: clicking a ceiling's `DOWN` face already
selects the adjacent block immediately below the ceiling.

The generated model transformation reflects every element around model Y=16:

```text
newFromY = 16 - oldToY
newToY   = 16 - oldFromY
```

Rotation origins are reflected too; X/Z rotation angles would be negated, top and
bottom faces are exchanged, and side-face V coordinates are reversed. The
checked-in generator makes the transformation reproducible. Existing texture
references and model display transforms are reused rather than duplicating art.

The inherited stalagmite shape is `Y=0..2` in normalized block coordinates. Its
exact ceiling reflection around the attachment plane at Y=1 is `Y=-1..1`, which
is used for both selection and collision. This covers the anchor block below the
ceiling and the intentionally extended block beneath it, corresponding to the
mirrored models' `Y=-16..16` maximum envelope.

Seven blocks, seven `BlockItem`s, translations, four-way blockstates, block
models, item models, and Decorative-tab entries were added. The new entries are
grouped immediately after their stalagmite counterparts.

## Changed components

- `src/main/java/com/seggellion/britannia_mod/block/StalactiteBlock.java`
- `src/main/java/com/seggellion/britannia_mod/gametest/StalactiteGameTests.java`
- `src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java`
- `src/main/resources/assets/britannia_mod/blockstates/stalactite_1.json` through `stalactite_7.json`
- `src/main/resources/assets/britannia_mod/lang/en_us.json`
- `src/main/resources/assets/britannia_mod/models/block/decorations/cave/stalactite_1.json` through `stalactite_7.json`
- `src/main/resources/assets/britannia_mod/models/item/stalactite_1.json` through `stalactite_7.json`
- `src/test/java/com/seggellion/britannia_mod/StalactiteResourceParityTest.java`
- `src/test/java/com/seggellion/britannia_mod/bannerdyeing/Milestone14RRemovalAndPreservationTest.java`
- `tools/generate_stalactite_assets.py`
- `docs/stalactites.md`

## Automated validation

- `gradlew.bat compileJava compileTestJava --rerun-tasks --no-configuration-cache --console=plain`
  - Passed: all main and test sources compiled.
- `python tools\validate_resources.py --scope stalactite_`
  - Passed with 0 errors and 57 warnings. The matching `stalagmite_` scope also
    reports exactly 0 errors and 57 warnings; these warnings are inherited model
    characteristics (display-transform completeness, inventory bounds, and
    coplanar faces), not missing stalactite resources.
- `gradlew.bat test --tests com.seggellion.britannia_mod.StalactiteResourceParityTest --tests com.seggellion.britannia_mod.bannerdyeing.Milestone14RRemovalAndPreservationTest --rerun-tasks --no-configuration-cache --console=plain`
  - Passed. All seven models, textures, reflected coordinates, face/UV mappings,
    four facing variants, item parents, and the updated repository item count have
    parity.
- `gradlew.bat runGameTestServer --rerun-tasks --no-configuration-cache --console=plain`
  - The server started and ran all 460 GameTests. All four new stalactite tests
    passed. One unrelated existing training-dummy test failed:
    `validHitsArePerPlayerRateLimitedAndDoNotConsumeDurability`.
- `gradlew.bat runGameTestServer --no-configuration-cache --console=plain`
  - The full 460-test retry produced the identical one-test training-dummy
    failure; all stalactite tests passed again.
- `gradlew.bat build --rerun-tasks --no-configuration-cache --console=plain`
  - Compilation, resources, and packaging passed. The broad unit suite ran 2,222
    tests (17 skipped) and failed 22 tests: 21 unrelated existing banner
    asset-hash/validator contracts and one Windows line-ending-sensitive
    display-case source-text assertion. The focused stalactite test passed within
    this run.

No stalactite registration, missing-model, missing-texture, or resource-resolution
error appeared during validation or GameTest startup.

## Manual in-game validation still required

Automated checks cannot establish final visual quality. In a graphical client:

1. Build a flat stone ceiling and stand beneath it.
2. Select each `stalactite_1` through `stalactite_7` item from the Decorative tab.
3. Click the underside of the ceiling and confirm the block appears immediately
   below the selected support.
4. Confirm every broad end is flush with the ceiling, every point faces down, and
   no variant floats or unexpectedly penetrates the support.
5. Walk or fly around each variant and compare selection/collision with its model.
6. Remove each supporting ceiling block and confirm its stalactite disappears.
7. Place each original stalagmite beside its counterpart and visually confirm the
   profiles are vertical inverses and the originals still point upward.

## Limitations and deferred questions

- No natural generation was added because the existing stalagmites have no
  discoverable world-generation integration to mirror.
- The source stalagmite models intentionally extend into a second block and carry
  validator warnings. Their stalactite counterparts preserve those exact traits.
- Final in-client model, lighting, and hitbox inspection remains outstanding as
  described above.
