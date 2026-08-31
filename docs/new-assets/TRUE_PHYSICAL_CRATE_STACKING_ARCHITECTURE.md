# True Physical Crate Stacking — Architecture Report

**Scope:** investigation only — no source files were modified, no experimental code was written.
**Branch:** `patch-18` @ `d1c7f433` (main worktree)
**Platform:** NeoForge 21.1.72 / Minecraft 1.21.1
**Supersedes the recommendation of:** `STACKABLE_CRATES_INVESTIGATION.md` (Investigation 1)

Minecraft and NeoForge behaviour below is cited from the decompiled, NeoForge-patched 1.21.1
sources in this repo's own NeoForm cache
(`build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/patchUserDev/output.jar`).
Investigation 1 read the *pre-patch* NeoForm output; two of the conclusions below differ
because the NeoForge patches change the relevant methods.

---

## 1. Executive summary

**Yes. True compact stacking with independent logical containers is practical**, and the two
mechanisms that looked hardest — selecting one crate out of a stack for *breaking*, and
rendering several crate models at exact sub-block heights — are both already solved, one by
Minecraft's own break contract and one by code that exists in this repository today.

Three findings carry the recommendation:

1. **Individual breaking works, with no packets and no client desync.**
   `BlockState.onDestroyedByPlayer` is the sole authority on whether a position is cleared, it
   receives the `Player`, and **both the server and the client call it** —
   `ServerPlayerGameMode.removeBlock:296–301` and `MultiPlayerGameMode.destroyBlock:107–133`.
   If the boolean it returns is derived from *synchronised block-entity state* ("does this stack
   hold more than one crate?") rather than from the raycast, both sides independently reach the
   same answer, the client never predicts a removal the server refuses, and the block survives a
   layer break cleanly. This was the single biggest open risk and it is retired.

2. **Rendering is a solved problem in-repo.** `DecorativeOffsetModel.shiftQuad`
   (`client/model/DecorativeOffsetModel.java:120–145`) already translates the vertices of an
   *existing baked model* by an arbitrary X/Y/Z offset, and `AdaptiveRoofBakedModel` +
   `AdaptiveRoofBlockEntity` already drive a `BakedModelWrapper` from block-entity state through
   a `ModelProperty`, chunk-baked rather than through a `BlockEntityRenderer`. Composing N crate
   models at exact Y offsets is those two patterns put together. **The crate JSON models are
   reused as-authored; nothing is redrawn.**

3. **Automation does not have to be sacrificed.** Investigation 1 treated hopper support as a
   casualty of composite storage. It is not: `WorldlyContainer.getSlotsForFace(Direction)` lets
   one block entity expose a *different slot range per face*, which is precisely the primitive
   needed to say "a hopper underneath sees the bottom crate, a hopper above sees the top crate".
   The repo does not use `WorldlyContainer` anywhere yet, so this is new but not exotic.

**Recommended architecture: C — the crate column.** One `CrateStackBlockEntity` at the stack
root owns an ordered list of `LogicalCrate` records with stable integer identities; the stack
occupies `ceil(totalHeight / 16)` world cells, with continuation cells above the root providing
occupancy, their own share of the collision and selection shapes, and their own share of the
rendered geometry. Architecture B (everything in one BlockPos) is the degenerate single-cell
case of C and is *not* a separate design — see §6.

**What this costs.** It is a genuinely larger change than Investigation 1's Option A: a new
block, a new block entity, a new baked model, a break interceptor, a Grabby extension, and a
promotion path for existing crates. Estimated eight milestones. Option A remains the right
answer *if the visual gap turns out to be acceptable* — but the product requirement as stated
says it is not, and Option A cannot be made to satisfy it at any height (§3).

**One thing to decide before any code is written:** the stack height cap (§20, D2). Everything
else in this report is an engineering decision I have taken a position on.

---

## 2. Accepted findings from Investigation 1

These remain valid and are not re-litigated here.

**Blocker 1 — the crate menu eats the click.** `CrateBlock.useWithoutItem` returns
`InteractionResult.CONSUME`, and `ServerPlayerGameMode.useItemOn` runs the block's interaction
before the item's unless the player is sneaking. `DecorativeMultiblockItem.useOn` is never
reached. The fix is to override `useItemOn` on `CrateBlock` and return
`ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION` when the held stack is a crate and the
clicked face is `UP`.

**Blocker 2 — the support gate rejects crate tops.** `DecorativeMultiblockItem.mayUseSupport`
requires `isFaceSturdy(pos.below(), UP)`, which resolves to `SupportType.FULL` and needs a
collision face reaching `y = 1.0`. No crate reaches it. The fix is a `CrateItem` subclass
overriding `mayUseSupport`, exactly as `AdventureScarecrowItem` already does. Because it lives
on the item, hand placement and Grabby placement stay in agreement.

**Stale collision geometry.** Verified independently for this report by recomputing the model
bounds from the JSON with element rotations applied:

| Crate | Art bounds X / Y / Z (voxels) | Art height | Footprint | Current shape | Shape height |
| --- | --- | ---: | --- | --- | ---: |
| `small_crate` | `2.00–14.00 / 0.00–7.15 / 1.30–9.10` | **7.15** | 12.00 × 7.80 | `box(2,0,2,14,11,14)` | 11 |
| `medium_crate` | `1.27–15.52 / 0.09–11.60 / 0.61–14.86` | **11.51** | 14.25 × 14.25 | `box(1,0,1,15,14,15)` | 14 |
| `large_crate` | `−2.50–16.50 / 1.00–19.00 / −2.50–16.50` | **18.00** | 19.00 × 19.00 | 2×2×2 cells, env. 28×19×22 | 19 |

Every shape is taller than the art it describes, and the small crate's shape is ~4.9 voxels
deeper on the south side than any geometry that exists. Investigation 1's reading that these
are *stale rather than deliberate* — the medium crate's shape still matches the superseded
`medium_crate.old` exactly — is corroborated and unchanged.

**The crate family is already a multiblock with a single root-owned inventory.**
`CrateBlock extends DecorativeMultiblockBlock implements EntityBlock`; only the root cell gets
a `CrateBlockEntity`; `anchorPosition()` maps any cell back to it. Grabby's `GrabbyRootResolver`
is written against that same seam.

---

## 3. Product requirement conflict

Option A places one crate per BlockPos. Crate *n*'s block origin is therefore at `16n` voxels,
while its art must begin at `n·h` for the stack to be continuous. The offset that would have to
be applied is:

```text
offset(n) = 16n − n·h = n · (16 − h)

small   h = 7.15  →  8.85 voxels per level
medium  h = 11.51 →  4.49 voxels per level
large   h = 18.00 → 14.00 voxels per level (32-voxel pitch; the block is 2 cells tall)
```

Because `h < 16` for every crate, this grows without bound. There is no value of any blockstate
property, and no amount of model offsetting, that makes a one-crate-per-BlockPos stack
continuous at arbitrary height — by the third small crate the required offset exceeds a full
block, and the crate's shape would have to sit entirely outside its own position.

**Option A therefore cannot satisfy the requirement**, and the gap it leaves for the small
crate (8.85 voxels) is larger than the crate itself (7.15). Investigation 1 was right that the
gap is the only thing wrong with Option A, and right that it is cosmetic in the sense of
touching no data — but "cosmetic" is not the same as "acceptable", and the product requirement
has now answered that question.

The converse is the arithmetic that makes compact stacking possible at all:

```text
2 × small  = 14.30 voxels  →  fits inside one 16-voxel cell, 1.70 to spare
3 × small  = 21.45 voxels  →  spans 2 cells
1 medium + 1 small = 18.66 →  spans 2 cells
```

Two crates fitting inside one BlockPos is exactly why a composite block entity is *required*:
Minecraft permits only one block entity per position (`ChunkAccess.blockEntities` is a
`Map<BlockPos, BlockEntity>`), so two crates in one cell cannot be two block entities.

---

## 4. Minecraft constraints

| Constraint | Verified at | Consequence |
| --- | --- | --- |
| **One BlockEntity per BlockPos.** Hard. | `ChunkAccess.blockEntities` | Two crates in one cell must share one composite BE. Non-negotiable. |
| **No world-side sub-block API.** | NeoForge 21.1.72 scan (Investigation 1) | "Multipart" in NeoForge is model selection only. Nothing to inherit. |
| **`onDestroyedByPlayer` decides removal, and runs on BOTH sides.** | `ServerPlayerGameMode.java:296–301`; `MultiPlayerGameMode.java:107–133` | **The key enabler.** Returning `false` keeps the position; `Block.destroy` and `playerDestroy` are then skipped. The client runs the same method, so a consistently-computed `false` produces no desync. |
| **The server is NOT told where on the block a break was aimed.** | `ServerPlayerGameMode.handleBlockBreakAction(BlockPos, Action, Direction, int, int):135` | Only position and face cross the wire. Layer selection for breaking must come from a **server-side raycast**, not from the packet. |
| **Server-side raycast is available and already idiomatic here.** | `player.pick(20.0D, 0.0F, false)` at 6 call sites incl. `AbstractHouseDeedItem:55`, `GrabbyDiagnosticsCommand:69`, `MiningDebugCommand:78` | Layer selection is server-authoritative by construction. |
| **`getDestroyProgress` receives the Player.** | `BlockBehaviour.java:343` | Per-layer hardness is possible. Not recommended (§10). |
| **Break can be intercepted before it starts.** | `PlayerInteractEvent.LeftClickBlock` fired at `handleBlockBreakAction:136`; `BlockEvent.BreakEvent` at `destroyBlock:248` | Both already used by `CustomBlockBreakHandler`. A second, coarser interception point if needed. |
| **Selection and collision are separate methods** that this repo merely aliases. | `DecorativeMultiblockBlock.getCollisionShape` returns `getShape(...)` | Separating them is a one-line change, and §12 needs them separated. |
| **The break crack overlay and the block outline both use `getShape`.** | `LevelRenderer.renderHitOutline:2125` | The whole stack outlines and cracks unless `RenderHighlightEvent.Block` is intercepted. Outline is fixable; the crack overlay is not, cheaply. |
| **Oversized voxel shapes are legal but fragile.** | `Shapes.create` falls back to `ArrayVoxelShape`; `ArmoireBlock` already ships them, and `GrabbyRootResolver`'s javadoc calls the armoire "the awkward case" | Entity collision only queries blocks whose *positions* intersect the entity AABB. This is the reason Architecture C uses continuation cells instead of one tall shape. |
| **Chunk-baked model data can be driven by a BlockEntity.** | `AdaptiveRoofBlockEntity.getModelData():80`, `ModelProperty` at `:20`, `requestModelDataUpdate()` at `:38/:94/:145` | Stack contents can drive geometry without a `BlockEntityRenderer` and without per-frame cost. |

---

## 5. Composite stack architecture (Architecture B)

### Data model

```java
record LogicalCrate(
        int id,                       // stable, never reused, never renumbered
        CrateVariant variant,         // SMALL | MEDIUM | LARGE
        Direction facing,
        NonNullList<ItemStack> items,
        GrabbyInstanceState grabby) { }

final class CrateStackBlockEntity extends BlockEntity {
    private final List<LogicalCrate> crates = new ArrayList<>();  // index 0 = bottom
    private int nextId = 0;                                       // monotonic
}
```

`id` is the crux and is discussed in §7. `variant` carries the height (7.15 / 11.51 / 18.00)
and the slot count (9 / 27 / 54), so a stack may mix variants freely.

### Vertical packing

```text
baseY(0) = 0
baseY(n) = baseY(n−1) + height(crate n−1)          // voxels above the stack origin
totalHeight = baseY(crates.size())
cellsRequired = max(1, ceil(totalHeight / 16))
```

A crate is **not** forbidden from crossing a cell boundary — forbidding it would reintroduce
padding, which is the gap the requirement rejects. Crossing is handled in §12.

### Why B alone is not enough

Architecture B as literally described — the *whole* stack inside one BlockPos — works only
while `totalHeight ≤ 16`, which is two small crates and nothing else. Beyond that the stack
must either wear a voxel shape taller than its own block (the armoire anti-pattern this repo
has already flagged) or stop growing. **B is therefore the single-cell special case of C, not a
competing architecture.** They converge because the composite block entity is common to both;
the only question either answers differently is where the geometry above 16 voxels lives.

---

## 6. Crate-column architecture (Architecture C) — recommended

```text
WORLD CELLS                                     LOGICAL CRATES (all in the root BE)

Y+1  [ crate_stack  PART=1 ]  continuation      ┌─ id=9  small   baseY 14.30 → 21.45  (straddles)
                                                │
Y+0  [ crate_stack  PART=0 ]  ROOT ─ BE ────────┼─ id=4  small   baseY  7.15 → 14.30
                                                └─ id=2  small   baseY  0.00 →  7.15

WORLD CELLS  ≠  LOGICAL CRATES.
The root owns all state. Continuation cells own occupancy, their slice of the shapes,
and their slice of the rendered geometry. They hold no data of their own.
```

`PART` already exists on `DecorativeMultiblockBlock` as `IntegerProperty.create("part", 0, 26)`
and already encodes a local offset that lets any cell resolve its root without a block entity.
A column reuses it directly — `PART` becomes the cell index above the root. The existing
`anchorPosition()` maths works unchanged.

The one thing the existing chassis cannot do is **vary the cell count at runtime**:
`DecorativeMultiblockBlock` computes `cells` once in its constructor from fixed min/max ranges.
A crate stack grows and shrinks. So `CrateStackBlock` extends the chassis' *conventions*
(root/part addressing, `duringMutation`, single-point teardown) but owns its own cell-set logic
driven by the block entity. That is the main structural cost of C, and it is confined to one
new block class.

### What C buys over B

| | B (one cell) | C (column) |
| --- | --- | --- |
| Max stack | 2 small crates | unbounded (policy-capped) |
| Voxel shapes | must exceed the block | always inside their own cell |
| Building above a stack | impossible without oversized shapes | naturally blocked by real occupancy |
| Entity collision correctness | inconsistent (armoire problem) | correct — every cell is a real block |

---

## 7. Independent inventory identity

**Identity is the `id` field, never the list index.** This is the single most important design
decision in the report, and it is what makes §11 safe.

- `nextId` increments on every append and is persisted. Ids are never reused and never
  renumbered, so removing a crate from the middle does not renumber the crates above it.
- A menu is opened against `(stackPos, crateId)`, not `(stackPos, index)`. If another player
  breaks a lower crate while a menu is open, the open menu still resolves to the same
  `LogicalCrate`; only its rendered height changes.
- If the crate a menu is bound to *is* removed, `stillValid` fails and vanilla closes the menu
  on the next tick, which is the correct outcome.

### Menu plumbing

No custom packet and no custom menu type are required. Contents are synchronised by
`AbstractContainerMenu` itself, so a plain `ChestMenu` over a server-side `Container` view is
enough:

```java
final class LogicalCrateContainer implements Container {
    private final CrateStackBlockEntity stack;
    private final int crateId;

    @Override public boolean stillValid(Player player) {
        return !stack.isRemoved()
            && stack.getLevel().getBlockEntity(stack.getBlockPos()) == stack
            && stack.crateById(crateId).isPresent()
            && player.distanceToSqr(Vec3.atCenterOf(stack.getBlockPos())) <= 64.0D;
    }
    // getItem/setItem/removeItem delegate into stack.crateById(crateId).items()
}

player.openMenu(new SimpleMenuProvider(
        (id, inv, p) -> ChestMenu.threeRows(id, inv, new LogicalCrateContainer(stack, crateId)),
        Component.translatable(variant.titleKey())));
```

The client builds its side of a `ChestMenu` from the `MenuType` and a `SimpleContainer` of the
matching size, so the variant's row count must be chosen from the *menu type*, exactly as
`CrateBlockEntity.createMenu` does today. Nothing new is needed there.

### Opener counting

`CrateBlockEntity` drives its chest sounds from a `ContainerOpenersCounter` keyed on the block
entity. A stack needs **one counter per logical crate**, otherwise opening crate B plays a
sound for crate A and Grabby's `IN_USE` refusal (`transportRefusal`) becomes stack-wide. Each
`LogicalCrate` therefore owns its own counter. This is a small but easily-missed detail: it is
also what lets §14 refuse to move only the crate that is actually open.

---

## 8. Placement flow

**UX:** hold a crate, aim anywhere at the stack's top crate, right-click. No sneaking.

Traced for a player holding `small_crate`, aiming at the top face of a stack at (100, 64, 100)
that already holds two small crates (`totalHeight = 14.30`, one cell):

```text
1  ServerPlayerGameMode.useItemOn
     flag1 = isSecondaryUseActive() && holding → false (not sneaking)
2  → blockstate.useItemOn(stack, level, pos, player, hand, hit)
     CrateStackBlock.useItemOn:
       held item is a crate BlockItem?          yes
       hit.getDirection() == UP?                yes
       → return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
     (consumesAction() == false, and it is not PASS_TO_DEFAULT_BLOCK_INTERACTION,
      so useWithoutItem is skipped and the menu does NOT open)
3  → stack.useOn(context)   ....................... CrateItem.useOn
4  CrateItem.useOn:
     resolve the clicked cell to its root                    → (100,64,100)
     level.getBlockEntity(root) instanceof CrateStackBlockEntity → yes
     stack.canAppend(SMALL)?                                  → height + 7.15 ≤ cap
     cellsRequired grows 1 → 2?  14.30+7.15 = 21.45 → yes
       every new cell must be replaceable and within bounds   → check (100,65,100)
     → stack.append(new LogicalCrate(nextId++, SMALL, facing, empty, worldPlaced()))
       under duringMutation(): set the new continuation cell, suppress drops
     → setChanged(); requestModelDataUpdate(); sendBlockUpdated(...)
     → play place sound, GameEvent.BLOCK_PLACE, shrink the held stack
```

**Clicking the side of a stack** goes down step 2's `hit.getDirection() != UP` branch, returns
`PASS_TO_DEFAULT_BLOCK_INTERACTION`, and the container opens as it does today. That matches the
UX the requirement asks for.

**Placing the first crate onto a non-crate block** never reaches `CrateStackBlock` at all — the
target is ordinary terrain, so `DecorativeMultiblockItem.useOn` places a *legacy* single crate,
unchanged from today. This is what makes the migration in §13 lazy.

**Facing.** Investigation 1's D4 recommendation stands and is cheaper here: an appended crate
inherits the facing of the crate below it, so a stack reads as one object.

---

## 9. Interaction flow — upper vs lower crate

Right-clicking a stack without a crate in hand must open *the crate the player is pointing at*.
Unlike breaking, this one is easy: `ServerboundUseItemOnPacket` carries a full `BlockHitResult`,
so the server already has the exact hit location.

```java
// CrateStackBlock.useWithoutItem — server side
double localVoxelY = (hit.getLocation().y - stackOriginPos.getY()) * 16.0D
                   + 16.0D * (pos.getY() - stackOriginPos.getY());   // cell-relative → stack-relative
LogicalCrate target = stack.crateAtHeight(localVoxelY);
player.openMenu(providerFor(stack, target.id()));
```

`crateAtHeight` walks the packing table and returns the crate whose `[baseY, baseY + height)`
band contains the hit, clamping to the top crate when the hit lands exactly on the stack's upper
surface (`localVoxelY == totalHeight`).

Worked example — one BlockPos holding two small crates:

```text
hit y (voxels from stack origin)   band                   result
  3.2                              [0.00, 7.15)           crate A  (id 2)
 10.0                              [7.15, 14.30)          crate B  (id 4)
 14.30 (exact top face)            clamp                  crate B  (id 4)
```

Because the hit vector arrives from the client, this is one place where the client *does*
influence the outcome — but only by choosing an aim point, which the server then validates
against its own shapes. That is the same trust model as every other `useItemOn` in the game.
The server does not have to take the client's word for which crate; it recomputes the band
itself from a location the client cannot place outside the block.

---

## 10. Breaking flow — removing a crate from the middle

This is the mechanism Investigation 1 could not resolve, and the requirement asks for it to be
proved concretely rather than hand-waved. It works. Here it is end to end.

### The two facts that make it work

1. `ServerPlayerGameMode.removeBlock` (NeoForge patch, lines 296–301) is the *only* thing that
   clears the position, and it does exactly this:

   ```java
   private boolean removeBlock(BlockPos pos, BlockState state, boolean canHarvest) {
       boolean removed = state.onDestroyedByPlayer(this.level, pos, this.player, canHarvest, ...);
       if (removed) state.getBlock().destroy(this.level, pos, state);
       return removed;
   }
   ```

   Return `false` and the block stays, `destroy` is skipped, and `playerDestroy` is skipped.
   `DecorativeMultiblockBlock` already relies on the `true` half of this contract.

2. `MultiPlayerGameMode.destroyBlock` (lines 107–133) calls **the same method** client-side and
   only removes the block locally if it returns `true`. So there is no client prediction to
   fight — provided both sides compute the same boolean.

### Making both sides agree

The boolean must not depend on the raycast, because the client's aim and the server's aim can
differ by a tick. It depends only on synchronised block-entity state:

```java
@Override
public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                   Player player, boolean willHarvest, FluidState fluid) {
    CrateStackBlockEntity stack = resolveRoot(level, pos);
    if (stack == null) return super.onDestroyedByPlayer(...);

    // Decided identically on client and server from replicated state:
    boolean positionSurvives = stack.crateCount() > 1;

    if (level instanceof ServerLevel server) {
        int targetId = aimedCrateId(server, stack, player);   // server-authoritative raycast
        stack.removeCrate(targetId, /* dropItem */ !player.hasInfiniteMaterials());
        if (positionSurvives) {
            stack.repack();                                    // §11
            stack.syncToClients();
        }
    }
    return !positionSurvives;   // false → the BlockPos survives; true → vanilla removes it
}
```

- **`crateCount() > 1`** is replicated (the BE's `getUpdateTag` carries the crate list), so the
  client reaches the same answer without doing any raycast at all.
- **`aimedCrateId`** raycasts server-side — `player.pick(reach, 0.0F, false)` — and maps the hit
  Y to a band exactly as §9 does. This is necessary because
  `handleBlockBreakAction(BlockPos, Action, Direction, int, int)` gives the server the position
  and the face and nothing else. The client never names the crate.
- When the **last** crate goes, the method returns `true` and vanilla clears the position
  through the ordinary path, which is where the existing `dismantle()` teardown belongs.

### Worked trace — breaking B out of A / B / C

```text
Stack at (100,64,100): A id=2 small [0.00,7.15)  B id=4 small [7.15,14.30)  C id=9 small [14.30,21.45)
cellsRequired = 2   → cells (100,64,100) PART=0 and (100,65,100) PART=1
Player holds left-click aimed at y ≈ 64.65  (≈ 10.4 voxels up the stack)

server  onDestroyedByPlayer at the clicked cell
        resolveRoot                      → (100,64,100)
        crateCount() = 3 > 1             → positionSurvives = true
        player.pick(...)                 → hit y 64.6500 → 10.4 vox → band [7.15,14.30) → id 4
        removeCrate(4, dropItem=true)
            popResource small_crate item                    ← exactly one item
            Containers.dropContents(B.items)                ← exactly one copy of the contents
            close any menu bound to (stackPos, 4)
        repack()                         → A id=2 [0.00,7.15)   C id=9 [7.15,14.30)
                                           totalHeight 14.30 → cellsRequired 1
                                           remove cell (100,65,100) under duringMutation()
        syncToClients()
        return false                     → position NOT cleared, destroy()/playerDestroy() skipped

client  onDestroyedByPlayer with crateCount() = 3 > 1 → returns false, block kept
        block-entity update arrives → model data invalidated → C now renders resting on A
```

A's and C's inventories were never touched — `repack()` changes `baseY` only. C keeps id 9, so
a player who had C's menu open keeps looking at C.

### What is *not* solved

- **The crack overlay covers the whole stack.** `LevelRenderer` renders destroy progress against
  `state.getShape(...)`, so all three crates crack while one is being broken. Cosmetic, and
  there is no cheap fix; a `BlockEntityRenderer`-drawn overlay would be a disproportionate
  amount of machinery for it.
- **The selection outline covers the whole stack** unless `RenderHighlightEvent.Block` is
  intercepted to draw only the aimed crate's box. That one *is* worth doing (§12) and is
  client-only.
- **Break speed is uniform.** `getDestroyProgress` does receive the `Player`, so per-crate
  hardness is possible, but it would need the raycast on both sides and gains nothing. Use one
  hardness for the whole family.

---

## 11. Repacking algorithm

```java
void repack() {
    int cursor = 0;                                  // voxels above the stack origin
    for (LogicalCrate c : crates) { c.setBaseY(cursor); cursor += c.height(); }
    totalHeight = cursor;

    int want = Math.max(1, Mth.ceil(totalHeight / 16.0));
    int have = currentCellCount();
    duringMutation(() -> {
        for (int k = have - 1; k >= want; k--) level.setBlock(cell(k), AIR, UPDATE_SUPPRESS_DROPS);
        for (int k = have; k < want;  k++) level.setBlock(cell(k), stackState(k), UPDATE_CLIENTS);
    });
    setChanged();
    requestModelDataUpdate();
    level.sendBlockUpdated(rootPos, state, state, Block.UPDATE_CLIENTS);
    level.updateNeighbourForOutputSignal(rootPos, this);   // comparators
}
```

Three properties matter, and all three follow from ids being stable:

- **Inventories are untouched.** `repack` writes `baseY` and nothing else. No item is copied,
  moved, or reallocated, so there is no code path on which an item can be lost or duplicated.
- **Open menus survive.** They are bound to ids, and ids do not shift.
- **`duringMutation` is mandatory** when removing a shed continuation cell, otherwise
  `onRemove`/`neighborChanged` sees a broken structure and calls `dismantle`, which would drop
  the entire stack's contents. `DecorativeMultiblockBlock` already provides exactly this guard
  and already uses it for the same reason.

Growing the column has one failure mode worth naming: if the cell above is occupied, `append`
must fail *before* mutating anything, which is why §8 checks `cellsRequired` before calling
`append`.

---

## 12. Rendering and collision

### Rendering — reuse the authored models, chunk-baked

The composition is `AdaptiveRoofBakedModel`'s structure with `DecorativeOffsetModel`'s quad
maths:

```java
public final class CrateStackBakedModel extends BakedModelWrapper<BakedModel> {
    public static final ModelProperty<StackLayout> LAYOUT = new ModelProperty<>();

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                                    ModelData data, RenderType type) {
        StackLayout layout = data.get(LAYOUT);
        if (layout == null) return List.of();
        List<BakedQuad> out = new ArrayList<>();
        for (Slice slice : layout.slicesFor(state.getValue(PART))) {
            BakedModel crate = bakedModelFor(slice.variant(), slice.facing());  // the existing JSON
            for (BakedQuad q : crate.getQuads(state, side, rand, ModelData.EMPTY, type)) {
                out.add(shiftQuad(q, 0.0F, slice.offsetYBlocks(), 0.0F));       // DecorativeOffsetModel
            }
        }
        return out;
    }
}
```

- **The crate JSON models are reused as baked**, resolved from `ModelManager` by their existing
  `britannia_mod:block/new_assets/{small,medium,large}_crate` locations. Nothing is redrawn and
  no new art is required.
- `shiftQuad` already exists and already does exactly this vertex translation
  (`DecorativeOffsetModel:120–145`); it should be lifted into a shared utility rather than
  copied, since a third caller now needs it.
- Model data comes from the block entity via `getModelData()`, invalidated by
  `requestModelDataUpdate()` + `sendBlockUpdated`, precisely as `AdaptiveRoofBlockEntity` does
  at lines 38, 80, 94 and 145. This is **chunk-baked**, not a per-frame `BlockEntityRenderer`,
  so a warehouse of stacks costs no more than a warehouse of ordinary blocks.

**Crates that straddle a cell boundary** are rendered whole by the cell that contains their
*origin*, overflowing up to `height` voxels into the cell above. That cell is part of the same
stack, so nothing foreign is overdrawn. The cost is the known culling artefact: geometry can
disappear when the owning chunk *section* is frustum-culled but the neighbour is drawn. This
already affects the large crate, which spans −2.5 → 16.5 today. It bites only for stacks
crossing a `y % 16 == 0` plane. The alternative — clipping quads at the boundary so each cell
draws only its own slice — means splitting arbitrary geometry and is not worth it.

### Collision and selection must now be separated

They are the same shape today only because `DecorativeMultiblockBlock.getCollisionShape`
returns `getShape(...)`. The stack needs them to differ:

| | Shape | Why |
| --- | --- | --- |
| **Collision** (`getCollisionShape`) | one merged box per cell: the union of every crate slice overlapping that cell, clamped to `[0,16]` | A player should walk on top of a stack and not fall between crates. Merged means no internal surfaces to catch on. |
| **Selection / raycast** (`getShape`) | one box **per crate slice**, not merged | §9 and §10 both resolve the aimed crate from the hit Y. Merging would still work (the Y band lookup is arithmetic, not shape identity) but keeping the boxes separate makes the outline honest and lets `RenderHighlightEvent` highlight one crate. |

Both are strictly inside their own cell, so no oversized shapes are introduced anywhere —
this is the concrete payoff of C over B, and it keeps the crate family clear of the armoire
anti-pattern the repo has already flagged.

Client-side, `RenderHighlightEvent.Block` should be intercepted to draw only the aimed crate's
box. Without it the player sees the whole stack outlined and cannot tell which crate they are
about to open or break — which would undermine the entire feature.

---

## 13. Persistence and migration

**Existing crates must be assumed to exist in live worlds with contents** — crates landed in
`6c9761dd` and Grabby enrolment in `40fa27d2`, both ancestors of `origin/patch-18`. They appear
in none of the 20 structure NBT files, so there are no world-generated crates.

### Lazy promotion, and no demotion

```text
place a crate on terrain      →  legacy small_crate + CrateBlockEntity   (UNCHANGED from today)
place a crate on that crate   →  promote: crate_stack + CrateStackBlockEntity
                                 old CrateBlockEntity.items  → LogicalCrate id 0
                                 the crate being placed      → LogicalCrate id 1
break down to one crate       →  STAYS a crate_stack
```

The promotion is a single-tick, single-chunk operation guarded by the existing mutation guard:

```java
duringMutation(() -> {
    CrateBlockEntity old = (CrateBlockEntity) level.getBlockEntity(pos);
    NonNullList<ItemStack> carried = old.copyItems();          // read before anything is written
    GrabbyInstanceState grabby = old.grabbyState();
    level.setBlock(pos, stackState(0), UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS);
    CrateStackBlockEntity stack = (CrateStackBlockEntity) level.getBlockEntity(pos);
    stack.adopt(variantOf(oldState), oldState.getValue(FACING), carried, grabby);
    stack.append(newCrate);
    return true;
});
```

`UPDATE_SUPPRESS_DROPS` plus `duringMutation` together are what stop `CrateBlock.onRemove` from
firing `dismantle` and spilling the old inventory on the floor. Both mechanisms already exist
and are already used for exactly this purpose during placement rollback.

**Why no demotion.** It is tempting to convert a one-crate stack back to a legacy crate for
tidiness. Do not. Every conversion is a moment when an inventory is copied between two
representations, and that is the only place in this design where items can be lost. Promotion-
only means **at most one conversion in a stack's entire lifetime**; promotion-plus-demotion
means an unbounded number as a player stacks and unstacks. The tidiness is not worth the
exposure. The cost of not demoting is that a lone crate that was once stacked is a different
block from a freshly placed one — invisible to players provided `getCloneItemStack`,
`getDrops`, the loot table and Grabby all answer for the *aimed* logical crate.

### What is safe by construction

| Change | Migration | Why |
| --- | --- | --- |
| Blockers 1 & 2 fixes | None | Behaviour only; no persisted data. |
| Shape corrections | None | Shapes are computed at runtime, never persisted. |
| New `crate_stack` block + BE | None for existing worlds | Nothing becomes a stack until a player stacks it. |
| Existing `CrateBlockEntity` | **Untouched** | Its class, its NBT and its block IDs are unchanged. A world that never stacks a crate never executes one line of new persistence code. |
| Large crate footprint | Deferred | Out of scope here — see §16 and Investigation 1's D2. |

This is the strongest argument for lazy promotion over a wholesale conversion: **the migration
risk to existing worlds is zero until the player opts in by stacking**, and even then it is one
guarded copy in one tick in one chunk.

---

## 14. Grabby Hands

`GrabbyRootResolver.resolveRoot` returns a `BlockPos` and nothing else, and every Grabby
transaction is written against "the root". A stack breaks that assumption: the root position is
now shared by several independently movable objects.

**Recommended behaviour — one visible crate is one movable object**, preserving today's mental
model:

| Gesture | Behaviour |
| --- | --- |
| Grabby-pick a crate in a stack | Picks up **the aimed crate only**. The crates above repack down onto the one below, exactly as in §11. |
| Grabby-place a crate onto a stack | Appends, via the same `CrateItem.useOn` path as hand placement. |
| Whole-stack move | Not offered initially. It is a second gesture and a second set of failure modes; defer it until the single-crate case is proven in play. |

**The change this needs.** Grabby's target must gain a layer dimension — a
`GrabbySubTarget(BlockPos root, int crateId)` threaded through the pickup, placement and
destruction transactions in place of the bare root. This is the largest integration cost in the
report outside rendering, because it touches `GrabbyPickupTransaction`,
`GrabbyPlacementTransaction`, `GrabbyDestructionTransaction` and the diagnostics.

**Serialising one crate independently** is already almost free: `GrabbyPortableState.writePortableState`
on `CrateBlockEntity` saves the whole block entity wholesale into the item. For a stack it must
save *one* `LogicalCrate` instead — its variant, facing and items — so the item that comes back
is a `small_crate` item carrying only that crate's contents. `detachForTransport` clears that
one crate's items, not the stack's.

**`transportRefusal` must become per-crate.** Today it refuses when `openersCounter > 0` or when
any slot holds a nested container. With one counter per logical crate (§7) both checks scope
naturally to the aimed crate, so an open crate B does not block picking up crate A.

**The trap to avoid**, named explicitly because the shared block entity invites it: never move
the whole stack because the crates share a `CrateStackBlockEntity`. The block entity is an
implementation detail; the player's unit of interaction is the visible crate.

---

## 15. Automation — hoppers and comparators

Investigation 1 treated automation as a casualty of composite storage. It need not be.

### Hoppers

`HopperBlockEntity.getContainerAt` fetches a `Container` from the block entity at a position.
The stack BE implements `Container` as a **flattened facade** over all logical crates:

```text
global slot index = Σ(sizes of crates below) + local slot
```

and implements `WorldlyContainer` to make the exposed range depend on the face:

| Face | `getSlotsForFace` returns | Effect |
| --- | --- | --- |
| `DOWN` | the **bottom** crate's slot range | A hopper underneath pulls from the bottom crate. |
| `UP` | the **top** crate's slot range | A hopper above pushes into the top crate. |
| Horizontal | the range of the crate whose `[baseY, baseY+height)` band contains the hopper's own Y within that cell; the whole stack if ambiguous | A side hopper addresses the crate it is physically next to. |

`canPlaceItemThroughFace` / `canTakeItemThroughFace` enforce it. The horizontal case is the only
one with real ambiguity, because a hopper occupies a whole cell that may span two crates —
resolve it to the crate with the greater overlap, and document the rule rather than trying to
be clever.

The repo uses `WorldlyContainer` nowhere today, so this is new code, but it is a vanilla
interface designed for exactly this problem (furnaces use it to separate fuel from input).

### Comparators

`CrateBlock.getAnalogOutputSignal` currently delegates to
`AbstractContainerMenu.getRedstoneSignalFromBlockEntity`. For a stack, **report the fullness of
the whole stack** — all logical crates' slots summed. It is predictable, it needs no extra
state, and per-crate comparator readings are not expressible anyway: one BlockPos emits one
signal.

Automation remains subordinate to inventory safety and independent usability, as the priority
order requires — but on this design it does not have to be given up.

---

## 16. Large crate

**No change is proposed here, and the 2×2×2 → 1×1×2 question stays where Investigation 1 left
it (D2).**

Its geometry argues against packing it into the column at all:

- **18.00 voxels tall** — every large crate straddles a cell boundary no matter where the stack
  starts, so it is always in the culling-artefact case.
- **19.00 × 19.00 footprint** — wider than a BlockPos in both horizontal axes. A column is a
  1×1 vertical run of cells; a block that overhangs its neighbours cannot honestly occupy one.
- **8 cells reserved today** for an object measuring 19×18×19.

**Recommendation:**

1. The large crate **remains a conventional `DecorativeMultiblockBlock`** and does not become a
   `LogicalCrate` variant in the first implementation.
2. It **may act as a stack foundation** — `CrateItem.mayUseSupport` accepts a large crate's top,
   so a small/medium column can begin on top of one. That is a two-line allowance and gives the
   warehouse look most of its value.
3. Small and medium crates **do not stack on top of a large crate's overhang**, only over its
   root column, until its footprint is settled.
4. Revisit large-crate participation **after** D2 is decided. If it becomes 1×1×2 with a
   16-wide footprint, it can join the column as a variant with height 18.00 and no further
   architecture change — the packing maths already handles it.

---

## 17. Architecture comparison

**A** — one crate per BlockPos (Investigation 1's Option A).
**B** — composite `CrateStackBlockEntity`, whole stack in one BlockPos.
**C** — crate column: composite BE at the root, continuation cells above. **Recommended.**
**D** — crate *entities* rather than blocks (discovered during this investigation; see below).

| Criterion | A: BlockPos crates | B: Composite | C: Crate column | D: Entities |
| --- | --- | --- | --- | --- |
| **Physical contact** | ✗ gaps of 8.85 / 4.49 / 14.00 vox | ✓ exact | ✓ exact | ✓ exact, arbitrary |
| **Independent inventories** | ✓ free (already true) | ✓ via logical crates | ✓ via logical crates | ✓ free per entity |
| **Natural placement** | ✓ after the 2 blocker fixes | ✓ same fixes + append | ✓ same fixes + append | ~ custom entirely |
| **Individual breaking** | ✓ vanilla, free | ✓ `onDestroyedByPlayer` + raycast | ✓ `onDestroyedByPlayer` + raycast | ✓ `Entity.hurt`, free |
| **Tall stacks** | ✓ unlimited (with gaps) | ✗ 2 small crates max | ✓ unlimited, policy-capped | ✓ unlimited |
| **Mixed crates** | ✓ free | ✓ packing table | ✓ packing table | ✓ free |
| **Existing-world safety** | ✓ nothing persisted changes | ~ one guarded promotion | ~ one guarded promotion | ✗ crates would have to become entities |
| **Grabby Hands** | ✓ unchanged | ~ needs a layer dimension | ~ needs a layer dimension | ✗ Grabby is block-oriented throughout |
| **Hopper support** | ✓ vanilla per position | ~ `WorldlyContainer` facade | ~ `WorldlyContainer` facade | ✗ hoppers cannot see entities |
| **Comparators** | ✓ per crate | ~ whole-stack signal | ~ whole-stack signal | ✗ none |
| **Multiplayer safety** | ✓ vanilla | ✓ ids + server raycast | ✓ ids + server raycast | ✓ entities are server-authoritative |
| **Complexity** | Low — 2 files | High | High — 1 new block, 1 BE, 1 model, 1 break hook | Very high — new subsystem |
| **Maintainability** | ✓ vanilla semantics throughout | ~ one bespoke subsystem | ~ one bespoke subsystem, but on the existing chassis | ✗ parallel to every existing pattern |

### Why B and C converge

They share the composite block entity, the logical-crate model, the id scheme, the menu
plumbing, the break interception, the repacking algorithm and the renderer. **The only thing
they answer differently is where geometry above 16 voxels lives**: B puts it in an oversized
voxel shape hanging out of its own BlockPos, C puts it in real continuation cells. Since B's
answer caps the feature at two small crates and drags in the anti-pattern this repo has already
identified, C is simply B done properly. They are one architecture with a cell-spanning policy,
not two.

### Architecture D — crate entities

Worth stating because it genuinely solves the geometry problem outright: an entity has a real
position, so crates could rest at exact heights with no packing maths, no continuation cells and
no quad offsetting, and per-crate identity, interaction and breaking would all be free.

It is rejected on integration cost, not on elegance. Hoppers and comparators cannot address
entities at all — that is a hard loss, not a design choice. Grabby Hands, `DecorativeMultiblockBlock`,
`GrabbyRootResolver` and the placement item are block-oriented from top to bottom, so crates
would leave every shared system they currently sit in. And existing crates in live worlds would
need converting from blocks to entities, which is the one migration this report is most
determined to avoid.

---

## 18. Recommendation

**Adopt Architecture C, and implement Investigation 1's two blocker fixes and three shape
corrections first as their own shippable step.**

The reasoning, against the stated priority order:

1. *No inventory loss or duplication* — C's exposure is one guarded promotion per stack
   lifetime (§13) and a repack that provably never touches items (§11). Ids rather than indices
   remove the class of bug where an open menu silently retargets.
2. *Visible crates actually rest on one another* — C is the only candidate that achieves this at
   arbitrary height without oversized shapes. A cannot, at any height. B stops at two crates.
3. *Each visible crate behaves as an independent container* — logical crates with their own
   items, their own opener counters and their own menus.
4. *Natural placement* — the same two fixes A needs, plus an append path.
5. *Individual breaking/removal* — proved in §10 against the decompiled sources, both sides.
6. *Existing-world safety* — nothing converts until a player stacks.
7. *Dedicated-server correctness* — layer selection is a server raycast; the survives/does-not
   boolean is derived from replicated state so both sides agree without trusting the client.
8. *Preserve existing visual models* — the authored JSON is reused as baked, offset per quad.
9. *Grabby compatibility* — preserved in meaning ("one visible crate is one movable object") at
   the cost of a layer dimension through the transactions.
10. *Maintainable implementation* — one bespoke subsystem, built on the existing multiblock
    chassis and two existing rendering patterns rather than beside them.
11. *Hopper/comparator compatibility* — retained via `WorldlyContainer`, better than
    Investigation 1 assumed.
12. *Tall warehouse stacks* — unbounded, capped only by policy.

**Do not read this as Investigation 1 having been wrong.** Its Option A is the correct answer to
the question it was asked, and its blocker analysis is load-bearing for C as well — C cannot
place a crate at all until both blockers are fixed. What changed is the requirement, and with it
the weight on priority 2.

**The honest cost.** C is roughly eight milestones against Option A's two files. If the schedule
cannot absorb that, M1 alone (the blockers and shapes) ships working stacking with gaps and is a
strict prerequisite for C, so nothing is wasted by doing it first.

---

## 19. Implementation milestones

Each milestone is independently shippable and independently testable.

| # | Milestone | Content | Gate |
| --- | --- | --- | --- |
| **M1** | **Placement blockers and geometry** | `CrateBlock.useItemOn` → `SKIP_DEFAULT_BLOCK_INTERACTION`; `CrateItem.mayUseSupport`; correct the three voxel shapes. **This is Investigation 1's Option A and ships stacking-with-gaps on its own.** | — |
| **M2** | **Spike: prove the break contract in-game** | A throwaway block that returns `false` from `onDestroyedByPlayer` on alternate hits, confirming client and server stay in step and no ghost block appears. Retires the last residual risk before real code. | M1 |
| **M3** | **`CrateStackBlockEntity` + `LogicalCrate`** | Data model, stable ids, NBT round-trip, packing table, `repack()`. No world interaction yet — pure unit-testable core. | M2 |
| **M4** | **Block, shapes, and rendering** | `CrateStackBlock` with per-cell collision (merged) and selection (per slice) shapes; `CrateStackBakedModel`; `shiftQuad` lifted to a shared utility; `RenderHighlightEvent` per-crate outline. | M3 |
| **M5** | **Interaction and menus** | Hit-Y → crate band; `LogicalCrateContainer`; per-crate opener counters; menus bound to ids. | M4 |
| **M6** | **Placement, append, and promotion** | `CrateItem.useOn` append path; lazy promotion from `CrateBlockEntity`; column growth with pre-flight cell checks. | M5 |
| **M7** | **Breaking and repacking** | `onDestroyedByPlayer` layer removal; server raycast; drop-exactly-once; column shrink; menu invalidation. | M6 |
| **M8** | **Grabby, hoppers, comparators** | `GrabbySubTarget` through the transactions; per-crate portable state and refusal; `WorldlyContainer` faces; whole-stack comparator. | M7 |

**Testing that must exist before M7 is called done** — this is where inventory loss would hide:
exactly-one-drop on a middle break under GameTest; a stack of three where crate C's menu is open
while B is broken; save/reload of a mixed stack; two players breaking different crates in the
same tick; and a promotion interrupted by a chunk unload.

---

## 20. Decisions required

Genuine product decisions. Everything else in this report is an engineering call I have taken.

### D1 — Do we accept the cost of true stacking? *(the only blocking decision)*

Architecture C is ~8 milestones and one new bespoke subsystem, against Investigation 1's
Option A at two files. The report recommends C because the product requirement rules Option A
out on its primary goal. **If the gap turns out to be tolerable once seen in-game, A is
dramatically cheaper and safer** — and M1 delivers exactly that view.

**Recommendation: ship M1, look at a real stack, then commit to M2–M8.** M1 is a prerequisite
for C either way, so this costs nothing but sequencing.

### D2 — Maximum stack height

The column is unbounded in principle. It needs a cap for sanity — collision, chunk-section
straddling, and the size of one block entity's NBT all degrade slowly with height.

- **(a) 4 cells / 64 voxels** — about 8 small crates. Generous for a warehouse, bounded.
- **(b) 8 crates regardless of variant** — simpler to explain, uneven physical height.
- **(c) Uncapped** — invites a 60-crate tower in one block entity.

**Recommendation: (a).** Cell-based caps keep the failure modes bounded in the units that
actually matter.

### D3 — Does the stack collapse when its supporting block is removed?

Investigation 1's D3 recommended a floating crate, consistent with the rest of the mod's
furniture. A packed stack makes the floating case more visible.

**Recommendation: unchanged — leave it floating.** It is the only option with zero
inventory-loss surface. Falling stacks would need a block-entity-carrying falling entity, which
is a whole subsystem and a duplication risk.

### D4 — Grabby: single crate or whole stack?

**Recommendation: single crate only, initially** (§14). Whole-stack transport is a second
gesture with its own failure modes; add it later if players ask.

### D5 — Horizontal hopper rule

The side-hopper case is genuinely ambiguous when a hopper's cell spans two crates.

**Recommendation: greater overlap wins, documented plainly.** The alternative — exposing the
whole stack to side hoppers — is simpler but makes automation non-deterministic when a stack
repacks.

### Deferred, not decided here

- **Large-crate footprint (Investigation 1's D2).** Untouched, per the brief. §16 recommends the
  large crate stay a conventional multiblock and act only as a foundation until it is settled.
- **Vertical spacing art (Investigation 1's D1).** Superseded: C gives exact contact, so the
  question disappears. If the artwork wants a deliberate seam between crates it becomes a
  per-variant constant in the packing table, which is a one-line change.

---

*Investigated against `patch-18` at `d1c7f433`. No source files were modified and no
experimental code was written: the break-pipeline uncertainty that would have justified a
proof of concept was resolved by reading the NeoForge-patched decompilation of both
`ServerPlayerGameMode` and `MultiPlayerGameMode`, which agree that `onDestroyedByPlayer`
governs removal on both sides. M2 exists to confirm that in-game before real code is built on
it.*
