# Grabby Hands — Milestone 14 Final Report

**Branch:** `grabby-hands`
**Base:** `50061f07` · **Implementation commit:** `d7c317b3` (94 files, +11,413 / −25)
**Date:** 2026-08-13
**Status:** All gates passed. Feature complete as scoped.

---

## 1. What shipped

Adventure-mode players can place, use, pick up, and axe-destroy 32 enrolled furniture, lighting,
wine and container blocks, plus 30 loose decorative items on a generic host block — while every
pre-existing object in every existing save remains immovable with **zero migration**.

The whole system is **2,975 lines** across 33 classes in one package, hanging off **one event
subscriber**. It changes no game mode, relaxes no permission, touches no break pipeline, and adds no
mixin, no Rails endpoint, no tick loop, and no second container/seating/housing/wine system.

The single architectural discovery that made this possible: `PlayerInteractEvent.RightClickBlock`
fires as the *first statement* of `ServerPlayerGameMode.useItemOn` — before `ItemStack.useOn`, which
is the only place Adventure's `mayBuild` gate lives. Everything else follows from hooking there.

Implementation documentation: [`docs/grabby-hands.md`](docs/grabby-hands.md).

---

## 2. Gates

| Gate | Scope | Status |
|---|---|---|
| M0 | Reconnaissance + authorization-path addendum | Passed |
| M1 | Provenance, policy, eligibility, world seam | Passed |
| M2 | Pickup transaction, mutation guard, two-stage audio | Passed |
| M3 | Placement transaction, Adventure gate | Passed — owner confirmed live |
| M4 | Lighting/decor enrollment, adapter reuse | Passed |
| M5 | Generic placed-item host block | Passed |
| M6 | Wine state preservation | Passed |
| M7 | Portable containers, sound-type fixes | Passed |
| M8 | Axe destruction + R-2.11 confirmation | Passed |
| M9 | — | Skipped by owner |
| M10 | Protection and public-usability audit | Passed |
| M11 | Loose-item enrollment | Passed |
| M12 | GameTest unblock, persistence + multiplayer tests | Passed |
| M13 | Full live Adventure acceptance pass | **Passed — owner approved** |
| M14 | Documentation and completion | This report |

---

## 3. Final completion criteria

Every criterion from the playbook, with its evidence.

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | All required gates passed | ✅ | §2 |
| 2 | Full relevant automated suite green | ✅ | 1919 JUnit + 364 GameTests, 0 failures, 0 errors |
| 3 | Live Adventure-mode validation passed | ✅ | Owner-approved M13 pass |
| 4 | Two-player usability validated | ✅ | Live, plus `aSecondPlayerCanSitOnAChairSomebodyElsePlaced` |
| 5 | Furniture sitting passed | ✅ | Live, plus `sittingStillWorksAfterAChairHasBeenMoved` |
| 6 | Supported furniture stacking passed | ✅ | Live, plus `aChairStacksOnAChair` |
| 7 | Two-stage pickup audio passed | ✅ | Live — grab and stow judged audibly distinct |
| 8 | Axe destruction audio passed | ✅ | Live — wood/glass/metal distinct |
| 9 | Wine repeated exact-state round trip passed | ✅ | Live ×2 cycles, plus Appendix B |
| 10 | Filled-container round trip passed | ✅ | Live, plus `aFilledContainerKeepsItsContentsAcrossAReload` |
| 11 | Container spill-on-destroy exactly once | ✅ | Live, plus `destroyingAFilledContainerSpillsItsContentsExactlyOnce` |
| 12 | Static decoration protection passed | ✅ | Live 4-way sweep, plus `sceneryStaysImmovableForEveryone` |
| 13 | No broad Adventure-mode bypass exists | ✅ | §4 |
| 14 | Working tree contains no unexplained changes | ✅ | §7 |

---

## 4. Appendix C — failure-rule audit

The playbook lists 15 conditions that must halt progression. Each is addressed by construction and
guarded by a test.

| Failure condition | Status | What prevents it |
|---|---|---|
| Broad Adventure building permission enabled | Not present | Nothing writes `mayBuild`; enrolled blocks are *still* refused by the ordinary path (`anEnrolledChairIsAlsoStillRefusedByTheOrdinaryPlacementPath`) |
| Static decoration becomes lootable | Not present | Absent tag → `WORLD` → refused. Fail-closed on missing, malformed, and unsupported-version tags |
| Placed chair no longer seats players | Not present | `useWithoutItem` untouched; `sittingStillWorksAfterAChairHasBeenMoved` |
| Non-placer cannot use ordinary furniture | Not present | No code path reads `placerUuid` for a use decision — enforced by source scan |
| Supported furniture stacking stops working | Not present | Placement delegates to `BlockItem.place`/`isUnobstructed`; `aChairStacksOnAChair` |
| Pickup deletes an object when inventory is full | Not present | Room pre-check **before** removal; failed insert → `dropAtFeet` |
| Stow sound plays despite failed insertion | Not present | `DROPPED_AT_FEET` plays grab only; asserted |
| Wine state changes through a round trip | Not present | Whole origin `ItemStack` carried; Appendix B |
| Container contents duplicate or vanish | Not present | `detachForTransport()` before removal; `pickingUpAFilledContainerLeavesNothingOnTheFloor` |
| Axe destruction returns intact container + contents | Not present | Destruction consumes the object; contents spill via `onRemove` exactly once |
| Custom axe unrecognized (vanilla-only check) | Not present | `instanceof AxeItem \|\| stack.is(ItemTags.AXES)` — the first arm catches `TwoHandedAxeItem`, which is **not** in the vanilla tag |
| Multi-block parts independently drop | Not present | `GrabbyRootResolver` resolves one canonical root before any mutation |
| Client becomes authoritative for a mutation | Not present | All three transactions are server-side; the client only answers a confirmation prompt |
| New Rails authority introduced | Not present | No Rails code added |
| Environmental forces move Grabby objects | Not present | `EXPLOSION`, `FLUID`, `PISTON` are refused unconditionally by policy |

