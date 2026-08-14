# UltimaCraft Managed Vegetation Regrowth System
## Design Document

**Status:** Proposed  
**Target:** UltimaCraft / NeoForge 1.21.1 / Java 21  
**Feature theme:** Zelda-style cuttable grass, shrubbery, and flowers that naturally regrow  
**Primary goal:** Introduce a reusable, data-driven managed vegetation system that can support future grass-cutting jobs/services without globally changing vanilla vegetation behavior.

---

## 1. Executive Summary

UltimaCraft needs a persistent vegetation-regrowth mechanic inspired by Zelda-style grass cutting.

A managed vegetation location should:

1. Exist only in a valid outdoor-style placement position directly above a grass block.
2. Require at least three blocks of usable vertical space above the supporting grass block.
3. Randomly produce vegetation from a configurable spawn table.
4. Initially support:
   - Vanilla short grass
   - Vanilla tall/long grass as the mature form of short grass
   - Vanilla fern
   - Existing UltimaCraft flower species
5. Give flowers an exact default selection chance of 5%.
6. Spawn managed grass as short grass first, then allow it to mature into tall/long grass.
7. Spawn a selected flower at the beginning of the existing seven-stage flower growth lifecycle, then delegate growth to the established flower system wherever possible.
8. Allow managed vegetation to be cut only by an item recognized by the project's existing `SwordType` system.
9. Regrow after a configurable amount of time.
10. Persist across server restart and chunk unload/reload.
11. Be extensible so new vegetation types, weights, growth paths, and future gameplay rewards/jobs can be added without redesigning the system.

The implementation must reuse existing UltimaCraft infrastructure and assets wherever possible. Codex must not duplicate systems that already exist.

---

## 2. Non-Goals

This feature does **not** initially need to:

- Replace all naturally generated Minecraft vegetation.
- Make every vanilla short grass, tall grass, or fern block sword-only.
- Change unrelated player-placed vegetation.
- Add an economy payout.
- Add grass-cutting contracts, NPCs, job boards, or profession progression yet.
- Add Zelda-style rupee, heart, seed, or item drops unless an existing UltimaCraft behavior already requires them.
- Rewrite the existing seven-stage flower system.
- Create duplicate flower models or textures.
- Add new custom grass textures if the vanilla models/assets are suitable.
- Run a world-wide scan every server tick.

This milestone should establish the reusable foundation that later gameplay systems can consume.

---

## 3. Terminology

### Managed Vegetation Node

A persistent logical regrowth location controlled by this feature.

A node has a canonical base position: the block position immediately above its supporting grass block.

A node may currently be:

- dormant/empty,
- showing a regrowth-controller block,
- occupied by short grass,
- occupied by tall grass,
- occupied by fern,
- occupied by an UltimaCraft flower,
- waiting for a future growth transition,
- suspended because its environment is temporarily invalid.

The persistent node identity must survive replacement of the visible block.

### Controller / Regrowth Block

A new custom block used by the managed vegetation system when an actual plant is not occupying the node.

The exact implementation should be determined after repository discovery. Preferred properties:

- no collision,
- no visible full cube,
- no occlusion,
- not obtainable in survival unless explicitly intended,
- can be placed by admins/development tools for testing,
- server-authoritative,
- capable of scheduling or participating in the next regrowth transition.

If an existing project pattern provides a better persistent marker mechanism, Codex may reuse it instead.

### Managed Plant

A vanilla or UltimaCraft plant currently associated with a registered node.

Only **managed plants** receive the special sword-only cutting and automatic regrowth behavior.

---

## 4. Required Repository Discovery Before Implementation

Codex must inspect the project before choosing class names or wiring.

At minimum, locate and document:

