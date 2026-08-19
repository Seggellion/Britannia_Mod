# Managed Vegetation Remediation — `patch-18`

## Codex handoff

- Branch: `patch-18`
- Baseline inspected: `792ade47`
- Prepared: 2026-08-18
- Scope of this file: implementation task list and durable project memory only. No feature code was changed while creating it.
- Before implementation, re-read the current worktree and preserve all unrelated user changes. In particular, do not fold unrelated asset, fence, or foundation work into this patch.

## Feature invariants to preserve

- Managed vegetation occupies air above ordinary grass/dirt-family substrate; it never replaces the supporting grass block.
- If any required air position is occupied by unrelated construction, managed vegetation must not spawn there or overwrite it.
- Ordinary/unmanaged plants retain vanilla behavior.
- Non-admin Survival and Adventure players cut managed vegetation with any item recognized by `ManagedVegetationCutTools.isSword`, including Britannia grain-harvest blades.
- A successful managed cut remains server-authoritative, produces no vanilla block drop, and returns the node to its existing regrowth lifecycle.
- Permanent node deletion remains an explicit administration operation such as `/managedvegetation remove`; ordinary breaking is not permanent deletion.

## Five requirements

### 1. Slow natural vegetation spawning by 50%

- [ ] Increase the interval between natural discovery/spawn attempts by 50%, equivalent to reducing the current placement rate to two-thirds of its current value.
- [ ] Apply this specifically to natural node discovery/spawn placement. Do not silently stretch sword-cut regrowth, short-to-tall grass growth, flower stage timing, or retry timing.
- [ ] The current defaults are `4096 * 2 = 8192` effective random-tick opportunities per success. The target equivalent is `12288`.
- [ ] Make the change effective for existing installations that already contain `naturalGrowthSpeedDivisor = 2`; changing only a Java default is insufficient.
- [ ] Keep the debug/status output accurate so it reports the actual effective chance after the 50% interval increase.
- [ ] Update the checked-in development config if it remains tracked or used by tests.

Implementation note: a runtime `3/2` interval scale, or a newly added config value that is safely defaulted/migrated for old config files, will satisfy the migration requirement. Avoid requiring server owners to delete their config.

### 2. Placing a block must relinquish vegetation ownership

- [ ] When a successful player/entity placement claims any position reserved by a managed node, retire that node's persisted ownership without deleting the newly placed block.
- [ ] Cover both single-block and multi-block placement events and all reserved positions used by the node (base, tall-grass upper half, and clearance positions where applicable).
- [ ] Process only successful, non-cancelled placements. A cancelled placement must leave the node and vegetation unchanged.
- [ ] Construction wins: reconciliation must never restore vegetation over an unrelated placed block.
- [ ] Once ownership is retired, the replacement block must have its ordinary vanilla break rules; it must never become sword-only.

### 3. Eliminate the post-cut ghost block

- [ ] After a managed plant is cut, the vacated coordinate must immediately accept normal block placement even while the node is regrowing.
- [ ] Make `managed_vegetation_controller` replaceable while retaining its invisible/no-collision behavior if the controller architecture is kept.
- [ ] On successful replacement of the controller, retire the regrowing node without removing the replacement block.
- [ ] Guard all break/left-click interception with a live ownership check. Persisted metadata alone must not authorize cancellation of a vanilla break.
- [ ] Reconciliation must distinguish a valid representation, safely repairable air/owned partial state, and unrelated obstruction. Retire stale ownership on unrelated obstruction rather than retrying forever.
- [ ] The same rules must survive save/reload and chunk unload/reload.

### 4. Creative users and admins can break with anything

- [ ] Treat a player as elevated when `player.isCreative()` or `player.hasPermissions(2)` is true, matching existing Britannia administrator conventions.
- [ ] Elevated players may break/cut managed vegetation with an empty hand or any equipped item in any game mode, including an administrator currently in Adventure mode.
- [ ] Non-admin Survival and Adventure players remain sword-only.
- [ ] Centralize the authorization decision so `LeftClickBlock`, `BreakEvent`, the server-side cut transaction, and the Adventure client path cannot disagree.
- [ ] Do not damage or consume the held item for the Creative/admin bypass. Normal non-admin sword cuts retain the current durability cost.
- [ ] Continue routing elevated breaks through the atomic managed-cut transaction so there are no duplicate drops, half-removed tall plants, or stale node data.

Permission matrix:

| Player | Held item | Expected result |
| --- | --- | --- |
| Adventure, non-admin | recognized sword/harvest blade | managed cut succeeds |
| Adventure, non-admin | hand or non-sword | break is denied |
| Survival, non-admin | recognized sword/harvest blade | managed cut succeeds |
| Survival, non-admin | hand or non-sword | break is denied |
| Creative | hand or any item | managed cut succeeds; no durability cost |
| Permission level 2+ in any mode | hand or any item | managed cut succeeds; no durability cost |
| Any player targeting a replacement block no longer owned by a node | any item | ordinary block behavior; no managed cancellation |

### 5. Use the Britannia fern, not the vanilla fern

