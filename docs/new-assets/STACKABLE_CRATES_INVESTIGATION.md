# Stackable Wooden Crates — Investigation

**Scope:** investigation only — no source files were modified.
**Branch:** `patch-18` @ `d1c7f433` (worktree `stackable-wooden-crates-e60016`)
**Platform:** NeoForge 21.1.72 / Minecraft 1.21.1
**Blocks:** `small_crate`, `medium_crate`, `large_crate`

Minecraft behaviour is cited from the decompiled 1.21.1 sources in the local NeoForm
cache; all `net.minecraft` line references are to that decompilation.

---

## 1. Executive summary

Crates are **already** a multiblock family:
`CrateBlock extends DecorativeMultiblockBlock implements EntityBlock`, with exactly one
`CrateBlockEntity` on the root cell holding the whole inventory. Independent inventories
per crate are therefore free — they exist today. Nothing about the container model blocks
stacking.

Stacking fails for two independent, sequential reasons, both in the interaction path and
neither of them a Minecraft limitation:

### Blocker 1 — the crate menu eats the click (hit first)

`CrateBlock` overrides `useWithoutItem` and returns `CONSUME`. Minecraft runs the *block's*
interaction before the *item's*, so right-clicking a crate's top face while holding a crate
opens the existing crate instead. `DecorativeMultiblockItem.useOn` is never invoked at all.

- `ServerPlayerGameMode.java:362–375`
- `CrateBlock.java:57–76`

### Blocker 2 — the support gate rejects crate tops (hit when sneaking)

Placement requires `isFaceSturdy(pos.below(), UP)`. That resolves to `SupportType.FULL`,
which needs a collision shape reaching `y = 1.0` across the full face. Every crate tops out
below that, so the check returns `false` and placement returns `FAIL`.

- `DecorativeMultiblockItem.java:68–73, 109–114`
- `SupportType.java:12–17`

### The main architectural constraint

It is **not** "one block entity per BlockPos". That constraint is real
(`ChunkAccess.blockEntities` is a `Map<BlockPos, BlockEntity>`, verified) but crates never
needed two.

The binding constraint is **arithmetic**: a crate whose art is *h* voxels tall, placed
one-per-BlockPos, leaves a gap of `16 − h` voxels above it. For the small crate that is
8.85 voxels — more than half a block of visible air between stacked crates.

### Recommended direction

**Option A — one crate, one BlockPos, one block entity.** Remove the two blockers, correct
the three collision shapes to match the art they were authored against, and let vanilla
semantics do the rest. This satisfies every hard requirement (independent inventories, no
inventory-loss risk, server authority, existing worlds, Grabby Hands, hoppers, unlimited
stack height, mixed variants) with a change confined to two files.

