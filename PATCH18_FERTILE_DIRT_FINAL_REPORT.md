# Patch 18 Fertile Dirt Recovery — Final Integration Report

Date: 2026-08-24
Branch: `patch-18`
HEAD: `e57f3e3e2530fbbe5f90c14774d2f0f9fb19e595`

## Final verdict

Milestone 8 implementation and automated regression are complete.

- The implemented Patch 18 system passes its full server-side gameplay loop.
- Focused Patch 18 and subsystem JUnit coverage passes.
- The complete repository JUnit suite passes.
- All 819 GameTests pass from a clean GameTest world.
- The existing shared `run/gametest` world still reproduces the single Milestone 0 baseline managed-deposit identity conflict; no Patch 18 GameTest fails.

The implementation is technically integrated, but a **Survival-complete release claim remains blocked** by two product decisions already recorded at Milestone 0: the repository has no approved Survival acquisition route for `britannia_mod:empty_bowl` or `britannia_mod:britannia_shovel`. The integration GameTest provisions those two entry items and then exercises the real gameplay services. No recipe, vendor mapping, or other economy route was invented during this patch.

## Final gameplay behavior

### Dung lifecycle

- Dung is the internal WildResource `britannia_mod:dung` and uses the existing WildResource scheduler, node ledger, per-chunk cap, cooldown, reconciliation, and harvest event paths.
- A natural node can be placed only in a safe target directly above exact `minecraft:dirt` or `minecraft:coarse_dirt`.
- Grass, stone, sand, air support, wet targets, and targets with block entities are rejected.
- Naturally scheduled dung is tracked. A successful Survival or Adventure harvest removes the node, schedules its respawn, and grants exactly one `britannia_mod:dung` item.
- Creative removal grants no economic drop. Repeated, simultaneous, fake-player, foreign-house, unsupported, and already-removed harvest paths grant nothing.
- The dung commodity is a plain item, not a `BlockItem`; players cannot place untracked dung nodes.

### Renewable custom dirt

- Main-hand use of the exact `britannia_mod:britannia_shovel` on exact `minecraft:dirt` grants one `britannia_mod:dirt`.
- The terrain block is not broken, replaced, flattened, or otherwise mutated.
- The action is server-authoritative, player-scoped, protected by reach/world/house checks, and works for real Survival and Adventure players.
- Creative and fake players cannot create the dirt commodity.
- The gesture does not invoke the Mining extraction/deposit pipeline and does not award Mining progression.
- A successful gather charges one shovel durability and claims a persistent 1,200-tick cooldown. Immediate packet repeats, a replacement shovel, a held-item swap, and hand switching do not bypass it.

### Bowl preparation and water

The custom preparation chain has a fixed hand contract:

1. Main `britannia_mod:empty_bowl` + offhand `britannia_mod:dirt` -> `britannia_mod:bowl_of_dirt`.
2. Main `britannia_mod:bowl_of_dirt` + offhand `britannia_mod:dung` -> `britannia_mod:bowl_of_fertile_dirt`.
3. Exact `britannia_mod:empty_bowl` filled from source water or the UltimaCraft well -> `britannia_mod:bowl_of_water`.
4. Main `britannia_mod:bowl_of_fertile_dirt` + offhand `britannia_mod:bowl_of_water` -> one canonical `britannia_mod:fertilized_dirt` + two `britannia_mod:empty_bowl` containers.

All mutations occur on the server after revalidating both live hand stacks. Each successful callback consumes exactly one paid input set in Survival and Creative. Reversed hands, the offhand callback, vanilla bowls, `empty_pewter_bowl`, vanilla dirt items, stale plans, unpaid repeat packets, and wrong ingredients pass without mutation.

Source-water targeting has priority over the dry dirt tuple. Only a true water source is valid; flowing water and non-water targets are rejected. Filling does not consume the source. The existing well remains public, unlimited, and stateless, with the same `mayInteract` world gate used for its other supported containers.

### Five-harvest lifecycle