- Minecraft/NeoForge version and Java version.
- Block and item registry patterns.
- Existing custom block base classes.
- Existing custom flower blocks.
- The full seven-stage flower growth implementation.
- Flower species registration/data.
- Flower blockstates, models, textures, renderer behavior, and any tint/mask logic.
- Existing `SwordType` definition and all call sites.
- Existing sword-based harvesting/cutting logic.
- Any interaction or break-event hooks.
- Existing scheduled-tick, random-tick, server-tick, SavedData, capability, attachment, or persistent-world-state systems.
- Existing block placement validators.
- Existing server-authoritative gameplay patterns.
- Existing configuration/data loading systems.
- Existing codec/JSON-driven registries if present.
- Existing admin/debug items that place special blocks.
- Existing tests for blocks, growth, persistence, harvesting, or multiplayer authority.
- Existing world-generation or structure-placement systems that may eventually place these nodes.
- Existing Creative Tab registration conventions.
- Existing naming conventions and package locations for farming/world/vegetation systems.

Codex must produce a short discovery report before implementation.

No new parallel framework should be introduced when an established project system can meet the requirement.

---

## 5. Proposed Architecture

### 5.1 Persistent Node Registry

Because the visible plant may be a vanilla block, the system cannot rely exclusively on a block entity attached to the visible vegetation.

Preferred architecture:

- Maintain a per-dimension persistent registry of managed vegetation nodes.
- Use the project's existing persistent-world mechanism if available.
- If no suitable system exists, use NeoForge/Minecraft `SavedData` or the current project-standard equivalent.
- Key each node by its canonical base `BlockPos`.
- Persist only the minimum required state.

Suggested node state:

```text
position
lifecycle_state
vegetation_entry_id
flower_species_id? 
growth_stage?
next_transition_game_time
retry_count / suspended_reason?   (only if useful)
schema_version
```

Do not persist derived information that can be reconstructed safely.

### 5.2 Lifecycle State Machine

Recommended states:

```text
EMPTY / REGROWING
  -> SHORT_GRASS
  -> TALL_GRASS

EMPTY / REGROWING
  -> FERN

EMPTY / REGROWING
  -> FLOWER(stage 1)
  -> FLOWER(stage 2)
  -> ...
  -> FLOWER(stage 7)

ANY_OCCUPIED_STATE
  --SwordType cut-->
EMPTY / REGROWING
```

A flower should use the existing flower system's stage transitions rather than duplicating seven-stage growth logic.

The node registry should track enough information to recognize that the current flower belongs to the node.

### 5.3 Why Not Globally Modify Vanilla Grass?

Do **not** make all vanilla short grass, tall grass, or ferns sword-only.

That would cause unexpected behavior across the entire world and could break:

- vanilla mechanics,
- structure decoration,
- unrelated city/world assets,
- player landscaping,
- other mods,
- existing UltimaCraft gameplay.

Special behavior must apply only when the affected position is registered as a managed node.

---

## 6. Vegetation Selection

### 6.1 Default Selection Model

Initial spawn should select a **vegetation family**, not blindly choose a final block.

Recommended initial default:

| Entry | Weight / Chance | Initial Result | Later Growth |
|---|---:|---|---|
| Grass family | 75% | Vanilla short grass | Vanilla tall/long grass |
| Fern | 20% | Vanilla fern | No required second stage in MVP |
| Random UltimaCraft flower | 5% | Existing flower stage 1 | Existing stages 2-7 |

The flower chance must remain 5% by default.

The 75/20 split between grass and fern is a starting balance value, not a hard-coded gameplay rule. It must be easy to change.

### 6.2 Long Grass Handling

The default grass-family lifecycle is:

```text
short grass -> tall/long grass
```

Do not directly spawn tall grass in the default profile unless repository discovery identifies an existing mechanic that makes direct tall-grass selection preferable.

The architecture should allow a future table entry for direct tall-grass spawning if desired.

### 6.3 Random Flower Selection

If the 5% flower roll succeeds:

1. Select from the existing allowed flower species.
2. Spawn that flower at its first growth stage.
3. Let the established flower growth system advance the plant through all seven stages.
4. Preserve the selected flower species for the node's current lifecycle unless existing project rules require rerolling.

Codex must derive the available flower pool from existing project registrations/data when possible.

Do not maintain a second manually duplicated list if the project already has an authoritative flower registry.

### 6.4 Data-Driven Extensibility

The spawn system must be designed so future entries can be added without rewriting the selection algorithm.

