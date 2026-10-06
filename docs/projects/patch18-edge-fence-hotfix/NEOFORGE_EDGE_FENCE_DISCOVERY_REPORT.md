# NeoForge edge fence facing and connection discovery

Prepared 2026-10-06. Discovery baseline: `patch-18`, `c8044e4565281514253e290167c555413c7a5293`.

## 1. Disposition and evidence labels

**Ready with documented limitations.** The wooden fence's facing reversal is reproduced on the authoritative server in 12 of 24 ordinary BlockItem placement scenarios, and its first incorrect facing assignment is identified in the Patch 18 source. Its connected-state design deliberately discards the player's edge choice. A complete correction must also separate physical layout from mutable connection flags: preserving `facing` alone leaves automatic corner/T geometry able to replace the occupied edges. No application fix, dependency/version change, release build, merge, deployment, or production-world operation was performed.

Evidence labels used here:

- **Owner observation:** isolated placement looks correct; joining changes south to north or exchanges east/west. The owner's registry ID, click sequence, and client/server details were not supplied.
- **Source-confirmed:** conclusions derived from the checked-out Patch 18 Java and JSON, the actual resolved Minecraft/NeoForge sources, and read-only history.
- **Computed geometry:** vertices transformed from checked-in JSON using the resolved `FaceBakery` element rotation/rescale rules. These are mathematical measurements, not screenshots or observed client rendering.
- **Runtime:** only results explicitly recorded in section 10. The disposable probe uses actual registered blocks, a `ServerLevel`, an ordinary named `ServerPlayer`, and `BlockItem.place` with constructed hit results. It does not prove a keyboard/mouse raycast or client prediction behavior.
- **Proposed:** implementation design and regression expectations, not implemented or passing fixes.

The investigation can proceed to implementation without the owner's original click sequence: the source-level loss of edge information is decisive. Client visual acceptance and exact owner-surface confirmation remain separate requirements.

## 2. Baseline, policy, and release relationship

| Item | Verified value |
| --- | --- |
| Repository | `C:/projects/britannia/mod/Britannia_Mod` (verified with `git rev-parse --show-toplevel`) |
| Branch / HEAD | `patch-18` / `c8044e4565281514253e290167c555413c7a5293` |
| Starting tracked changes | None; `git diff` empty |
| Starting untracked owner files | `ULTIMACRAFT_PATCH18_0.1.8d_ALLIGATOR_CODEX_KICKOFF.md`, `ULTIMACRAFT_PATCH18_0.1.8d_ALLIGATOR_HOTFIX_PLAYBOOK.md`, `ULTIMACRAFT_STARFARER_MEDALLION_PHASE2_CODEX_KICKOFF.md`, `ULTIMACRAFT_STARFARER_MEDALLION_PHASE2_PATCH18_PLAYBOOK.md` |
| Configured version | `0.1.8d`; `gradle.properties:37` (see exact property, no bump) |
| Minecraft / NeoForge | Minecraft `1.21.1`, NeoForge `21.1.72`; hardcoded dependency agrees with properties |
| Mappings | NeoForm `1.21.1-20240808.144430`; Parchment properties `1.21` / `2024.07.28` are declared, but this build uses NeoGradle userdev, not the commented ModDevGradle example |
| Gradle / plugin | wrapper `8.9`; NeoGradle userdev `7.0.165`; foojay resolver `0.8.0` |
| Java | toolchain/daemon request 21; default PATH Java is Oracle `1.8.0_491`; explicitly selected launcher Temurin `21.0.9+10`; executed GameTest JVM Microsoft `21.0.8` |
| Other pinned libraries | GeckoLib `4.6.6`, nanohttpd `2.2.0`; JUnit BOM `5.11.4` |
| Cached remote Patch 18 | `origin/patch-18 = c1f2b5b819834ab971a2fc65b86fcd484d31e7c9`; local branch ahead 8, behind 0; no fetch performed |
| Release relationship | `4b060fcf` bumped Patch 18 to `0.1.8d`; `44359121` marked it undeployed; HEAD incorporates the recent alligator work |
| Main discrepancy | Local `main` already exists at `f039c4b2`; it was neither created nor used. User's remote-only-main guardrail was honored for this task |
| Patch 19 | Exists at `27bce5d3`; merge base with Patch 18 `a4ed88cf5a03435c01eed049810898ea98b18ebf`. No Patch 19 source was used as the baseline or imported |

No applicable `AGENTS.md` was found at the workspace, `C:/`, `C:/projects`, `C:/projects/britannia`, `C:/projects/britannia/mod`, or within relevant source/docs/tool paths. No tracked contribution instructions or `CLAUDE.md` were found. Read `README.md`, `.github/workflows/build.yml`, `build.gradle`, `settings.gradle`, the existing fence evidence, and the relevant isolated-run/release instructions. Owner kickoff files were read as context, not as authorization for their unrelated implementation work.

`README.md:9` identifies this as an undeployed recovery candidate and retains the medallion visual release hold. The dirty-tree packaging gate counts untracked files. Discovery does not invoke that gate or create release jars. Windows paths and the installed Windows JDK/Gradle were sufficient; no WSL mapping was needed.

Existing worktrees were inventoried and left untouched:

| Checkout, relative to repository unless absolute | HEAD | Branch |
| --- | --- | --- |
| canonical root | `c8044e45` | `patch-18` |
| `.claude/worktrees/grape-growth-hotfix-bbd73d` | `c1f2b5b8` | `claude/starfarers-medallion-phase-2-b56948` |
| `.claude/worktrees/grape-growth-investigation-71dfce` | `a4ed88cf` | `claude/grape-growth-investigation-71dfce` |
| `.claude/worktrees/ultimacraft-harness-discovery-dfc252` | `f039c4b2` | `claude/wood-trader-spawn-investigation-f2cbb8` |
| `.claude/worktrees/ultimacraft-plant-growth-discovery-8db6b9` | `bed34811` | `claude/ultimacraft-plant-growth-discovery-701580` |
| `.claude/worktrees/unruffled-goldwasser-32ba34` | `9bc19d30` | `claude/ultimacraft-p18-celestial-discovery-1cffea` |
| `tmp/medallion-patch18-verify` | `df170a4f` | detached |
| `tmp/medallion-patch19-port` | `27bce5d3` | `patch-19` |
| `C:/projects/ultimacraft-worktrees/neoforge/harness-h6-neoforge` | `a362a66b` | `task/harness-h6-neoforge` |

The canonical checkout is an appropriate existing Patch 18 baseline. Diagnostics live under ignored `tmp/edge-fence-discovery`; an init script supplies temporary probe sources and separate compilation/test outputs. No production Java was instrumented. Existing run worlds and release artifacts were not selected for testing.

## 3. Affected inventory

Paths in this report are repository-relative. Java paths below use prefix `src/main/java/com/seggellion/britannia_mod/`; resource paths use `src/main/resources/assets/britannia_mod/`.

