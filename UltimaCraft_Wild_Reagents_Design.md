# UltimaCraft Wild Reagent & Swamp Environment System
## Design Document

**Status:** Proposed  
**Target:** UltimaCraft / NeoForge 1.21.1 / Java 21  
**Feature family:** Wild reagent/resource spawning and swamp atmosphere  
**Dependency:** Implement after the managed vegetation/regrowth feature is stable.

## 1. Executive Summary

UltimaCraft needs a second ecological system related to, but distinct from, managed vegetation.

This feature introduces three world resources:

1. **Sulfurous Ash**
   - Randomly appears in eligible loaded chunks.
   - Valid only within 20 blocks of lava.
   - Uses an original placeholder block/model until final art exists.

2. **Black-lipped Oyster**
   - Randomly appears in eligible loaded chunks.
   - Must be on UltimaCraft limestone or vanilla calcite.
   - The candidate must be within 20 blocks of water.
   - In Adventure mode, it can only be harvested with a recognized dagger.
   - Successful authorized harvest gives exactly one **Black Pearl**.
   - Uses placeholder block/item art until final art exists.

3. **Blood Moss**
   - Appears only through the managed vegetation system in configured swamp biomes.
   - In swamps, the normal 5% flower slot becomes a 5% Blood Moss slot.
   - It must not create a second vegetation framework.
   - Uses placeholder art until final art exists.

The feature also adds a subtle green environmental tint/fog effect in swamp biomes to suggest swamp gas.

All authoritative spawning, harvesting, resource accounting, and loot decisions are server-side. Client code is limited to visual atmosphere.

---

## 2. Architectural Relationship to Managed Vegetation

Do not force Sulfurous Ash and Black-lipped Oysters into the vegetation node lifecycle.

Use two related systems:

```text
Managed Vegetation
  -> short grass
  -> tall grass
  -> fern
  -> flowers
  -> Blood Moss

Wild Resource Spawner
  -> Sulfurous Ash
  -> Black-lipped Oyster
  -> future environmental reagents
```

Reuse shared project abstractions where useful:

- weighted/configurable entries,
- scheduling conventions,
- biome helpers,
- persistence patterns,
- server-authoritative interaction hooks,
- event conventions.

Do not duplicate:

- vegetation node persistence,
- flower growth,
- `SwordType`,
- managed vegetation cut logic.

---

## 3. Mandatory Repository Discovery

Before implementation, Codex must inspect the repository after the managed vegetation feature lands.

Locate:

- final managed vegetation classes,
- its persistent node system,
- its scheduler,
- weighted vegetation profiles,
- cut/harvest event hooks,
- `SwordType`,
- any existing reagent items,
- any existing Sulfurous Ash, Blood Moss, Black Pearl, oyster, limestone, or calcite references,
- actual UltimaCraft limestone registry ID,
- all dagger items/classes/tags/types,
- Adventure-mode break logic,
- loot-table conventions,
- chunk load/unload hooks,
- server scheduler/tick infrastructure,
- world/chunk persistence,
- client biome/fog/color hooks,
- Creative Tab conventions,
- asset/model/texture conventions,
- dedicated-server client-isolation patterns,
- relevant tests.

Do not invent a duplicate dagger abstraction if a reusable one already exists.

---

## 4. Wild Resource Spawner

### 4.1 Goal

Sulfurous Ash and Black-lipped Oysters should opportunistically appear in already loaded chunks when local conditions are valid.

The system must not scan every block in every loaded chunk.

Preferred lifecycle:

```text
loaded chunk
-> scheduled resource attempt
-> choose candidate resource
-> sample bounded random candidate positions
-> validate resource-specific environmental conditions
-> enforce density/cooldown rules
-> place resource if valid
```

### 4.2 Resource Entry Model

A generic resource entry should conceptually support:

```text
id
spawn frequency / weight
max nodes per chunk
spawn cooldown
max probes per attempt
candidate generator
placement validator
substrate restrictions
biome restrictions
nearby block/fluid rule
harvest strategy
loot/result
```

Use existing project configuration/codec/registry conventions when possible.

Avoid giant resource-specific switch statements.

### 4.3 Performance Requirements

Forbidden:

- full-chunk scans every tick,
- iteration over every loaded block,
- continuous lava/water searches,
- force-loading neighboring chunks,
- client-driven spawning.

