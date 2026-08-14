# Grabby Hands — M0 Addendum: Authorization Path, Lifecycle Ownership, and Provenance

**Date:** 2026-08-12
**Branch:** `grabby-hands` @ `50061f07`
**Supersedes:** §12, §16, §17 (partial) and §19–§20 of `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_0_REPORT.md`
**Trigger:** owner direction on OQ-1 (authorization) and OQ-2 (lifecycle ownership), the instruction to move provenance into the core design, and new requirement **R-2.11** (Part 6).

**New product requirement introduced here:** **R-2.11 — axe-mode interaction and destruction confirmation** (Part 6). To be folded into §2 of `ULTIMACRAFT_GRABBY_HANDS_DESIGN.md` as requirement 2.11, and into the Playbook's invariant list.

---

## Part 1 — Complete axe/break authorization path

Traced end-to-end across `CityGameModeHandler`, `SurvivalZoneHandler`, all five `BreakEvent` subscribers, all four `LeftClickBlock` subscribers, both `BreakSpeed` subscribers, the client mixin, the vegetation systems, and tool-specific behaviour. Vanilla claims verified against the **NeoForge-patched** sources (`build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/patchUserDev/output.jar`), not the unpatched decompile.

### 1.1 The authorization stack is four independent layers

| Layer | Mechanism | Effect |
|---|---|---|
| **L1 — game mode** | `CityGameModeHandler` (`PlayerTickEvent.Post`), `SurvivalZoneHandler` (`ServerTickEvent.Post`) | decides whether the client will even *send* a break packet |
| **L2 — destroy progress** | `ToolInteractionHandler.onBreakSpeed` + `BreakSpeedHandler.onBreakSpeed` (near-duplicate, both registered), plus `TwoHandedAxeItem.getDestroySpeed` / `QualityToolItem.getDestroySpeed` returning `0.0F` | decides whether the block can ever reach 100 % progress |
| **L3 — break event** | `WoodChopEventHandler`, `CustomBlockBreakHandler`, `StructureProtectionHandler`, `FlowerInteractionHandler`, `TreeKarmaHandler` | decides what actually happens when a break completes |
| **L4 — client reachability** | `Player.blockActionRestricted` inside `MultiPlayerGameMode.startDestroyBlock` | in Adventure, kills the packet before anything server-side runs |

### 1.2 L1 — game-mode arbitration, exactly as written

`CityGameModeHandler.onPlayerTick` (`event/CityGameModeHandler.java:19-61`):

1. CREATIVE / SPECTATOR → return.
2. `CityRegistry.isPlayerInAnyCity(player.position())` → force **ADVENTURE**, `return`. **The city check precedes and short-circuits the tool check.**
3. Holding `QualityToolItem` or `TwoHandedAxeItem` → force **SURVIVAL**.
4. Otherwise, if SURVIVAL → force **ADVENTURE**.

`SurvivalZoneHandler.onServerTick` (`structure/SurvivalZoneHandler.java:29-84`), for every online player:

1. CREATIVE / SPECTATOR → skip.
2. No `StructureRecord` in the player's chunk → ADVENTURE **unless holding a tool**.
3. Inside a structure the player **owns** (`record.getOwnerUuid().equals(player.getUUID())` && `getFullBox().contains(playerVec)`) → force **SURVIVAL**, unconditionally.
4. Otherwise → ADVENTURE unless holding a tool.

**This is the gameplay purpose the owner requires preserved:** *the axe grants Survival — and therefore general breaking — only outside cities and only outside structures you do not own.* Inside a city, step 2 of `CityGameModeHandler` wins and the axe grants nothing.