- [ ] Replace every managed-vegetation use of `Blocks.FERN` with `BlockRegistry.FERN.get()` (`britannia_mod:fern`).
- [ ] Update spawn, reconciliation, owned-state checks, Adventure-mode client candidate checks, tests, and user-facing config comments.
- [ ] Keep the managed profile entry id `britannia_mod:fern`; this is already the correct logical id.
- [ ] Do not create a second fern block or duplicate model/texture. Reuse the existing registered block and assets:
  - `BlockRegistry.FERN`
  - `ItemRegistry.FERN_ITEM`
  - `assets/britannia_mod/blockstates/fern.json`
  - `assets/britannia_mod/models/block/new_assets/fern.json`
- [ ] Confirm the custom fern survives on the same ordinary grass/dirt substrates used by managed growth and remains replaceable.
- [ ] Do not change unrelated vanilla-fern checks elsewhere in the mod unless they specifically represent managed vegetation.

## Confirmed defect causes in the current implementation

- `ManagedVegetationInteractionHandler` cancels left-click and break events whenever persisted node metadata resolves, before confirming that the live block is still the node's owned representation. A stale node therefore imposes sword-only behavior on a replacement block.
- `ManagedVegetationControllerBlock` is invisible and has empty collision/outline behavior, but its registration is not replaceable. The controller left after `cutNode` is the reported unplaceable ghost coordinate.
- Reconciliation currently returns only a boolean and repeatedly schedules retries when it encounters unrelated construction. It lacks an explicit obstructed/stale-ownership outcome.
- There is no placement-event ownership handoff covering all claimed positions, especially multi-place blocks.
- The managed fern spawn, reconciliation, ownership, and client candidate paths currently reference `Blocks.FERN` even though `BlockRegistry.FERN` already exists.
- `ManagedVegetationService.cutNode` enforces a sword internally and durability is skipped only for `instabuild`; it does not currently implement the permission-level admin bypass.

## Recommended implementation order

1. [ ] Add tests that reproduce the current five failures before changing behavior.
2. [ ] Introduce one shared cut authorization policy for recognized swords versus Creative/admin bypass.
3. [ ] Add a live-representation/ownership predicate and use it before event cancellation and cutting.
4. [ ] Make the controller replaceable and implement placement-driven node retirement for single and multi-place events.
5. [ ] Refactor reconciliation to represent `VALID`, `REPAIRABLE`, and `OBSTRUCTED` outcomes; retire ownership for unrelated obstruction.
6. [ ] Change managed fern state references to `BlockRegistry.FERN.get()`.
7. [ ] Apply the 50% natural-spawn interval increase with existing-config compatibility.
8. [ ] Run focused unit tests, GameTests/in-world cases, and then the broader project test suite.

## Likely source areas

- `src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationConfig.java`
- `src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationService.java`
- `src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationManager.java`
- `src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationCutTools.java`
- `src/main/java/com/seggellion/britannia_mod/event/ManagedVegetationInteractionHandler.java`
- A placement-event handler under `src/main/java/com/seggellion/britannia_mod/event/`
- `src/main/java/com/seggellion/britannia_mod/block/ManagedVegetationControllerBlock.java`
- `src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java`
- `src/main/java/com/seggellion/britannia_mod/mixin/client/ManagedVegetationAdventureModeMixin.java`
- Existing tests under `src/test/java/com/seggellion/britannia_mod/vegetation/`
- New integration/GameTests if unit seams cannot prove event ordering and world replacement behavior

## Required regression coverage

- [ ] Natural-spawn effective interval is exactly 1.5 times the prior effective interval, including when loading an old config containing divisor `2`.
- [ ] Cutting short grass places a regrowing controller; placing stone at that coordinate succeeds, preserves stone, and removes node ownership.
- [ ] Placing a block by replacing occupied managed short grass preserves the placed block and retires the node.
- [ ] Placing a two-block structure through a tall-grass reserved position retires the correct base node without deleting either placed block.
- [ ] A cancelled placement does not retire ownership.
- [ ] A stale node under an unrelated solid block does not cancel that block's break and is retired during reconciliation.
- [ ] Reconciliation never overwrites unrelated construction and does not retry an obstructed stale node forever.
- [ ] Save/reload does not resurrect vegetation through a player-built block.
- [ ] Adventure non-admin: sword succeeds; bare hand and arbitrary item fail.
- [ ] Creative: bare hand and arbitrary item succeed with no held-item durability loss.
- [ ] Permission-level-2 Survival and Adventure players: bare hand and arbitrary item succeed with no held-item durability loss.
- [ ] Sword cuts still cost one durability point for non-admin players and emit only the existing managed cut event/drop behavior.
- [ ] Managed fern spawning produces `britannia_mod:fern`, not `minecraft:fern`.
- [ ] Custom fern resolves as owned, cuts correctly, regrows correctly, reconciles correctly, and is allowed through the Adventure client path.
- [ ] Unmanaged vanilla fern and ordinary placed blocks remain unaffected by managed handlers.

Suggested focused command:

```powershell
.\gradlew test --tests "com.seggellion.britannia_mod.vegetation.*"
```

## Definition of done

- [ ] All five requirements above are implemented on `patch-18`.
- [ ] Automated regressions cover authorization, placement handoff, ghost-controller replacement, reconciliation, timing, and fern identity.
- [ ] Manual Adventure, Survival, Creative, and permission-level-2 checks agree with the permission matrix.
- [ ] Existing server config files receive the slower natural-spawn behavior without manual deletion.
- [ ] No managed path can overwrite unrelated construction or apply sword-only rules to a block it does not currently own.
- [ ] No unrelated worktree files are modified or included with the feature changes.