- Applying canonical `britannia_mod:fertilized_dirt` to exact vanilla dirt or the prepared community plot initializes five remaining successful harvests.
- Only the authoritative successful crop-harvest commit or accepted ripe-fruit click decrements the count.
- Planting, growth, immature attempts, wrong tools, unrelated interaction, and a second stale player interaction do not decrement it.
- Annual community uses 1–4 retain the FarmingBlock and reopen the existing 1,200-tick seed window. The fifth returns to the base community farm block.
- Ordinary soil returns to vanilla dirt after the fifth paid harvest.
- Perennial crop regrowth remains intact while uses remain.
- Each ripe fruit click is one harvest. The fifth click grants its fruit, cleans up the tree without bonus drops, and exhausts the soil.
- Flower conversion snapshots preserve the remaining count but flowers themselves do not spend it.
- Existing house plots remain permanent/untracked because this item cannot be applied to them.

## Exact registry identities

| Role | Registry ID | Registry/type |
| --- | --- | --- |
| Custom dirt commodity | `britannia_mod:dirt` | item, plain stackable item |
| Dung commodity | `britannia_mod:dung` | item, plain non-placeable item |
| Dung natural node | `britannia_mod:dung` | block, scheduler-owned WildResource block |
| Empty custom bowl | `britannia_mod:empty_bowl` | item |
| Bowl of dirt | `britannia_mod:bowl_of_dirt` | item |
| Bowl of fertile dirt | `britannia_mod:bowl_of_fertile_dirt` | item |
| Bowl of water | `britannia_mod:bowl_of_water` | item |
| Canonical fertile output | `britannia_mod:fertilized_dirt` | existing item; no duplicate `fertile_dirt` ID |
| Required gather tool | `britannia_mod:britannia_shovel` | existing tool item |
| Dung WildResource entry | `britannia_mod:dung` | internal WildResource registry |

The existing Earth Elemental loot table continues to output `britannia_mod:fertilized_dirt`; the Patch 18 final mix resolves the same item holder.

## Balance values

| Mechanic | Shipped value |
| --- | --- |
| Dung spawn weight | 1 |
| Dung per-chunk cap | 2 tracked nodes |
| Dung spawn-attempt interval | 3–6 minutes |
| Dung post-removal cooldown | 20–40 minutes |
| Dung probes per attempt | 4 |
| Dung same-type spacing | 8 blocks; the existing strict comparison permits the exact-radius boundary |
| Dirt gathering cooldown | 1,200 ticks / 60 seconds per player |
| Cooldown feedback throttle | 40 ticks / 2 seconds |
| Community re-seed window | 1,200 ticks / 60 seconds |
| Fertilized-dirt uses | 5 successful harvests |

Dung uses the closest existing sulphurous-ash cadence. Its exact support rule means normal grass-covered terrain is not eligible; live-world density remains a balance check.

## Five-harvest storage and compatibility

`FarmingBlockEntity` owns the authoritative integer `RemainingFertileHarvests`.

- A canonical application initializes it to 5.
- Successful commits decrement it through `consumeSuccessfulFertileHarvest()` and synchronize the block entity.
- Values serialize in disk NBT and the existing update-tag/update-packet path.
- Flower soil snapshots serialize and restore the same value while a flower temporarily owns the block position.
- Saved values are clamped to 0–5 on load.
- Missing legacy NBT maps to `-1`, the explicit untracked/unlimited sentinel. Existing worlds are not retroactively exhausted.
- Zero never underflows and immediately transitions a current non-house plot to its established exhausted state after the paid output has been committed.

The integration GameTest serializes, removes, recreates, and reloads the FarmingBlockEntity after harvest two, then completes uses three through five. Unit coverage separately round-trips disk and client update tags.

## Exploitation audit