| Registry ID | Implementation / registration | Resources / shared behavior | Defect disposition |
| --- | --- | --- | --- |
| `britannia_mod:wooden_fence` | `block/WoodenFenceBlock.java`; `registry/BlockRegistry.java:2492`; ordinary `BlockItem`, `registry/ItemRegistry.java:2396` | `blockstates/wooden_fence.json`, seven `models/block/structure/wooden_fence/*.json`, `textures/block/structure/wooden_fence.png`; server chunk reconciliation | Reproduced on server in all six south and all six east scenarios; primary affected family |
| `britannia_mod:iron_fence` | `block/IronFenceBlock.java`; `BlockRegistry.java:917`; ordinary `BlockItem`, `ItemRegistry.java:1861` | `blockstates/iron_fence.json` multipart, bottom/middle/top `models/block/decorations/iron_fence_*.json`; vertical part only | Source excludes ordinary horizontal facing reassignment; runtime family-specific check pending |
| `britannia_mod:corral_fence` | `block/HorizontalTallBlock.java`; `BlockRegistry.java:935`; ordinary `BlockItem`, `ItemRegistry.java:1423` | `blockstates/corral_fence.json`; four style variants of straight authored corral model | Source has no neighbor-facing update; style/hitbox defects separately noted |
| `britannia_mod:corral_corner_fence` | `block/structure/ThinWall.java` (declares package `...block`), registered **FIXED_FACING**, `BlockRegistry.java:920`; ordinary `BlockItem`, `ItemRegistry.java:1420` | `blockstates/corral_corner_fence.json`; authored end piece, not an automatic two-panel elbow; inherited `corner` / `filled` behavior | Fixed-facing update retains facing; can change collision/fullness, distinct from reported reversal |
| `britannia_mod:iron_fence_gate` | `block/TripleMetalDoorBlock.java` → `AutoClosingDoorBlock` → vanilla `DoorBlock`, `BlockRegistry.java:2426`; custom `item/TripleMetalDoorItem.java`, `ItemRegistry.java:1758` | three-cell `part`, door hinge/open models under `models/block/structure/iron_fence_gate/`; intentional opening/closing | Not a `FenceGateBlock`; not a wooden-fence connection target; source excludes facing reversal on ordinary adjacency |
| related `corral_pillar`, `corral_wall`, `corral_wall_pole` | `HorizontalTallBlock` / fixed-facing `ThinWall`; `BlockRegistry.java:938,1919,1930` | corral end/wall/pole models | Construction companions, not material subclasses of `WoodenFenceBlock`; no inferred inclusion in hotfix |

No other registered material subclasses of `WoodenFenceBlock`, custom wooden-fence `BlockItem`, fence-specific placement mixin, or fence-facing placement event was found. Generic placement events can retire vegetation nodes; that path does not rewrite fence orientation (`event/ManagedVegetationInteractionHandler.java:46`).

### State schema and semantics

| Family | Properties, allowed values, defaults | Meaning |
| --- | --- | --- |
| wooden | `facing`: N/E/S/W, N; `north/east/south/west`: booleans, all false; 64 states | Facing is player-look opposite initially, intentional physical edge only while isolated; connected it is topology-derived edge/representative. Side flags currently mean loaded cardinal owning positions contain another wooden fence, irrespective of endpoints |
| iron | `facing`: N/E/S/W, N; `part`: single/bottom/middle/top, single; 16 states | Facing maps to the **opposite** physical edge; part comes from same registry block above/below, without requiring matching orientation |
| corral straight / pillar | `facing`: N/E/S/W, N; `style`: integer 0–3, 0 | Facing maps to same-named shape edge; style changes models but is ignored by shapes |
| corral end / ThinWall | `facing`: N/E/S/W, N; `corner`: boolean false; `filled`: boolean false; `style`: integer 0–2, 0; 96 states | Fixed facing, pivot/fullness tracking; JSON also contains style 3, which the state definition cannot represent |
| iron gate | `facing`: N/E/S/W, N; `open`: false; `hinge`: left/right, left; `powered`: false; `part`: lower/middle/upper, lower; `half`: lower/upper, lower; `creative_origin`: false | Three-cell door placement; facing from player direction, occupied edge opposite facing when closed; normal door hinge behavior when opened |

Neither wooden, iron, corral straight nor corral end declares `WATERLOGGED` or implements `SimpleWaterloggedBlock`. Gate also has no waterlogged property. Do not promise preservation of nonexistent water state; adding waterlogging is a separate schema/fluid feature.

### Resources, hooks, tests, and saved-state consumers

`tools/scaffolding/gen_wooden_fence.py:105–180` generates seven models and all 64 variants. Read-only comparison found **zero blockstate mapping differences** between its `entry_for` and checked-in JSON. Its **model-writing portion is stale**: checked-in isolated/end/straight/corner/T artwork has rotated diagonal elements and different dimensions; cross remains the simpler generated mesh. Running `main()` would overwrite authored artwork. Update only the state-mapping generator or split model generation from mapping generation in the implementation.

Wooden models parent `minecraft:block/block`; item parents isolated. Iron fence planes use three iron textures under `textures/block/decoration/`. Gate models inherit vanilla `block/door_*` parents and substitute iron textures. Corral models use `textures/block/structure/corral_fence_end.png`. Corral resources are authored JSON; no corral generator was found.

`data/minecraft/tags/block/fences.json:4` and item equivalent list wooden fence; no custom `wooden_fences` entry was found. Wooden's own connection predicate does not consult tags, gates, sturdy solid faces, or walls (`WoodenFenceBlock.java:109`). Vanilla oak fence can consequently refuse it while a nonwooden vanilla fence can classify it as a fence; this is a tag-driven asymmetric policy, not proof of an edge-compatible joint. NeoForge's `collisionExtendsVertically` hook uses fence/wall tags or `FenceGateBlock`; wooden's tag allows its 1.5-block collision extension to participate in collision broad-phase checks. Keep that tag.

`mixin/DirtPathCustomFenceMixin.java:21,26` permits existing dirt paths under this custom wooden fence and cancels queued conversion; retain it. `WoodenFenceLoadHandler.java:21,38,55` queues loaded chunks and loaded cardinal neighboring chunks, inspects at most four queued chunks per server post-tick, and schedules each wooden fence for reconciliation. It does not load chunks. The **run scan inside each reconciliation remains unbounded by run length**; the queue budget is not a bound on total fence work.

Tests: `src/test/java/.../woodenfence/WoodenFenceContractTest.java` has five resource/source contracts; `gametest/WoodenFenceGameTests.java` covers topology/removal and collision; `gametest/GameplayFenceGameTests.java:26,51,120` explicitly demands connected orientation independent of placement facing and repairs saved facings to canonical runs. These assertions encode the old requirement and must change. Existing tests do not protect a placed south/east anchor through ordinary BlockItem placement. `GameplayFenceGameTests.java:159` directly derives and sets states rather than clicking items.

Generic structures save blockstates and invoke rotate/mirror. A read-only NBT decoder inspected all 20 checked-in `.nbt` files under resources. Both `assets/britannia_mod/structures/large_patio.nbt` and `data/britannia_mod/structures/large_patio.nbt` contain five wooden-fence palette states: north E+S corner, west N+S straight, west N end, north E+W straight, north W end. This is a concrete legacy-state fixture for the implementation; keep both resource copies synchronized if migration fixtures are ever updated. Castle templates also contain vanilla dark-oak fence states. `structure/StructurePlacer.java:137,156` applies rotation and places with flags 3; `structure/GhostStructurePreviewRenderer.java:153` rotates preview states. `event/ClientEventHandler.java:374–381` loads client structure NBT explicitly from the plural `structures/` asset directory. No production chunk or world archive was opened.

`item/InteriorDecoratorToolItem.java:153–163` rotates any eligible horizontal property using direct `setValue(FACING, ...)`; wooden/iron/corral straight qualify. This is an intentional editing mechanism, although wooden scheduled/topology updates can currently undo it. ThinWall is excluded from that generic rotation branch and has its own style-cycling branch.

## 4. Minimal reproduction and first wrong transition

### Source-derived minimal states

Use `A=(0,64,0)`, `B=(1,64,0)`, player looking north (yaw 180), plain wooden fence item, no other wooden fences. For a frontal support click, put the solid support at `A.north()` and click its south face at `(0.5,64.5,0)`, so the owning placement target is A. Stand south of A. All side flags initially false:

