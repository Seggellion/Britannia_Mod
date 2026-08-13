# Grabby Hands

Adventure-mode players can place, pick up, and axe-destroy approved furniture, containers, and
stateful objects — while permanent map and admin decoration stays immovable, and every existing
object system keeps deciding what its objects actually *do*.

The governing rule is **generic transport, specialized behavior**. Grabby Hands moves objects
between the world and an inventory. It does not know what a chair is for, what wine tastes like, or
who may open a chest.

---

## 1. Architecture

### The whole thing hangs off one event

`GrabbyInteractionHandler` subscribes to `PlayerInteractEvent.RightClickBlock` at
`EventPriority.HIGHEST`. That is the entire integration surface.

This works because of where vanilla fires that event: it is the **first statement** of
`ServerPlayerGameMode.useItemOn` — before the spectator branch, before
`BlockState.useItemOn`/`useWithoutItem`, and before `ItemStack.useOn`, which is the only place
Adventure mode's `mayBuild` gate lives. The client sends the packet unconditionally.

The consequences are worth stating plainly, because they are the reason the design is safe:

- **No mixin.** No game-mode switching. No `mayBuild` relaxation. No mixin on the break pipeline.
- **Adventure restrictions are untouched.** An enrolled chair is *still* refused by the ordinary
  placement path — enrollment grants no general building rights. `GrabbyAdventureGameTests`
  asserts exactly that.
- **Axes gain no breaking authority.** Grabby Hands does not participate in breaking at all, so
  `CityGameModeHandler`, `SurvivalZoneHandler`, tree/log/leaf harvesting, and city protection are
  all completely unaffected.

`GrabbyAuthorizationRegressionTest` enforces these as source-scan contracts: the package may not
reference `setGameMode`, `GameType`, `mayBuild`, `BlockEvent.BreakEvent`, or `Level.setBlock`.

### Gesture dispatch

`GrabbyGesture` decides what a right-click means from hand contents and posture alone:

| Gesture | Meaning |
|---|---|
| Interior Decorator tool in either hand | **Defer entirely** — that tool owns its own right-click language |
| Sneak + right-click, **both hands empty** | Pick up |
| Any recognized axe in the main hand | Ask whether to destroy (R-2.11) |
| Holding an enrolled item | Place |
| Anything else | Fall through to existing behavior |

Both hands must be empty for pickup because vanilla only suppresses block use when a sneaking
player holds *something* — allowing an occupied offhand would make one gesture mean two things.

### Transactions

Three transaction classes own all mutation. Each is server-authoritative, each takes a
`(levelIdentity, pos)` claim from `GrabbyMutationGuard` so two players cannot consume one object,
and each returns an outcome enum rather than a boolean.

**`GrabbyPickupTransaction`** — the ordering *is* the design:

```
claim → validate → capture portable stack → room pre-check
      → detach payload → remove block → GRAB cue → insert → STOW cue
```

The room pre-check happens **before** removal, so a full inventory cannot delete an object. If
insertion still fails after removal, the object drops at the player's feet and the outcome is
`DROPPED_AT_FEET` — with **no stow cue**, because nothing was stowed.

**`GrabbyPlacementTransaction`** — dual path. Enrolled blocks go through `BlockItem.place`, which
has no permission check of its own but supplies facing, `canSurvive`, `isUnobstructed` (this is what
makes furniture stacking and intersection rejection work), `setPlacedBy`, the native place sound,
and item consumption **only on success**. Non-block decorative items go onto the generic host block
instead. Provenance stamping is fail-closed: if it cannot be written, the outcome is
`PLACED_WITHOUT_PROVENANCE`.

**`GrabbyDestructionTransaction`** — `refusalFor(...)` is shared by both the confirmation prompt and
the execution, and is re-run *inside* the claim, because the world can move between the question and
the answer.

---

## 2. Eligibility vs. instance mobility

These are separate concepts and conflating them is the main way this system could be made unsafe.

| Question | Answered by | Storage |
|---|---|---|
| May this **type** participate at all? | Block/item tags | `data/britannia_mod/tags/…` |
| May this **one placed block** be moved? | Provenance | `GrabbyState` NBT on the block entity |

A chair *type* being enrolled says nothing about whether the chair in the tavern may be carried off.

### Provenance, and why there is no migration

`GrabbyInstanceState` is a record of `(schemaVersion, provenance, Optional<UUID> placerUuid,
placedAtGameTime)` stored under the NBT key `GrabbyState`.

`GrabbyProvenance` has two values, and **absence decodes to `WORLD`**:

- `read()` returns `worldPlaced()` for a missing, malformed, or unsupported-version tag;
- `byNameOrWorld` fails closed on an unrecognized name;
- `write()` *removes* the key for `WORLD`, so the common case costs no bytes.