| Attempt | Result/evidence |
| --- | --- |
| Spam clicking | Dirt cooldown, dry preparation, and final mixing repeat tests grant only one output per paid action. |
| Packet double execution | Main-hand transactions revalidate live stacks; unpaid repeat calls pass without mutation. |
| Main/offhand duplication | Only the fixed main-hand owner executes; offhand and reversed-hand callbacks are rejected. |
| Stack underflow/overflow | Stale count/component plans are rejected atomically; stacked and full/near-full inventory cases preserve exact deltas or drop only the exact remainder. |
| Creative duplication | Gather/dung economy is denied; bowl transformations deliberately debit exact inputs even in Creative and conserve outputs. |
| Relog/death cooldown bypass | Cooldown and feedback timestamps live in persistent player data and are copied through `PlayerEvent.Clone`; a real reconnect remains a live-client check. |
| Held-item cooldown bypass | Replacing or moving the shovel does not change the player-scoped cooldown. |
| Vanilla bowls | `minecraft:bowl` and legacy `britannia_mod:empty_pewter_bowl` are rejected. |
| Vanilla dirt ingredient | `minecraft:dirt` as an item is rejected; only `britannia_mod:dirt` is accepted. |
| Mining pathway | Architecture tests forbid Mining/deposit calls and runtime tests verify Mining skill is unchanged. |
| Invalid dung support | Placement rejects it; reconciliation removes tracked unsupported dung without loot and starts cooldown. |
| Dung break/replacement multiplication | Ledger/block rechecks allow one winner; Survival/Adventure yield one, Creative and administrative invalidation yield zero. |
| Two-player interaction | Dirt cooldowns are independent; bowl transactions are inventory-local; one dung node and one mature crop permit only one winner. |
| Chunk/reload during fertility use | Authoritative NBT/update tags and mid-sequence block-entity reload retain the exact remaining count. A forced real chunk unload is not practical while the GameTest template chunk is held loaded. |

## Automated validation

### Compilation

`./gradlew compileJava compileTestJava`

- PASS.

### Focused regression matrix

Command groups covered Patch 18, bowl preparation, dirt gathering, WildResource, farming, Mining, structure/protection, managed deposits, water access, and the cross-system asset audit.

- 757 tests run.
- 751 passed.
- 6 skipped.
- 0 failures or errors.

### Complete JUnit suite

`./gradlew test`

- 3,140 tests run.
- 3,123 passed.
- 17 skipped.
- 0 failures or errors.

### Patch 18 server scenario

The new `Patch18FullLoopGameTests.renewableIngredientsCompleteTheFiveHarvestLoopWithoutDuplication` test passes in isolation. Together with the Milestone 1–7 server tests, the patch contributes 35 passing GameTests covering registry/assets, dung, dirt gathering, bowls, water/well behavior, final mixing, persistence, farming exhaustion, multiplayer, protection, and exploitation cases.

### Complete GameTest suite

`./gradlew runGameTestServer --no-configuration-cache`

- Shared existing `run/gametest` world: 819 run, 818 passed, 1 failed.
- Sole failure: `authoredgeometrysurvivesreconstructionandmaterializes` with `deposit_identity_conflict`; the persisted ID already described the same silver/admin/geometry seed at unrelated old coordinates. This is the exact managed-deposit dirty-world baseline identified before Milestone 1.
- Fresh build-local GameTest directory, same code and enabled namespace: 819 run, all 819 passed.

This A/B result classifies the shared-world failure as pre-existing persisted test state rather than a Patch 18 or current-code regression. The shared baseline world was not deleted or rewritten.

## Known limitations and release blockers

1. **No approved Survival source for `empty_bowl`.** The item exists, is creative-tab visible, and works through the complete chain, but no vendor, loot, crafting, or world-acquisition route was authorized.
2. **No approved Survival source for `britannia_shovel`.** The required existing tool likewise lacks a repository-visible Survival acquisition route.
3. **Exposed support is intentionally strict.** Dung cannot spawn above grass blocks; it needs exposed exact dirt or coarse dirt. Natural density needs live terrain observation.
4. **No dung biome/region/dimension/house spawning filter.** The existing WildResource manager considers every server level; exact support is the shipped environmental restriction. Player-placed dirt can therefore be eligible.
5. **Invalid-support cleanup follows WildResource reconciliation timing.** A tracked node in a continuously loaded chunk can remain until the existing load-triggered reconciliation runs. It does not produce loot when reconciled away.
6. **Well behavior remains public and unlimited.** Patch 18 did not retrofit house ownership semantics onto all well container families.
7. **Legacy farming blocks remain unlimited.** Provenance cannot be recovered for block entities saved before the new NBT key existed.
8. **House farm plots remain untracked.** Fertilized dirt cannot be applied there under current farming rules.
9. **Fruit trees spend one use per ripe-fruit click.** This is the explicit Milestone 0 unit, not one use per whole canopy cycle.
10. **Dedicated restart and forced chunk-unload checks are represented by NBT/update-tag and block-entity reload tests.** A manual dedicated-server save/restart remains useful release QA.
11. **The shared GameTest directory contains stale managed-deposit identity data.** A clean run proves the repository suite passes; the shared directory should only be reset under an explicit test-environment maintenance decision.