```text
A before: britannia_mod:wooden_fence[east=false,facing=south,north=false,south=false,west=false]
rendered edge: south, local z=13..16 / 16
outline: local x=0..1, y=0..1, z=.75..1
collision: same x/z, y=0..1.5

place B against A's east face at (1,64.5,.875), or its solid support,
or the top of the ground below B, keeping player look north:

B proposed by getStateForPlacement:
britannia_mod:wooden_fence[east=false,facing=north,north=false,south=false,west=true]
A after horizontal update:
britannia_mod:wooden_fence[east=true,facing=north,north=false,south=false,west=false]

remove B:
A = britannia_mod:wooden_fence[east=false,facing=north,north=false,south=false,west=false]
```

These explicit illustrative coordinates are **source-derived**, not the absolute generated GameTest coordinates. Owning position A never moves. Its selected/collision strip shifts 0.75 blocks in negative Z; the authored rendered strip shifts from z=13..16 to z=0..3 (0.8125 blocks between strip minima). Both maintain an east–west axis. Removal preserves the **already displaced** north edge; it cannot recover the original south intent.

For a north–south run, use B=`(0,64,1)`. Player looking west yields initial `facing=east`; both connected panels resolve to west. Player looking east initially yields west and does not flip in this two-panel case. North and west initial facings also do not flip on their compatible straight axes. These are not universal opposite operations: a panel placed on an incompatible axis can turn 90 degrees, and junctions can select other edges.

### Probe and evidence boundaries

Temporary source `tmp/edge-fence-discovery/probe/.../gametest/EdgeFenceDiscoveryGameTests.java` drives 24 scenarios: four requested initial facings × three second-click surfaces × two placement orders. It records player look, clicked owning position, face, hit vector, actual placement target, proposed state, both authoritative states immediately, after five ticks, after removal, and after another three ticks. First isolated placement is also observed after three ticks. The successful run used a newly created `tmp/edge-fence-discovery/world-probe`; the earlier namespace-failed run created a separate `world`.

Mode 0 supplies a hit on the first panel at its rendered-edge end, mode 1 a hit on a second support, mode 2 a ground hit. Reversed order starts at the positive-axis cell and extends negatively. The vanilla control uses actual oak `BlockItem.place` on the ground. This exercises placement and synchronous neighbor shape propagation plus scheduled block ticks, but does not send a Minecraft use-item packet or inspect rendered client frames. The probe constructs its hit contexts independently of camera raycasts; its support face/player-position combinations are synthetic inputs, not certified mouse-reachable sequences. The frontal source-derived sequence above is the practical client verification handoff.

See section 10 for the executed outcome and actual generated coordinates. A probe success means the observation procedure completed; **it does not mean the defect is fixed**.

### Measured authoritative-server reproduction

Successful run 2026-10-06, 12:41–12:42 America/Vancouver: NeoForge 21.1.72 / Minecraft 1.21.1 / Microsoft Java 21.0.8. All 24 cases completed; the trace contains 196 records, including the vanilla control. [Complete state and input trace](BASELINE_STATE_TRACE.txt), SHA-256 `fb97fb4fb4b5f8c102fe755196267f5f4adea8199974cf184692706326d4cd31`.

Forward first cell P=`(-3865860,-56,-5726192)`. For N/S cases Q=P.east()=`(-3865859,-56,-5726192)`; for E/W Q=P.south()=`(-3865860,-56,-5726191)`. Forward A=P,B=Q; reversed A=Q,B=P. A is always the existing/first panel, B the new/second panel. Only stone supports and the two fences are present in each cleared fixture.

| Cases | Player look / initial facing | Second-click variants, placement orders | Existing A / new B immediately | After 5 ticks | A after removal +3 ticks |
| --- | --- | --- | --- | --- | --- |
| 0–5 | south / north | first-panel end, support, ground; each forward and reversed | N / N; no reversal | identical full states | N, all connections false |
| 6–11 | north / south | same six sequences | **S→N** / placement draft **S→N** | identical full states | N, all connections false; original south not recovered |
| 12–17 | west / east | same six sequences | **E→W** / placement draft **E→W** | identical full states | W, all connections false; original east not recovered |
| 18–23 | east / west | same six sequences | W / W; no reversal | identical full states | W, all connections false |

Full state tuples below are `(facing,north,east,south,west)`, all for registry ID `britannia_mod:wooden_fence`:

- Initial A: `(requested,false,false,false,false)`, unchanged after three isolated ticks.
- X run forward: A=`(north,false,true,false,false)`, B=`(north,false,false,false,true)`.
- X run reversed: A=`(north,false,false,false,true)`, B=`(north,false,true,false,false)`.
- Z run forward: A=`(west,false,false,true,false)`, B=`(west,true,false,false,false)`.
- Z run reversed: A=`(west,true,false,false,false)`, B=`(west,false,false,true,false)`.
- Removal immediately and after settling: `(north,false,false,false,false)` for X; `(west,false,false,false,false)` for Z.

Example case 6: first support `(-3865860,-56,-5726191)`, face north, hit `(-3865859.5,-55.5,-5726191.0)`, target P, player look north. Second click on P's east face at `(-3865859.0,-55.5,-5726191.125)` targets Q. Before placing B, its proposed state already contains facing north and west=true. Immediately after placement A also faces north and east=true. Local A outline shifts from `[0,0,.75]..[1,1,1]` to `[0,0,0]..[1,1,.25]`; collision shifts identically in X/Z and remains 1.5 high. Thus the actual collision barrier, not only a resource variant, moves.

Case 12: first support `(-3865859,-56,-5726192)`, west face, hit `(-3865859.0,-55.5,-5726191.5)`; then P's south face at `(-3865859.125,-55.5,-5726191.0)` targets P.south(). A's outline changes X `0.75..1` to `0..0.25`, collision likewise. All support/ground click coordinates for the other modes and reversed orders are preserved in the complete trace.

Vanilla control A=`(-3865855,-56,-5726187)`, B=A.east(): isolated oak has all side flags false and waterlogged=false; placement sets A.east=true and B.west=true immediately and at five ticks; removal clears A.east. Central post remains X/Z `0.375..0.625`, with only the east arm added/removed and collision Y0..1.5. Oak has no facing field to normalize.

Rendered-edge positions above are derived from the recorded server states and verified JSON mapping; no client pixels were observed. The owner's details display still requires direct client/server comparison.

## 5. Geometry contract

North is −Z, south +Z, west −X, east +X. Model and `Block.box` inputs below are voxels; divide by 16 and add owning `(x,y,z)` to obtain world coordinates. Rotations are blockstate Y rotations about `(8,8,8)`; y=90 turns the north edge to east. `FaceBakery.java:189–212` confirms element rotations use their authored origins before model rotation.

### Wooden isolated/linear family

All rows own P=`(x,y,z)`; the measured forward fixture uses P=`(-3865860,-56,-5726192)` for all four initial facings. Model bounds below are **isolated** transformed vertices. Connected end/straight details follow the table. Outline bounds are full strip boxes, not the thin/gapped mesh.

| facing | Occupied physical edge | Panel axis | Model Y | Model bounds X;Y;Z (voxels) | Outline X;Y;Z (voxels) | Collision X;Y;Z | Nominal strip endpoints in world space |
| --- | --- | --- | --- | --- | --- | --- | --- |
| north | north | X | 0 | −1..17;0..18;0..3 | 0..16;0..16;0..4 | same X/Z; Y0..24 | x=x and x+1, z in z..z+.25 |
| south | south | X | 180 | −1..17;0..18;13..16 | 0..16;0..16;12..16 | same X/Z; Y0..24 | x=x and x+1, z in z+.75..z+1 |
| east | east | Z | 90 | 13..16;0..18;−1..17 | 12..16;0..16;0..16 | same X/Z; Y0..24 | z=z and z+1, x in x+.75..x+1 |
| west | west | Z | 270 | 0..3;0..18;−1..17 | 0..4;0..16;0..16 | same X/Z; Y0..24 | z=z and z+1, x in x..x+.25 |

