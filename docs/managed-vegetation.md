# Managed Vegetation

Managed vegetation nodes are persistent, server-owned regrowth points. A node's canonical
position is the plant block directly above a vanilla grass block. Nodes require the canonical
position and the next two blocks above it to remain clear and dry. Nearby vanilla or
player-placed vegetation is not managed and retains its normal behavior.

## Lifecycle

The default weighted profile is:

| Family | Weight | Lifecycle |
| --- | ---: | --- |
| Grass | 75 | vanilla short grass -> vanilla tall grass |
| Fern | 20 | vanilla fern |
| UltimaCraft flower | 5 | random registered species, stages 1 -> 7 |

Cutting any occupied managed node returns it to the invisible controller state and schedules a
randomized regrowth. Managed plants are cut only when
`GrainHarvestTools.isGrainHarvestBlade` accepts the player's main-hand item. The complete plant
is removed without drops, accepted non-creative blades take one durability, and either half of
tall grass resolves to the same logical node. Unmanaged plants never enter this policy.

Managed wild flowers are intentionally independent of `FarmingBlock` and farm ownership.
`ManagedFlowerBlockEntity` is only a synchronized visual projection. The node's `SavedData` is
authoritative. Species come directly from `FlowerRegistry`; the seven stage contract, each
species' `baseGrowthTicks`, baked stage models, textures, dye-mask rendering, and fallback tint
all reuse the existing flower implementation.

## Configuration

NeoForge generates the per-world server config `britannia-managed-vegetation.toml`. The
`managedVegetation` section contains:

- `grassWeight`, `fernWeight`, and `flowerWeight` (defaults 75, 20, and 5)
- `cutRegrowMinTicks` and `cutRegrowMaxTicks`
- `grassGrowthMinTicks` and `grassGrowthMaxTicks`
- `retryTicks`
- `flowerStageTickMultiplier`

Weights are relative. To retain an exact five-percent flower chance, keep the three weights at
75/20/5 or another ratio where the flower weight is five percent of the total.

New vegetation families are added by defining a `ManagedVegetationEntry` in the centralized
`ManagedVegetationProfile` and implementing its `ManagedVegetationGrowthStrategy` transition.
The weighted selector itself does not change when entries are added.

## Administration

All commands require permission level 2 and a loaded target position:

```text
/managedvegetation add <x y z>
/managedvegetation remove <x y z>
/managedvegetation inspect <x y z>
/managedvegetation force <x y z>
/managedvegetation reroll <x y z>
```

`add` expects the canonical plant position, not the supporting grass block. `force` makes the
current scheduled transition due on the next server tick. `reroll` safely removes an owned plant,
returns the node to regrowth, and makes the next selection due. Neither internal block is exposed
as a survival item or Creative Tab entry.

## Persistence, recovery, and performance

Each dimension stores one schema-versioned `ManagedVegetationSavedData` record. Each node stores
its position, lifecycle, selected entry, optional flower species/stage, and absolute next game
time. Runtime indexes provide:

- a `TreeMap` due-time queue, polled at a maximum of 256 transitions per tick;
- a chunk-to-node index, used only when a chunk naturally loads;
- a bounded chunk-load queue of 4,096 entries per level, processing at most 64 chunks per tick.

There is no per-tick full-node scan, no forced chunk loading, no client timer, and no block entity
for grass, fern, tall grass, or the invisible controller. A lightweight block entity exists only
while a managed flower is visible. Unloaded overdue nodes retain their persisted due time and are
reconciled after their chunk naturally loads. Recovery repairs only air or the node's own partial
representation and never overwrites an unrelated block.

For thousands of nodes, ordinary tick cost is proportional to transitions currently due, not the
total node count. The main future optimization point, if transition bursts become very large, is
making the per-tick cap configurable or distributing same-tick deadlines more aggressively.

## Future job/service integration

Subscribe to `ManagedVegetationCutEvent` on the NeoForge event bus. It is posted once after a
successful logical cut and includes the server player, dimension, canonical node position,
vegetation entry ID, and optional flower species/stage. The vegetation system contains no
currency, Rails, NPC, quest, or profession dependency.

## Validation and limitations

Automated coverage includes persistence and corrupt-data quarantine, placement clearance,
weighted boundaries, authoritative flower discovery, all seven stages for all registered flower
species, ownership resolution, timing bounds, stale-queue removal, and a 10,000-cycle scheduling
simulation. The dedicated NeoForge server reaches `Done` on Java 21 with the feature registered.

The interactive 22-step player test matrix from the implementation playbook still requires a
human/client session. Use `reroll` and `force` to exercise each outcome quickly. The full repository
unit suite retains its pre-existing banner scaffold/intake failures; managed vegetation and farming
tests pass.