It leaves one cosmetic question — vertical spacing between stacked crates — which is an
**art decision, not an architecture decision**, and is isolated in [D1](#13-decisions-required).
Do not let it hold up the rest.

---

## 2. Existing crate architecture

Crates were added in `6c9761dd` and enrolled in Grabby Hands in `40fa27d2`. Both are
ancestors of `origin/patch-18`, so crates must be assumed to exist in live player worlds.

| File | Responsibility |
| --- | --- |
| `block/DecorativeMultiblockBlock.java` | Base class. Owns `FACING` + `PART` (0–26) state, cell↔world position maths, per-cell voxel shapes, root-only rendering, and the single authoritative `dismantle()` teardown used by player breaks, explosions, `onRemove`, and neighbour mismatch. |
| `block/CrateBlock.java` | Adds the container. 95 lines. Creates a `CrateBlockEntity` *only* on the root cell, opens the menu from any cell via `anchorPosition()`, drops contents in `beforeDismantle`, exposes a comparator signal. |
| `block/entity/CrateBlockEntity.java` | The sole inventory. Implements `Container` + `MenuProvider` + Grabby's `GrabbyProvenanceHolder`/`GrabbyPortableState`. Size comes from the block (9/27/54); menu is a vanilla `ChestMenu`; opener counting drives the chest sounds. |
| `item/DecorativeMultiblockItem.java` | Placement. Overrides `useOn` entirely; validates every cell, then places transactionally with rollback. Extension points: `mayPlaceCell`, `mayUseSupport`. |
| `registry/BlockRegistry.java:2675–2700` | The three blocks and the shared `crate` block-entity type. **Every voxel shape in the crate family is defined here**, inline, as a `CellShapeFactory` lambda. |
| `registry/ItemRegistry.java:2445–2447` | All three items are plain `DecorativeMultiblockItem` — no crate-specific subclass exists yet. |
| `grabbyhands/GrabbyRootResolver.java` | Maps any clicked cell back to the anchor, so Grabby always acts on the inventory-owning position. |
| `grabbyhands/GrabbyActor.java:230–231` | Calls `blockItem.useOn(...)` directly for `GrabbyStructurePlacementItem`s — **bypassing the block-interaction phase**, and therefore Blocker 1. |
| `assets/.../blockstates/*_crate.json` | `multipart` with four `facing` × `part=0` cases. Only part 0 has a model; other parts are deliberately blank. |
| `assets/.../models/block/new_assets/*_crate.json` | Plain JSON element models, no parent. Rendered on `RenderType.cutout()` (`ClientModSetup:718–720`). **No GeckoLib, no custom renderer.** |
| `data/.../loot_table/blocks/*_crate.json` | Empty pools by design — `getDrops` returns `List.of()` and `dismantle()` pops the item. |

### Call path when a player right-clicks the top of a crate while holding a crate

```text
ServerGamePacketListenerImpl
  └─ ServerPlayerGameMode.useItemOn(player, level, stack, hand, hit)
       ├─ flag  = holding something            → true
       ├─ flag1 = isSecondaryUseActive() && flag → FALSE  (not sneaking)
       ├─ [line 362] !flag1, so run the BLOCK first:
       │    blockstate.useItemOn(...)
       │      └─ CrateBlock does not override useItemOn
       │           → PASS_TO_DEFAULT_BLOCK_INTERACTION
       ├─ [line 369] result was PASS_TO_DEFAULT + MAIN_HAND, so:
       │    blockstate.useWithoutItem(...)
       │      └─ CrateBlock:72  player.openMenu(crate)
       │           → InteractionResult.CONSUME
       └─ [line 371] CONSUME.consumesAction() == true → RETURN

     [line 378] stack.useOn(...) ......................... NEVER REACHED
     DecorativeMultiblockItem.useOn ..................... NEVER REACHED
```

The item's placement code — including every cell check, the support gate, and the
transactional placement — is dead code on this path. That is why the crate never even
*tries* to place.

---

## 3. Current geometry

Art bounds below are computed from the model JSON with element rotations applied.
Collision and selection are the same shape: `DecorativeMultiblockBlock` returns `getShape()`
from `getCollisionShape()`, and player raycasts use `ClipContext.Block.OUTLINE`, which is
also `getShape`. So **one shape drives collision, selection, and targeting** for all three
crates.

| Crate | Slots | Cells | Art bounds (voxels, X / Y / Z) | Art height | Collision / selection shape | Shape height | Full block? | Stackable now? |
| --- | ---: | ---: | --- | ---: | --- | ---: | --- | --- |
| `small_crate` | 9 | 1 | `2.00–14.00 / 0.00–7.15 / 1.30–9.10` | 7.15 | `box(2,0,2,14,11,14)` | 11 | No | No |
| `medium_crate` | 27 | 1 | `1.26–15.52 / 0.09–11.60 / 0.61–14.86` | 11.51 | `box(1,0,1,15,14,15)` | 14 | No | No |
| `large_crate` | 54 | 8 | `−2.50–16.50 / 1.00–19.00 / −2.50–16.50` | 18.00 | 2×2×2 cells; envelope X 0–28, Y 0–19, Z 0–22 | 19 | Over-tall | No |

The three crates have **materially different heights** — 7.15, 11.51 and 18.00 voxels — and
none is a clean fraction of 16. That single fact drives most of section 7.

### Figure 1 — elevation, drawn to scale

```text
  small_crate                medium_crate               large_crate
  1 cell · 9 slots           1 cell · 27 slots          8 cells (2×2×2) · 54 slots

  y=16 ┄┄┄┄┄┄┄┄┄┄┄┄┄┄        y=16 ┄┄┄┄┄┄┄┄┄┄┄┄┄┄        y=19 ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
                                                              │███████████████│       │
       ┌ ─ ─ ─ ─ ─ ┐ 11            ┌ ─ ─ ─ ─ ─ ┐ 14     y=16 ┄┼┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┼┄┄┄┄┄┄┄┤
       │           │               │███████████│ 11.51        │███████████████│       │
       │███████████│ 7.15          │███████████│              │███████████████│ 18    │
       │███████████│               │███████████│              │███████████████│       │
  y=0 ─┴───────────┴──        y=0 ─┴───────────┴──      y=0 ─ └───────────────┴───────┘
       └── 12 wide ──┘              └── 14 wide ─┘             └── art 19 ──┘└ +9 vox ┘
                                                                              phantom
  ███ authored art (model JSON)
  ─ ─ collision = selection = raycast shape
```

In all three crates the shape is taller than the art. In the large crate the shape is also
nearly half a block wider than the art, and the art overhangs 2.5 voxels into a neighbour
that has no cell reserved at all.

### The shapes are stale, not deliberate

`DecorativeMultiblockBlock`'s javadoc calls the non-root cells "deliberately authored
collision". For crates that claim does not survive inspection:

- **`medium_crate`** — the shape is `box(1,0,1,15,14,15)`, exactly 14 voxels tall. The
  superseded model `medium_crate.old` was *exactly* `Y 0.00–14.00`. The current model is
  `Y 0.09–11.60`. **The art was replaced and the shape was not.**
- **`small_crate`** — shape is 11 tall against 7.15 of art, and spans `Z 2–14` against art
  at `Z 1.30–9.10`. Roughly 4.9 voxels of the south side is solid but empty, while
  0.7 voxels of art at the north pokes outside the hitbox.
- **`large_crate`** — height is right (19 vs 19), but the footprint reserves 8 BlockPos and
  28×22 voxels for an object that is 19×19. Placing one needs a clear 2×2×2 volume.

These mismatches matter here because **the support gate reads the shape, and the raycast
reads the shape**. Both stacking and targeting are decided by numbers that no longer
describe the crates.

---

## 4. Exact root cause

### Worked trace — small crate at (100, 64, 100)

```text
Player holds  small_crate, aims at the top surface of the crate at (100,64,100)

Crate outline shape      box(2,0,2,14,11,14)
World AABB               (100.125, 64.0000, 100.125)
                      →  (100.875, 64.6875, 100.875)
Raycast (OUTLINE)        hits y = 64.6875, face = UP, blockPos = (100,64,100)

── PATH 1: not sneaking ───────────────────────────────────────────────
ServerPlayerGameMode:359   flag  = true
ServerPlayerGameMode:360   flag1 = false
ServerPlayerGameMode:363   blockstate.useItemOn(...)
                             → PASS_TO_DEFAULT_BLOCK_INTERACTION
ServerPlayerGameMode:370   blockstate.useWithoutItem(...)
                             → CrateBlock:72  player.openMenu(crate)
                             → CONSUME
ServerPlayerGameMode:373   return   ← item useOn never runs

  Expected  a crate is placed at (100,65,100)
  Actual    the lower crate's 9-slot GUI opens

── PATH 2: sneaking (flag1 = true, block phase skipped) ───────────────
BlockPlaceContext ctor     replaceClicked = crateState.canBeReplaced(ctx) = false
BlockPlaceContext:56       getClickedPos() → relativePos = (100,65,100)   ✓ correct
DecorativeMultiblockItem:44 clickedFace == UP                             ✓
DecorativeMultiblockItem:53 anchor = anchorForMinimumPosition(...) = (100,65,100)
  cell(0,0,0) → position (100,65,100)
    in world bounds ......... ✓        getBlockEntity == null ... ✓
    chunk loaded ............ ✓        air canBeReplaced ........ ✓
    mayInteract ............. ✓        mayPlaceCell ............. ✓
DecorativeMultiblockItem:68  cell.y() == minimumY() → check support at (100,64,100)
DecorativeMultiblockItem:112 state.isFaceSturdy(level, pos, UP)
  → BlockBehaviour:864   SupportType.FULL
  → SupportType:15       Block.isFaceFull(getBlockSupportShape(...), UP)
  → BlockBehaviour:285   getBlockSupportShape defaults to the COLLISION shape
  → Block:251            shape.getFaceShape(UP)
  → VoxelShape:226       slice at y = 0.9999999 ; shape max Y = 0.6875
                         → SliceShape is EMPTY
  → isShapeFullBlock(empty) = FALSE
DecorativeMultiblockItem:71  return InteractionResult.FAIL

  Expected  a crate is placed at (100,65,100)
  Actual    nothing happens, no message, no sound
```

### Independent corroboration inside the repo

`AdventureScarecrowItem` exists *because of this exact gate*. It overrides `mayUseSupport`
to special-case `CommunityFarmBlock`, whose shape is `box(0,0,0,16,15,16)` — full 16×16 top
face, one voxel short of the block ceiling. Even that fails `isFaceSturdy`. A crate topping
out at 11 voxels across a 12×12 face fails far more comfortably.

### What is *not* the cause

| Candidate | Verdict | Evidence |
| --- | --- | --- |
| Placement position resolution | Correct | `BlockPlaceContext.getClickedPos()` correctly returns `y+1` because the crate is not replaceable. |
| Raycast / clicked face | Correct | The crate's top is a horizontal face; the hit reports `Direction.UP`. |
| Target BlockPos occupancy | Correct | Air above passes `canBeReplaced` and has no block entity. |
| `canSurvive` | N/A | Not overridden anywhere in the multiblock family. There is **no post-placement support rule** — a crate will happily float. |
| Block-entity restrictions | N/A | One BE per BlockPos is satisfied; stacked crates sit at different positions. |
| `neighborChanged` teardown | Safe | `structureMatches` only inspects the caller's own cells. Two single-cell crates cannot dismantle each other. |

### A third defect, specific to the large crate

Even with both blockers removed, large crates would stack *crookedly*. Its four upper cells
each present a clickable top face, and `anchorForMinimumPosition` returns the clicked
position unchanged (all `min*` are 0):

```text
click top of BlockPos(0,1,0) → anchor (0,2,0)   ✓ aligned with the crate below
click top of BlockPos(1,1,0) → anchor (1,2,0)   ✗ offset one block east
click top of BlockPos(0,1,1) → anchor (0,2,1)   ✗ offset one block south
click top of BlockPos(1,1,1) → anchor (1,2,1)   ✗ offset diagonally
```

Three of the four landing spots produce a staggered stack. Multi-cell crates need the anchor
snapped to the supporting crate's anchor, not taken from the clicked cell.

---

## 5. Minecraft / NeoForge constraints

| Constraint | Verified at | Consequence for crates |
| --- | --- | --- |
| **One BlockEntity per BlockPos.** Hard. | `ChunkAccess.java:82` — `Map<BlockPos, BlockEntity>` | Two crates cannot share a position *as separate block entities*. They can share one composite BE (Option B) — nothing else. |
| **No multipart / sub-block API.** | NeoForge 21.1.72 jar scan | The only "multipart" in NeoForge is `MultiPartBlockStateBuilder` / `MultipartModelData` — *model* selection in blockstate JSON. There is no world-side sub-block framework. **Option E does not exist.** |
| **Block interaction precedes item interaction** unless the player sneaks. | `ServerPlayerGameMode.java:362–378` | Blocker 1. Escapable: returning `SKIP_DEFAULT_BLOCK_INTERACTION` from `useItemOn` skips `useWithoutItem` and falls through to the item. |
| **`SupportType.FULL` needs a face at y=1.0.** | `SupportType.java:12–17`, `VoxelShape.java:217–229` | Blocker 2. The mod owns this check (`mayUseSupport`) and may relax it — the ladder and scarecrow items already do. |
| **Voxel shapes *may* exceed the unit cube.** | `Shapes.java:54–65`, `ArmoireBlock.java:33–50` | `Shapes.create` falls back to `ArrayVoxelShape` for out-of-range coords, and `ArmoireBlock` already ships shapes spanning −16→32. So "render and collide below your own block" is *possible* — but see the caveat below. |
| **Block models may exceed the cube.** | `large_crate` spans −2.5→16.5 | Already relied on. Cost: geometry can vanish when the owning chunk section is culled but the neighbour is drawn. |
| **Selection = collision = raycast here.** | `ClipContext.java:53`, `DecorativeMultiblockBlock.java:186–189` | One shape to get right, but no way to give targeting a different hitbox from collision without adding an override. |

> **Caveat on oversized shapes.** Oversized shapes are legal but fragile, and this repo
> already knows it — `GrabbyRootResolver`'s javadoc singles out "the armoire family… a
> single position wearing an oversized voxel shape" as the awkward case. Entity collision
> only queries blocks whose *positions* intersect the entity AABB, so a shape reaching far
> outside its block is inconsistently enforced. A crate whose hitbox sits entirely below its
> own BlockPos would be a targeting hazard, not a clean solution.

---

## 6. Existing Britannia patterns

| System | What it already solves | Reusable here? |
| --- | --- | --- |
| **`DecorativeMultiblockBlock`** *(direct)* | Root/part addressing, per-cell shapes, atomic placement with rollback, single-point teardown, root-only rendering. Six blocks already extend it. | **Yes — this is the chassis.** Crates already sit on it. Stacking needs no new framework. |
| **`DisplayCaseBlock`** *(strong precedent)* | A 2-cell (y 0→1) multiblock where the *upper* cell carries an authored, invisible interaction surface (`UPPER_INTERACTION_SURFACE = box(1,0,1,15,1,15)`) sitting above the root's model, and connection flags mirror from the root into the child cells' state. | **Yes, for the pattern**: separating "what you can click" from "what the model draws", and deriving child-cell state from the root. Not for merchandise/connection logic, which is unrelated. |
| **`AdventureScarecrowItem`** *(exact template)* | Overrides `mayUseSupport` to accept a specific non-sturdy block (`CommunityFarmBlock`, 15/16 tall) as valid support. | **Yes.** Blocker 2's fix is the same three-line shape as this class, scoped to crate tops. |
| **Grabby Hands** *(must stay in step)* | Crates are in `grabby_movable`. `GrabbyPortableState` moves contents inside the item; `transportRefusal` blocks in-use and nested containers; `GrabbyRootResolver` maps any cell to the anchor. | Not a stacking pattern, but a hard constraint: Grabby places via `blockItem.useOn` directly, so it feels Blocker 2 but not Blocker 1. Fixing only `useItemOn` would make Grabby and hand-placement disagree. |
| **`ArmoireBlock`** *(anti-pattern)* | Single BlockPos wearing a shape spanning up to three blocks. | **No.** Explicitly flagged in-repo as the awkward case. Do not extend it to crates. |
| **`HalfBlock` / `QuarterBlock`** | Partial-height decorative blocks with no inventory. | No container semantics to borrow. |

The Display Case is the closest architectural relative and it confirms the direction: it did
*not* invent sub-block placement. It took extra BlockPos and gave the child cells authored,
purpose-built shapes.

---

## 7. Solution options

### The arithmetic that decides this

For stacked crates to physically touch, crate *n*'s art must start at `n·h`. With one crate
per BlockPos, crate *n*'s block origin is at `16n`. The visual offset needed is:

```text
offset(n) = 16n − n·h = n·(16 − h)

small   h = 7.15   →  8.85 per level   → exceeds one block at n = 2
medium  h = 11.51  →  4.49 per level   → exceeds one block at n = 4
large   h = 18.00  → 14.00 per level   → exceeds one block at n = 2
                     (pitch is 32: the large crate is two cells tall)
```

Because `h < 16` always, the required offset grows without bound. **One crate per BlockPos
cannot produce a visually continuous stack of arbitrary height.** It supports a bounded run
— roughly 2 small, 4 medium, 2 large — before the offset would have to push a crate's shape
entirely below its own block, which section 5 rules out as fragile.

### Figure 2 — the core trade-off

```text
   One crate per BlockPos                Crates actually touching
   (what Option A gives you)             (both fit inside ONE BlockPos)

y=66 ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄               y=66 ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
     │███████████████│ crate B
y=65 ┄┼┄┄┄┄┄┄┄┄┄┄┄┄┄┄┼               y=65 ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
     │  gap  8.85    │                    │███████████████│ crate B
     │               │                    │███████████████│ crate A
     │███████████████│ crate A            │               │  2 crates = 14.3 vox
y=64 ─┴───────────────┴──           y=64 ─┴───────────────┴──  < one 16-vox block
```

Two small crates in contact occupy 14.3 voxels, less than one block. Exact contact therefore
*requires* two crates in one BlockPos, which requires one composite block entity.
One-per-BlockPos buys safety and simplicity at the price of an 8.85-voxel gap.

### Comparison

| Architecture | Independent inventories | Natural placement | Existing-world safety | Complexity | Tall stacks | Recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| **A — Separate BlockPos**<br>one crate = one BE, shapes corrected | Free — unchanged | Yes, after 2 fixes | No ID/NBT change | Low — 2 files | Unlimited (16-vox pitch) | **Recommended** |
| **A+ — A with bounded visual lowering**<br>adds `STACK_OFFSET` 0–3 | Free | Yes | New property defaults safely | Moderate | 2–4 before a seam | Optional follow-on if D1 says the gap matters |
| **B — Composite BlockEntity**<br>N crate units in one BE | Must be rebuilt (sub-`Container` views) | Custom, per-layer | New BE + NBT shape | High | Exact, unlimited | Only if exact contact is mandatory |
| **C — Dedicated stacked block**<br>`single_crate` → `double_crate` | Two inventories in one BE | Conversion on place | New block IDs | High | Combinatorial | **Reject.** Same cost as B, worse ceiling |
| **D — Multiblock roots + support parts** | Free | Cell-alignment problems | Cell-range change | High | Awkward | **Reject** as a stacking mechanism |
| **E — Platform partial-block API** | **Does not exist.** Verified against NeoForge 21.1.72 and MC 1.21.1: no world-side multipart facility, and `ChunkAccess.blockEntities` is strictly one BE per position. | | | | | **N/A** |

### Notes on the rejected options

**Option B (composite BE)** is the only architecture that reproduces the art exactly at any
height, and it is genuinely buildable — a `CrateStackBlockEntity` owning an ordered list of
units, each exposing a `Container` view, with the hit result's Y coordinate selecting the
layer. But it dismantles everything that currently works for free: per-crate `Container`
identity, hopper addressing, Grabby's provenance and portable state, the comparator signal,
and — critically — the saved NBT of crates that already exist in the live world. It also has
no answer for mixed variants that is simpler than Option A's.

**Option C** is Option B with a worse ceiling: *k* stack heights × 3 variants × mixed
orderings is a combinatorial explosion of block IDs. It offers no simplification over B.

**Option D** is what crates already do horizontally, and section 4 shows it is the source of
the large crate's staggering problem. Adding more cells makes placement harder, not easier.

---

## 8. Recommended architecture

```text
WORLD COORDINATES                       BLOCK ENTITIES

Y=66   [ small_crate  PART=0 FACING=n ] ── CrateBlockEntity  (9 slots)
Y=65   [ small_crate  PART=0 FACING=n ] ── CrateBlockEntity  (9 slots)
Y=64   [ medium_crate PART=0 FACING=e ] ── CrateBlockEntity  (27 slots)
Y=63   [ any sturdy or crate-topped block ]

One crate = one BlockPos = one BlockEntity = one inventory.
No shared state. No new block IDs. No new NBT.
```

Three changes, in two files plus the registry.

### 1 · Placement — let the item have the click

Override `useItemOn` on `CrateBlock`. When the held stack is a crate `BlockItem` and the
clicked face is `UP`, return `ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION`. That
value's `consumesAction()` is false and it is *not* `PASS_TO_DEFAULT_BLOCK_INTERACTION`, so
`ServerPlayerGameMode` skips `useWithoutItem` entirely and falls through to `stack.useOn`.
Every other click still opens the menu.

### 2 · Support — accept a crate top

Add a `CrateItem extends DecorativeMultiblockItem` overriding `mayUseSupport` to return true
when the support block is a `CrateBlock`, else defer to `super`. Modelled directly on
`AdventureScarecrowItem`. Because it lives in the item, **hand placement and Grabby Hands
placement stay in agreement** — both go through `DecorativeMultiblockItem.useOn`.

### 3 · Geometry — make the shapes describe the crates

| Crate | Shape today | Shape that matches the art | Effect |
| --- | --- | --- | --- |
| small | `box(2,0,2,14,11,14)` | `box(2,0,1,14,7.15,9.1)` | Removes 3.85 vox of phantom height and ~4.9 vox of phantom depth. |
| medium | `box(1,0,1,15,14,15)` | `box(1,0,1,15.5,11.6,15)` | Restores the match that `medium_crate.old` had. |
| large | 2×2×2 cells, env. 28×19×22 | 1×1×2 cells, env. 16×19×16 | See [D2](#13-decisions-required) — this one has a migration cost. |

### How each behaviour then works

| Concern | Mechanism |
| --- | --- |
| **Placement** | `BlockPlaceContext` already resolves the clicked crate's top to `y+1` correctly. Unchanged. |
| **Rendering** | Untouched. Each crate renders its own model from its own root cell at its own position. No offsets, no custom renderer, no lighting or occlusion changes. |
| **Collision** | Each crate's corrected shape, inside its own block. No oversized shapes, no cross-block hazards. |
| **Clicking** | Vanilla raycast against `getShape`. Because the shapes are inside their own blocks and non-overlapping, upper/lower targeting is unambiguous by construction — **no custom hit detection needed**. This is the strongest argument for Option A. |
| **Independent inventories** | Free. Distinct `BlockPos` → distinct `CrateBlockEntity` → distinct `NonNullList<ItemStack>`. Already true today. |
| **Breaking** | Existing `dismantle()`. `structureMatches` inspects only the caller's own cells, so breaking one crate cannot reach another. Contents drop once via `beforeDismantle`; `MUTATING` guards re-entry. |
| **Serialization** | Untouched. `ContainerHelper.saveAllItems` / `loadAllItems` per position, as now. |
| **Hoppers / comparators** | Unchanged and correct — each crate's root is its own `Container` at its own position, so a hopper under the lower crate sees only the lower crate. |
| **Grabby Hands** | Unchanged. `GrabbyRootResolver` already maps a clicked cell to its anchor; with one cell per crate that is the identity. |
| **Tall stacks** | Unlimited. No stack-height state, no accumulating offsets, no cap to design. |
| **Mixed variants** | Free — `mayUseSupport` only asks "is the block below a crate?". Any variant stacks on any other. No combinatorial states. |

> **What Option A does not do.** Stacked crates do not physically touch. Each occupies a
> 16-voxel slot, so a stack shows a gap of 8.85 (small), 4.49 (medium) or 14.00 (large, per
> 32-voxel pitch). For reference, a vanilla chest is 14 voxels tall and stacks with a
> 2-voxel gap that nobody objects to. Medium is close to that. Small and large are not.
> That is [D1](#13-decisions-required).

---

## 9. Risks and edge cases

| Risk | Severity | Assessment |
| --- | --- | --- |
| **Inventory loss** | Low | Option A adds no new teardown path. Each crate's contents live at its own position and drop through the existing single `beforeDismantle` hook. |
| **Item duplication** | Low | The `MUTATING` thread-local plus `UPDATE_SUPPRESS_DROPS` already prevent re-entrant drops; `NewAssetsCrateGameTests` asserts exactly-once for the large crate. Extend that assertion to stacks. |
| **Breaking the lower crate** | Design choice | Nothing overrides `canSurvive`, and `structureMatches` only checks the caller's own cells — so the upper crate **stays floating**, keeping its contents. Safe, and consistent with the rest of the mod's furniture. See [D3](#13-decisions-required). |
| **Existing crate-item loss on non-player removal** | Pre-existing | `onRemove` calls `dismantle(..., dropItem = false)`. Contents drop, but the crate *block item* does not. A crate removed by `/setblock` or another mod vanishes. Not caused by stacking; worth logging separately. |
| **Ceilings** | Low | `useOn` already fails if the target cell is non-replaceable. Placing under a ceiling simply fails, which is correct. |
| **Chunk boundaries** | Low | Option A adds no cross-block rendering, so the section-culling artefact that affects the large crate's existing overhang is not made worse. |
| **Multiplayer desync** | Low | Placement, menus and breaking are all server-side; the client path returns `InteractionResult.SUCCESS` for prediction only. See section 11. |
| **Neighbouring blocks** | Watch | Only the large crate has multiple cells; reducing its footprint ([D2](#13-decisions-required)) removes the phantom-collision surprise where players cannot build beside a crate. |
| **Moving crates (Grabby)** | Watch | Grabby bypasses Blocker 1 but not Blocker 2, so it must be re-tested against stacks. `transportRefusal` already refuses in-use and nested-container crates. |
| **Pistons** | None | All three crates set `pushReaction(PushReaction.BLOCK)`. |
| **Shape change under standing entities** | Low | All three shape corrections *shrink* the hitbox, so nothing can become trapped. Players standing on an existing crate will drop a few voxels once. |

---

## 10. Migration strategy

Crates are pushed to `origin/patch-18`, so **assume they exist in player worlds with
contents**. They do not appear in any of the 20 structure NBT files, so there are no
world-generated crates to convert.

| Change | Migration needed | Why |
| --- | --- | --- |
| Blocker 1 fix (`useItemOn`) | None | Behaviour only; touches no persisted data. |
| Blocker 2 fix (`CrateItem`) | None | Item class swap in the registry. Item IDs are unchanged; existing crate items in chests and inventories keep working. |
| Small/medium shape correction | None | Shapes are computed at runtime, never persisted. Existing crates just get accurate hitboxes. |
| Optional `STACK_OFFSET` property | None | Chunk palettes store property values by name; a state saved before the property existed loads with the property's default. Adding is safe; renaming or removing would not be. |
| **Large crate 2×2×2 → 1×1×2** | **Lazy cleanup required** | See below. |

> ### The one migration hazard
>
> Shrinking the large crate's cell range changes `cells().size()` from 8 to 2. Existing
> large crates in the world have cells with `PART` values 0–7. `PART` is declared
> `IntegerProperty.create("part", 0, 26)`, so those states still *load* — but
> `hasValidPart` becomes false for parts 2–7, leaving six invisible, non-interactive orphan
> blocks around every existing large crate.
>
> **The inventory itself is safe:** `rootPart` is index 0 in both the old and new layouts,
> so the root cell keeps its `PART=0` state and its `CrateBlockEntity` loads unchanged. Only
> occupancy is affected.
>
> **Mitigation:** a lazy sweep — when a `CrateBlock` state with an out-of-range `PART` is
> ticked or neighbour-updated, replace it with air. No DataFixer, no world scan, no block or
> block-entity ID change.

In short: **Option A needs no migration at all** unless D2 is accepted, and even then the
migration is a self-healing cleanup that cannot touch an inventory.

---

## 11. Testing strategy

Two existing suites cover crates and must keep passing: `CrateBlockEntityTest` (JUnit) and
`NewAssetsCrateGameTests` (GameTest, template `service_npc_spawn_test_empty`). Note the
standing repo rule — **only `GameTestAssertException` may leave a sequence callback** — and
mock players are hard-coded creative, so any survival-mode assertion must set the game mode
explicitly as the existing test already does.

### Unit — JUnit

- Corrected shapes report the expected bounds for all three crates and all four facings.
- A crate's top face is *not* `isFaceSturdy` — pinning the reason the override exists.
- `CrateItem.mayUseSupport` accepts every crate block and rejects air, and defers to `super`
  otherwise.
- `CrateBlock.useItemOn` returns `SKIP_DEFAULT_BLOCK_INTERACTION` for a crate item on the UP
  face, and `PASS_TO_DEFAULT_BLOCK_INTERACTION` for every other item, face, or empty hand.
- Existing NBT round-trip test still passes byte-for-byte.

### Placement — GameTest

- Crate places normally on a full block (regression).
- Crate places on top of a short crate, **without sneaking** — the acceptance test for
  Blocker 1.
- Placement on a crate top does **not** open the lower crate's menu.
- Stacked crate receives the intended orientation (per [D4](#13-decisions-required)).
- Placing onto an occupied position fails and mutates nothing — no inventory is overwritten.
- Placement under a ceiling fails cleanly, leaving no partial structure.
- Mixed variants: small on medium, medium on large, large on small.

### Containers

- Two stacked crates hold different contents; each reopens with its own.
- Both survive a save/load cycle with contents intact.
- A hopper beneath the lower crate pulls only from the lower crate.
- Comparator on each crate reads only its own fill level.

### Breaking

- Break the upper crate: lower crate and its contents are untouched; upper contents drop
  exactly once; exactly one crate item drops.
- Break the lower crate: upper crate survives with its contents (per
  [D3](#13-decisions-required)).
- Break under explosion and via `onRemove`: no duplication, no loss.
- Break a 4-high stack from the middle: exactly one crate is removed.

### Persistence & multiplayer

- Chunk unload/reload and server restart preserve every inventory in a stack. **Never call
  `setChunkForced(false)` in a test** — it strips neighbours' tickets and hangs the run.
- Two mock players open different crates in the same stack simultaneously; opener counts and
  sounds stay per-crate.
- One player breaks the lower crate while another has the upper crate open — the open menu
  must not be silently invalidated into an item-loss path.
- All assertions run against `ServerLevel` state, never client state.

### Geometry & Grabby

- A raycast at the upper crate's body returns the upper BlockPos; at the lower body, the
  lower BlockPos.
- Grabby pickup of the upper crate leaves the lower crate intact, and vice versa.
- Grabby placement onto a crate top succeeds (it bypasses Blocker 1, so it exercises
  Blocker 2 in isolation).

### Existing-world compatibility

- A crate state saved under the current code loads and keeps its contents under the new
  code.
- If D2 is accepted: a large crate written with `PART` 0–7 still yields a working root
  inventory, and the orphan cells clear lazily.

---

## 12. Recommended implementation milestones

| | Milestone | Content |
| --- | --- | --- |
| **M1** | Geometry truth | Correct the small and medium shapes to match their models; add unit tests pinning the bounds. Independently valuable, and it makes the crates targetable where they actually are. No behaviour change. |
| **M2** | Support gate | Add `CrateItem` with the `mayUseSupport` override; register all three items against it. After M2, Grabby Hands can already stack crates — a cheap, isolated proof that the diagnosis is right. |
| **M3** | Hand placement | `CrateBlock.useItemOn` returning `SKIP_DEFAULT_BLOCK_INTERACTION`. This is the milestone that delivers the feature: plain right-click stacking, no sneaking. |
| **M4** | Interaction & breaking proof | GameTests for independent menus, independent contents, per-crate breaking, drop-exactly-once across a 4-high stack, and hopper/comparator isolation. |
| **M5** | Dedicated-server & persistence | Chunk reload, restart, two-player concurrent access, and the break-while-open case. Run on a real dedicated server, not only in GameTests. |
| **M6** | Large-crate footprint | Conditional on [D2](#13-decisions-required): reduce to 1×1×2, snap the placement anchor to the supporting crate's anchor, add the lazy orphan-cell sweep and its migration test. |
| **M7** | Visual pitch | Conditional on [D1](#13-decisions-required): either an art revision, or the bounded `STACK_OFFSET` property with its own rendering and shape tests. Deliberately last — nothing else depends on it. |

M1–M5 are the complete feature and carry essentially no migration risk. M6 and M7 are
separable and each gated on a decision below.

---

## 13. Decisions required

### D1 — Vertical spacing between stacked crates

Option A leaves 8.85 voxels of air above a small crate, 4.49 above a medium, 14.00 above a
large. Three ways forward:

- **(a)** accept it — chest-like, ships with M1–M5;
- **(b)** revise the art so each crate fills its 16-voxel slot, which conflicts with keeping
  the models unchanged;
- **(c)** add a bounded `STACK_OFFSET` property (M7) giving exact contact for 2 small /
  4 medium crates before a seam.

**Recommendation: (a) now, revisit with (c) once you can see a real stack in-game.** The gap
is cosmetic and reversible; nothing else in the design depends on the answer.

### D2 — Large-crate footprint

The large crate reserves 8 BlockPos and a 28×19×22 collision envelope for an object
measuring 19×18×19. It needs a clear 2×2×2 volume to place, blocks building in two adjacent
columns for no visible reason, and stacks crookedly from three of its four clickable top
cells.

**Recommendation: reduce to 1×1×2 and snap the stacking anchor.** This is the only change in
the whole report that touches existing world data — see section 10 for why the inventory is
nonetheless safe. **Needs approval before implementation.**

### D3 — What happens to the upper crate when the lower one is broken

Today's behaviour, unchanged, is that the upper crate stays floating with its contents.
Alternatives: drop it as an item (risks a full inventory on the ground), or make it fall
(needs a falling-block entity that carries a container).

**Recommendation: leave it floating.** It is the only option with zero inventory-loss
surface, it matches the rest of the mod's furniture, and it is what players expect from
shelves and chests.

### D4 — Orientation of a stacked crate

`DecorativeMultiblockItem` derives `FACING` from the placing player. A crate placed on top of
another will therefore face wherever the player stood, which can look untidy in a warehouse
stack.

**Recommendation: inherit the supporting crate's facing when stacking, player facing
otherwise.** Cheap, and it makes stacks read as one object.

### D5 — Mixed-variant stacking

Option A supports it for free — `mayUseSupport` only asks whether the block below is a crate.
Restricting it would take extra code.

**Recommendation: allow it.** No complexity cost, and it is the behaviour players will try
first.

---

*Investigated against `patch-18` at `d1c7f433` in worktree `stackable-wooden-crates-e60016`,
which was reset onto `patch-18` at the start of this work — it had been branched 501 commits
earlier, before crates existed. No source files were modified.*
