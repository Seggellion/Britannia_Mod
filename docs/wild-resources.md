# Wild Reagents

Wild Reagents provides three persistent environmental resources, swamp Blood Moss, and a
client-only swamp atmosphere effect. Sulphurous Ash, Black-lipped Oysters, and Dung share one
server-authoritative loaded-chunk scheduler. Blood Moss deliberately remains part of managed
vegetation.

## Architecture

`WildResourceEntry` owns each resource's ID, weight, chunk cap, timing, probe count, candidate
strategy, placement rules, existing-node validator, placement behavior, harvest behavior, and
loot. `WildResourceManager` tracks only chunks delivered by normal chunk lifecycle events. It
rotates those chunks and active dimensions rather than walking every loaded chunk or world block.

Global server-tick ceilings are:

They are declared as `MAX_CHUNKS_PER_TICK`, `MAX_RESOURCE_ATTEMPTS_PER_TICK`, and
`MAX_RECONCILIATIONS_PER_TICK` in `WildResourceManager`.

| Work | Maximum per tick |
| --- | ---: |
| Loaded chunks considered | 16 |
| Due resource attempts | 32 |
| Persisted-node reconciliations | 16 |

One due entry is selected by weight for each considered chunk. Each entry then samples at most
its configured number of random surface candidates. Chunk availability is checked only with
`getChunkNow`; the system never force-loads a chunk. The global budget is distributed across
active dimensions in round-robin order, so a busy Overworld cannot starve Nether attempts.

Balance values live in `WildResourceEntries`:

| Resource | Weight | Chunk cap | Attempt interval | Respawn cooldown | Probes | Same-type spacing |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| Sulphurous Ash | 1 | 2 | 3–6 min | 20–40 min | 4 | 8 blocks |
| Black-lipped Oyster | 1 | 3 | 4–8 min | 30–60 min | 4 | 6 blocks |
| Dung | 1 | 2 | 6–12 min | 20–40 min | 4 | 8 blocks |

Intervals use normal server game time and do not depend on `randomTickSpeed`.
Dung's doubled attempt interval is specific to Dung and reduces its long-run placement-attempt
frequency by approximately 50% relative to the original 3–6 minute profile. Sulphurous Ash and
Black-lipped Oyster retain their existing cadence.

## Placement and proximity

Candidates use the loaded chunk's `MOTION_BLOCKING_NO_LEAVES` heightmap and target the air block
immediately above its top support. A target must be replaceable, dry, and have no block entity.
This prevents replacement of structures, fluids, containers, or unrelated decorations.

The environmental radius is 20 blocks using inclusive three-dimensional squared Euclidean
distance (`dx² + dy² + dz² <= 400`). Before searching, the validator confirms that every chunk
intersecting the sphere's bounding box is already loaded. If any is unavailable, the result is
`INCOMPLETE` and the attempt/reconciliation defers without loading it.

Sulphurous Ash requires lava in that radius and a sturdy natural surface. The allowlist includes
common stone, dirt, sand, gravel, Nether stone, basalt, blackstone, and magma surfaces; it excludes
player-building materials such as planks.

Black-lipped Oysters require water in that radius and a substrate in
`britannia_mod:black_lipped_oyster_substrates`. The established UltimaCraft limestone identity is
`minecraft:calcite`. The tag also contains optional, non-required `britannia_mod:limestone` so a
future registered block can join without changing scheduler code.

Dung is surface-only and uses a closed direct-support rule: the block immediately below must be
exactly `minecraft:dirt` or `minecraft:coarse_dirt`. Grass, farmland, mud, podzol, sand, stone,
tags, and custom soil are intentionally excluded. Because candidates use the exposed surface,
grass-covered terrain does not qualify until ordinary terrain changes expose valid support.

## Harvest behavior

| Content | Survival | Adventure | Creative |
| --- | --- | --- | --- |
| Sulphurous Ash | Ordinary break; exactly 1 existing Sulphurous Ash item | Any held item may harvest; exactly 1 Ash | Administrative removal; no drop/event |
| Black-lipped Oyster | Ordinary break; exactly 1 Black Pearl | Exact registered dagger required; non-daggers leave it intact | Administrative removal; no drop/event |
| Dung | Ordinary authorized break; exactly 1 Dung item | Any held item may harvest; exactly 1 Dung | Administrative removal; no drop/event |
| Blood Moss | Managed-vegetation sword cut; no item drop | Any vanilla `SwordItem`, including `QualitySwordItem`; no item drop | Managed administrative cut; no durability loss |

Oyster dagger recognition is the exact `WeaponRegistry.DAGGER` item identity. It intentionally
does not treat every sword, or every `QualitySwordItem`, as a dagger. Adventure decisions and the
atomic remove/account/drop sequence execute on the server. A second simultaneous harvest sees no
persisted node and cannot duplicate the Black Pearl.

Every loot-bearing WildResource harvest requires a real non-Creative player within block reach,
normal world interaction permission, and the applicable house build right. A tracked Adventure
gesture remains consumed when authorization or a resource-specific tool rule refuses it, so a
denied click cannot fall through into vanilla destruction. Ordinary Creative removal remains an
administrative no-drop path; fake-player automation produces no resource economy.

Successful loot-bearing wild-resource harvests post one `WildResourceHarvestEvent` containing the
server player, resource ID, immutable position, dimension, defensive result-stack copy, and tool
category (`DAGGER` or `OTHER`). Rejected attempts and Creative removals post no harvest event.
Blood Moss continues to use `ManagedVegetationCutEvent`.