A vegetation entry should conceptually support:

```text
id
weight
initial_placement
growth_strategy
clearance_requirement
allowed_substrates
cut_behavior
optional future metadata
```

Use the project's existing data/config system if it has one.

Acceptable implementation options include:

- data-driven JSON definitions,
- registry objects with configurable weights,
- a centralized immutable/configurable entry table,
- codecs if the project already uses them.

Avoid scattered switch statements and magic percentages.

---

## 7. Placement Rules

A node is valid only when all required environmental conditions are met.

### 7.1 Supporting Block

The canonical plant position must be directly above a valid grass block.

Initial requirement:

- vanilla Minecraft grass block is valid.

Codex should centralize substrate validation so additional grass-like blocks can be supported later.

### 7.2 Vertical Clearance

There must be three blocks of usable vertical space beginning at the node position.

For a node at `P` above the grass substrate:

```text
P
P + 1Y
P + 2Y
```

must all be air or otherwise explicitly recognized as replaceable vegetation space according to project conventions.

Do not overwrite:

- solid blocks,
- containers,
- structures,
- decorations,
- fluids,
- other protected custom blocks,
- unrelated plants unless the placement system explicitly owns them.

### 7.3 Runtime Revalidation

Validate the environment:

- when the node is created,
- before spawning,
- before growing into a taller form,
- after chunk reload when necessary,
- after a failed transition.

If the supporting grass block disappears, the node should not place vegetation until valid again.

Preferred behavior is to suspend/retry rather than spam logs or continuously tick.

---

## 8. Cutting Rules

### 8.1 SwordType Requirement

Managed vegetation can only be cut by an item recognized by the existing project `SwordType` abstraction.

Codex must discover the exact API. Do not invent a second sword tag/type if one already exists.

The authorization check must be server-side.

### 8.2 Managed-Only Enforcement

When a player attempts to break vegetation:

1. Determine whether the plant position belongs to a managed vegetation node.
2. If it is not managed, preserve existing behavior.
3. If it is managed:
   - allow cutting only with `SwordType`,
   - prevent unauthorized break attempts,
   - handle all occupied blocks for multi-block vegetation,
   - mark the node as cut/regrowing,
   - schedule its next regrowth,
   - preserve multiplayer/server authority.

### 8.3 Multi-Block Plants

Tall grass is a two-block plant.

Cutting either half must:

- resolve back to the same node,
- remove the complete plant safely,
- avoid orphan top/bottom halves,
- schedule one regrowth transition only.

If any UltimaCraft flower can occupy more than one block, apply the same ownership rule.

### 8.4 Drops

Do not introduce new drops in this feature.

Codex must inspect existing sword-cutting and flower-harvest behavior and preserve compatible project behavior.

If managed vegetation requires a special no-drop path to prevent farming exploits, implement that explicitly and document it.

Future loot hooks should remain possible.

### 8.5 Sword Durability and Animation

Reuse existing sword interaction behavior where possible.

Do not invent custom durability damage, swing animation, or sounds if vanilla/project-standard behavior already handles them correctly.

---

## 9. Regrowth and Timing

### 9.1 Goals

Regrowth should feel natural and should not create a server-wide performance burden.

Timing must be configurable.

At least two transition delays are needed:

- `cut -> new vegetation`
- `short grass -> tall grass`

Flower stage timing should remain owned by the existing flower system if possible.

### 9.2 Scheduling Strategy

Preferred order:

1. Reuse an existing project scheduler if one exists.
2. Otherwise use scheduled block ticks or a persistent, efficient timing queue.
3. Avoid scanning every managed node every server tick.
4. Avoid one heavyweight block entity per plant if not required.
5. Do not depend on a client timer.

When a chunk is unloaded, the node should not require continuous ticking.

On reload, overdue transitions may execute after normal validation.

### 9.3 Randomized Delays

Support a delay range rather than a single exact tick count if consistent with project style.

This avoids all vegetation regrowing simultaneously.

Do not hard-code final gameplay timing values throughout the code. Centralize them.

---

## 10. Existing Seven-Stage Flower System Integration