This is why **every pre-existing object in every existing save is protected with zero migration**.
A map-placed chair has no tag, so it reads as `WORLD`, so it cannot be moved by a player. Only
objects positively marked at placement time are mobile.

`toClientTag()` omits the placer UUID — the client never needs it and it is not broadcast.

### Provenance is not an ACL

The placer UUID is recorded, but **no code path reads it to make a use decision**, and
`GrabbyAuthorizationRegressionTest` enforces that. Anyone may sit on a chair you placed, open a
container you placed (subject to that container's own rules), or pick it up again.

This is deliberate. Placement provenance answers "was this put here by a player?", not "whose is
it?". House and city ownership are already solved by `StructureRegionManager`, and `GrabbyPolicy`
delegates to it rather than inventing a second authority.

---

## 3. Policy

`GrabbyPolicy.mayMutate` is a pure function of primitives, so it unit-tests without a level or a
player:

```java
mayMutate(boolean grabbyManaged, boolean creativeMode, int permissionLevel,
          boolean insideForeignStructure, GrabbyMutationReason reason)
```

The rules, in order:

1. A `null` reason is refused.
2. `SYSTEM_MUTATION` is allowed.
3. **Environmental reasons are always refused** — `EXPLOSION`, `FLUID`, `PISTON`. Player possessions
   left in the world are not collateral for a creeper, and a piston is never a transport mechanism.
4. `ADMIN_REMOVE` requires an administrator.
5. **Not player-placed → administrator only.** Staff may rearrange scenery; players may not,
   whatever the type tag says.
6. **Inside somebody else's structure → administrator only.** A player-placed object in another
   player's house follows that house's existing rules.
7. Otherwise allowed.

`mayPlace` is the same shape, minus provenance. An administrator is `creativeMode ||
permissionLevel >= 2`.

Cities are deliberately **not** protected from littering — that was an explicit owner decision.

---

## 4. Enrolled content

Four tags define participation.

| Tag | Purpose | Count |
|---|---|---|
| `britannia_mod:grabby_movable` (block) | May be picked up | 32 |
| `britannia_mod:grabby_axe_destroyable` (block) | May be axe-destroyed | 32 |
| `britannia_mod:grabby_deed_placed` (block) | **Hard veto** — never either | 6 |
| `britannia_mod:grabby_placeable_items` (item) | Loose items placeable on the host block | 30 |

The movable and destroyable sets are currently identical, but they are separate tags because the
concepts are separate and a future object may want one without the other.

**The deed veto is checked first**, inside both `movableType` and `axeDestroyableType`:

```java
!deedPlaced(state) && state.is(ModTags.Blocks.GRABBY_MOVABLE)
```

So adding a block to both `grabby_movable` and `grabby_deed_placed` leaves it immovable. Anything
placed with a deed — beds, chandeliers — is out of scope by owner decision, since deeds may only be
placed inside your own house in the first place.

### The 32 enrolled blocks

**Seating and tables (12)** — `wooden_chair`, `straw_chair`, `chair_trinsic`, `chair_vesper`,
`stool`, `footstool`, `bench`, `wooden_throne`, `magincia_style_throne`, `yew_table`, `small_table`,
`counter`

**Wine (4)** — `wine_bottle_green`, `wine_bottle_brown`, `wine_bottle_blue`, `wine_bottle_clear`

**Lighting (8)** — `candle`, `candelabra_small`, `brazier_small`, `torch_standing`, `wall_sconce`,
`torch_wall`, `lamp_post_regular`, `lamp_post_fancy`

**Containers (8)** — `chest_wooden`, `chest_metal`, `chest_metal_bronze`, `armoire_brown`,
`armoire_red`, `chest_of_drawers_brown`, `chest_of_drawers_red`, `britannia_lockable_chest`

---

## 5. How to enroll a new practical item

### A native block

1. **Check the preconditions.** `GrabbyEnrollmentPreconditionTest` will fail the build if any are
   violated, but check first:
   - its `BlockItem` must be a plain `BlockItem`, **not** `RaisedBlockItem` — that subclass places
     at an offset and silently mis-places through this path (this is why `candelabra_tall` and
     `villa_lamp_post` are excluded);
   - it must not be deed-placed;
   - it must declare a real `SoundType`, or destruction will sound wrong.
2. **Add the block id** to `grabby_movable.json` and/or `grabby_axe_destroyable.json`.
3. **If it has a block entity, implement `GrabbyProvenanceHolder`** — a field, `grabbyState()`,
   `setGrabbyState()`, plus `grabbyState.write(tag)` in `saveAdditional`,
   `GrabbyInstanceState.read(tag)` in `loadAdditional`, and `writeClient(tag)` in `getUpdateTag`.
   Without this the block cannot be marked player-placed and will stay protected.
4. **If it holds state**, see §6.
5. **Run the enrollment tests** — `GrabbyEnrollmentTagTest` proves the tag binds at runtime,
   `GrabbyEnrollmentPolicyTest` proves the set is coherent.

A block with **no** block entity needs no adapter, but also cannot carry provenance — so it can only
be enrolled if being permanently protected is acceptable.

### A loose decorative item

Add its item id to `grabby_placeable_items.json`. It will be placed on the generic
`grabby_placed_item` host block, which stores the whole `ItemStack` as an opaque payload and renders
it. `GrabbyEligibility.hostablePlainItem` deliberately excludes `BlockItem`s — those should be
enrolled as blocks instead.

---

## 6. Adapter responsibilities

Adapters are how an existing object tells the transport layer what it needs, without the transport
layer knowing anything about the object. Every one of them carries **opaque payloads** — whole
`ItemStack`s and whole `saveAdditional` tags — rather than enumerating fields.

| Interface | Implement when | Contract |
|---|---|---|
| `GrabbyProvenanceHolder` | Always, for any enrolled block entity | `grabbyState()` / `setGrabbyState()`, persisted in `saveAdditional` / `loadAdditional` |
| `GrabbyDetachable` | The object must release something before removal | `detachForTransport()` returns whether anything was detached |
| `GrabbyPayloadHolder extends GrabbyDetachable` | The object *is* a wrapper around one stack | `grabbyPayload()`, `setGrabbyPayload()`, `detachGrabbyPayload()`; default `detachForTransport()` provided |
| `GrabbyPortableState extends GrabbyDetachable` | The object carries state that must survive transport | `writePortableState()` / `restorePortableState()`, plus the three optional refusal hooks below |

`GrabbyPortableState`'s optional hooks all have safe defaults:

- `transportRefusal()` → `Optional.empty()` — return `IN_USE` when somebody has the container open,
  or `NESTED_CONTAINER` to refuse container-inside-container;
- `occupiedSlotCount()` → `0` — used only to word the destruction prompt;
- `securedAgainstDestruction()` → `false` — see §8.

`GrabbyWorld` and `GrabbyActor` are the seams that keep transactions testable: every world mutation
and every player interaction goes through them, so the transactions can be driven by
`FakeGrabbyWorld` / `FakeGrabbyActor` in unit tests and by the real implementations in GameTests.

---

## 7. Wine state-preservation contract

Wine bottles were the sharpest state-loss risk in the design, and the naive implementation lost
state. The contract now is:

> **The bottle stores the entire `ItemStack` it was placed from, and hands that exact stack back.**

- `WineBottleBlock.setPlacedBy` calls `be.setOriginStack(stack)`, storing `stack.copyWithCount(1)`.
- `WineBottleBlockEntity.portableStack(...)` returns `originStack.copy()` when present, and only
  falls back to rebuilding from `WineData` when the origin is unknown (a bottle placed before this
  system existed, or placed in Creative).
- `WineBottleBlock.getCloneItemStack` delegates to the same method, so pick-block agrees.
- `TAG_ORIGIN_STACK` is persisted in `saveAdditional` but **removed from `getUpdateTag`** — the
  client has no need for it.

Because the whole stack is carried, all six `WineData` fields survive, **and so does everything
else** — custom names, enchantments, any component added later. No Grabby code names a wine field;
`GrabbyWineContractTest` enforces that by scanning the sources.

---

## 8. Container semantics

### Pickup

`writePortableState` copies the container's whole `saveAdditional` tag onto the portable item, then
`detachForTransport()` clears the contents **before** the block is removed. That ordering is what
prevents the double-drop: `onRemove` finds nothing left to spill, so the contents exist in exactly
one place — the item in your hand.

`restorePortableState` is deliberately narrower than the write: detaching only cleared contents, so
restoring only restores contents.

Refusals: a container somebody currently has open returns `IN_USE`; a container holding another
container returns `NESTED_CONTAINER`.

### Destruction

The reverse. A host's payload **is** the object, so `detachObjectPayloadForDestruction` consumes it.
A container's contents are **not** the object, so they are deliberately left attached and vanilla's
`onRemove` spills them — exactly once, asserted by
`destroyingAFilledContainerSpillsItsContentsExactlyOnce` against real item entities.

The container itself is never returned as an intact item. Destruction consumes; pickup transports.

### Locked containers

`securedAgainstDestruction()` returns true for a locked `britannia_lockable_chest`, producing the
`SECURED` outcome. Two things about that outcome matter:

1. It **does not consume the interaction**, so `LockpickingEventHandler` still gets its turn.
   Holding an axe does not silently disable lockpicking.
2. Carrying a locked chest away is **still allowed** — an explicit owner decision. The lock protects
   the contents, not the box.

Chopping a locked chest open would spill its contents with no key, no lockpicks, and no skill check,
leaving the whole lock system decorative.

---

## 9. Public usability vs. movement permission

| Action | Who | Governed by |
|---|---|---|
| Sit on, open, drink, use | **Anyone** | The object's own existing behavior, unchanged |
| Pick up / destroy a player-placed object | Anyone, outside a foreign structure | `GrabbyPolicy` |
| Pick up / destroy inside someone's house | Administrators | `StructureRegionManager` ownership |
| Pick up / destroy world/admin decoration | Administrators | Provenance = `WORLD` |

The first row is the important one. Grabby Hands **never** intercepts ordinary use. Right-clicking a
chair seats you; right-clicking a chest opens it. Only the sneak-empty-handed gesture and the
axe gesture are claimed, and even those fall straight through when the target is not an enrolled,
player-placed object.

---

## 10. Sound roles

`GrabbySoundRoles` defines three roles, so the audio language is in one place:

| Role | Sound | Where it plays | When |
|---|---|---|---|
| `grab()` | `ITEM_FRAME_REMOVE_ITEM` | At the block position, for everyone | The instant the object leaves the world |
| `stow()` | `ITEM_PICKUP` | Personally, to the picking-up player | **Only** on successful inventory insertion |
| `destroy(state)` | `state.getSoundType().getBreakSound()` | At the block position | After the removal commits |

Two stages, not one, and the stow cue is genuinely conditional — a pickup that ends in
`DROPPED_AT_FEET` plays the grab cue and nothing else.

`destroy()` deriving from the block's own `SoundType` is why M7 added explicit `.sound(SoundType.WOOD)`
to 12 wood furniture blocks and `.sound(SoundType.METAL)` to 7 lighting blocks — they had been
inheriting defaults that sounded wrong.

---

## 11. Keeping static decoration immovable

**By default, it already is.** No `GrabbyState` tag means `WORLD` means protected. Map decoration,
worldgen furniture, and anything placed before this system existed all qualify automatically.

To keep something immovable that would otherwise qualify:

- **Don't enroll the type** — the simplest option, and correct for anything that is never player
  furniture;
- **Add it to `grabby_deed_placed`** — a hard veto that overrides both movable and destroyable, for
  types that are placed through the deed system;
- **Leave the block entity without `GrabbyProvenanceHolder`** — it can then never be marked
  player-placed, so `NOT_GRABBY_MANAGED` refuses every attempt.

`GrabbyProtectionAuditTest` and `sceneryStaysImmovableForEveryone` cover this.

---

## 12. Testing

```bash
./gradlew test
```

```bash
./gradlew runGameTestServer
```

**1919 JUnit tests** (240 Grabby, across 21 classes) and **364 GameTests** (27 Grabby), 0 failures.

The Grabby suite uses five techniques:

- **Unit tests** over transaction ordering, policy, provenance encoding, session validity;
- **Fake seams** (`FakeGrabbyWorld`, `FakeGrabbyActor`) for failure paths that are hard to reach
  live — full inventory, failed removal, failed provenance stamp, object-refused transport;
- **Real registries under `Bootstrap`** for tag membership, component round trips, NBT, sound types;
- **GameTests against a real level** for container spill, live seating, concurrency, persistence,
  and the Adventure gate itself;
- **Source-scan contract tests** for the load-bearing *absences* — the package must never reference
  `setGameMode`, `mayBuild`, break events, `setBlock`, the placer UUID in a use decision, or any
  wine field name. Comments are stripped before scanning.

Every guard has been mutation-tested. That discipline caught two guards that were silently doing
nothing — see `ULTIMACRAFT_GRABBY_HANDS_TESTING_LIMITATIONS.md` §5a.

---

## 13. Live validation

The full 23-step Adventure-mode acceptance pass in
`ULTIMACRAFT_GRABBY_HANDS_MILESTONE_13_ACCEPTANCE.md` was completed and **approved by the owner**,
covering two-player usability, seating, stacking, two-stage pickup audio, axe destruction audio,
repeated wine round trips, filled-container round trips, spill-on-destroy, static protection, and a
save/restart/rejoin cycle.

---

## 14. Known exclusions

Not covered, and deliberately so:

| Excluded | Reason |
|---|---|
| Beds, all 5 chandeliers | Deed-placed; owner decision |
| Trash barrel | Owner decision |
| `candelabra_tall`, `villa_lamp_post` | `RaisedBlockItem` — mis-places silently through this path |
| Multi-block furniture beyond root resolution | `GrabbyRootResolver` resolves one canonical root; no multi-block object is currently enrolled |
| Entities | This is a block transport system |
| Anything without a block entity | Cannot carry provenance |

Two questions remain open and non-blocking: container nesting depth (OQ-3) and whether city
container destruction warrants a stricter rule than city placement (OQ-5).

Grabby Hands introduces **no Rails endpoint** and **no new persistence authority**. Nothing ticks —
there is no periodic scan of placed objects, by design.