## Persistence and reconciliation

Each dimension stores schema-versioned `britannia_wild_resources` saved data containing absolute
next-attempt times and exact managed node positions. Chunk unload/reload and server restart
therefore do not reset caps or produce trivial duplicate bursts.

When a chunk loads, reconciliation visits only persisted positions in that chunk:

- a valid owned block stays registered;
- an unavailable neighboring chunk defers validation;
- an owned block with removed lava/water or invalid substrate/support is removed and enters cooldown;
- an externally deleted block or player replacement is removed from the ledger and enters
  cooldown, but the world block is never changed;
- an unknown persisted resource ID is discarded safely.

There is intentionally no broad world scan and no adoption of untracked resource-shaped blocks.
That keeps ownership unambiguous and prevents recovery from overwriting player content.
Support reconciliation is chunk-load driven rather than an immediate neighbor-survival rule; this
keeps world removal, ledger removal, and cooldown scheduling in one authoritative transition.

## Blood Moss and swamp atmosphere

`SwampBiomeRules` is the single predicate used by gameplay and rendering. It uses NeoForge's
`Tags.Biomes.IS_SWAMP`, which includes vanilla swamp and mangrove swamp and remains datapack/tag
extensible.

The default managed-vegetation weights are:

| Biome profile | Grass family | Fern | Random flower | Blood Moss |
| --- | ---: | ---: | ---: | ---: |
| Non-swamp | 75% | 20% | 5% | 0% |
| Swamp | 75% | 20% | 0% | 5% |

The swamp entry reuses the configured `flowerWeight`; it replaces that slot rather than adding
another five percent. Blood Moss has one static managed lifecycle—no invented seven-stage crop
state—and reuses managed node persistence, regrowth timing, cutting, and recovery. Its block is
independent of `FarmingBlock` and reuses the existing reagent model/texture.

`SwampEnvironmentEffects` is registered only from the `Dist.CLIENT` bootstrap. For an air camera
in a configured swamp, it blends fog color by 12% toward RGB `(0.34, 0.46, 0.24)` and clamps each
channel. It does not alter fog distance/density, underwater/lava fog, textures, or server code.
The effect updates with the camera biome each frame; no separate temporal fade state is retained.

## Placeholder assets

Stable runtime paths:

- `textures/block/wild_resource/sulphurous_ash_patch.png` — original 32×32 transparent placeholder
- `textures/block/wild_resource/black_lipped_oyster.png` — original 32×32 transparent placeholder
- `textures/block/wild_resource/dung.png` — 32×32 transparent Patch 18 resource art
- `textures/item/wild_resource/black_pearl.png` — original 16×16 transparent placeholder
- `textures/item/reagent_blood_moss.png` — pre-existing 32×32 reagent art reused as requested
- `textures/item/reagent_sulphurous_ash.png` — pre-existing item art reused

The original three missing Wild Reagents visuals were created with the built-in image generator,
then locally keyed and downscaled without external artwork. Patch 18 added Dung through the same
asset workflow:

- Ash prompt: pale yellow/yellow-gray low sulfur ash/crystal pile, low-profile, crisp 16-bit game
  sprite on a magenta key background. Source: local image-generator output.
- Oyster prompt: dark black-purple Black-lipped Oyster shell cluster with a lighter lip,
  low-profile on pale stone, crisp 16-bit game sprite on a green key background. Source: local
  image-generator output.
- Pearl prompt: single dark Black Pearl with a small bright highlight, crisp 16-bit inventory
  icon on a green key/transparent background. Source: local image-generator output.
- Dung prompt: one compact brown animal-dung pile viewed directly from overhead, crisp low-resolution
  pixel art on a transparent background. Source: local image-generator output.

Models, blockstates, loot tables, localization, creative entries, dimensions, alpha, and texture
references are protected by `WildResourceAssetContractTest`.

## Validation and manual QA

Automated results at the Patch 18 Milestone 2 gate:

- focused WildResource suite: 46/46 passed;
- full JUnit suite: 3,109 run, 3,092 passed, 0 failed, 17 skipped;
- dedicated GameTest server: 791 run, 790 passed. The sole failure is the pre-existing persisted
  `deposit_identity_conflict` in `authoredgeometrysurvivesreconstructionandmaterializes`; all five
  Patch 18 Milestone 2 lifecycle/authority scenarios passed.

Manual in-client checks still recommended before release:

1. Observe ash near lava and confirm none beyond 20 blocks.
2. Observe oysters on calcite/limestone near water and reject other substrates/ranges.
3. In Adventure, try hand, non-dagger sword, and the registered dagger on an oyster.
4. Confirm one pearl and repeat with two players attempting the same oyster.
5. Enter/leave both swamp variants and inspect the subtle fog-color change.
6. Use `/managedvegetation spawn blood_moss <x y z>` in a swamp, cut it, and confirm regrowth.
7. Restart during resource cooldown, unload/reload its chunk, and confirm no duplicate burst.
8. Remove the environmental fluid/substrate and verify safe reconciliation.
9. Observe Dung only on exposed dirt/coarse dirt, harvest it in Adventure and Survival, and verify
   that Creative, automation, and foreign-house attempts create no item.

There is no wild-resource force/debug command in this milestone, so natural spawn observation uses
the centralized attempt intervals above. Future reagents should register another
`WildResourceEntry`; payments, profession XP, quests, jobs, and additional reagent types remain
deferred consumers/extensions rather than scheduler responsibilities.