### Safety invariants

All 24 owner invariants hold. The load-bearing ones, restated with their guard:

- **No game-mode switching, no `mayBuild`, no uncancelled Adventure placement/breaking** —
  `GrabbyAuthorizationRegressionTest` forbids the symbols outright.
- **`CityGameModeHandler` intact** — Grabby Hands never participates in breaking, so axes gain no
  general breaking authority anywhere, and tree/log/leaf harvesting is untouched.
- **`ShrineLifecycleService` not generalized or modified** — studied as reference only; Grabby Hands
  owns its own named transaction services.
- **Eligibility ≠ instance mobility** — separate tags and NBT, never conflated.
- **Provenance is not an ACL** — recorded, never read for a use decision.

---

## 5. Appendix A — enrollment candidate matrix

Populated from `BlockRegistry`, the block-entity hierarchy, and the tag files — not from
assumptions.

| Registry ID | Category | Block class | Block entity | Stateful | Sittable | Stack-compat | Multi-block | Container | Static-world use | Grabby adapter | Enrolled |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `wooden_chair` | Seating | `ChairBlock` | `ChairBlockEntity` → `NudgeableBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `straw_chair` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `chair_trinsic` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `chair_vesper` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `stool` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `footstool` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `bench` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `wooden_throne` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `magincia_style_throne` | Seating | `ChairBlock` | `ChairBlockEntity` | No | **Yes** | Yes | No | No | Common | Provenance | **Yes** |
| `yew_table` | Table | `RotatableFurnitureBlock` | `RotatableFurnitureBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `small_table` | Table | `RotatableFurnitureBlock` | `RotatableFurnitureBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `counter` | Table | `RotatableFurnitureBlock` | `RotatableFurnitureBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `wine_bottle_green` | Wine | `WineBottleBlock` | `WineBottleBlockEntity` | **Yes** | No | Yes | No | No | Occasional | Provenance + origin stack | **Yes** |
| `wine_bottle_brown` | Wine | `WineBottleBlock` | `WineBottleBlockEntity` | **Yes** | No | Yes | No | No | Occasional | Provenance + origin stack | **Yes** |
| `wine_bottle_blue` | Wine | `WineBottleBlock` | `WineBottleBlockEntity` | **Yes** | No | Yes | No | No | Occasional | Provenance + origin stack | **Yes** |
| `wine_bottle_clear` | Wine | `WineBottleBlock` | `WineBottleBlockEntity` | **Yes** | No | Yes | No | No | Occasional | Provenance + origin stack | **Yes** |
| `candle` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `candelabra_small` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `brazier_small` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `torch_standing` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `wall_sconce` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `torch_wall` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `lamp_post_regular` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `lamp_post_fancy` | Lighting | `CandelabraBlock` | `CandelabraBlockEntity` | No | No | Yes | No | No | Common | Provenance | **Yes** |
| `chest_wooden` | Container | `BritanniaChestBlock` | `BritanniaChestBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `chest_metal` | Container | `BritanniaChestBlock` | `BritanniaChestBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `chest_metal_bronze` | Container | `BritanniaChestBlock` | `BritanniaChestBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `armoire_brown` | Container | `ArmoireBlock` | `ArmoireBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `armoire_red` | Container | `ArmoireBlock` | `ArmoireBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `chest_of_drawers_brown` | Container | `ArmoireBlock` | `ArmoireBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `chest_of_drawers_red` | Container | `ArmoireBlock` | `ArmoireBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state | **Yes** |
| `britannia_lockable_chest` | Container | `BritanniaLockableChestBlock` → `BritanniaChestBlock` | `BritanniaChestBlockEntity` | **Yes** | No | Yes | No | **Yes** | Common | Provenance + portable state + `securedAgainstDestruction` | **Yes** |

### Deliberately excluded

| Registry ID | Block class | Reason |
|---|---|---|
| `double_bed` | `DoubleBedBlock` | Deed-placed — owner decision. In `grabby_deed_placed` (hard veto) |
| `wooden_chandelier` | `ExtendedLightChandelierBlock` | Deed-placed — hard veto |
| `large_wooden_chandelier` | `ExtendedLightChandelierBlock` | Deed-placed — hard veto |
| `small_wooden_chandelier` | `ExtendedLightChandelierBlock` | Deed-placed — hard veto |
| `large_iron_chandelier` | `ExtendedLightChandelierBlock` | Deed-placed — hard veto |
| `small_iron_chandelier` | `ExtendedLightChandelierBlock` | Deed-placed — hard veto |
| `candelabra_tall` | `CandelabraBlock` | Its `BlockItem` is a `RaisedBlockItem`, which places at an offset and mis-places silently through this path |
| `villa_lamp_post` | `VillaLampPostBlock` | Same `RaisedBlockItem` problem |
| `trash_barrel` | `TrashBarrelBlock` | Owner decision — a portable item destroyer is an exploit surface |

The `RaisedBlockItem` exclusions were caught **before** enrollment by
`GrabbyEnrollmentPreconditionTest`, which fails the build if a `RaisedBlockItem` is ever added to the
tags.

---

## 6. Appendix B — wine preservation matrix

Property names taken from the live record:

```java
public record WineData(String wineryName, String grapeType, int year,
                       int quality, String region, String labelColor)
