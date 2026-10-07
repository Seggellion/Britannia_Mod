# Patch 18 plaster/support corner discovery

Prepared 2026-10-06. Target: `patch-18`, release `0.1.8d`. Discovery only; correction is **not implemented**.

The repository confirms a geometry defect matching the transcribed state: the selected corner's only full-height timber post is at its perpendicular arm's free end, while plaster occupies the junction. All four facings with `branch_right=false`, either `mirrored` value, select this arrangement. The `branch_right=true` corners instead contain a timber post at the junction. Recommend a narrowly scoped correction to the authored false-branch corner assets and their half-height derivatives, keeping registry IDs, state mappings and Java collision behavior.

Client confirmation is still required. Original screenshots were not accessible, no Minecraft scene was created, and no rendered before/after evidence was collected. The junction edge is established from geometry; the final post footprint and whether a free-end post should also remain need visual agreement at M0. An implementation playbook can now specify concrete files and gates, but should not treat M0's visual/design contract as already accepted or claim discovery's full rendered completion criterion is met.

## 1. Baseline and scope

| Fact | Observed value |
|---|---|
| Verified Git/repository root | `C:/projects/britannia/mod/Britannia_Mod` |
| Current branch / required target | `patch-18` / `patch-18` |
| HEAD and cached `origin/patch-18` | `7ca44a78d2438ae2204c4f64271ef84bb0672740` |
| Cached local/remote relationship | `git rev-list --left-right --count patch-18...origin/patch-18` = `0 0`; no fetch or live remote query |
| Configured remote | `origin`, fetch/push `https://github.com/Seggellion/Britannia_Mod.git` |
| Tracked/index baseline | Clean, `git diff --stat` empty |
| Initial untracked owner work | Five root playbooks/kickoffs listed below plus `docs/hotfixes/edge-fence-0.1.8d/` |
| Actual source runtime pins | Minecraft `1.21.1`, NeoForge `21.1.72`, GeckoLib `4.6.6` |
| Metadata ranges | MC `[1.21.1,1.21.2)`, NeoForge `[21.1.72]`, loader `[4,)` |
| Source release | `gradle.properties:37`: `mod_version=0.1.8d` |
| Build tools | Gradle wrapper `8.9`; NeoGradle userdev `7.0.165`; foojay `0.8.0`; Java daemon requirement `21` |
| Executed installed-tool identity | Gradle 8.9, launcher Microsoft Java `21.0.8+9-LTS`, Windows 11 |
| Declared mapping properties | Parchment MC `1.21`, mappings `2024.07.28`; not the runtime MC version |

No applicable `AGENTS.md` was found at the root, ancestor directories through `C:/`, or the inspected documentation/source/tool paths. Read `README.md`, `build.gradle`, `settings.gradle`, `gradle.properties`, CI workflow and asset script instructions. Did not switch branches, reset/discard files, commit, push, build release JARs, edit implementation/resources/versions, launch the production client, or modify a world. No worktree was needed for read-only source inspection and isolated baseline test attempts.

Preserved initial untracked files:

* `ULTIMACRAFT_PATCH18_0.1.8d_ALLIGATOR_CODEX_KICKOFF.md`
* `ULTIMACRAFT_PATCH18_0.1.8d_ALLIGATOR_HOTFIX_PLAYBOOK.md`
* `ULTIMACRAFT_PATCH18_0.1.8d_EDGE_FENCE_HOTFIX_PLAYBOOK.md`
* `ULTIMACRAFT_STARFARER_MEDALLION_PHASE2_CODEX_KICKOFF.md`
* `ULTIMACRAFT_STARFARER_MEDALLION_PHASE2_PATCH18_PLAYBOOK.md`

Nine existing worktree entries were inventoried (including the canonical checkout). Other branches/worktrees remain untouched, including `patch-19`, medallion, plant-growth and harness work. This is NeoForge plaster corner discovery; those tasks and other materials' implementation are excluded.

Version authority is `gradle.properties`. `build.gradle:7` sets project version from it; `:290–311` expands `src/main/templates/META-INF/neoforge.mods.toml:26`; `:359–403` emits `britannia_mod_build.properties`; `:488,572` checks artifact version against the same property. README is release status prose, not a second version input. Generated metadata/resources and already built JARs are snapshots, not independent authorities. Both `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml:26` and `build/resources/main/META-INF/neoforge.mods.toml:26` currently contain `0.1.8d`. Existing `build/generated/sources/buildInfo/britannia_mod_build.properties` records `0.1.8d`, HEAD `e350a67b7f5be33afed665387ec1a2fe74e0125e`, dirty=true, timestamp `2026-10-06T15:17:35.491834800Z`, so it does not identify current HEAD.