Sources: `WoodenFenceBlock.java:39–42,183–220`; `blockstates/wooden_fence.json`; authored `models/block/structure/wooden_fence/isolated.json:11`; computed artifact `tmp/edge-fence-discovery/geometry.json`.

End/straight north-base bounds X0..16.088077, Y0..18, Z0..3; end mirrored Z13..16. Tangential vertex overhang is about .005505 blocks; isolated posts overhang 1/16 block on both ends. Rails from x=3..16 meet a neighbor's x=0..3 post at the shared boundary, so use authored elements and transformed cross sections, not raw pre-rotation minima/maxima. `straight.json` includes a post; its name does not imply the generator's post-free mesh.

For canonical reachable end states, resource mapping and Java select the same edge. For arbitrary command-assigned states, the one-connection model's edge is determined by connection direction plus the mirrored choice and can disagree with `facing`/Java. Example `facing=north,south=true` renders east while Java selects north (`gen_wooden_fence.py:124–150`). Preserving only facing without filtering connections can make such states ordinary.

### Wooden junctions: facing alone is insufficient

| Topology / relevant full mask | Java occupied strips | Model mapping and computed base bounds | Feasible endpoints / defect |
| --- | --- | --- | --- |
| corner E+S | N+W | corner y0; X0..16.088077,Y0..18,Z0..17.1 | East arm on N meets east neighbor N; south arm on W meets south neighbor W |
| corner S+W | N+E | corner y90 | West arm on N, south arm on E |
| corner W+N | S+E | corner y180 | West arm on S, north arm on E |
| corner N+E | S+W | corner y270 | East arm on S, north arm on W |
| T missing N/E/S/W | missing side + its counterclockwise side | T y0/90/180/270; base X0..16.088077,Y0..18,Z0..15.849556 | Authored T branch is **central** (post x7..10), while Java treats the branch as the west edge. Thus render and collision are inconsistent before this hotfix |
| all four | F + counterclockwise(F); reconciliation forces F=N | cross y(F); base X0..16,Y0..18,Z0..16 | Mesh and shape are an L, not a conventional four-ended cross. Four owning neighbors do not imply four physical contacts |

Corner and T ignore facing in resource rotation and most Java footprint selection; cross uses facing. See `WoodenFenceBlock.java:190–209`, generator `rotation_for:120`, authored corner/T/cross JSON. No single four-row facing table can truthfully describe all junction footprints; the mask-dependent table above completes that mapping. Each strip's outline is Y0..16 and collision Y0..24; union bounding boxes can cover the full square but the union is not a full cube.

### Iron straight family

For every vertical part and owning P, Y0..16 outline/collision, panel spans a full owning-cell tangent. Base model is a double-faced plane at Z=15; zero-angle element rotations do not thicken it.

| facing | Physical edge / axis | Model Y / plane | Outline and collision X;Z voxels | World endpoint boundaries |
| --- | --- | --- | --- | --- |
| north | south / X | 0 / Z15 | 0..16;14..16 | x=x,x+1; z in z+.875..z+1 |
| south | north / X | 180 / Z1 | 0..16;0..2 | x=x,x+1; z in z..z+.125 |
| east | west / Z | 90 / X1 | 0..2;0..16 | z=z,z+1; x in x..x+.125 |
| west | east / Z | 270 / X15 | 14..16;0..16 | z=z,z+1; x in x+.875..x+1 |

Sources: `IronFenceBlock.java:25–28,40–47`; multipart JSON `blockstates/iron_fence.json:3`; `models/block/decorations/iron_fence_bottom.json` and its middle/top counterparts. Opposite facing would move its plane, but horizontal update never assigns that opposite.

### Corral straight and corral end families

Owning P and model Y rotations N0/S180/E90/W270 apply to both. Corral straight has Y0..20 outline/collision (1.25 blocks). Corral end has Y0..16 outline and ordinary collision, with filled/corner exceptions below.

| facing | style 0 model edge / axis / X;Z voxels | Straight outline/collision X;Z | End outline/ordinary collision X;Z | Model endpoint boundaries |
| --- | --- | --- | --- | --- |
| north | N / X / 0..16;0..4 | 0..16;0..4 | 0..16;0..5.33 | tangent x=x,x+1; z=z..z+.25 |
| south | S / X / 0..16;12..16 | 0..16;12..16 | 0..16;10.66..16 | tangent x=x,x+1; z=z+.75..z+1 |
| east | E / Z / 12..16;0..16 | 12..16;0..16 | 10.66..16;0..16 | tangent z=z,z+1; x=x+.75..x+1 |
| west | W / Z / 0..4;0..16 | 0..4;0..16 | 0..5.33;0..16 | tangent z=z,z+1; x=x..x+.25 |

Style 1 translates the base model −16 voxels in X before the blockstate rotation: N shifts ownership footprint one cell west, E north, S east, W south. Style 2 mirrors to the opposite physical edge (N→S, E→W, S→N, W→E). Java still returns the style-0 edge. Style 3 references nonexistent `*_mirrored_shifted.json`; files are named `*_shifted_mirrored.json`. Style 3 is additionally unreachable for the ThinWall corral end (allowed 0–2). These are confirmed distinct resource/shape issues, not evidence for a facing-field reversal.

Corral end `filled=true` returns full-cube outline; `corner=true` returns full-cube collision (`ThinWall.java:263–279`). Its authored 'corner fence' is a straight end panel, not the wooden L mesh. Do not treat it as the wooden fence's junction representation.

### Iron gate: three-cell door geometry

Closed gate outline/collision in each of its three owning cells is a 3-voxel strip, Y0..16: N→south Z13..16, S→north Z0..3, E→west X0..3, W→east X13..16. Tangent endpoints are the owning-cell boundaries, as for iron above. Closed left-hinge model Y transforms are E0/S90/W180/N270; parent `minecraft:block/door_bottom_left` supplies west-edge geometry. Right-hinge parent supplies the matching mirrored mesh. Opening rotates the occupied edge intentionally: N becomes W (left) or E (right); S E or W; E N or S; W S or N. This follows resolved `DoorBlock.java:49–52,80–91` and gate multipart JSON. Do not connect it as if it were a centered vanilla `FenceGateBlock`.

### Practical compatibility rules

1. A wooden north/south panel continues along ±X only if the neighboring panel presents the same world strip endpoint; east/west continues along ±Z. Merely finding `WoodenFenceBlock` is insufficient.
2. Opposite edges in the same owning row are disjoint (N vs S have a .5-block gap between their collision strips); do not connect or move either panel to fix that gap.
3. Existing E+S wooden corner at P physically needs its east neighbor's N strip and south neighbor's W strip. A south neighbor with E strip does not fit that L. Current code nevertheless sets identical boolean flags.
4. Two single perpendicular panels can turn a corner across distinct owning positions if their boundary strips touch. Example P=N and P.east()=W meet at x=x+1 with z=z..z+.25. P=N and P.south()=W do **not** meet unless P also contains its W strip. Use explicit L layout or different owning cells.
5. A closed enclosure can be built with perimeter-edge strips and four explicit L layouts; oriented single pieces can also turn across neighboring cells. Test real endpoints/collision, not 'corner' model names.
6. Diagonally owned strips can share only a boundary segment at a grid vertex. Opposing cells can lie on opposite sides of one conceptual boundary (P=N vs P.north()=S), producing two parallel strips on either side of the boundary rather than the same mesh. Current cardinal lookup ignores diagonal contacts and gives no duplicate-boundary protection. Defer diagonal connections/deduplication to a deliberate follow-up; never relocate existing owners.
7. T and cross names currently overpromise connectivity. Preserve installed geometry, document the mismatch, and do not advertise newly automatic three/four-way joins until matching models/collision are implemented.

## 6. Cause, paths, and history

**Confirmed primary cause, high confidence:** intentional topology normalization overwrites placement anchor.