Preferred:

- bounded random probes,
- per-chunk attempt cooldown,
- per-resource density caps,
- early-exit proximity checks,
- processing only normally loaded chunks,
- persistent cooldown/resource accounting.

If a proximity check reaches an unloaded chunk, fail or defer the attempt rather than loading it.

---

## 5. Density, Frequency, and Cooldowns

All tuning values must be centralized.

Each wild resource should support:

```text
max_per_chunk
attempt_interval_min
attempt_interval_max
respawn_cooldown_min
respawn_cooldown_max
max_random_probes
optional minimum_same_type_spacing
```

Do not hard-code final balance values throughout event code.

Codex may choose conservative initial defaults, but the final report must identify exactly where they can be tuned.

---

## 6. Sulfurous Ash

### 6.1 Spawn Condition

A Sulfurous Ash node may spawn only when its final placement position is within **20 blocks of lava**.

The distance metric must be centralized and documented.

Preferred interpretation is squared Euclidean distance with radius 20, unless existing project conventions strongly favor another metric.

### 6.2 Safe Placement

"Anywhere within 20 blocks of lava" should not imply arbitrary destructive placement.

A candidate should:

- be air/replaceable,
- have a suitable solid support surface beneath,
- not replace lava or another fluid,
- not overwrite a block entity,
- not overwrite protected/custom structure content,
- not overwrite unrelated player construction,
- be in a loaded position.

No biome restriction is required.

### 6.3 Block and Item

Search for existing content first.

If missing:

- add `Sulfurous Ash` block,
- add a Sulfurous Ash reagent item if needed for harvesting,
- generate original placeholder art,
- use stable registry/resource paths so final art can replace it later.

### 6.4 Harvesting

No special harvesting tool has been specified.

Therefore:

- do not invent one,
- use existing project-standard resource-block break behavior,
- make Adventure behavior explicit,
- if a reagent item is needed, default to one Sulfurous Ash item per successful harvest unless the repository already defines another result.

---

## 7. Black-lipped Oyster

### 7.1 Spawn Condition

A Black-lipped Oyster may spawn only when:

1. Its support block is an allowed substrate.
2. Initial allowed substrates are:
   - UltimaCraft limestone,
   - vanilla calcite.
3. The oyster/candidate substrate is within **20 blocks of water**.
4. The target position is safe and replaceable.

The substrate list must be centralized/extensible.

### 7.2 Water Definition

At minimum, vanilla water fluid states count.

Codex must inspect whether UltimaCraft has custom water/fluid content that should also count.

Do not force-load chunks to satisfy the 20-block search.

### 7.3 Adventure-Mode Dagger Harvest

In Adventure mode:

- only a dagger recognized by the existing project tool/weapon architecture may harvest the oyster,
- a non-dagger attempt must not destroy the oyster,
- a non-dagger attempt must not produce a Black Pearl,
- validation is server-side.

Preferred identity resolution:

1. existing dagger-specific abstraction,
2. existing weapon family/category,
3. existing dagger tag,
4. only if none exists, add one in the style of the current weapon architecture.

Do not treat all `SwordType` items as daggers unless the project already does so.

### 7.4 Black Pearl

Successful authorized oyster harvest gives:

```text
1 Black Pearl
```

Search for an existing Black Pearl item first.

If absent:

- add the item,
- add localization,
- generate an original placeholder item texture/model.

Do not add randomized multi-pearl yields yet.

### 7.5 Game Mode Matrix

Adventure dagger-only behavior is mandatory.

Codex must inspect and document final Survival and Creative behavior rather than accidentally inheriting incorrect break rules.

Creative should normally preserve administrative removal behavior unless project conventions say otherwise.

---

## 8. Blood Moss

### 8.1 Biome Eligibility

Blood Moss may be selected only in configured swamp biomes.

At minimum, vanilla swamp must be supported.

Whether mangrove swamp counts should be an explicit centralized biome-tag/list decision based on project semantics.

Use one shared swamp-biome predicate for both Blood Moss and swamp atmospheric effects.

### 8.2 Managed Vegetation Integration

Blood Moss must use the managed vegetation system.

Default non-swamp profile remains:

```text
Grass family: 75%
Fern: 20%
Random flower: 5%
```