Existing release work is material: version bump commit `4b060fcf` already established `0.1.8d`; latest commits integrate alligator and wooden-fence hotfixes. `docs/projects/patch18-edge-fence-hotfix/RELEASE_HANDOFF.md` records a held clean candidate from `07359f98`, plus missing client acceptance. `README.md:10–12` retains the medallion visual hold; alligator client acceptance also remains pending. A future plaster candidate must include the then-current Patch 18 integrations and preserve these holds, without reusing an older same-version JAR or overwriting its evidence. These are integration dependencies, not permission to perform other fixes or distribution.

## 2. Evidence and runtime limitations

Owner observation A names `image(20261006-222222).png`: exterior plaster/timber building corner lacks the expected junction pillar; timber is described at the opposite end. Observation B names `image(20261006-222241).png` and transcribes:

```text
Targeted Block: 5260, 81, 4160
britannia_mod:plaster_wall_and_support_blank
branch_right: false
facing: south
half: lower
mirrored: false
shape: corner
```

The supplied attachment directory contains only `Pasted text.txt`; the named screenshots were not in the repository's `run/screenshots` inventory. Neither image was inspected. Camera direction, neighboring blocks, installed client JAR and ownership of visible neighboring pillars remain unknown. World coordinates are a scene reference, not a test requirement.

Source evidence includes registry/classes, all 96 exact blockstate variants, four corner meshes, half derivatives, generator functions, shared-resource reference searches, existing tests and cached NeoForge/Minecraft source. [GEOMETRY_AUDIT.json](GEOMETRY_AUDIT.json) records selected models, every element/pivot/face, hashes, bounds and comparisons. [STATE_MODEL_MATRIX.md](STATE_MODEL_MATRIX.md) gives all 32 valid corner property combinations. [audit_geometry.py](audit_geometry.py) reproduces these read-only computations.

`build.gradle:118–127` lists resource sources `src/main/resources`, `src/generated/resources`, `build/generated/gametest`; generated metadata and build identity are also resource inputs. `src/generated/resources` was absent. Development client uses compiled project output, not a release JAR by design. An actual launch's effective resource list was not observed. No Java/Javaw process was returned by the runtime inventory at that point; a later Gradle attempt created a daemon, not a game client.

Repository-local `run/options.txt:51–52` has empty selected/incompatible resource-pack lists; `run/resourcepacks` had no entries. `run/mods` contains Iris `1.8.0`, Sodium `0.6.0` and WorldEdit `7.3.8`; `run/config/iris.properties:6,8` enables `photon_v1.1.zip`. This is evidence about the local default run directory only, not proof of the owner's screenshot configuration. Do not change these settings for discovery. Later use a separate fresh client directory without overrides for the first visual check.

Read-only JAR comparison: existing `0.1.8c-all` and `0.1.8c-thin` carry dirty source `c1f2b5b` from 2026-09-23; all 16 target blockstate/model files are byte-identical to current source. Existing `build/libs/britannia_mod-0.1.8d-all.jar` carries clean source `44359121` and timestamp `2026-10-06T00:15:17.175199Z`; target blockstate is byte-identical and all 15 model JSONs are semantically identical (byte formatting differs). Thus this source defect also exists in these packaged assets; differences in file bytes alone must not be called stale geometry. The screenshot's loaded JAR, overrides, or shader effects cannot be excluded without its runtime identity. No packaged candidate was rebuilt or replaced.

## 3. Registry, properties and state producers

Exact registration: `src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java:2479` -> `MirrorableWallBlock extends DoubleWallBlock`; stone properties, strength 2, `noOcclusion()`. Item registration: `registry/ItemRegistry.java:2392` -> ordinary `BlockItem`. The item model `src/main/resources/assets/britannia_mod/models/item/plaster_wall_and_support_blank.json:1` parents the straight model. The corner is automatically connected, with player-facing and decorator overrides: a hybrid of automatic connection selection and explicit orientation/feature control, not a separately registered manually chosen corner item.

All Java paths in the following table are under `src/main/java/com/seggellion/britannia_mod/`:

| Property | Valid values / default | Discovered meaning | Writers | Readers |
|---|---|---|---|---|
| `facing` | north/east/south/west; north | Edge the main strip hugs, not a ray inferred from screenshot | `block/DoubleWallBlock.java:82–95,133–146,209–221`; decorator `item/InteriorDecoratorToolItem.java:170–179` | connection derivation, shape, JSON Y rotation |
| `shape` | straight/corner/t_junction; straight | One strip or two perpendicular strips; corner vs T has different mesh cut/ownership | `DoubleWallBlock.java:95,116,144`; `block/WallConnection.java:61–127`; enum `block/WallShape.java:5–8` | shape and JSON model selector |
| `branch_right` | false/true; false | Relative CCW/CW branch from facing before feature mirror; ignored for straight rendering | connection derivation, `DoubleWallBlock.java:221` structure mirror | connection tie-break, collision, model suffix |
| `half` | lower/upper; lower | Two stored blocks; lower mesh spans Y 0..32, upper mesh is air | placement `DoubleWallBlock.java:93,99–101`; commands/structure state loading | paired destruction, neighborhood base, mesh suppression |
| `mirrored` | false/true; false | Reflect authored furniture across canonical x=8; for corners also reflects the branch footprint | `block/MirrorableWallBlock.java:33–46,71–77`; offhand decorator `InteriorDecoratorToolItem.java:139–150` | mirrored model and effective branch in `MirrorableWallBlock.java:62–67` |

There is no style property on this block. Each stated domain is defined by the registered Java properties, not inferred from one screenshot. Total state domain = 4 × 3 × 2 × 2 × 2 = 96.

Placement sets facing opposite the player's horizontal look direction, checks replaceable space above and build height, derives connections, and installs upper with copied state. Clicked face and fractional hit location are not consulted by this class (`DoubleWallBlock.java:82–101`); ordinary BlockItem targeting determines clicked position. Every `updateShape`, including relevant vertical updates, rederives; loss/misidentification of the companion half returns air (`:105–116`). Both halves derive from the lower-position horizontal neighborhood (`:133–146`). Full walls, doors and tagged `wall_connectable`/`wall_terminal` blocks qualify (`:154–174`); an arbitrary solid stone or a half wall is not automatically a full-wall peer. Tags are defined in `block/ArchitecturalTags.java` and `src/main/resources/data/britannia_mod/tags/block/wall_{connectable,terminal}.json`; connectable includes doors and optional gates, terminal is empty by default.

`WallConnection.java:66–127` counts neighbors on both axes, preserves a straight's edge when possible, adopts straight-run edges, and omits a return if one existing strip already reaches both neighbor axes. A retained isolated corner is not a normal placement outcome: any subsequent derivation makes it straight. `shape=corner` can be reached when neither single strip satisfies a two-axis join. Derivation's final junction facing is north/south (the east-west strip); east/west corners are legal saved/command states and can be selected by decorator rotation, but are not returned as junction facings by the normal derive path. Updates can normalize those orientations again. Both branch values are reachable; all mirrors can be set with the tool; all upper states can exist independently but normal placement initially copies lower.

Rotation changes facing only. Structure mirror flips facing and, for non-straights, branch; the subclass additionally toggles mirrored (`DoubleWallBlock.java:209–221`, `MirrorableWallBlock.java:71–77`). Offhand decorator flips the clicked block's mirrored value; main hand rotates the clicked block's facing. Neither path explicitly synchronizes the companion half or rederives the clicked state. Generic `/setblock`, `/fill`, structure palettes can carry legal states. `commands/RandomizeWallsCommand.java:164–176,196–209` operates on `CustomWallBlock`, not this family. No additional target-specific property writer was found.

Two separate shared-code regression risks need tests, not an assumed widening of this hotfix: connection peer extraction does not include a neighbor's `mirrored` value, although its rendered branch changes; and mirror/decorator operations can leave the upper half with different feature state. Structure mirror flips both chirality selectors, so effective branch reflection also needs direct transformed-geometry testing. These do not explain the fixed-state missing post proven below; any Java correction needs a separately reviewed contract and shared-window regression scope.

## 4. Exact mesh chain and geometry cause

`src/main/resources/assets/britannia_mod/blockstates/plaster_wall_and_support_blank.json:187–193` selects:

```text
south,corner,branch_right=false,mirrored=false,half=lower
 -> britannia_mod:block/structure/plaster/plaster_wall_and_support_blank_corner
 -> Y=180 degrees (no X transform, no uvlock)
 -> parent minecraft:block/block
 -> textures #1 dirty_plaster, #2 wood_support
```