```text
BlockItem.place(context)
  -> getPlacementState -> WoodenFenceBlock.getStateForPlacement (59)
       defaultBlockState + FACING = horizontal player look opposite (61)
       -> deriveConnections (74)
          four cardinal loaded owning-position class checks (75–78)
          set side booleans, without considering geometry (80–81)
          pure X run -> resolvedRunEdge(axis=X) (90–91)
          no junction found -> NORTH (134)    <-- new panel's first wrong FACING
  -> placeBlock -> Level.setBlock(flags=11)
       -> markAndNotifyBlock -> updateNeighbourShapes -> neighbor shapeChanged
          -> existing state.updateShape -> WoodenFenceBlock.updateShape (66–71)
             -> same deriveConnections/resolvedRunEdge
             -> existing SOUTH becomes NORTH  <-- old panel's first wrong FACING
  -> onPlace schedules next-tick reconciliation (138–146)
       -> deriveConnections; setBlock UPDATE_ALL if changed
chunk load -> WoodenFenceLoadHandler.inspect -> scheduleTick -> same path
```

For Z runs the fallback is WEST. Negative-axis junction is scanned before positive-axis junction (`WoodenFenceBlock.java:115–134`). Mixed-axis masks explicitly set facing at lines 101–106; four neighbors force north at 85. Source flags from resolved `Block.java:77–86` distinguish `UPDATE_IMMEDIATE=8` from `UPDATE_KNOWN_SHAPE=16`; vanilla BlockItem's flags=11 are not suppressing neighbor shape updates. Do not repeat the unrelated door comment that calls flag 8 'send to client'.

Resolved dependencies: actual NeoGradle classpath points to `.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/21.1.72/neoforge-21.1.72.jar` (SHA-256 `1ffad023b8b56b62e861badc47d36a499d1529bc702f125566d159a6ea899c40`). Its patched/unzipped source directory is `C:/Users/dusti/.gradle/caches/ng_execute/98ab97877c6086d6ec2a98ad9426eea0fc066abc7e18e078fb6989884dd10002/output`. Copies of relevant files are in `tmp/edge-fence-discovery/dependencies`. The placeholder project-local `*-sources.jar` contains only `readme.md`; it was not mistaken for usable sources. Read the real resolved outputs: `BlockItem.java:54–160`, `BlockPlaceContext.java:30–59`, `Level.java:211–280`, `BlockBehaviour.java:700–707,772–773`, `FenceBlock.java:65–139`, `CrossCollisionBlock.java:23–60,142–174`, `FenceGateBlock.java:239–240`, and `IBlockExtension.java:731–736`.

Placement takes **player direction**, not clicked face/location. `BlockPlaceContext.getClickedPos` is the resulting owning target, not necessarily the clicked support. Custom wooden placement has no final `setPlacedBy` orientation change. Vanilla item state components can intentionally override properties afterward (`BlockItem.java:135`); ordinary plain items in the probe have none.

History explains the requirement conflict. `317a9bd7` introduced wooden fence with `inheritedRunEdge`: copy a neighbor's compatible saved edge to align a newly connected run. `ea8499f764fcd059edf4d4b0a0cfc98c0d9db8ea` replaced inheritance with deterministic junction search and canonical fallbacks, added scheduled/load reconciliation and 1.5-block collision. `docs/projects/gameplay-bugfixes/M8_FENCE_EVIDENCE.md:3` explicitly documents connected normalization. That solved placement-history/L→T→L differences under the old tests, but sacrificed the newly specified invariant that a placed panel's selected edge survives connection changes. This is not caused by a generic Minecraft direction bug.

| Hypothesis | Disposition |
| --- | --- |
| neighbor updates recompute/overwrite anchor | **Confirmed**; placement, updateShape, scheduled tick, chunk reconciliation all call the same facing-writing helper |
| default reconstruction loses old placement state | **Ruled out as update cause**; derive mutates the supplied immutable BlockState with setValue; default reconstruction occurs only for initial placement |
| connection direction confused with outward normal | **Confirmed design limitation**; class-only owning adjacency selects topology and then substitutes a physical edge |
| neighbor facing copy/getOpposite reverses existing panel | **Ruled out at this HEAD**; getOpposite sets initial facing, current resolver does not copy neighbor facing. Copying exists in earlier history |
| facing serves incompatible roles | **Confirmed**; initial anchor, connected run alignment, junction/cross representative |
| update order/oscillation is necessary for reversal | **Ruled out for minimal pure run**; fallback is deterministic and immediately wrong. Loaded junction availability can still change answers; exhaustive runtime stability not newly verified here |
| models/shape disagreement | **Confirmed supporting defects**, particularly T, arbitrary end masks, styled corral; isolated wooden mapping agrees on edge, so cannot explain the authoritative facing assignment |
| client prediction alone causes the symptom | **Unresolved client measurement**, but unnecessary as root cause: server update code itself overwrites orientation |

Current wood connections are symmetric as **class/owning-position membership**, not as physical endpoints. A run can select different distant junctions depending on which end is reachable and loaded; raw connection presence is independent of flags, which prevents facing-copy oscillation but does not guarantee geometrical joins or full-run notification after a distant change. Water state is not lost because none exists.

An additional source-confirmed transform issue matters for design: `mirror` mirrors facing and side flags, but T/cross shapes always use a counterclockwise secondary strip. Reflection turns that into a clockwise strip. For example, a T missing north occupies N+W; reflecting X should yield N+E, while unchanged missing-north logic yields N+W. Cross N+W similarly fails that reflection. The existing neighborhood-permutation tests do not call `state.mirror` and compare reflected geometry. Simply reusing that method cannot claim geometrically correct mirrors.

## 7. Vanilla comparison

Resolved vanilla `FenceBlock` extends `CrossCollisionBlock` and has no FACING. It owns a central post and derives four centered arms from neighboring solid faces, fence material tags, and `FenceGateBlock.connectsToDirection`. Horizontal `updateShape` changes the relevant side boolean on the supplied state; waterlogged updates schedule a fluid tick. Collision reaches 24 voxels and outline 16. Rotation/mirroring permute connection flags. NeoForge allows tagged fences' collision to extend above their blockspace.

Reusable: retain state identity/other properties, update only connection flags, use a symmetric deterministic eligibility predicate, preserve 1.5-block collision, transform every directional component together, and test actual placement/update paths. Not reusable without adaptation: central arm endpoints, same-grid-cell material/tag checks as sufficient evidence of contact, centered gate axis compatibility, or vanilla geometry constructors. Wholesale FenceBlock inheritance would change placement footprint, waterlogging/schema, shape, render expectations, and gate behavior.