## Art and asset status

No load-blocking Patch 18 asset is missing.

- All six new items have models and language entries.
- Dirt intentionally uses the vanilla dirt texture through its generated item model.
- Dung has a custom transparent 32×32 pile texture, cutout block model, blockstate, item model, and exact-one loot table.
- The four bowl states use distinct custom transparent 16×16 textures and generated item models.
- Automated asset contracts validate existence, dimensions, transparency, distinct bowl hashes, IDs, loot, and creative-tab exposure.

The custom sprites are functional code-authored pixel art. Final artist approval, in-hand/inventory readability review, and in-world dung depth/z-fighting review are still required before calling the visuals release-final.

## Live-client checks still required

- Confirm custom shovel right-click prediction and action-bar cooldown feedback in Survival and Adventure with real network latency.
- Confirm main/offhand animations do not visually double-fire for every bowl step.
- Inspect source-water and root/child well filling with a real client.
- Inspect dung pile scale, transparency, collision, lighting, and break feedback in several biomes.
- Observe real-world exposed-dirt/coarse-dirt dung density over time and tune cadence only with product approval.
- Perform a dedicated-server save, stop, restart, and harvest continuation check.
- Validate the eventual bowl and shovel acquisition routes against the live vendor/economy system once product assigns them.

## Branch and dirty-worktree status

- Branch remains `patch-18` at `e57f3e3e2530fbbe5f90c14774d2f0f9fb19e595`.
- Local `origin/patch-18` comparison is 0 ahead / 0 behind.
- No commit, merge, rebase, reset, branch integration, or cleanup was performed.
- The worktree remains intentionally dirty with the accumulated Milestone 1–8 implementation and tests.
- The pre-existing staged Patch 18 notes/image and unrelated staged model/texture edits remain untouched.
- `build.gradle` may appear modified because of line-ending/stat metadata, but its normalized content hash exactly matches the index (`886ac6688c476c003db28c306d68305fc2c36e7e`). All temporary isolated/clean GameTest settings were reverted and their generated directories removed.
- `git diff --check` passes; only existing LF-to-CRLF checkout warnings are emitted.

## Acceptance matrix

| # | Condition | Result |
| --- | --- | --- |
| 1 | Dung uses WildResource | PASS |
| 2 | Natural dung support is dirt/coarse dirt only | PASS |
| 3 | Dung break yields exactly one | PASS |
| 4 | Custom empty bowl exists; vanilla bowl rejected | PASS; Survival acquisition route still unassigned |
| 5 | Custom shovel gathers custom dirt without breaking terrain | PASS |
| 6 | Gathering is independent of Mining | PASS |
| 7 | Server-authoritative anti-spam cooldown | PASS |
| 8 | Empty bowl + custom dirt | PASS |
| 9 | Bowl of dirt + dung | PASS |
| 10 | Source-water filling | PASS |
| 11 | Water-well filling | PASS |
| 12 | Final mix outputs canonical fertilized dirt | PASS |
| 13 | Two custom bowls returned | PASS |
| 14 | Earth Elemental loot remains canonical | PASS |
| 15 | Exactly five successful harvests per application | PASS |
| 16 | Only successful harvest decrements | PASS |
| 17 | Fertility persistence and sync | PASS |
| 18 | Multiplayer cannot duplicate/double-decrement | PASS |
| 19 | Adventure/protection behavior | PASS in automated server coverage; live-client QA remains |
| 20 | WildResource/farming/Mining/regressions healthy | PASS in a clean world; shared-world baseline documented |

**Milestone 8 engineering gate: PASS.**
**Survival-complete release gate: BLOCKED pending approved `empty_bowl` and `britannia_shovel` acquisition routes plus the listed live-client checks.**