Both textures are PNGs under `src/main/resources/assets/britannia_mod/textures/block/structure/plaster/`. The model has its own elements, so the vanilla parent supplies no replacement pillar mesh. Resolved vanilla `assets/minecraft/models/block/block.json` and `air.json` were read directly from `.gradle/caches/minecraft/versions/1.21.1/client.jar`: block has display transforms but no parent/elements; air has no elements. Their complete JSON is retained in [VANILLA_PARENTS.json](VANILLA_PARENTS.json). The target sets `ambientocclusion=false`, no custom `render_type`, and no face `cullface` declarations. Its `format_version=1.21.11` is author/export metadata; it does not change the verified game pin `1.21.1`. Every element rotation is angle zero; listed origins therefore do not move this geometry. UV `rotation` entries rotate texture coordinates, not the solid element.

Model source: `src/main/resources/assets/britannia_mod/models/block/structure/plaster/plaster_wall_and_support_blank_corner.json`. Elements have no names; stable descriptions below use zero-based index and explicit bounds:

| Element | Source bounds `(from -> to)` | Texture / role | Bounds after Y=180 |
|---|---|---|---|
| 0, lines 12–14 | `(5,0,0) -> (16,31.9,5)` | #1 main north slab | `(0,0,11) -> (11,31.9,16)` |
| 1, lines 24–26 | `(5,0,-1) -> (16,1,0)` | #2 outer main trim | `(0,0,16) -> (11,1,17)` |
| 2, lines 36–38 | `(5,0,5.5) -> (16,1,6.5)` | #2 inner main trim | `(0,0,9.5) -> (11,1,10.5)` |
| 3, lines 48–50 | `(0,0,0) -> (5,31.9,13)` | #1 west branch slab | `(11,0,3) -> (16,31.9,16)` |
| 4, lines 61–63 | `(-1,0,0) -> (0,1,13)` | #2 outer branch trim | `(16,0,3) -> (17,1,16)` |
| 5, lines 74–76 | `(5.5,0,0) -> (6.5,1,16)` | #2 inner branch trim | `(9.5,0,0) -> (10.5,1,16)` |
| 6, lines 87–89 | `(-0.8,0,13.1) -> (6.7,32,16.1)` | #2 only vertical timber post | `(9.3,0,-0.1) -> (16.8,32,2.9)` |

The canonical mesh's main north and west branch meet at the **northwest edge junction**, outer vertex `(x,z)=(0,0)`, slab seam near `x=5,z=0..5`. After Y=180 it is the **southeast edge junction**, vertex `(16,16)`, seam `x=11,z=11..16`. Its only vertical post instead occupies z≈0..3, near the northeast/free end of the east branch. No timber element reaches the junction's z≈11..16 region at full height. This is a solid geometry relocation, not a center pillar `(8,*,8)`, UV-lock issue, hidden cullface, wrong texture alias, or extra upper-half mesh.

Y rotation follows `(x,z)->(16-x,16-z)` for 180 degrees, the same transform implemented in `src/test/java/com/seggellion/britannia_mod/block/WindowArt.java:35–43`. Mapping and slab orientation match Java's south/east strip contract. Changing the entire model rotation to move the post would also move the wall slabs and break that contract.

For canonical `branch_right=false,mirrored=true`, the mesh reflects x and retains the far-end post z=13.1..16.1. In contrast, `plaster_wall_and_support_blank_corner_branch_right.json:100–102` has a junction timber at `(9.3,0,-0.1)->(16.8,32,2.9)` and a second free-end main post at `:48–50`. Its mirror `...corner_branch_right_mirrored.json` provides the northwest junction counterpart `(-0.8,0,-0.1)->(6.7,32,2.9)`. These are positive **source comparators**, not client-certified known-good corners. The straight model has one post at `(-0.1,0,-0.8)->(2.9,32,6.7)` (`...straight.json:51–53`), demonstrating the established timber dimensions/texture.

Affected source coverage: 8 lower corner states = four rotations × two mirrors × false branch. Their 8 companion upper variants draw air and therefore cannot supply the absent post; the lower model's Y 16..32 supplies the visible upper section. All 8 lower true-branch cases contain a junction post. Separate single-height `plaster_wall_and_support_blank_half` has no mirrored/half property; its four false-branch rotations inherit the same free-end placement. Half corner elements equal the current full meshes sliced to Y16 in all four junction suffixes. T-junctions have different through-run ownership and pillar counts; they must be regression checked but this investigation does not classify their free-end support as the same confirmed corner defect.