Version-scoped official references support these general placement/state/model distinctions; exact method signatures above come from the resolved sources: [Blocks](https://docs.neoforged.net/docs/1.21.1/blocks/), [Blockstates](https://docs.neoforged.net/docs/1.21.1/blocks/states/), [Models](https://docs.neoforged.net/docs/1.21.1/resources/client/models/). These pages were consulted on 2026-10-06.

## 8. Options and bounded recommendation

| Option | Benefits | Limits / compatibility | Verdict |
| --- | --- | --- | --- |
| A: keep facing; recompute supported connections | Tiny Java change; fixes isolated→straight primary reversal | Boolean masks still choose L/T/cross physical geometry; end mapping can conflict with retained facing. Removal changes an installed corner to a single panel. Does not alone preserve valid enclosures | Necessary invariant, insufficient complete patch |
| B: separate anchor/layout from connections | Stores physical intent independently; supports preservation of existing junction footprints and reload | One added property and lazy legacy conversion; expanded states and model mapping/tests required | Recommended with A |
| C: align only new panel | Predictable extension without moving a neighbor | Multiple neighbors can conflict; clicked intent needs priority. Cannot recover old lost intent or solve junction schema alone | Recommended, limited placement-time use |
| D: extend corner/junction geometry | Can express real endpoints and complete junctions | Reauthoring T/cross, diagonal contacts, and mixed gates has substantially wider art/collision scope | Reuse existing L for bounded corner placement; defer new T/cross art |
| E: center geometry | Vanilla-style connectivity becomes much easier | Violates edge placement, collision footprint, ownership and installed builds | Unsuitable for Patch 18 |

### Recommended schema: existing facing + immutable layout + mutable connections

Keep registry ID, `facing` values, and four boolean names/values. Add **one proposed integer `layout_code` with values 0–32**, default **32 = legacy/not yet materialized**. Values 0–15 record the *old topology mask used to select the physical layout*, with bits N=1,E=2,S=4,W=8. Values 16–31 carry the corresponding layout with reflection parity. This is a **layout selector**, never recomputed from current connections after placement/materialization. Facing retains its stored value and is no longer topology-normalized. Reflection parity is needed for the demonstrated handed T/cross mirror problem, not ordinary joining.

Why a selector rather than another anchor property: the existing `facing` already stores the primary edge for normal reachable states. A second facing would duplicate it and would default incorrectly for old south/east panels. The missing information is the junction footprint that currently lives in mutable booleans. Snapshotting that footprint also retains T/cross artwork and existing collision behavior without guessing the original pre-defect placement.

There are 4×16×33 = **2,112 states** versus 64. This is a measurable memory/model-loading cost and must be checked on the target client. JSON can match `facing,layout_code` while omitting current connection properties, plus preserve all 64 legacy mappings for selector 32. About 192 mapping entries suffice before optional connection-driven caps, rather than writing 2,112 duplicate models. Seven original meshes plus reflected variants suffice. An enum eliminating equivalent/redundant layouts could reduce invalid states substantially; this integer encoding is a complete reference design, not a requirement to retain redundant states if a smaller encoding proves the same coverage.

For legacy states, before changing any boolean, copy the **existing saved mask** into layout_code with parity zero. Shape/model selection using `(facing,layout_code)` must reproduce the **old shape/model selection exactly**, including unusual saved combinations. No need to recover placement history. StateDefinition's actual property codec uses the registered default for missing properties (`StateDefinition.java:75–85`), so old serialized states decode with selector 32; verify this with real serialized-state tests rather than relying on inference alone. Legacy model variants continue to render correctly before materialization. Chunk load materializes/recalculates connections without changing facing/layout. No eager world scan or chunk rewriting/migration command.

Define a fixed reflection Q of local Z about 8 voxels (north/south swap). For unreflected layouts, use the old geometry decoder G(facing,mask). For parity-one layouts, decode `Q(G(Q(facing),Q(mask)))`, reflecting both shapes and authored vertices. Rotation transforms facing and selector direction bits; reflection transforms those bits and toggles parity. This preserves arbitrary legacy geometry under real transforms without pretending that the old T/cross decoder was reflection-equivariant. Reflected assets must include correct element origins/angles, faces and UV mapping; generate variants from current authored JSON rather than calling the stale model generator. Test the transformed outline/collision and rendered vertex positions, not just direction values. An equivalent sparse enum or model wrapper can implement the same contract.

New isolated pieces use layout 0 and the selected facing. New elbows use the appropriate existing corner selector (E+S=6 for N+W, S+W=12 for N+E, W+N=9 for S+E, N+E=3 for S+W). Never auto-place old T/cross selectors: their visual/collision mismatch needs separate design. Pin a new elbow's two panels even after neighbor removal; the physical elbow was chosen during placement, not a temporary topology accident. Terminal detail/caps can vary only if their physical primary edges and collision endpoints remain constant. Initial hotfix can keep authored models fixed and accept doubled posts; removing duplicate posts is follow-up polish.

**Invariants:** ordinary adjacent placement/removal, solid changes, block tick and load reconciliation preserve facing and materialized layout; connections derive only from world endpoints of fixed layouts; existing canonical and arbitrary saved layouts retain their render/collision location; transformations explicitly transform both facing and layout; no new waterlogging/IDs/dependency upgrades.

This is the smallest recommended design that meets both strict anchor stability and installed-junction preservation. A one-line facing freeze is a valid diagnostic/partial fix, not a complete compatible recommendation. If the implementation chooses a smaller schema, it must demonstrate all legacy shape/model mappings and corner removal invariants before replacing this design.

### Placement priority and endpoint policy

Proposed policy (current behavior only uses player direction):

1. For a horizontal solid/support click, preserve the existing isolated anchor rule: `context.getHorizontalDirection().getOpposite()`. The outward clicked support face ordinarily agrees with that edge in a frontal click; it is not the opposite-face normal. Face and hit location establish the actual target and break otherwise equal compatible choices. For a click against an existing fence endpoint, select the smallest fixed layout that meets that endpoint, aligning only the new panel. Check world hit/target coordinates so replacement clicks are not misinterpreted as adjacent support clicks.
2. The explicit support placement anchor or clicked fence endpoint has priority over unrelated neighbor hints. Do not relocate an existing fence to satisfy it. For equally compatible endpoint choices use hit location nearest edge, then player-look-opposite, then N/E/S/W order. This retains the source-confirmed isolated placement convention; a new independent face-based rotation gesture is outside this hotfix.
3. For a ground/replaceable click, maximize compatible existing endpoint contacts first, minimize number of panels second, choose the edge closest to hit location third, player-look-opposite fourth, N/E/S/W final tie-break. A single strip wins when it connects the same endpoints as an L; when two neighboring endpoints require an existing L layout, choose that L once at placement. No arbitrary run-wide scans.
4. No compatible hint: preserve the isolated behavior's player-look-opposite. Conflicting hints: deterministic best candidate; leave incompatible neighbors disconnected. Never rewrite neighbors' facing/layout. Opposite chosen anchors are a legitimate disconnected result, not a reason to flip a run.

Candidate set: four single edge strips and four existing L layouts. Eligibility must use **fixed layout strips transformed to world coordinates**, with Y-range overlap and matching/contacting endpoints. Straight continuation requires the same strip cross section; perpendicular boundary contacts need a physical overlap/contact segment, not just a single mesh AABB corner. Mixed iron/corral/vanilla/gate targets remain ineligible for this hotfix; they have distinct thicknesses, ownership conventions and behavior. Keep wooden-family class policy for material scope, then filter by geometry. Support blocks influence placement intent but do not become automatic rail geometry.

Each connection is computed symmetrically from the two fixed footprints. Examine cardinal owners for the initial hotfix; explicitly omit diagonal visual joins. An existing L's endpoint footprint is independent of its current booleans, so this avoids recursive connection dependencies and update oscillation. Unloaded neighbor: clear connection/decoration as needed without changing fixed layout; recheck when chunk becomes available. Existing compatible runs remain on their stored edge rather than canonicalizing globally.

### Candidate scope and pseudocode

Required candidate files: `block/WoodenFenceBlock.java`, optional small `block/WoodenFenceGeometry.java` helper, `block/WoodenFenceLoadHandler.java` comments/reconciliation expectations, `item/InteriorDecoratorToolItem.java` wooden rotation branch, `tools/scaffolding/gen_wooden_fence.py` mapping-only path, `blockstates/wooden_fence.json`, reflected variants derived from current authored wooden meshes (or a narrowly scoped reflected-model wrapper), existing JUnit/GameTest fence suites and new legacy serialization/placement tests. Do not modify iron, corral, dirt-path mixin, textures, release version, or alligator work for this defect.

```java
materializeLegacy(state):
    if state.layout_code == 32:
        return state.with(layout_code, maskOfExistingSideFlags(state))
    return state

getStateForPlacement(context):
    intent = supportFaceHitOrPlayerDirection(context)
    choice = chooseFixedSingleOrL(intent, fixedLoadedNeighborFootprints(context))
    state = defaultBlockState.with(FACING, choice.primaryEdge)
                             .with(layout_code, choice.legacyLayoutSelector)
    return deriveConnections(state, world, targetPos)

deriveConnections(state, world, pos):
    state = materializeLegacy(state) // before overwriting any saved flags
    footprint = fixedGeometry(state.FACING, state.layout_code)
    for each cardinal direction d:
        other = loadedNeighborWithoutLoadingChunks(pos+d)
        joined = sameFamily(other) && compatible(footprint, fixedFootprint(other), d)
        state = state.with(sideProperty(d), joined)
    return state // FACING and layout_code unchanged

getShape / getCollisionShape:
    select fixedGeometry(FACING, effectiveLayoutSelector)
    preserve current outline Y and collision Y (24 voxels for wood)

rotate / mirror / decorator:
    materialize before transform
    transform facing and the four layout-selector bits, plus connection bits
    toggle reflection parity for a non-NONE mirror
    retain sentinel handling and complete round-trip tests
```

The registry default remains legacy 32 for old-state decoding; **placement must explicitly set a concrete selector**. Resolve a legacy neighbor footprint read-only; never write/migrate a neighbor just to ask whether it connects. Generic decorator currently changes only facing: route wooden edits through the complete transform so an intentional rotation turns its pinned elbow too.

Models and shape selection must switch to the same fixed selector in the same patch; Java-only or JSON-only changes cannot satisfy the requirement. Keep authored files intact initially; if a newly supported L mesh needs endpoint repair, edit its outline/collision contract and asset together and prove legacy layouts remain representable. Do not silently fix the T center/edge mismatch as part of a compatibility migration. Follow-up can introduce corrected explicit T geometry with an opt-in conversion/replacement path.

Existing displaced pieces stay at their **currently saved physical layout**, including north/west canonical positions already chosen by the defect. Original south/east intent is lost. Offer intentional decorator rotation/replacement to repair those builds; do not infer original edges or automatically reverse them. Adding layout_code is a compatible forward read strategy, not a downgrade guarantee: an older mod ignores it and may resume normalization. Both server and clients must use the same implementation/resources.

## 9. Implementation sequence and proposed regressions

1. **Baseline and red tests:** start strictly from current Patch 18 (include subsequent authorized hotfix integration). Add real `BlockItem.place` tests for south/east reversal, untouched original anchors, removal and load. Preserve the disposable probe as evidence only. Expected 0.5 day; existing helpers/template make this bounded.
2. **Anchor/layout separation:** implement selector/default legacy conversion, remove run-wide facing derivation, fixed geometry selection, pairwise connection filtering, complete transforms/decorator handling and reflected mesh variants. Mapping-only generation plus authored-asset reflection; no artwork overwrite. Expected 1–3 days; moderate risk from legacy state/model/UV coverage and the reference encoding's 2,112-state cache footprint. Prove any smaller equivalent enum against all old states.
3. **Placement and corner calibration:** implement deterministic new-only selection, test an L and closed enclosure in all rotations and conflicting neighbors. Expected 0.5–1 day; moderate interaction risk. If an existing authored L cannot meet its documented endpoints, repair that exact mesh/shape with a compatibility variant; do not expand to T/cross.
4. **Acceptance:** focused JUnit and server tests; then required full JUnit/GameTest checks in an isolated run; ordinary client selection/collision, save/reload, dedicated-server/client parity. Expected 0.5–1 day plus client availability. Source compilation is not a visual pass.
5. **Later release integration:** separate authorized implementation project commits/integrates to Patch 18, builds a clean-source `0.1.8d` bundled candidate and records identity, preserves all existing release holds. No Patch 19 merge, main creation, deployment or dirty release bypass during discovery.

These are engineering estimates based on the single shared wooden class, existing seven meshes, two GameTest classes and resource generator. They are not measured completion times.

| Scenario | Coordinates/input | Required regression evidence | Harness |
| --- | --- | --- | --- |
| four initial edges | P=(0,64,0), player opposite each edge; support-face and ground hit | Correct FACING, fixed selector, model and outline/collision edge; explicit click priority | BlockItem GameTest + client |
| straight forward/reversed | N/S: P and (1,64,0); E/W: P and (0,64,1) | Both original anchors/layouts stable immediately and after >1 scheduled tick; world endpoints match | GameTest |
| add/remove either end | P±X for N/S, P±Z for E/W | Only connection flags change; removal keeps layout, including a pinned L | GameTest |
| unrelated changes | stone/air at each cardinal and vertical neighbor | Full physical state unchanged; no duplicate scheduled oscillation | GameTest |
| repeat same update | apply identical neighborhood updates 20 times, observe several ticks | State reaches fixed point without anchor changes or new run-wide scans | GameTest |
| L and full enclosure | E+S L at P=N+W; neighbors (1,64,0)=N and (0,64,1)=W; rotate/mirror; four-corner 4×4 enclosure | Touching endpoints and continuous collision; no old panel moved; remove arms and retain physical elbow | GameTest + client walk/jump |
| T/cross | all old 64 states, actual three/four neighboring owners | Preserve legacy meshes/shapes; no false claim all grid arms connect; new unsupported geometry stays documented | JUnit serialization/assets + GameTest + client |
| conflicts | N run to west, S run to east of target, reversed placement order | Deterministic new selection under same intent; neighbors unchanged; actual incompatible contact flags false | BlockItem GameTest |
| mixed materials/controls | iron, corral, oak/nether fence, vanilla gate, iron gate, solid block | Documented family policy; no asymmetric custom claims; no relocation; vanilla control behavior intact | GameTest |
| water | place in water with plain item | Current no-waterlogging behavior recorded; do not add nonexistent WATERLOGGED assertion. If added later, assert fluid state/ticks | GameTest + client |
| rotations/mirrors/tool | every legacy/new layout, each Rotation/Mirror; decorator click | Geometric footprint transforms intentionally; composition/round trip correct; no next-tick undo | JUnit + GameTest + client |
| structures | serialize old states without selector, deserialize, rotate then place | Default legacy decode, pre-update visual mapping, first reconciliation pins correct old footprint; no facing normalization | JUnit codec + structure GameTest |
| chunk boundary/reload | X at 15/16, Z at 15/16, both load orders; save/restart disposable world | Fixed anchor/layout survives unavailable neighbors and real unload/reload, connections reconcile only | dedicated server + client |
| dedicated server/client | actual packet placement at representative P | Server blockstate and client's displayed details/render agree before/after settle | manual two-sided session |
| physical barrier | every edge, L inner/outer corners, feet Y/Y+1.249/Y+1.51 | Selection targets mesh vicinity; normal walking/jump blocked to 1.5; intended gap above allowed; no hidden full-cube fill | server movement tests + client |
| existing saved installations | isolated, end, straight, L, T, cross; all four facing values, including noncanonical masks | Exact old shape/model footprint retained on migration; subsequent removal/update never changes primary edge; originally displaced states are not guessed backward | codec, GameTest + client |

Revise `GameplayFenceGameTests.everyNeighborhoodConvergesAcrossFacingsAndPlacementOrders`: equivalence applies when the **same explicit intent/fixed layout** was supplied. Different intentional edges must not converge to one topology-selected facing. Revise `loadedRunRepairsSavedFacingWithoutUpdateLoops` to preserve saved anchors/layouts while repairing stale connection flags. Keep dirt-path and collision-height checks. Test at placement/neighbor boundaries; helper-only equality tests are insufficient.

## 10. Validation record

Commands discovered: README's `gradlew test`, `runGameTestServer --no-configuration-cache`, `runClient -Pdev --no-configuration-cache`; CI runs `build` and `runGameTestServer`; `build.gradle` exposes `gameTestRunDirectory` and `gameTestNamespaces` properties. `tools/alligator/run-isolated.ps1` sanitizes service environment and constrains worlds to its own task directory; its isolation pattern was followed, not its alligator world reused.

Executed read-only work: branch/HEAD/status/worktree inventory; applicable-instruction search; narrowed Java/resource/test/history searches; actual dependency-source reads; explicit Temurin Java and installed Gradle 8.9 version checks; rotation-aware JSON computation and blockstate-generator comparison; decoding all 20 checked-in resource NBT templates. `geometry.py` returned seven wooden model bounds, corral/iron bounds, 64 variants with no mapping drift, and the two missing style-3 corral model references. `nbt_inventory.py` found the five-state wooden patio palette and vanilla castle controls; detailed palette output is `tmp/edge-fence-discovery/nbt-inventory.log`.

Initial execution failures:

- Wrapper `gradlew.bat --version --offline ...` attempted a distribution download and failed under network restrictions. The complete installed Gradle 8.9 distribution was then located and used directly; no upgrade or installation.
- First sandboxed Gradle test attempt could not resolve foojay 0.8.0 in offline mode. No test executed; this was an environment/cache-access failure, not a fence test failure.
- First cache-access-authorized disposable run reached compile tasks but failed starting Gradle Test Executor because the diagnostic init script's relative working directory did not exist. Its `file(...)` paths were resolved relative to the init script; the output ended up nested under the ignored discovery directory. Corrected to explicit project-absolute paths and created the test working directory. No fence assertion passed in that attempt.
- The next attempt compiled the probe and unchanged Patch 18 source, then failed `processResources` because both the original and isolated generated-template directories supplied the same alligator NBT. The temporary init script was corrected to include only the isolated template directory; production build configuration was not edited. These harness setup failures are not baseline application test failures.
- The first server launch then passed the five JUnit fence contracts but failed starting the probe: this exact NeoForge annotation prefixes the template namespace, so embedding `britannia_mod:` in `template` produced a double namespace. Read the resolved `GameTest`/`GameTestRegistry` and changed only the temporary probe annotation to an unqualified template, copying the existing base64 empty fixture into the discovery namespace. No application assertion was executed in that failed server run.

Corrected focused command (PowerShell, explicit Java 21; service-related environment variables removed from child, restored afterward):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
& 'C:\Users\dusti\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat' `
  test --tests com.seggellion.britannia_mod.woodenfence.WoodenFenceContractTest `
  runGameTestServer -I tmp/edge-fence-discovery/discovery.gradle `
  -PgameTestRunDirectory=tmp/edge-fence-discovery/world `
  -PgameTestNamespaces=britannia_edge_discovery `
  --no-configuration-cache --console=plain
```

**Executed results:** five `WoodenFenceContractTest` JUnit tests, 0 failures/errors/skips (XML `tmp/edge-fence-discovery/build/test-results/test/TEST-com.seggellion.britannia_mod.woodenfence.WoodenFenceContractTest.xml`, timestamp 2026-10-06T19:38:08Z). Unchanged application compilation completed with 100 existing deprecation/Mixin warnings. The successful server observation was **one required GameTest**, internally running **24 placement scenarios plus oak control**; do not count those scenarios as 24 registered GameTests. `All 1 required tests passed`, `BUILD SUCCESSFUL in 1m 52s`, server test duration 7.189 seconds. This confirms baseline observations, including 12 opposite reversals, not a corrected application.

Final observation invocation used the same init script and namespace, `runGameTestServer -x compileJava`, with fresh `-PgameTestRunDirectory=tmp/edge-fence-discovery/world-probe`. Application classes had already compiled through Gradle from unchanged source. Only the disposable annotation-adjusted probe was compiled using Java 21 `javac -proc:none` against the resolved NeoForge compile classpath plus the isolated classes directory. The sandboxed compiler printed an access-denied exception while closing a cached LWJGL archive; its class output was produced and `javap -v` confirmed the corrected annotation before the run. The real server successfully loaded and executed that class. This is a targeted discovery iteration, not a clean release-build validation.

Logs are retained at `tmp/edge-fence-discovery/{baseline-unit,runtime,runtime-corrected,runtime-final,probe}.log`; the task-local probe/build/worlds, scripts, source copies and geometry computations remain ignored evidence. The complete filtered state trace is delivered beside this report. Trace summary assertions confirmed 24 complete cases, correct isolated initial facings, immediate/settled full-state equality, immediate/settled removal equality, and 12 opposite reversals. The server logged missing initial server.properties and unavailable default local backend data for synthetic players; those warnings/errors did not prevent observations. No backend parity or real-player service behavior is certified.

A temporary diagnostic class exists only in isolated source/output, not production source. Do not package or copy these outputs into a release. Ending tracked-source diff remains empty; new deliverables are this report and its state trace, alongside the untouched pre-existing owner files.

Not executed during discovery: a full release build, the entire unrelated test suite, client render/screenshots or mouse placement, dedicated client/server packet comparison, production saved-state sampling, actual save/restart/unload acceptance, exhaustive new fixed-layout rules (not implemented). Historical M8 passing tests are historical evidence of the former specification, not new validation of this report or hotfix.

## 11. Optional improvements, independently prioritized

1. **P1: T mesh/collision alignment and true junction endpoint coverage.** Authored center T versus edge collision is source-confirmed. Preserve old layout on conversion; use explicit new geometry/layout for repaired junctions. Adds art and collision testing, likely 1–2 days beyond hotfix.
2. **P1: corral style/model/shape consistency.** Fix mirrored-shifted resource names, unreachable style 3, and styles that shift/mirror geometry without shapes. Separate family; scope may require model-dependent shapes and transformation support (HorizontalTallBlock currently has no rotate/mirror override).
3. **P2: mixed fence/gate policy.** Correct wooden material tags deliberately and define endpoint adapters for iron, corral and gates. Simply adding `wooden_fences` changes vanilla connectivity and may create visually impossible central-to-edge arms; test before shipping.
4. **P2: diagonal ownership/duplicate-boundary building tools.** Explicit endpoint graph and deterministic placement hints can make enclosures easier without shifting existing owners. Extend notification radius if diagonal owners are supported.
5. **P2: authored post/cap polish, selection height.** Wooden mesh reaches 18 voxels, selection only 16; collision 24. Reconcile overhang targeting and duplicate posts without changing barrier position.
6. **P3: waterlogging/leads/pathfinding parity.** Add only as deliberate features with fluids, entity interactions and serialization tests. Wooden currently inherits Block behavior instead of FenceBlock's no-pathfinding/leash behavior.
7. **P2: bounded connection work.** Removing `resolvedRunEdge` eliminates length-dependent scans. Keep loaded-chunk queue budget and avoid load/generation or repeated scans of long runs when connection-only updates suffice.

## 12. Open evidence and handoff

Remaining material evidence: identify the owner's actual registry ID/click sequence and observe its client detail display; visually inspect model/outline/collision agreement after a real item packet; verify legacy property decoding with saved structures and disposable restart; validate added state-count/model cache cost and preserved authored L endpoints on the target client. These limit acceptance, not source diagnosis.

No owner decision is required to remove topology-driven facing normalization. The proposed compatibility selector is an implementation design choice justified by installed junction preservation. If implementation discovers a smaller representation that exactly preserves all 64 legacy geometry mappings, it may use it after demonstrating that coverage. Expanding to mixed gates, waterlogging, new T/cross art, or a centered fence is outside this bounded hotfix.

**Handoff:** a subsequent implementation agent can immediately add red BlockItem placement tests, pin FACING and legacy physical layout before any connection update, replace class-only adjacency with fixed-footprint compatibility, update mapping generation without overwriting meshes, and change the existing tests that explicitly require anchor normalization. Keep this Patch 18 baseline, version `0.1.8d`, alligator work, medallion release hold, and owner files intact. Complete client/reload acceptance before a separately authorized clean-source release integration.