Default swamp profile becomes:

```text
Grass family: 75%
Fern: 20%
Blood Moss: 5%
Random flower: 0%
```

This means Blood Moss **replaces** the 5% flower slot in swamp, rather than adding another 5%.

Use biome-aware profiles or conditional entries. Do not duplicate weighted-selection code.

### 8.3 Lifecycle

Initial Blood Moss lifecycle:

```text
REGROWING
-> Blood Moss
-> cut
-> REGROWING
```

Do not invent seven growth stages for Blood Moss.

The architecture should permit later expansion if desired.

### 8.4 Harvesting

Reuse managed vegetation cutting semantics initially.

Therefore, unless the repository provides a reagent-specific override, managed Blood Moss should be cut by the existing `SwordType`.

Future vegetation entries should be able to override harvest strategy cleanly.

### 8.5 Item Result

Search for a Blood Moss item.

If absent, add one only if the established vegetation/loot system requires a reagent item result.

Document whether the initial cut produces Blood Moss as an inventory item or only clears/regrows the node.

---

## 9. Swamp Gas Environmental Hue

### 9.1 Goal

While the player is in a configured swamp biome, apply a subtle green atmospheric cast suggestive of swamp gas.

The effect must remain readable and should not feel like a solid green screen overlay.

### 9.2 Preferred Implementation

Inspect current NeoForge/client rendering hooks first.

Preferred order:

1. biome-specific fog color adjustment,
2. subtle biome-specific fog-distance/density adjustment if appropriate,
3. an existing project-standard color overlay,
4. custom shader only if the project already uses one and simpler hooks are unsuitable.

Do not recolor all textures.

Do not mutate block textures.

### 9.3 Behavior

Target characteristics:

- subtle green tint,
- configurable intensity,
- activates only in configured swamp biomes,
- smooth interpolation when entering/leaving if practical,
- no flickering at biome borders,
- state resets correctly on dimension changes,
- joining while already inside swamp works.

### 9.4 Dedicated Server Safety

All rendering classes and event registration must remain client-only.

A dedicated server must never classload client-only rendering classes because of this feature.

---

## 10. Placeholder Artwork

Final art does not exist.

Codex must generate original local placeholders and must not download artwork.

First inspect project asset resolution and conventions.

Suggested visual direction:

### Sulfurous Ash

- pale yellow/yellow-gray,
- ash/crystal pile,
- low-profile,
- visually distinct from sand/gravel.

### Black-lipped Oyster

- dark black-purple shell,
- lighter lip/interior,
- low-profile cluster,
- readable against pale limestone/calcite.

### Black Pearl

- dark pearl,
- small highlight,
- simple item icon.

### Blood Moss

- crimson/blood-red,
- low carpet/cross-plane plant,
- visually distinct from vanilla moss.

If separate Sulfurous Ash or Blood Moss item icons are needed, create matching placeholders.

Use stable final resource names even though the image content is temporary.

---

## 11. Chunk Lifecycle and Persistence

### 11.1 Loaded Chunks Only

Resource spawn attempts operate only in chunks loaded through normal server activity.

Never force-load chunks for reagent generation.

### 11.2 Persistent Accounting

Prevent trivial resource multiplication through:

- chunk unload/reload,
- server restart,
- repeatedly crossing chunk boundaries.

Recommended state may include:

```text
next_attempt_game_time
active resource positions or compact counts
resource cooldown
schema version
```

Use existing project persistence if possible.

### 11.3 Reconciliation

Handle:

- resource externally destroyed,
- lava/water source removed,
- oyster substrate removed,
- admin/world-edit changes,
- resource position occupied by unrelated content.

Never restore a reagent by overwriting an unrelated player block.

---

## 12. Proximity Validation

Shared validators should support the 20-block rules.

Common checks:

- loaded source/candidate positions,
- bounded search,
- early exit when match found,
- no forced loading.

Specific rules:

```text
Sulfurous Ash:
  within 20 blocks of lava

Black-lipped Oyster:
  on limestone/calcite
  within 20 blocks of water
```

Test the exact radius boundary.

---

## 13. Future Extensibility

The generic system should later support additional reagents such as:

- Garlic,
- Ginseng,
- Mandrake Root,
- Nightshade,
- Spider's Silk.

Future conditions may depend on:

- biome,
- substrate,
- nearby blocks/fluids,
- light level,
- height,
- weather,
- time,
- region,
- specialized tools.

Do not implement those resources now.

---

## 14. Future Profession/Event Integration

Expose or reuse a project-standard successful resource harvest event.

Conceptually:

```text
WildResourceHarvestEvent
- player
- resource id
- position
- dimension
- result item
- tool category
```

Do not couple the base system to:

- jobs,
- professions,
- quests,
- currency,
- NPCs,
- Rails.

Blood Moss may continue to use the managed vegetation cut event if that is architecturally cleaner.

---

## 15. Edge Cases

Explicitly handle:

- radius crossing unloaded chunks,
- source lava/water removed,
- oyster substrate removed,
- resource externally destroyed,
- player builds on candidate location,
- duplicate spawn attempts,
- chunk unload during attempt,
- restart during cooldown,
- Adventure non-dagger oyster attempt,
- Adventure dagger oyster harvest,
- two players harvest same oyster simultaneously,
- full inventory/drop behavior,
- Creative removal,
- invalid persisted resource ID,
- removed resource type after update,
- swamp biome border,
- joining inside swamp,
- dimension changes,
- dedicated-server classloading.

---

## 16. Testing Requirements

### Scheduler

- only loaded chunks are processed,
- probes are bounded,
- cooldown is enforced,
- max-per-chunk is enforced,
- no force-loading,
- restart preserves relevant accounting.

### Sulfurous Ash

- valid at <=20 blocks from lava,
- invalid at >20,
- unsafe candidate rejected,
- no forced load,
- density cap respected.

### Oyster

- limestone valid,
- calcite valid,
- other substrate invalid,
- <=20 blocks from water valid,
- >20 invalid,
- Adventure non-dagger rejected,
- Adventure dagger succeeds,
- exactly one Black Pearl,
- concurrent harvest cannot duplicate loot.

### Blood Moss

- outside swamp profile unavailable,
- swamp profile contains exactly the intended 5% Blood Moss entry,
- normal flower 5% slot is absent from the default swamp profile,
- non-swamp profile unchanged,
- managed vegetation lifecycle reused.

### Swamp Hue

Where client tests are practical:

- activates in swamp,
- deactivates outside swamp,
- dimension change resets state,
- join inside swamp initializes correctly,
- dedicated server has no client-class crash.

---

## 17. Acceptance Criteria

The feature is complete when:

- Sulfurous Ash naturally appears in loaded chunks only within 20 blocks of lava.
- Black-lipped Oysters naturally appear only on limestone/calcite within 20 blocks of water.
- Adventure-mode oyster harvesting requires a recognized dagger.
- Successful oyster harvest gives exactly one Black Pearl.
- Blood Moss replaces the normal 5% flower slot in the default swamp vegetation profile.
- Blood Moss appears only in configured swamp biomes.
- Existing managed vegetation architecture is reused rather than duplicated.
- Swamp biomes receive a subtle green swamp-gas environmental hue.
- Original placeholder assets exist for all missing new visuals.
- Frequencies, caps, cooldowns, substrates, and biome definitions are centralized.
- No system scans all loaded blocks every tick.
- No chunk is force-loaded for reagent spawning.
- Persistence prevents obvious restart/reload duplication.
- Server authority is preserved.
- Dedicated server runs without client-rendering classloading errors.
- Focused and regression tests pass.

---

## 18. Recommended Names

Adapt these to actual project conventions after discovery:

```text
WildResourceManager
WildResourceEntry
WildResourceSpawnScheduler
WildResourceSavedData
WildResourceHarvestEvent

SulfurousAshBlock
SulfurousAshItem
BlackLippedOysterBlock
BlackPearlItem
BloodMossBlock
BloodMossItem

SwampBiomeRules
SwampEnvironmentEffects
```

---

## 19. Design Principle

The world should feel as though reagents emerge from Britannia's ecology.

Use two lifecycle models:

```text
Managed vegetation:
  persistent regrowth locations

Wild resources:
  condition-driven opportunistic spawning in loaded chunks
```

Blood Moss uses the first.

Sulfurous Ash and Black-lipped Oysters use the second.

That separation keeps the system performant, understandable, and extensible enough for a future reagent-gathering profession.