The existing flower system is authoritative for flower growth.

The managed vegetation feature should:

- discover all supported flower species,
- select a flower species from that authoritative source,
- create the proper stage-1 plant,
- allow normal seven-stage progression,
- recognize every stage as belonging to the same managed node,
- support cutting the flower at any stage,
- return the node to regrowth after cutting,
- avoid duplicating flower state tables, model tables, or textures.

If the current flower system requires a custom placement/init path, use it.

If existing flowers have protection/ownership semantics, managed wild flowers must be intentionally marked or initialized so their behavior is correct without weakening player/admin protections elsewhere.

---

## 11. Assets and Rendering

### 11.1 Reuse

Initial grass and fern visuals should reuse vanilla Minecraft assets whenever technically possible:

- short grass
- tall grass
- fern

Existing UltimaCraft flower assets must be reused.

### 11.2 New Custom Asset Requirements

The regrowth/controller block may need:

- block registration,
- blockstate JSON,
- model JSON,
- item model only if exposed as an item,
- loot table behavior,
- localization,
- Creative Tab entry if appropriate.

Prefer a visually empty/non-invasive controller representation for normal gameplay if the architecture uses an in-world controller block.

For development, an optional debug rendering/state may be acceptable if isolated behind development/admin tooling.

### 11.3 Asset Audit

Before creating any model or texture, Codex must search the repository for existing assets that can be reused.

Do not generate duplicate textures because a file was simply stored under a different package or naming convention.

---

## 12. Multiplayer and Authority

All meaningful state transitions must be server-authoritative:

- node creation,
- vegetation selection,
- growth,
- cut authorization,
- block replacement,
- regrowth scheduling,
- persistence.

The client may render and animate normal blocks but may not decide:

- whether a cut is legal,
- which plant spawns,
- when a plant regrows,
- which flower species was selected.

Avoid custom networking unless existing APIs require it.

Use normal block/world synchronization whenever sufficient.

---

## 13. Persistence and Recovery

The system must survive:

- dedicated server restart,
- dimension reload,
- chunk unload/reload,
- player disconnect/reconnect.

On load:

1. Validate persisted data schema.
2. Do not eagerly force-load every chunk.
3. Reconcile a node when its chunk becomes available.
4. If the visible block is missing or inconsistent, repair conservatively only if the node still has a valid substrate/environment.
5. Do not overwrite unrelated player blocks during recovery.

Include a schema/version field if custom persistent data is introduced.

---

## 14. Future Grass-Cutting Business Integration

This feature should expose a clean integration point without implementing the business system now.

Potential future consumers:

- grass-cutting service NPCs,
- town maintenance contracts,
- player job objectives,
- regional cleanup quests,
- wage calculation,
- "cut N managed vegetation" objectives,
- anti-abuse cooldowns,
- town cleanliness simulation.

Recommended extension point:

```text
ManagedVegetationCutEvent
```

or an equivalent project-standard event/service callback containing at least:

```text
player
node position
vegetation entry id
flower species/stage if applicable
dimension/region context
```

Do not couple the base vegetation system directly to money, jobs, quests, Rails calls, or NPC services in this milestone.

---

## 15. Administration and Debugging

Codex should inspect current admin tooling and implement the smallest useful development path.

Recommended capabilities:

- place/register a managed vegetation node,
- remove/unregister a node,
- force a node to regrow,
- inspect a node's current state,
- optionally reroll vegetation.

Do not add public commands if an existing admin item/tool can naturally support this.

Development logging should be concise and disabled or low-noise in production.

---

## 16. Safety and Edge Cases

The implementation must explicitly handle:

- substrate changed from grass to another block,
- space above node becomes blocked,
- tall grass upper block removed externally,
- flower replaced by another block,
- node position covered by water,
- explosion or world edit removes vegetation,
- chunk unload while a transition is pending,
- server restart while a transition is overdue,
- duplicate node registration,
- node removal while occupied,
- player tries breaking managed vegetation by hand,
- player tries a non-sword tool,
- player uses an accepted `SwordType`,
- creative mode behavior,
- multi-block vegetation half-breaks,
- concurrent multiplayer break attempts,
- invalid/outdated persisted vegetation IDs,
- future removal of a flower species from the registry.