Cause ranking: (1) authored corner geometry is confirmed inconsistent with the expected junction pillar; (2) state changes can select a different arrangement in a live scene but are not needed for the exact fixed-state defect; (3) no wrong exact-state variant/rotation or shared parent pillar was found; (4) loaded-resource/shader identity remains an untested runtime hypothesis, reduced by equal packaged geometry. Historical generator composition explains how a far-end post can arise: `tools/scaffolding/gen_junctions.py:250–265` clips the original west-side post out of the main arm, then rotates its straight copy to the far end of the west branch. The current authored corner retains that arrangement, but current generator output differs in trim details; do not claim the currently shipped file is freshly generated.

## 5. Asset ownership and impact

| Source / process | Observed ownership and later implications |
|---|---|
| Four full corner meshes | No `GENERATED FILE` comment; treated as hand authored by `tools/mcjson.py:76–98`. Direct authoritative inputs for a bounded corner art fix; no corresponding plaster `.bbmodel` was located. |
| `tools/scaffolding/gen_junctions.py:295–313` | Has support family, but skips hand-authored destination models. Pure `build()` comparison differs for every current full corner/T mesh; never overwrite these by assuming generated ownership. |
| `tools/scaffolding/gen_blockstates.py:48,102–115,148–163` | Mirrorable list includes only the two window families, omitting support; generates `_window_right`, whereas shipped support mapping uses `_mirrored`. Its main would overwrite this blockstate with a non-mirrored selector. Not a safe regeneration command for shipped support states. |
| `tools/generate_half_walls.py:68–120,180–225` | Half junctions are generated slices from full meshes. `--check` verifies straight cutting only, not whether all checked-in derived files are current. Current check refuses because full straight trim changed while half straight trim did not. |
| `tools/scaffolding/normalize_sources.py:20–23` | Mentions `tools/build_assets.py`, which does not exist in this checkout. Do not invent that command or a Gradle wall datagen task. |

Discovery imported pure generator functions with bytecode writing disabled, without invoking their write/main paths. Pure comparison proves all four current half junction suffixes equal their full sources after slicing. It also proves `gen_blockstates`' mirrorable inventory drift. Existing `tools/gen_expected_states.py` correctly includes support as mirrorable and enumerates 96 states.

All JSON parent and blockstate references were searched. Full target corners are directly selected by this blockstate only; no other model parents them. Their half counterparts are used by `plaster_wall_and_support_blank_half.json`. Target straight and half-straight are item-model parents. The target texture is shared broadly by plaster architecture; do not edit textures to relocate a solid post. Generic geometry helpers/generators also serve windows, sandstone, bannister and other plaster families; a shared edit multiplies regression scope. Pure local asset edits avoid this wider impact.

Later source workflow should explicitly preserve protected full meshes and regenerate only the affected support half corners through a bounded family/suffix path. Add that narrow path or equivalent checked derivation if necessary. Resolve or explicitly exclude the straight-half trim mismatch before using the broad generator; do not silently change all four families to make it pass. The stale blockstate generator should either gain a safe support mapping path with exact current mirror names or be explicitly excluded from this correction's workflow. Asset ownership is clear; the broad regeneration scripts are not currently reliable authorities for every shipped file.

## 6. Selection, collision, support and occlusion

`DoubleWallBlock.java:59–62,235–254` uses seven-pixel edge strips. Exact observed state selection/collision is union of south `(0,0,9)->(16,16,16)` and east `(9,0,0)->(16,16,16)`, in each stored half. Collision calls virtual `getShape`, so the mirrored subclass branch flip applies. With mirrored=true effective branch is `branch_right XOR mirrored`; the mesh and shape occupy the same pair of edges.

These are coarse slabs, not per-element pillar outlines. Rendered five-pixel wall cores and projecting trim/posts extend outside or leave parts of the seven-pixel strip unused. The missing visual timber does not mean the junction lacks collision: its plaster and coarse strips still block movement. Pillar/trim fractional overhangs already lie outside the voxel outline. Do not turn a visual correction into a silent collision expansion.

No custom shape cache, support or occlusion override is defined in either target class. Resolved dependency `net/minecraft/world/level/block/state/BlockBehaviour.java:280–285` defaults occlusion shape to selection and support to collision. `:498–504,889–928` caches non-dynamic state collision and support queries; `noOcclusion` skips occlusion face caching/ordinary solid occlusion. Dependency read location was `C:/Users/dusti/.gradle/caches/ng_execute/98ab97877c6086d6ec2a98ad9426eea0fc066abc7e18e078fb6989884dd10002/output/`. It is a cached resolved source, not a repository file. Relevant seven-pixel occupancy and trim tolerances need explicit tests, plus mouse outlines/walk checks after corrected art is selected.

## 7. Reproduction and verification matrix