**Pre-existing conflict (not Grabby Hands' to fix, but must be known):** a player standing inside their **own structure inside a city** is claimed by both handlers — `CityGameModeHandler` forces ADVENTURE, `SurvivalZoneHandler` forces SURVIVAL. `ServerTickEvent.Post` runs after `PlayerTickEvent.Post`, so `SurvivalZoneHandler` wins at end-of-tick and the player ends up in SURVIVAL. That appears intentional (you may build in your own house) but it is emergent from tick ordering, not stated anywhere. Grabby Hands must not depend on it either way — see §1.7.

### 1.3 L2 — the axe is already contained at the tool level

This is the most important discovery of the addendum, and it is **stronger than the game-mode layer**.

- `TwoHandedAxeItem.getDestroySpeed` returns `0.0F` for anything failing `AxeHarvestRules.isAllowedAxeHarvestBlock` (`item/TwoHandedAxeItem.java:38-43`). Zero speed ⇒ `getDestroyProgress` is zero ⇒ **the block never breaks, in any game mode**.
- `ToolInteractionHandler.onBreakSpeed:52-70` **and** `BreakSpeedHandler.onBreakSpeed:42-62` each independently `event.setCanceled(true)` on `PlayerEvent.BreakSpeed` for the same condition.
- `QualityToolItem.getDestroySpeed:77-79` does the same for `PickaxeMiningRules`.

`AxeHarvestRules.isAllowedAxeHarvestBlock` covers **only** logs / trunks / branches / leaves / fruit / `WeightedWoodBlock` / orange-tree blocks (`util/AxeHarvestRules.java:17-19`). Chairs, tables, wine bottles and chests are not in it.

**Consequence: holding the two-handed axe today, a player cannot break furniture anywhere, in any game mode, even standing in their own house in Survival.** The axe is not a general breaking tool that happens to be geofenced — it is a wood-only tool that additionally requires Survival.

### 1.4 L3 — break-event handlers, and a second total containment

`WoodChopEventHandler.onBlockBreak:66-85` cancels the `BreakEvent` for **every** block whenever the main hand holds `TWO_HANDED_AXE`, then delegates to `handleAxeHarvest`, which returns without acting for anything that is not wood/fruit/leaf. So even if L2 were bypassed, a two-handed-axe break of a chair would be cancelled and do nothing.

`StructureProtectionHandler.onBlockBreak:27-58` is the house-protection layer — but note it **returns early for tool holders** (`if (isAllowedTool) return;`), so it does not restrain axes at all. Axes are restrained by L1 (cities) + L2/L3 (wood-only), not by this handler.

`CustomBlockBreakHandler` (pickaxes → stone/ore, cancel + custom drop) and `FlowerInteractionHandler.onBreak` (cancel + `FlowerProtectionService.mayMutate`) are the two exemplars of the project's *"cancel the vanilla break, run a server-authoritative transaction"* idiom.

### 1.5 L4 — Adventure reachability, verified against patched sources

`MultiPlayerGameMode.startDestroyBlock` (patched):

```java
public boolean startDestroyBlock(BlockPos pos, Direction face) {
    if (this.minecraft.player.blockActionRestricted(this.minecraft.level, pos, this.localPlayerMode)) {
        return false;                       // ← no packet, no event, nothing
    }
    …
    PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(…);   // ← fired only AFTER the gate
```

**`PlayerInteractEvent.LeftClickBlock` is fired inside `startDestroyBlock`, after the restriction check.** In Adventure mode it never fires at all — client or server.

**Correction to the M0 report §12.** It listed `PlayerEventHandler.onLeftClickBlock:155-183` (the `TWO_HANDED_AXE` + `GameType.ADVENTURE` branch) as an active "correct server half". It is **unreachable dead code** on this base:

- the branch requires `getGameModeForPlayer() == ADVENTURE`;
- but `CityGameModeHandler` forces SURVIVAL whenever a `TwoHandedAxeItem` is held outside a city;
- and inside a city the player is ADVENTURE, so `blockActionRestricted` returns true and the event never fires.

Live tree harvesting runs entirely through the **Survival** path: L1 grants Survival → L2 permits wood → `WoodChopEventHandler.onBlockBreak` cancels and calls `handleAxeHarvest`. Nothing Grabby Hands does may disturb that chain.

On `codex/wild-reagents`, `ManagedVegetationAdventureModeMixin` `@Redirect`s the `blockActionRestricted` call so that swords-on-vegetation and `AdventureHarvestableBlock` tools become reachable in Adventure — a genuinely narrow, per-block-type opening, with the server revalidating (`WildResourceInteractionHandler.onLeftClick` re-checks `GameType.ADVENTURE` and the node registry). Good pattern; **Grabby Hands does not need it.**

### 1.6 The decisive finding: right-click is unrestricted in Adventure

`ServerPlayerGameMode.useItemOn` (patched) — first statements:

```java
public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit) {
    BlockPos blockpos = hit.getBlockPos();
    BlockState blockstate = level.getBlockState(blockpos);
    if (!blockstate.getBlock().isEnabled(level.enabledFeatures())) return InteractionResult.FAIL;

    PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(player, hand, blockpos, hit);
    if (event.isCanceled()) return event.getCancellationResult();     // ← Grabby Hands hooks HERE
    …
```

`PlayerInteractEvent.RightClickBlock` fires **before** the spectator branch, **before** `blockstate.useItemOn` / `useWithoutItem`, and **before** `ItemStack.useOn` — which is the only place `mayBuild` is consulted. The client sends `ServerboundUseItemOnPacket` unconditionally (`MultiPlayerGameMode.useItemOn` has no restriction check). `ServerGamePacketListenerImpl.handleUseItemOn` gates only on reach, build height and `serverlevel.mayInteract` (spawn protection).

NeoForge additionally exposes `event.getUseBlock()` / `event.getUseItem()` as `TriState`, so a handler can suppress **only** the block-use half without cancelling the whole interaction.

Also verified in the patched source:

```java
boolean flag  = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
boolean flag1 = (player.isSecondaryUseActive() && flag) && !(mainHand.doesSneakBypassUse(…) && offhand.doesSneakBypassUse(…));
```

With **both hands empty**, `flag` is false ⇒ `flag1` is false ⇒ sneak does *not* suppress block use. The sneak + empty-hands pickup gesture is therefore reachable natively.

### 1.7 Design conclusion — the narrow path, and why it cannot leak

**Grabby Hands uses `PlayerInteractEvent.RightClickBlock` at `EventPriority.HIGHEST` for all three operations — place, pickup, and axe destruction — and never touches the break pipeline.**

This is also the more faithful RunUO analogue: lumberjacking furniture destruction was a *targeting* action (`HarvestTarget`), not mining.

| Grabby operation | Gesture | Hook |
|---|---|---|
| Place | enrolled portable item + right-click | `RightClickBlock` @ HIGHEST → `BlockItem.place(BlockPlaceContext)` server-side → `setCanceled(true)` + `setCancellationResult(SUCCESS)` |
| Pickup | **both hands empty** + sneak + right-click | same hook |
| Axe destruction | recognized axe in main hand + right-click | same hook |

Why each of the owner's constraints holds *structurally*, not by convention:

| Constraint | Why it holds |
|---|---|
| Axes gain **no** general block-breaking authority | Grabby Hands adds nothing to L2/L3/L4. `AxeHarvestRules`, both `BreakSpeed` cancellations, `getDestroySpeed → 0.0F`, and `WoodChopEventHandler`'s blanket cancel are all untouched. A player still cannot break *any* non-wood block with an axe. |
| No new authority **inside cities** | `CityGameModeHandler` is untouched. Grabby destruction is additionally gated by `GrabbyPolicy`, which requires positive player-placed provenance — and city scenery has none. Both gates must pass. |
| Tree / log / leaf harvesting keeps working | The Survival + `BreakEvent` + `handleAxeHarvest` chain is untouched. Grabby's handler returns immediately unless the target block carries Grabby provenance, so it never sees a log. |
| No game-mode switching added | The right-click path is game-mode independent. Grabby Hands neither reads nor writes game mode. |
| No mixin, no `mayBuild`, no broad uncancel | The hook precedes every Adventure gate. Nothing needs relaxing. |
| Existing/admin/world decoration protected | Absence of provenance ⇒ protected (Part 3). A chair placed by worldgen and a chair placed by a player are distinguished by BlockEntity state, not block type. |

**Smallest change achieving coexistence: one new `RightClickBlock` subscriber and one provenance field. Zero edits to any existing authorization class.** No broad refactor is required, and the analysis does not demonstrate one is needed.

### 1.8 Ordering and interference risks to test in M2/M8

1. **`ChairBlock.useWithoutItem` seats you on any right-click.** Right-clicking a chair with an axe currently makes the player sit. → **Resolved by requirement R-2.11 (Part 6): holding a recognized axe is axe mode, and normal use is suppressed for the duration.** Grabby's handler still must run at `HIGHEST` and cancel before `useItemOn`/`useWithoutItem` — the exact shape of `FlowerInteractionHandler.onRightClickBlock:25-45`. Test: axe right-click on an enrolled chair opens the confirmation and does **not** seat.
2. **Container right-click opens a menu.** Same ordering requirement for `BritanniaChestBlock` / `ArmoireBlock`; also resolved by R-2.11.
3. **`InteriorDecoratorToolItem`** is itself a right-click tool with rotate/nudge/mirror/cycle behaviour across `INudgeable`, `MirrorableWallBlock`, `ThinWall`, `BlankSignHolder`, `CarpetTeleporterBlock` and the shrine anchor. Grabby's handler must ignore interactions where either hand holds it, mirroring `ChairBlock.useWithoutItem:99-102`. Regression test required.
4. **`TwoHandedAxeItem.useOn` returns `InteractionResult.PASS`** deliberately — the right-click slot is free, no existing behaviour is displaced.
5. **`serverlevel.mayInteract`** (vanilla spawn protection) runs before the event. Grabby Hands inherits it for free; worth one assertion.
6. **Both `ToolInteractionHandler` and `BreakSpeedHandler` are registered** and implement the same `onBreakSpeed` logic (`BritanniaMod.java:186` and the `BreakSpeedHandler` registration). Pre-existing duplication; harmless (both cancel identically). **Do not clean this up in this epic** — note only.

### 1.9 Axe recognition, restated under this design

Because destruction no longer flows through `AxeHarvestRules` or `BreakSpeed`, `GrabbyAxes` is a pure classifier used only by Grabby's own handler:

```java
stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem
```

The `instanceof AxeItem` arm is what catches `britannia_mod:two_handed_axe`, which is **not** in `ItemTags.AXES` (no `data/minecraft/tags/` exists in the mod). This classifier is scoped to Grabby Hands and changes nothing about what axes may break elsewhere. **OQ-4 (adding the axe to the vanilla tag) is now moot for this epic** — do not do it; it would alter `GrapeVineBlock` and `TrellisBlock` behaviour for no Grabby Hands benefit.

---

## Part 2 — Lifecycle ownership: Grabby Hands owns its own services

Per owner direction, `ShrineLifecycleService` is **reference material only**. No shared abstraction is created, nothing in `structure/lifecycle/` or `structure/multiblock/` is modified, and the implementation is not copied wholesale.

Shrines and portable world objects share no domain: shrines are fixed 4/18-cell civic structures with variant cycling and integrity monitoring; Grabby objects are single-position player possessions moving between world and inventory. Coupling them would be coincidental.

### What is legitimately learned (concepts, re-derived for our domain)

| Concept observed | How Grabby Hands applies it — independently |
|---|---|
| One authoritative commit point per mutation | `GrabbyPickupTransaction` / `GrabbyPlacementTransaction` / `GrabbyDestructionTransaction` each expose one `execute(...)` returning a result record |
| Guard against concurrent mutation of the same object | Grabby's own guard keyed on `(ServerLevel, BlockPos)` — trivially simpler than the shrine's `(level, anchor)` because Grabby objects are single-position |
| Result records carrying counts, so "exactly once" is assertable | `GrabbyPickupResult(status, ItemStack stowed)`, `GrabbyDestructionResult(status, int spilledStacks)` |
| A testable world-mutation seam | `GrabbyWorld` interface, so transactions unit-test on the existing plain-JUnit harness with no MC bootstrap |
| Never force-load chunks; report the condition instead | single-position objects are always in a loaded chunk when interacted with; the concept reduces to a validity check |
| Schema-versioned portable state | `GrabbyInstanceState.schemaVersion`, following `FlowerPersistentState` / `ShrineItemState` |

### Naming (Grabby-owned, no shrine vocabulary)

```
grabbyhands/
  GrabbyEligibility.java            — tag lookup
  GrabbyInstanceState.java          — per-instance provenance record (Part 3)
  GrabbyProvenance.java             — enum: WORLD | PLAYER
  GrabbyPolicy.java                 — mayMove / mayDestroy; no use-permission API
  GrabbyMutationReason.java         — PICKUP | PLACE | AXE_DESTROY | EXPLOSION | FLUID | PISTON | SYSTEM
  GrabbyWorld.java                  — testable mutation seam
  GrabbyPickupTransaction.java
  GrabbyPlacementTransaction.java
  GrabbyDestructionTransaction.java
  GrabbyInteractionHandler.java     — the single RightClickBlock @ HIGHEST subscriber
  GrabbyAxes.java                   — axe classifier
  GrabbySoundRoles.java             — grab / stow / destroy
  adapter/GrabbyAdapter.java + FurnitureAdapter / StatefulItemAdapter / ContainerAdapter
```

A genuinely generic primitive (e.g. a shared position-keyed mutation guard) is extracted **only if** both systems demonstrably need the identical thing — revisited no earlier than M9, and only with evidence.

---

## Part 3 — Provenance moves into the core design (now M1, not M8)

### Requirement

Every object placed through Grabby Hands is positively marked as Grabby-managed and player-placed, carrying the placer UUID. Anything lacking that mark is protected. No backwards migration.

### `GrabbyInstanceState`

Modelled on `farming/FlowerPersistentState` — the project's existing, proven pattern for exactly this distinction (schema version, `ADMIN|PLAYER` origin, protected flag, planter UUID, compact-constructor invariants, legacy-tolerant `fromTag`, and a `toClientTag()` that withholds the UUID from clients).

```java
public record GrabbyInstanceState(
        int schemaVersion,            // CURRENT_SCHEMA_VERSION = 1
        GrabbyProvenance provenance,  // WORLD | PLAYER
        UUID placerUuid,              // required when PLAYER; absent when WORLD
        long placedAtGameTime
) {
    // invariant: PLAYER  => placerUuid present
    // invariant: WORLD   => placerUuid absent
}
```

Serialized under a single NBT key `GrabbyState` on the block's **existing** BlockEntity.

### Why "no migration" comes free

The mark is **positive**. Absence is the protected default:

```java
GrabbyProvenance.of(blockEntity)   // no "GrabbyState" tag  ->  WORLD  ->  immovable
```

Every block already in every save file — all Britannia scenery, every structure-template chair, every admin-placed decoration, every chest placed before this epic — has no tag and is therefore protected on day one. Nothing is scanned, rewritten, or versioned up. A `WORLD` value is never written by the placement path; it exists only as the decode-time default and for an explicit future admin "pin this object" command.

### Where it is written — one place only

`GrabbyPlacementTransaction`, after a successful `BlockItem.place(...)`, stamps the resulting BlockEntity with `PLAYER` + placer UUID. Nothing else writes provenance. In particular, ordinary vanilla/creative placement of a chair does **not** stamp it — a staff member placing scenery in Creative produces a protected object, which is the desired behaviour and needs no separate admin flag.

### Host BlockEntities — all already exist

| Content | BlockEntity carrying `GrabbyState` |
|---|---|
| chairs, stools, benches, thrones | `ChairBlockEntity` (`NudgeableBlockEntity`) |
| tables, counters | `RotatableFurnitureBlockEntity` (`NudgeableBlockEntity`) |
| wine bottles | `WineBottleBlockEntity` |
| chests, lockable chest | `BritanniaChestBlockEntity` |
| armoires, chests of drawers | `ArmoireBlockEntity` |

`NudgeableBlockEntity` already implements `saveAdditional` / `loadAdditional` / `getUpdateTag` / `handleUpdateTag` / `getUpdatePacket`, so chairs and tables need a few lines each. No new block, no new BlockEntity type, no `SavedData`, no per-tick scan.

### Provenance is for mobility only — enforced, not merely intended

`placerUuid` is read by `GrabbyPolicy.mayMove` / `mayDestroy` and by logging. It is **never** consulted by any `useWithoutItem` / `useItemOn` path.

Enforcement in M1:
- `GrabbyPolicy` exposes **no** use/interaction predicate — the absent method is the contract.
- `toClientTag()` omits `placerUuid` (following `FlowerPersistentState.toTag(boolean includePlanterUuid)`).
- Tests: a non-placer can sit on a placed chair; a non-placer can open a placed container; an architecture test asserts `GrabbyPolicy` has no method taking a "use"/"interact" reason.

---

## Part 4 — Revised Milestone 1 scope

1. **`data/britannia_mod/tags/block/grabby_movable.json`** + `grabby_axe_destroyable.json`, hand-written; `TagKey`s in `util/ModTags.Blocks`. Seed: chair family, `yew_table` / `small_table` / `counter`, four wine bottles. No containers yet.
2. **`GrabbyInstanceState`** + `GrabbyProvenance` — record, NBT round-trip, `toClientTag()`, absence ⇒ `WORLD`.
3. **Provenance storage wired into all five existing BlockEntities** (moved forward from M8 per owner direction). Read/write helpers only — no placement path yet.
4. **`GrabbyPolicy`** — `mayMove` / `mayDestroy` over `(GrabbyInstanceState, actor, GrabbyMutationReason)`; delegates region/house questions to `StructureRegionManager` and `HouseLotBlockEntity`. **No use-permission API.**
5. **`GrabbyAxes`** — `is(ItemTags.AXES) || instanceof AxeItem`.
6. **`GrabbySoundRoles`** — grab / stow / destroy resolution, no assets.
7. **`GrabbyWorld`** — the testable mutation seam. No transactions yet (M2).

### M1 tests

- enrolled type recognised; non-enrolled rejected;
- **a BlockEntity with no `GrabbyState` tag resolves to `WORLD` and is immovable** (the no-migration guarantee);
- `PLAYER` + placer UUID is movable by the placer;
- `PLAYER` is movable by a *different* player where region/house policy allows (mobility is not owner-only either — provenance marks *managed*, not *mine*);
- `WORLD` is immovable for a non-admin actor;
- state round-trips `toTag` → `fromTag` exactly, including the absent-tag case;
- `toClientTag` omits `placerUuid`;
- `GrabbyAxes` accepts vanilla axes **and** `TwoHandedAxeItem`; rejects `QualitySwordItem` and `BritanniaPickaxeItem`;
- architecture test: `GrabbyPolicy` exposes no use/interaction predicate;
- **regression guards** — `AxeHarvestRules.isAllowedAxeHarvestBlock` is unchanged for logs / leaves / fruit; no Grabby class references `setGameMode`, `mayBuild`, `BreakEvent`, `PlayerEvent.BreakSpeed`, or `blockActionRestricted`.

That last regression test is the machine-checkable form of the owner's constraint: **Grabby Hands cannot grant break authority it never touches.**

### Gate M1

One source of truth each for eligibility, provenance, policy, axe classification and sound roles; provenance defaults to protected with no migration; the authorization-regression tests pass; no existing authorization class modified.

---

## Part 5 — Remaining open questions

Both prior blockers are resolved by owner direction. Two non-blocking items remain:

- **OQ-3 (M7) — container nesting.** No capacity/weight/nesting rule exists anywhere in `BritanniaChestBlockEntity` / `ArmoireBlockEntity`. Recommend rejecting placement of a non-empty container ItemStack into any container slot.
- **OQ-5 (M8, new) — destruction of a *filled* player-placed container inside a city.** The right-click path works in Adventure, so an axe can destroy a Grabby-managed container inside a city where the same axe cannot break anything else. That is consistent with "Grabby-managed objects are the player's own possessions", but it is a genuine new capability inside city bounds and deserves an explicit yes/no. Recommend: **allow**, gated on `GrabbyPolicy` (player-placed provenance + house/region permission), since the object is by construction not city scenery.

- **OQ-4 is withdrawn** — adding `two_handed_axe` to `ItemTags.AXES` is no longer relevant (§1.9).

**Noted, deliberately not actioned:** the `CityGameModeHandler` / `SurvivalZoneHandler` own-structure-in-a-city tick-order conflict (§1.2), and the `ToolInteractionHandler` / `BreakSpeedHandler` duplication (§1.8 item 6). Both pre-date this epic and are outside its scope.

---

## Part 6 — R-2.11: axe-mode interaction and destruction confirmation

**New owner requirement, 2026-08-12.** Fold into `ULTIMACRAFT_GRABBY_HANDS_DESIGN.md` §2 as requirement 2.11.

### R-2.11.1 — Holding an axe suppresses normal use

> A player cannot interact with an object while holding an axe.

While a recognized axe (`GrabbyAxes.isAxe`) is in the main hand, right-clicking a **Grabby-enrolled** block does not perform that block's normal use. Sitting, opening a container, and any other `useItemOn` / `useWithoutItem` behaviour is suppressed. Holding an axe is *axe mode*.

Scope limits, stated explicitly so this cannot become a general interaction ban:

- Applies **only** to blocks in `britannia_mod:grabby_axe_destroyable`. An axe right-click on a door, a shrine, an NPC, a workbench or any non-enrolled block behaves exactly as it does today.
- Applies **only** to the main hand. Offhand contents do not put the player in axe mode.
- Does **not** suppress use for a player who is not holding an axe. Two players at the same chair — one axe-handed, one empty-handed — get destruction intent and seating respectively.
- Does **not** grant any authority. A non-provenanced (`WORLD`) object is still protected; see R-2.11.4.

### R-2.11.2 — Destruction requires explicit confirmation

> Before the object is destroyed, ask the player "Do you wish to destroy this?" with two choices: **Yes** / **No**.

Purpose: reduce accidental destruction. Destruction is irreversible, and for containers it also spills contents.

- **Yes** → the destruction transaction runs.
- **No**, `Esc`, or closing the screen → nothing happens. No block change, no sound, no durability cost, no partial state.
- The prompt names the object (`state.getBlock().getName()`), so "Destroy the Wooden Chair?" rather than a generic string.
- For a **non-empty container**, the prompt must additionally warn that contents will be spilled, and state the number of occupied slots.

### R-2.11.3 — The confirmation is intent-only; the server re-validates

This follows the project's existing destructive-confirmation convention exactly — `dye/preview/DyePreviewSession` + `network/payload/dye/C2SConfirmDyeApplicationPayload`, whose own doc comment reads *"Player intent only. No pigment, material, colour, match, banner, or tub authority is accepted."*

Flow:

1. Server-side `GrabbyInteractionHandler` resolves the target, checks eligibility, provenance and `GrabbyPolicy.mayDestroy`. **If any check fails, no prompt is shown at all** — the player is never offered a choice they are not permitted to make.
2. Server issues a `GrabbyDestructionSession` holding a random `sessionId`, the player UUID, the target `BlockPos`, a snapshot of the expected `BlockState`, the expected axe `ItemStack`, and `createdAtMillis` / `expiresAtMillis` — mirroring `DyePreviewSession`'s shape, including its `expired(nowMillis)` accessor.
3. Server sends an S2C payload carrying only display data (object name, container-contents warning, `sessionId`).
4. Client shows the two-option screen.
5. On **Yes**, the client sends `C2SConfirmGrabbyDestructionPayload(sessionId)` — **the sessionId and nothing else.** No position, no block, no target identity.
6. Server re-resolves from the session and **re-runs every check** — session exists, belongs to this player, not expired, block state still matches the snapshot, axe still held, provenance still `PLAYER`, `GrabbyPolicy.mayDestroy` still true, player still within reach — then runs the transaction and consumes the session.

A forged, replayed, stale or out-of-order confirmation is rejected. The session is single-use.

### R-2.11.4 — Confirmation is not authorization

The prompt is an *accident guard*, not a permission gate. Ordering is fixed: **policy first, prompt second, re-validate, then act.** A protected `WORLD` object never produces a prompt, and answering Yes can never destroy something the pre-check would have refused.

### R-2.11.5 — Session lifecycle

Tick-free, matching `dye/preview/DyePreviewLifecycle`:

- lazy expiry on read (short TTL — propose 30 s, confirm in M8);
- invalidate on `PlayerEvent.PlayerLoggedOutEvent`, `PlayerChangedDimensionEvent`, `LivingDeathEvent`;
- clear all on `ServerStoppingEvent`;
- at most one outstanding destruction session per player — a new axe interaction replaces any previous one;
- **no per-tick scanning**, satisfying design §20 and playbook invariant 22.

### R-2.11.6 — Presentation

Reuse the existing dialogue framework rather than adding a parallel one: `dialogue/DialogueViewModel`, `dialogue/DialogueOptionViewModel`, `dialogue/DialogueLayout`, `client/gui/DialoguePresentation`, driven by a `Screen` with `Button.builder` — the shape used by `client/gui/QuestDecisionScreen`. Two options: Yes / No.

Lang keys under the established namespace, e.g.
`screen.britannia_mod.grabby.destroy.title`,
`screen.britannia_mod.grabby.destroy.body` (`"Destroy the %s?"`),
`screen.britannia_mod.grabby.destroy.contents_warning`,
`screen.britannia_mod.grabby.destroy.yes`, `.no`.

Precedent for the wording style — `screen.britannia_mod.blacksmithing.confirm_smelt`: *"Smelting will destroy this item and recover %s material. Continue?"* (`BlacksmithyScreen.java:311`).

### R-2.11.7 — Sound

No sound on prompt display. The destruction sound (§11 of the M0 report — `state.getSoundType().getBreakSound()`) plays only after **Yes** and only once the server transaction has committed. Cancelling is silent.

### R-2.11.8 — Milestone placement

- **M1:** `GrabbyAxes` only.
- **M8:** everything else in R-2.11 — axe-mode suppression, session, payloads, screen, lang, sounds. R-2.11 is part of Gate M8; the milestone does not close without it.

### R-2.11.9 — Tests

| # | Assertion |
|---|---|
| 1 | Axe + right-click on an enrolled, player-placed chair shows the prompt and does **not** seat the player |
| 2 | Empty hand + right-click on the same chair seats normally — axe mode is per-player, per-interaction |
| 3 | Axe + right-click on a non-enrolled block behaves exactly as before (no prompt, no suppression) |
| 4 | Axe + right-click on a `WORLD`-provenance chair shows **no prompt** and destroys nothing |
| 5 | **No** / `Esc` / screen close leaves block, contents, sounds and axe durability untouched |
| 6 | **Yes** destroys exactly once; a replayed confirmation with the same `sessionId` is rejected |
| 7 | A confirmation whose `sessionId` was issued to a different player is rejected |
| 8 | An expired session is rejected |
| 9 | If the block changed between prompt and confirmation, the confirmation is rejected |
| 10 | If the player dropped or swapped away the axe before confirming, the confirmation is rejected |
| 11 | A filled container prompt states the occupied-slot count; on **Yes**, contents spill exactly once |
| 12 | Two players prompted on the same object: the first **Yes** wins, the second is rejected — no double destruction, no double spill |
| 13 | Logout / dimension change / death invalidates the outstanding session |
| 14 | An axe right-click while either hand holds `InteriorDecoratorToolItem` yields no prompt (decorator behaviour is preserved) |

---