Recovery must never overwrite unrelated structures just to restore vegetation.

---

## 17. Performance Requirements

The feature may eventually exist at many thousands of locations.

Therefore:

- no world-wide per-tick scans,
- no repeated full registry iteration every tick,
- no forced chunk loading,
- no client polling,
- no unbounded retry loops,
- no repeated disk writes for unchanged nodes.

Prefer scheduled transitions and dirty persistent data only when state changes.

Codex must include a short performance analysis in its implementation report.

---

## 18. Test Requirements

Automated tests should cover as much as the repository's current test infrastructure supports.

Minimum behavioral coverage:

### Placement

- node valid above grass block,
- node rejected/suspended above non-grass,
- three-block clearance enforced,
- obstruction prevents spawn,
- unrelated blocks are never overwritten.

### Spawn Selection

- selection table is weighted/configurable,
- flower default chance is 5% at the configuration level,
- flower species comes from authoritative existing flower source,
- grass starts as short grass,
- tall grass is reached through growth.

Do not use a flaky statistical test to prove 5%. Test the configured weighting/selection algorithm deterministically with controlled RNG where possible.

### Cutting

- managed short grass rejects non-sword break,
- managed tall grass rejects non-sword break,
- managed fern rejects non-sword break,
- managed flower rejects non-sword break,
- recognized `SwordType` can cut each,
- unmanaged vanilla grass preserves vanilla behavior,
- multi-block plants clean up both halves,
- cut schedules regrowth exactly once.

### Growth

- short grass grows to tall grass,
- blocked growth retries safely,
- flower starts at stage 1,
- managed flower remains compatible with all seven existing stages,
- cut flower at any stage returns node to regrowth.

### Persistence

- node survives save/load,
- regrowth timing survives restart,
- overdue transition recovers after chunk load,
- invalid persisted entry fails safely.

### Multiplayer / Authority

- server owns RNG and transitions,
- two players cannot double-trigger one cut,
- client cannot authorize a cut by itself.

---

## 19. Acceptance Criteria

The feature is complete when:

- A managed node can be created above a vanilla grass block.
- Creation/spawning requires three vertical blocks of clear/replaceable space.
- The node persists independently of its visible plant.
- The node can produce vanilla short grass.
- Short grass can mature into vanilla tall/long grass.
- The node can produce vanilla fern.
- The node has an exact default 5% flower selection entry.
- Flower selection uses the existing UltimaCraft flower species set.
- Managed flowers integrate with the existing seven-stage growth system.
- Managed vegetation can be cut only with an existing `SwordType` item.
- Unmanaged vanilla vegetation is not globally altered.
- Cutting schedules regrowth.
- Regrowth survives restart and chunk unload/reload.
- Selection weights and timing are centralized and designed for future configuration.
- New vegetation entries can be added without redesigning the core state machine.
- No existing flower, farming, sword, world, or multiplayer tests regress.
- Focused tests and the full relevant test suite pass.
- The implementation report documents all reused systems/assets.

---

## 20. Recommended Initial Naming

Codex should adapt these names to project conventions after discovery.

Possible names:

```text
ManagedVegetationBlock
VegetationRegrowthBlock
ManagedVegetationNode
ManagedVegetationSavedData
ManagedVegetationManager
VegetationEntry
VegetationGrowthStrategy
ManagedVegetationCutEvent
```

Avoid locking naming before repository inspection.

---

## 21. Design Principle Summary

The system should behave like a **persistent regrowth point**, not like a global rule applied to every plant in Minecraft.

That distinction provides the foundation needed for a future grass-cutting profession:

- the server knows which plants are legitimate managed work targets,
- each target can be cut and regrown repeatedly,
- arbitrary player landscaping does not become part of the job,
- future town/region/job logic can count verified cuts,
- the vegetation mix can evolve over time without rewriting the engine.

The result should feel simple to the player while remaining explicit, persistent, extensible, and server-authoritative internally.