The following is a **proposed test scene**, not a logged run. Use a fresh creative flat world with commands enabled, no resource packs/shaders, pinned MC/NeoForge, matching recorded mod/source identity. Convenient lower corner position C=`(0,80,0)` and upper C+Y. Place a floor at Y79 in the disposable world only. Use `plaster_wall_blank` arms first to prevent adjacent timber from disguising ownership; repeat using the same support material.

Exact-state fixture: west arm cells `(-1..-3,80,0)` facing=south; north arm cells `(0,80,-1..-3)` facing=east. Each arm has a correctly paired upper block at Y81. Corner lower is the owner state; corner upper has the same values except `half=upper`. With these neighbors `WallConnection.derive` should select south,corner,false. Main south slab continues west; east branch continues north. These are neighbor positions derived from world axes, not the camera.

Example commands for **that separate test world only** (command fixture; upper first, then lower, arms before corner). Repeat arm pairs at distances 2 and 3:

```mcfunction
/setblock -1 81 0 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
/setblock -1 80 0 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
/setblock 0 81 -1 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
/setblock 0 80 -1 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
/setblock 0 81 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper]
/setblock 0 80 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower]
```

Inspect both halves after the notifications; commands do not bypass derivation/pair destruction. If setup normalizes, record the before/after states rather than mislabeling the scene. For normal player placement, build both arms through BlockItem first (look north for a south edge, west for an east edge), then stand looking north and place support in C. It should derive the transcribed state with default mirrored=false. Repeat placing corner first and each arm order, recording every transition. An isolated command corner is a raw model probe only; it must not be assumed to persist through a neighbor update. Any test-only forced state installation should be clearly distinguished from normal BlockItem reachability.

Camera evidence: look from southeast toward C for the expected junction, from northeast for the existing free-end post, plus top/oblique and separate wall-face views. Give camera XYZ/yaw/pitch, lower and upper state dumps, neighboring IDs/states, order, texture/resource/shader lists, identity/hash, outline and collision observations. Capture without neighbors, with blank arms, then with support arms to distinguish block-local timber from neighbors. No camera coordinates or result screenshots have been invented here.

| Dimension | Required later check / acceptance |
|---|---|
| Facing/handedness | All 16 lower corner combinations and their 16 upper variants in the attached matrix; visually confirm all 8 affected cases and both true-branch comparators |
| Legal vs reachable | All 96 states have mappings; normally derived junctions face N/S, E/W junctions need forced/decorator states; no domain combinations are disallowed |
| Halves | Correct lower/upper pair, click/tool on each half independently, boundary Y16 continuity; support half-wall false/true branch across all four facings |
| Shapes | Straight, isolated/end-of-run (both use straight), corner, T, branch bits on straight; no `end` enum exists |
| Neighbors | Isolated, two blank arms, same-material arms, door/tagged gate, unrelated solid/post, full vs half family; record geometry across each cell boundary |
| Order | Arms first, corner first, reversed arms; compare equivalent intended edges, not arbitrary differently oriented builds |
| Transitions | Neighbor add/remove, decorator rotate and mirror, structure rotate/mirror, save/server restart, chunk eviction/load; dump state before and after |
| Runtime | Vanilla visual baseline then owner's shaders/resources once identity is known; reload resources and inspect packaged matching candidate |
| Physical | Selection outline, walking/jumping against both arms, no new invisible collision; preserve existing coarse-strip contract |

Smallest remaining discriminating experiment: open the unmodified exact-state scene with blank neighbors and no shaders, record state/identity and junction/free-end views, then compare `branch_right=true` in equivalent reflected surroundings. If the source post appears at the computed free-end bounds, the rendered source diagnosis is confirmed; if it does not, inspect effective loaded model/resource ownership before editing. At M0 agree whether the one existing post relocates or an additional junction post is required.

## 8. Executed checks and blockers