```

Verified across two full place→pickup cycles, live by the owner (M13 steps 12–15) and
automatically by `aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload` and
`GrabbyWineRoundTripTest`.

| Property | Before place | Placed world state | After pickup | After 2nd cycle | Persistence verified |
|---|---|---|---|---|---|
| Registry/type (`wine_bottle_*`) | Source item | Matching block | Identical item | Identical | ✅ real NBT save/load |
| Display/custom name | On the stack | Held in `originStack` | Identical | Identical | ✅ carried in the whole stack |
| `wineryName` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| `grapeType` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| `year` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| `quality` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| `region` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| `labelColor` | Set | In `WineData` on the BE | Identical | Identical | ✅ |
| Origin stack (`OriginStack` NBT) | — | Written by `setPlacedBy` | Returned verbatim | Re-written | ✅ persisted; excluded from `getUpdateTag` |
| Provenance (`GrabbyState`) | — | `PLAYER` + placer UUID | Cleared with the block | Re-stamped | ✅ |

**This is not visual equivalence.** The mechanism is that the bottle stores the *entire* `ItemStack`
it was placed from and hands that exact stack back — so the six named fields survive, and so does
anything else on the stack, including components added in future. `GrabbyWineContractTest` scans the
sources to prove no Grabby class names a single wine field, which is what makes the guarantee
open-ended rather than a list that will rot.

---

## 7. Working tree

```
d7c317b3  Grabby Hands M0-M13         94 files, +11,413 / -25
```

M14 adds documentation only — no code, no assets, no data:

| File | Change |
|---|---|
| `docs/grabby-hands.md` | New — implementation documentation |
| `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_14_FINAL_REPORT.md` | New — this report |
| `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_13_ACCEPTANCE.md` | Modified — owner sign-off recorded |

(`ULTIMACRAFT_GRABBY_HANDS_TESTING_LIMITATIONS.md` was folded forward to M13 and is already in
`d7c317b3`.)

**One expected modification remains and must never be staged:**
`gradle/wrapper/gradle-wrapper.jar` — this worktree required the real 43,504-byte jar in place of a
133-byte Git LFS pointer. It is a local environment fix, not a change to the project.

There are no other unexplained changes. No push, merge, tag, release, or deploy has been performed
or is proposed.

---

## 8. What this feature does *not* do

Stated plainly, because the playbook asks that unsupported objects are not claimed as covered.

- **Only the 32 enrolled blocks and 30 enrolled items participate.** Nothing else, in either
  direction — an unenrolled block is refused by Grabby Hands just as firmly as by Adventure mode.
- **No entity is transportable.** This is a block system.
- **No block without a block entity can be enrolled as mobile**, because it cannot carry provenance.
- **No multi-block object is currently enrolled.** `GrabbyRootResolver` exists and resolves a
  canonical root, but nothing in the enrolled set exercises it.
- **Nothing ticks.** There is no periodic scan of placed litter, and placed objects are never
  silently despawned. Clutter is permanent until a player removes it.
- **Cities are not protected from littering** — an explicit owner decision.

### Open questions, non-blocking

- **OQ-3** — container nesting depth. Currently one level is refused outright (`NESTED_CONTAINER`).
- **OQ-5** — whether city container *destruction* warrants a stricter rule than city placement.
- **M11 pickup rights** — anyone may pick up a loose item another player placed on a host block.
  Consistent with "placement provenance is not an ACL", but it was an assumed default rather than a
  confirmed decision.

---

## 9. Summary

Grabby Hands is complete as scoped. All 14 gates passed, the automated suite is green at 1919 JUnit
and 364 GameTests, and the owner has approved the full 23-step live Adventure-mode acceptance pass.

The design goal was that existing object systems remain authoritative and that no permission is
broadened to achieve any of this. Both hold: sitting, opening, drinking, locking, lockpicking, city
protection, house ownership and tree harvesting all behave exactly as they did before, and the only
thing that changed about Adventure mode is that 62 specific enrolled objects can now be picked up
and put down again.