| Executed check | Result / retained evidence |
|---|---|
| Git/version/instruction/resource/code/history inspection | Completed; baseline above; no tracked changes |
| `python -B docs/hotfixes/plaster-corner-0.1.8d/audit_geometry.py` | 96/96 state coverage, no missing mod models, full 32 corner matrix; generator/half/JAR comparisons in `GEOMETRY_AUDIT.json` |
| `python -B tools/gen_expected_states.py` | Saved existing tool's expected domains to `EXPECTED_STATES.json` |
| `python -B tools/validate_resources.py --states docs/hotfixes/plaster-corner-0.1.8d/EXPECTED_STATES.json --scope plaster_wall_and_support_blank` | 0 errors, 88 existing coplanar-face warnings; 14,168 findings outside scope suppressed. `RESOURCE_CHECK.txt`. Warnings are static overlap findings, not new client-observed flicker. |
| `python -B tools/generate_half_walls.py --check` | Exit 1: refuses because support straight cut does not match authored half straight; `HALF_GENERATOR_CHECK.txt`. Difference is trim at full z=-0.8..0.2 and 4.9..5.9 versus half z=-1..0 and 5.5..6.5. No assets written. |
| `./gradlew.bat --version --offline` | Wrapper still tried distribution download; network socket permission failure. `GRADLE_BASELINE.txt`. |
| Installed Gradle `--version --offline` | Passed tool identity only; `INSTALLED_GRADLE.txt`. |
| Four existing JUnit suites via installed Gradle, offline, isolated output init | First attempt failed resolving foojay 0.8.0 under sandbox cache access; `JUNIT_BASELINE.txt`. Cache-access retry failed `cacheVersionExecutableServer1.21.1`; `JUNIT_CACHE_ACCESS.txt`. **No test assertions executed.** |

Exact JUnit invocation, from repository root:

```powershell
& 'C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat' test `
  --tests com.seggellion.britannia_mod.block.WallJunctionAlignmentTest `
  --tests com.seggellion.britannia_mod.block.PlasterWallArtCollisionTest `
  --tests com.seggellion.britannia_mod.block.PlasterHalfWallArtCollisionTest `
  --tests com.seggellion.britannia_mod.block.PlasterRenderLayerContractTest `
  -I docs/hotfixes/plaster-corner-0.1.8d/discovery.gradle `
  --offline --no-configuration-cache --console=plain
```

Init script redirects build/test outputs into `tmp/plaster-corner-discovery` and supplies the source directory to `WindowArt`; it changes no application source. Existing test suites have useful shape/render-layer coverage but do not assert this full support corner's junction post location. `PlasterWallArtCollisionTest` covers blank plaster; `PlasterHalfWallArtCollisionTest` includes support half walls; `WallJunctionAlignmentTest` tests shared connection behavior. Comments claiming outdated art-profile/datagen/test availability are not evidence of current runtime code.

Not run: GameTests, client launch/render, actual BlockItem sequences, save/restart/chunk reload, full JUnit, build/check, packaging/hash gates, owner installed-runtime comparison. No baseline pass is inferred from earlier tasks' results.

Existing documented development/release commands are `./gradlew runClient -Pdev --no-configuration-cache`, `./gradlew test`, `./gradlew runGameTestServer --no-configuration-cache`, `./gradlew build`, `./gradlew artifactIdentity`; CI uses build and GameTestServer (`README.md:38–52`, `.github/workflows/build.yml:23,43`). Later client isolation can use the existing `-PalligatorClientRunDirectory=tmp/plaster-corner-client` property (`build.gradle:65–70`), despite its historical name; it also enables GameTests. Server/GameTest isolation uses `-PedgeFenceServerRunDirectory=...` / `-PgameTestRunDirectory=...` and `-PgameTestNamespaces=...` (`:77–95`). No plaster-specific namespace/test was located; do not invent an existing one. Check run-directory credentials and environment before any later runnable scene; do not reuse production worlds/configuration.

## 9. Fix comparison, compatibility and bounded project

| Option | Files/consumers | Compatibility / risk / verdict |
|---|---|---|
| Local authored asset correction | `.../plaster_wall_and_support_blank_corner.json`, `..._corner_mirrored.json`; derived `..._half_corner.json`; bounded half-generation support/check as needed | Preferred. Same saved selector shows corrected mesh automatically after resources reload/update. No IDs, domains, state migration or re-placement needed for correctly saved corners. Must preserve slab edges, Y32/Y16, timber dimensions, texture grain, boundary continuity and coarse collision. |
| Blockstate remapping | Target `blockstates/plaster_wall_and_support_blank.json`, generator mapping | Rejected as primary fix: current selector/rotation is correct; swapping branch/rotation moves slabs. Mapping false branch to reflected true art would also add/move another post, changing design beyond the reported single-post correction. |
| State/placement logic correction | DoubleWallBlock/WallConnection/MirrorableWallBlock/tool; all shared family consumers | Not justified for this exact-state post defect. Much broader saved-world and dedicated-server scope. Known mirrored/paired-half risks require focused evidence before separate contract changes. |
| Shared junction generator rewrite / combined change | All generated/authored plaster/sandstone consumers and dependencies | Rejected as default hotfix. Protected authored files do not regenerate now, and broad run is stale. Add only a bounded derivation/check path necessary for target assets. |
| Texture/uvlock change | Shared wood/plaster textures, mapping | Rejected: neither changes post volume location. |

Candidate geometric contract, to validate visually rather than implement blindly: canonical false-branch north/west junction timber should cover the established junction-side footprint `(-0.8,0,-0.1)->(6.7,32,2.9)` seen in the true-branch mirrored comparator. Reflect x for false-branch mirrored; rotate through all facings. For the observed south/unmirrored state this would place it at `(9.3,0,13.1)->(16.8,32,16.1)`, along the intended southeast junction. This is a proposed footprint consistent with existing family art, not an owner-approved final mesh.

Simply translating element 6 by z=-13.2 is **not a complete mesh prescription**: branch plaster currently stops at z=13 because the existing post fills its far end. A relocation must fill/re-cut that free-end segment, resolve post/plaster overlaps and hidden/copanar caps, and keep base trim continuous. Decide whether to preserve one post or retain a free-end support as well. Avoid copying the whole opposite-handed model without that decision: it contains two posts. Keep straight and T art unchanged unless new evidence justifies a separately recorded extension.

Saved-world effect: every placed false-branch corner using these models changes appearance automatically, including its visible upper portion. No registry or property migration is proposed. Independently mis-oriented saved states or neighborhood normalization are not repaired by an asset edit. Upper/lower values may already disagree, so physical observations must sample both. Derived support half corners would change all their false-branch placements as well; either include them for family consistency or explicitly defer with owner acceptance. Pure client resources have no new dedicated-server logic implication, but packaged client/server artifact parity and normal build gates still apply. If Java changes become necessary, require server interaction/save tests and expanded shared-window coverage.

Useful later regression assertions: a junction timber volume intersects the intended junction region and does not replace a required plaster run; reflected/rotated cases preserve bounds and UV orientation; every saved state selects exactly one existing model; current-authority mirrors/half slices regenerate deterministically; pillar remains in both vertical sections; collision/selection seven-pixel strips stay unchanged. Implement a model-driven support-corner test using existing `WindowArt`, with an independent geometric contract rather than a snapshot of implementation output. Add actual BlockItem/tool/neighbor paired-half fixtures and save/reload checks, and include affected half corners. These checks support but cannot replace client visual proof.

| Milestone | Concrete scope and dependencies | Acceptance evidence |
|---|---|---|
| M0 baseline/contract | Refresh latest exact `patch-18` SHA; retain `0.1.8d`; resolve usable dependency cache; reproduce exact state in fresh scene; inspect actual neighbor ownership; accept junction footprint and one-vs-two-post/half scope | Recorded resource identity, state/neighbor/camera sequence, baseline face views; accepted affected cases/design; generator ownership and narrow workflow written down |
| M1 corner correction | Edit authoritative false-branch corner + reflected counterpart; re-cut branch plaster/trim; derive affected half corner through scoped generator path; protect true branch/straight/T assets | Reviewable bounded diff, exact-state before/after plus both mirror views; unchanged IDs/properties/Java collision; generator-check evidence |
| M2 regression | All attached corner rows/vertical pairing, half family, shapes/neighbors/orders/transitions; target geometry/resource/JUnit checks and actual client mouse/movement/reload; shared checks only if shared code changes | No missing states, no added coplanar warnings, junction post/clean joins in all affected cases; baseline collision contract retained; actual save/reload/structure mirror results with unresolved existing risks called out |
| M3 release integration | Integrate into then-current Patch 18; retain other hotfixes/version/status; clean checkout build/check/full relevant suites; package bundled `-all` | Clean provenance, actual embedded metadata/hash/size and updated release notes; all required gates passed, existing medallion/alligator/fence holds still explicit |
| M4 handoff | Separately authorized distribution scope; packaged client smoke of same candidate; saved-world sample from backup and rollback procedure | Actual client visual evidence using packaged identity, matching server/client bytes; accurate held/released status and backup/rollback handoff |

M1 depends on M0 visual/design agreement and usable baseline checks. M2 depends on corrected assets and reliable narrow regeneration. M3 depends on M2 and concurrent integration review. M4 requires explicit later publishing/deployment authorization and release acceptance; this discovery grants none. Do not label source assets or held old same-version candidates release-ready.

**Status:** source geometry cause and affected selectors are established; an asset-focused implementation playbook can be written now with mandatory M0 client/design gates. Original screenshot/runtime identity, client reproduction and final post design remain missing. No correction, release preparation, publication or deployment was performed.
