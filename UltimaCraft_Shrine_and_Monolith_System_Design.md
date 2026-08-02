# UltimaCraft Shrine and Monolith System Design

**Document status:** Implementation specification  
**Project:** UltimaCraft / Britannia Mod  
**Feature:** Multi-block shrines and monolithic stones  
**Target runtime to verify in-repository:** Minecraft 1.21.1, NeoForge 21.1.72, Java 21  
**Recommended feature branch:** `shrines-monoliths`  
**Known integration branch:** `patch-18`  
**Companion document:** `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md`

---

## 1. Purpose

This document defines a reusable large-structure system for two initial UltimaCraft structure families:

1. **Shrines**
   - Logical footprint: two blocks wide, one block high, and two blocks deep.
   - Total occupied cells: four.
   - Blockbench visual size: approximately 32 by 32 horizontal voxels.
   - All shrine variants use the same geometry.
   - Shrine variants change texture only.

2. **Monoliths**
   - Logical footprint: three blocks wide, three blocks high, and two blocks deep.
   - Total occupied cells: eighteen.
   - Monolith variants may use different model geometry and textures.
   - The current Blockbench authoring workflow places the model sixteen voxels below the intended world horizon.
   - The placed renderer must therefore support a definition-level positive Y render offset of sixteen voxels.

The system must allow stairs, slabs, walls, and ordinary blocks to be placed immediately beside every outer structure cell without collision from oversized model geometry. The visual model, collision, occupied cells, persistence, and administrative variant selection must be treated as separate concerns.

This is a Minecraft-mod-only feature. It does not require Rails, API, database, website, economy, NPC, or account-system work.

---

## 2. Product Goals

The completed feature must provide the following player and administrator behavior:

- A shrine is placed as one logical object occupying a two-by-two horizontal footprint.
- A monolith is placed as one logical object occupying a three-by-three-by-two volume.
- Every occupied cell behaves as part of one structure.
- Breaking any occupied cell removes the complete structure.
- Survival removal produces at most one correctly configured structure item.
- Creative removal produces no duplicate item.
- Stairs and other blocks can be placed in all immediately adjacent, unoccupied cells.
- Oversized Blockbench geometry never contributes collision outside the structure footprint.
- A shrine administrator can use the existing Interior Decorator tool to cycle through shrine texture variants.
- A monolith administrator can use the same tool to cycle through monolith model variants.
- Cycling a variant does not remove and replace the structure when its footprint is unchanged.
- All authoritative changes occur on the server.
- Saved worlds survive reloads, missing assets, changed registries, and partial chunk loading without crashes or duplicate drops.

---

## 3. Non-Goals

The first release must not include:

- Cross-family conversion from a shrine into a monolith or from a monolith into a shrine.
- Arbitrary resizing of a structure after placement.
- Structures with different footprints inside the same cycle group.
- Player crafting recipes unless separately approved.
- Survival acquisition or progression rules unless separately approved.
- Rails-managed structure content.
- Networked content downloaded from the website.
- Physics, falling blocks, animated structural movement, or movable pistons.
- Rotation through the Interior Decorator tool unless separately approved.
- Per-cell independent drops.
- Separate authoritative state on every occupied part.
- Collision generated directly from Blockbench model bounds.
- Force-loading chunks to finish placement or integrity repair.
- Fabricated shrine artwork, monolith models, lore names, or unapproved variant IDs.
- A universal structure engine for every imaginable future feature. The framework may be reusable, but it must implement only the requirements needed by shrines and monoliths.

---

## 4. Terminology

### Large structure

One logical placed object that occupies multiple Minecraft block cells.

### Family

A group of variants sharing placement behavior, footprint, collision policy, item identity, and cycling rules.

Initial families:

- `shrine`
- `monolith`

### Variant

The selected visual identity within a family.

- A shrine variant changes texture while retaining the shrine geometry.
- A monolith variant may change model and texture while retaining the monolith footprint and render-offset contract.

### Anchor

The single authoritative occupied cell. It owns the block entity and the complete placed-structure state.

### Part

A non-authoritative occupied cell that points back to the anchor through a deterministic local offset and facing transform.

### Footprint

The complete ordered set of occupied local offsets.

### Render offset

A visual-only translation applied by the anchor renderer. It does not change placement cells, collision, selection, breaking, support, or persistence ownership.

### Interior Decorator

The existing administrator tool used to cycle the current variant of a placed structure.

---

## 5. Non-Negotiable Architectural Decisions

### 5.1 Collision is not model geometry

Blockbench or rendered model bounds must never be used as collision bounds.

The visual model may extend across several block cells. Collision, selection, placement occupancy, pathing, and block support must instead come from the logical blocks occupying those cells.

Every `VoxelShape` used by an anchor or part must remain inside that block's local coordinate range:

```text
X: 0 through 16
Y: 0 through 16
Z: 0 through 16
```

No collision, selection, occlusion, or support shape may use negative coordinates or coordinates greater than sixteen for a single cell.

This rule is the primary fix for the current stair-placement collision problem.

### 5.2 One anchor, many parts

A placed shrine or monolith is one logical object.

- The anchor stores authoritative state.
- Parts store only enough local placement state to find and validate the anchor.
- Parts do not store complete family or variant state.
- Parts do not produce independent drops.
- Only the anchor renders the visual model.
- The lifecycle service owns all whole-structure removal and repair.

### 5.3 Render offsets are definition metadata

The monolith's Blockbench alignment problem must not be solved by moving collision, moving occupied cells, inventing an invisible lower layer, or extending a block shape beyond its cell.

Instead, the monolith family or variant definition supplies:

```text
render_offset_voxels = [0, 16, 0]
```

The renderer converts this to block units:

```text
render_offset_blocks = [0.0, 1.0, 0.0]
```

The translation is applied only during rendering.

A model authored with its lowest intended point at Y = -16 will therefore appear with that point at world Y = 0 after the positive sixteen-voxel translation.

The implementation may support X and Z render offsets for precise model alignment, but the current required correction is positive sixteen voxels on Y.

### 5.4 Variants are state, not replacement blocks

Shrine variants share one geometry. Cycling a shrine changes only its texture variant ID.

Monolith variants may use different geometry. Cycling a monolith changes its model variant ID and associated texture resources.

For both families:

- The footprint remains unchanged.
- The anchor and parts remain in place.
- The renderer resolves the new visual resources.
- The anchor block entity is marked changed and synchronized.
- The complete structure is not destroyed and re-placed.

### 5.5 Same-family cycling only

The Interior Decorator cycles variants within the structure's current family.

It must not convert:

- Shrine to monolith
- Monolith to shrine
- A structure to another footprint
- A structure to a variant with incompatible collision or render-placement requirements

Future cross-family conversion would require a separate transactional replacement design and is outside this release.

---

## 6. Logical Dimensions

Dimensions use this order:

```text
width × height × depth
```

### Shrine

```text
2 × 1 × 2
```

Occupied local offsets:

```text
x = 0..1
y = 0
z = 0..1
```

Total cells:

```text
2 × 1 × 2 = 4
```

### Monolith

```text
3 × 3 × 2
```

Occupied local offsets:

```text
x = 0..2
y = 0..2
z = 0..1
```

Total cells:

```text
3 × 3 × 2 = 18
```

The footprint must come from a structure definition or a validated generated rectangular footprint. Gameplay logic must not hard-code lists of world positions by variant ID.

---

## 7. Coordinate and Facing Convention

The implementation must use one documented transform everywhere: planning, placement, part-to-anchor resolution, rendering, breaking, integrity checks, and tests.

Recommended convention:

- `FACING` is horizontal and points outward from the structure's front toward the viewer.
- The anchor is the lower, front-left occupied cell when viewed from the structure front.
- Local X increases to the viewer's right.
- Local Y increases upward.
- Local Z increases away from the viewer and into the structure.

Recommended basis:

```text
right = FACING.getCounterClockWise()
up    = world Y positive
away  = FACING.getOpposite()
```

Recommended transform:

```text
world_position =
    anchor_position
    + right * local_x
    + up    * local_y
    + away  * local_z
```

The exact clockwise/counter-clockwise operation may be adapted if existing UltimaCraft conventions differ, but one transform must be selected, documented, and proven through four-facing tests. It must not differ between placement, rendering, and part resolution.

The default Blockbench model orientation must also be documented. Renderer rotation must be derived from the same `FACING` convention.

---

## 8. Structure Definitions

The system requires a validated definition layer. The physical storage format must follow repository conventions discovered by Codex. It may be Java data, JSON resources, or another existing validated content pattern.

A conceptual definition contains:

```text
id
family_id
display_name_key
width_blocks
height_blocks
depth_blocks
placement_mode
collision_profile
cycle_order
default_variant_id
item_id
render_origin
render_offset_voxels
variants
```

A conceptual variant contains:

```text
id
display_name_key
model_resource
texture_resource
optional_animation_resource
cycle_order
enabled
provisional
```

### Shrine definition contract

All shrine variants must share:

- Family ID
- Footprint
- Geometry/model resource
- Collision profile
- Render origin
- Render offset
- Placement mode

Only the texture resource and localized display name are expected to change.

The currently intended shrine content family includes the Ultima virtue shrines and Chaos:

- Honesty
- Compassion
- Valor
- Justice
- Sacrifice
- Honor
- Spirituality
- Humility
- Chaos

Codex must inventory the repository's approved IDs and textures before creating the manifest. It must not rename files, invent lore, or fabricate missing art.

### Monolith definition contract

All monolith variants in the first cycle group must share:

- `3 × 3 × 2` footprint
- Placement mode
- Collision profile
- Anchor convention
- Positive sixteen-voxel Y render correction
- Compatible renderer pipeline

Monolith model count and approved names must be discovered from supplied assets or repository content. Missing names must use stable provisional IDs and visibly provisional labels rather than invented final lore.

### Validation

Definition validation must reject:

- Duplicate family IDs
- Duplicate variant IDs within a family
- Missing default variant
- Default variant that is disabled
- Zero or negative dimensions
- Dimensions beyond the part-offset encoding
- Empty footprints
- Duplicate occupied offsets
- An anchor offset not included in the footprint
- Variant resources outside the expected namespace
- A variant in a cycle group with a footprint different from its family
- A monolith variant without the required render-offset compatibility
- A shrine variant that attempts to replace the shared shrine geometry
- Missing localization keys where repository validation supports checking them

Missing client resources must fail safely at render time with a diagnostic fallback. They must not corrupt world state.

---

## 9. Registered Content

The preferred architecture registers:

### Blocks

- One large-structure anchor block
- One large-structure part block

Preferred IDs:

```text
britannia_mod:large_structure_anchor
britannia_mod:large_structure_part
```

If existing IDs or conventions provide a clearer compatible location, Codex may adapt after documenting the reason.

### Block entities

- One anchor block entity type

Preferred ID:

```text
britannia_mod:large_structure
```

Parts should not require block entities when local offsets and facing can be represented safely in block state. If repository or state-count constraints prove that a part block entity is required, Codex must document the evidence before changing this decision.

### Items

Preferred family-level placement items:

```text
britannia_mod:shrine
britannia_mod:monolith
```

The anchor and part blocks must not have ordinary direct-use `BlockItem` paths that create unconfigured structures.

A family item places the default or item-configured variant and returns the corresponding family item when the structure is recovered.

If the repository already contains approved shrine or monolith item IDs, preserve them.

---

## 10. Anchor Block Entity State

The anchor block entity is server-authoritative and common-side.

Minimum persisted state:

```text
schema_version
family_id
variant_id
placed_footprint
facing_or_facing_validation
```

`FACING` should normally remain authoritative in block state. It may be redundantly validated in persisted structure data if the repository has an established safe pattern.

### Placed footprint

The block entity must persist the exact ordered occupied offsets used when the structure was placed.

This prevents later definition edits from silently changing the size of an existing structure and gives lifecycle cleanup an authoritative footprint even when content is missing.

The footprint snapshot must include the anchor offset and must be validated on load.

### State that must not be duplicated on parts

Parts must not store:

- Full family state
- Full variant state
- Render resources
- Display names
- Model resources
- Texture resources
- Complete occupied-footprint lists
- Item data
- Administrator identity
- Render caches

### Missing content

If a saved family or variant ID is no longer available:

- Preserve the stable IDs.
- Preserve the placed footprint.
- Do not substitute a lexically first variant.
- Do not silently switch a shrine to another virtue.
- Render a safe diagnostic fallback.
- Allow whole-structure cleanup and recovery.
- Return an item preserving the stable family and variant IDs when structurally possible.
- Log a bounded useful diagnostic rather than spamming every tick.

### Structurally invalid state

Malformed state must not crash world loading.

A structurally invalid anchor must:

- Enter an explicit invalid state.
- Avoid rendering untrusted resources.
- Avoid duplicate drops.
- Permit safe cleanup.
- Never overwrite unrelated blocks during repair.

---

## 11. Part State and Anchor Resolution

The part block must encode or otherwise persist:

```text
horizontal facing
local X offset
local Y offset
local Z offset
```

Required ranges for the first release:

```text
X: 0..2
Y: 0..2
Z: 0..1
```

The anchor itself occupies local offset:

```text
[0, 0, 0]
```

The anchor cell uses the anchor block. Every other occupied offset uses the part block.

A part resolves the expected anchor by reversing the shared facing-aware transform.

Membership is valid only when all of the following agree:

- Part facing
- Part local offset
- Calculated anchor world position
- Anchor block type
- Anchor block entity
- Anchor persisted footprint
- Expected world position for the stored local offset
- Family dimensions or footprint validation, when the family definition is available

If the anchor chunk is not loaded, the part must defer classification. It must not force-load the chunk and must not immediately delete itself as an orphan.

---

## 12. Placement Behavior

### 12.1 Placement mode

Both initial families are floor-oriented structures.

Recommended initial interaction:

- Player uses the family item on the top face of a block.
- The clicked placement position becomes the anchor position.
- Horizontal facing is derived from the player's orientation using existing project conventions.
- Vertical or underside placement is rejected.
- The structure is self-supporting after successful placement, like ordinary stone blocks.
- Ongoing bottom-support checks are not required unless the product owner later approves them.

### 12.2 Immutable placement plan

Before any world mutation, the server creates an immutable placement plan containing:

- Family and variant state
- Anchor position
- Facing
- Ordered local offsets
- Ordered world positions
- Expected block state for each cell
- Original block state for rollback
- Required chunks
- Permission/protection result
- Collision profile
- Render metadata snapshot or resolved definition reference as required

### 12.3 Validation order

Before placement:

1. Confirm the used item is the expected shrine or monolith family item.
2. Decode and validate its family and variant state.
3. Confirm the family definition exists and is enabled.
4. Confirm the variant exists and is enabled.
5. Confirm the variant is compatible with the family footprint.
6. Confirm the clicked face and placement mode are valid.
7. Resolve horizontal facing.
8. Generate the complete footprint.
9. Validate world height and world-border limits.
10. Confirm every required chunk is already loaded.
11. Confirm every target cell is replaceable.
12. Confirm no target belongs to an unrelated large structure.
13. Apply existing protection or region-placement rules.
14. Validate the part offset encoding.
15. Confirm the anchor block entity can be created.
16. Confirm the complete state can be assigned.
17. Confirm rollback can restore the original states.

No world cell or item stack may be mutated during validation.

### 12.4 Cross-chunk behavior

Placement may cross chunk boundaries only when every required chunk is already loaded.

The system must:

- Never force-load chunks for placement.
- Fail deterministically when any required chunk is unloaded.
- Consume no item on failure.
- Change no blocks on failure.
- Produce concise administrator/player feedback.

### 12.5 Mutation order

Recommended transaction order:

1. Place the anchor block.
2. Create and initialize the anchor block entity.
3. Persist the complete placed state and footprint.
4. Place parts in stable row-major local-offset order.
5. Verify every final block and part-to-anchor relationship.
6. Mark the block entity changed.
7. Synchronize the anchor state.
8. Notify neighbors.
9. Emit sound/game event.
10. Consume one item in survival after all prior steps succeed.
11. Consume no item in creative.

If any step fails, restore original block states in reverse order with drops and side effects suppressed.

Rollback must not:

- Consume the item
- Produce structure drops
- Leave an anchor block entity
- Leave orphan parts
- Overwrite a newly detected unrelated obstruction
- Play success effects

---

## 13. Collision, Selection, Occlusion, and Stair Compatibility

### 13.1 Core rule

The renderer's visual bounds may span the complete structure. Each logical block's shapes may span only its own cell.

### 13.2 Collision profiles

The initial framework should support a small explicit set of profiles rather than arbitrary model-derived collision.

Recommended profiles:

- `SOLID_CELL`: full sixteen-by-sixteen-by-sixteen collision and support shape for every occupied cell.
- `BOUNDED_CUSTOM`: a reviewed per-family shape clipped to each occupied cell.
- `NO_ENTITY_COLLISION`: only when deliberately approved for a decorative object.

The default for stone shrines and monoliths should be `SOLID_CELL` unless the approved model height would create a clearly unacceptable invisible barrier. Any custom profile must still remain cell-bounded.

### 13.3 Neighbor placement guarantee

Placement validation for a shrine or monolith may inspect only its target cells. It must not reserve, reject, or claim adjacent perimeter cells based on render bounds.

An adjacent block placement may inspect the structure cell through normal Minecraft rules, but the structure must not extend collision into the adjacent target.

### 13.4 Sturdy faces

When `SOLID_CELL` is selected, occupied cells should expose ordinary sturdy faces consistent with a full solid block so adjacent attachments behave predictably.

If a custom bounded shape is used, support behavior must be tested separately and documented. Visual shape, collision shape, occlusion shape, and sturdy-face behavior must not be accidentally conflated.

### 13.5 Required adjacency test ring

For every facing of each structure family, test placement in every immediately adjacent perimeter cell with:

- Full blocks
- Stairs facing toward the structure
- Stairs facing away from the structure
- Inner-corner stair arrangements
- Outer-corner stair arrangements
- Top and bottom slabs
- Walls
- Fences
- Torches or another face-attached block where sturdy support is intended

Acceptance requires:

- No collision error caused by the rendered model.
- No occupied-cell overlap.
- No part replacement.
- No structure mutation.
- No visual model translation caused by neighbor placement.
- No unexpected culling or missing faces that exposes voids around the structure.

### 13.6 Internal occupancy

No ordinary block may be placed inside any anchor or part cell.

The structure's visual shape does not determine occupancy. The anchor and part blocks do.

---

## 14. Rendering

### 14.1 Anchor-only rendering

Only the anchor renders the complete structure.

Parts must use an invisible or otherwise non-rendering presentation and must never render duplicate geometry.

The anchor may also use an invisible baked block model while a block-entity renderer draws the complete model.

### 14.2 Repository-first renderer choice

Codex must inspect existing UltimaCraft rendering, Blockbench, GeckoLib, animation, and block-entity renderer conventions before choosing APIs.

The design requires behavior, not a guessed implementation class:

- One anchor renderer
- Dynamic family and variant selection
- Shrine geometry shared across texture variants
- Monolith model selection by variant
- Definition-level render translation
- Horizontal facing rotation
- Safe missing-resource fallback
- No authoritative client state

### 14.3 Shrine rendering

For shrines:

```text
geometry = shared shrine geometry
texture  = selected shrine variant texture
offset   = shrine family render offset
```

Changing shrine variant must not replace the geometry resource.

Existing approved shrine textures should be reused without modification.

### 14.4 Monolith rendering

For monoliths:

```text
geometry = selected monolith variant geometry
texture  = selected monolith variant texture
offset Y = +16 voxels
```

The renderer applies translation and rotation in a consistent order documented by tests.

Recommended order:

1. Translate to the structure render origin.
2. Apply definition render offset.
3. Rotate for horizontal facing.
4. Render selected model and texture.

Codex must verify the correct order against the existing model coordinate system. The final world-space result is authoritative, not this suggested matrix order.

### 14.5 Render bounds and culling

The anchor renderer's visibility bounds must cover:

- The complete logical footprint
- The complete visual model
- The positive sixteen-voxel monolith Y correction
- Any approved X or Z visual offset

A model must not disappear merely because part of it extends outside the anchor cell's default render bounds.

The implementation must avoid:

- Rendering once per part
- Z-fighting between baked and dynamic geometry
- Purple missing-texture crashes
- Client-side world mutation
- Render-time registry mutation
- Per-frame rebuilding of immutable definition data

### 14.6 Missing resources

When a model or texture is missing:

- Keep the structure state unchanged.
- Render a bounded diagnostic fallback.
- Log a useful message once or with rate limiting.
- Do not delete the structure.
- Do not switch its variant.
- Do not crash a dedicated server.

---

## 15. Interior Decorator Integration

### 15.1 Permission

Only an administrator satisfying the project's existing authoritative administrator check may cycle variants.

Creative mode alone must not replace the established administrator rule unless that is already the project's approved behavior.

The server must verify:

- Held item is the actual Interior Decorator tool.
- Player is authorized.
- Target resolves to a valid anchor.
- Family supports cycling.
- Current and next variants are valid.

The client may request or predict an interaction result but must not choose the authoritative variant ID.

### 15.2 Target resolution

Right-clicking either:

- The anchor, or
- Any valid part

must resolve to the same anchor and cycle the same logical structure.

An invalid or unavailable anchor must fail safely without force-loading chunks.

### 15.3 Shrine cycling

Shrine cycling changes:

```text
variant_id
```

It does not change:

- Geometry
- Footprint
- Parts
- Facing
- Collision
- Anchor position
- Render offset

### 15.4 Monolith cycling

Monolith cycling changes:

```text
variant_id
model resource
texture resource
```

It does not change:

- Footprint
- Parts
- Facing
- Collision profile
- Anchor position
- Required positive Y render-offset contract

A monolith variant incompatible with the current family contract must not be included in the cycle list.

### 15.5 Cycle order

Cycle order must be explicit and stable.

Do not derive user-facing order from:

- Hash-map iteration
- Filesystem order
- Registry load accident
- Lexical ID order unless lexical order is explicitly approved

Each enabled variant receives a unique cycle order or appears in an ordered manifest.

When the final variant is reached, the next interaction wraps to the first enabled variant.

### 15.6 Mutation and synchronization

On successful cycle:

1. Determine the next allowed variant.
2. Update the anchor state on the server.
3. Mark the block entity changed.
4. Send the normal block-entity update.
5. Request a visual block update when required by renderer conventions.
6. Play the existing decorator feedback sound or normal interaction sound.
7. Show localized action-bar feedback with family and selected variant.

The structure blocks must not be removed and re-placed.

### 15.7 Failure feedback

Expected failures should produce concise localized feedback:

- Administrator permission required
- Invalid large structure
- Anchor unavailable
- No alternate variants
- Variant data unavailable
- Structure content is incompatible
- Update failed safely

Feedback must not be duplicated by both client and server.

---

## 16. Whole-Structure Lifecycle

One central lifecycle service must own:

- Anchor break
- Part break
- Creative removal
- Explosion cleanup
- External replacement
- Orphan cleanup
- Failed-placement rollback
- Invalid anchor cleanup
- Integrity-repair failure
- Structure-item reconstruction

A per-level and anchor-position reentrancy guard must prevent callback recursion and duplicate drops.

### Survival player break

Breaking the anchor or any valid part:

- Removes the complete persisted footprint.
- Produces exactly one configured family item at the anchor or approved drop location.
- Preserves the family and variant IDs.
- Produces no ordinary anchor or part block item.
- Produces no duplicate vanilla loot.

### Creative player break

Breaking the anchor or any valid part:

- Removes the complete structure.
- Produces no survival item drop.
- Leaves no parts or block entity.

### Explosion

The initial safe policy should be selected from existing project conventions and documented. Regardless of whether one item is dropped:

- The structure is removed once.
- No part can drop separately.
- No duplicate item is possible.
- External unrelated replacement blocks are preserved.
- The world does not retain orphan parts.

### External replacement

If a world-edit operation or another system replaces one occupied cell:

- Preserve the replacement.
- Remove the remaining known structure cells.
- Do not overwrite the replacement during cleanup.
- Do not produce a structure item unless repository conventions establish that the replacement was a normal survival break.
- Avoid recursive callbacks and duplicate drops.

### Pistons

Anchor and parts must block piston movement and sticky-piston pulling.

The structure must not become partially moved.

### Fluids

Anchor and parts should not be waterloggable in the first release unless existing project conventions require it.

Fluid updates must not replace parts silently.

---

## 17. Persistence, Reload, and Integrity

### 17.1 Authoritative placed footprint

Reload and cleanup use the persisted placed footprint, not a newly resolved current definition footprint.

Definition changes must not resize existing structures silently.

### 17.2 Chunk-load integrity

Integrity inspection must be deferred until the server can distinguish:

- Temporarily unloaded anchor
- Temporarily unloaded part
- Definitive orphan
- Missing replaceable part
- Occupied obstruction
- Missing definition content

Do not classify a part as orphaned merely because its anchor chunk is not loaded.

Do not force-load chunks.

### 17.3 Repair policy

When an anchor is valid and a persisted part is missing:

- Repair only if the required chunk is loaded.
- Repair only if the cell is replaceable.
- Never overwrite an obstruction.
- If an obstruction prevents safe repair, preserve the obstruction and remove the remaining known structure without a duplicate drop.
- Record a bounded diagnostic.

When a part points to no possible valid anchor and all required chunks are loaded, it may be removed without a drop.

### 17.4 Save and synchronization

The anchor must support repository-standard:

- Disk save/load
- Initial chunk data or update tag
- Block-entity update packet
- Client application of synchronized state
- Server `setChanged`
- Block update notification after state change

The complete family and variant identity and authoritative placed footprint must survive all boundaries.

### 17.5 Dedicated server isolation

Common anchor, part, state, placement, lifecycle, and persistence classes must contain no client-only imports.

Renderer registration and model resource resolution must remain client-side.

---

## 18. Item Recovery and Pick Block

### 18.1 Recovered item

The recovered item must represent:

- Structure family
- Current variant
- Schema version or compatible instance-state version
- Any future approved state stored by the family item

It must not be an ordinary anchor or part block item.

### 18.2 Pick block

Middle-clicking the anchor or a valid part should return the same configured family item without changing the world.

An invalid part or unavailable anchor should return the safest existing raw fallback or no item according to repository conventions. It must not force-load chunks or fabricate a different variant.

### 18.3 Missing definitions

When stable family or variant IDs are missing from the current registry, recovery should preserve those IDs when the item state codec can represent them.

No automatic substitution is allowed.

---

## 19. Content and Asset Expectations

### Shrine assets

- Reuse the approved shared shrine model.
- Reuse approved shrine textures.
- Preserve texture dimensions and naming unless a separate asset migration is approved.
- Do not create a model per virtue when geometry is identical.
- Do not edit artwork as part of system implementation.

### Monolith assets

- Inventory every supplied Blockbench model and texture.
- Record model dimensions, origin, canonical facing, and lowest authored Y.
- Confirm which models require the positive sixteen-voxel Y correction.
- Use stable provisional IDs for unnamed assets.
- Do not invent final names or lore.
- Do not change geometry merely to avoid implementing the render offset.

### Asset manifest

The implementation log or content report must list:

- Family
- Variant ID
- Display-name status
- Model path
- Texture path
- Cycle order
- Footprint
- Render offset
- Enabled/provisional status
- Validation result

---

## 20. Security and Authority

The server owns:

- Placement validation
- Footprint selection
- Facing
- Family and variant validation
- Block mutation
- Interior Decorator permission checks
- Variant cycling
- Persistence
- Drops
- Cleanup
- Repair

The client owns only:

- Input request
- Visual rendering
- Localized display
- Non-authoritative interaction feedback when consistent with repository patterns

Never accept an arbitrary client-provided resource path, model path, texture path, footprint, render offset, or variant ID without server-side registry validation.

---

## 21. Performance Requirements

The system is expected to support architectural decoration without material server or client degradation.

Requirements:

- Parts have no ticking block entities.
- Anchor has no ticker unless a proven integrity workflow requires a bounded scheduled action.
- Variant definitions are immutable and cached.
- Render resources are not parsed every frame.
- Integrity work is event-driven or bounded.
- No chunk force-loading.
- No global per-tick scan of all structures.
- No repeated log spam for the same missing resource.
- Variant cycling updates one anchor state and does not rewrite all parts.
- Rendering occurs once per structure, not once per occupied cell.

The final validation must include a dense-placement smoke test with enough shrines and monoliths to expose obvious rendering or lifecycle regressions.

---

## 22. Compatibility and Migration

### Existing shrine content

Codex must inspect whether shrine blocks, items, models, textures, or IDs already exist.

When existing IDs are present:

- Prefer migration or extension over duplicate registrations.
- Preserve saved-world compatibility where practical.
- Do not remove existing content until replacement behavior is proven.
- Add aliases or data migration only when the repository has an established mechanism.
- Document any unavoidable ID change before implementing it.

### Existing Interior Decorator tool

The feature must extend the existing tool rather than create a second administrator wand.

Existing interactions for unrelated furniture or decoration must remain unchanged.

The new handler must be narrowly scoped to valid large-structure anchors and parts.

### Existing multiblock framework

If the completed banner feature or another system already provides a safe anchor-and-parts lifecycle framework, Codex must evaluate reuse before adding a parallel implementation.

Reuse is appropriate only when:

- Floor-oriented three-dimensional footprints are supported.
- Part offsets support `3 × 3 × 2`.
- Collision profiles are compatible.
- Saved footprint behavior is compatible.
- Renderer ownership can remain structure-specific.
- Reuse does not couple shrine/monolith state to banner state.

When reuse would create unsafe coupling, extract only the minimal generic primitives or implement a separate narrowly scoped system and document the reason.

---

## 23. Test Strategy

Automated tests must cover pure logic, registered content, persistence, lifecycle, and client/server separation where the repository's test framework permits.

### Definition and transform tests

- Shrine footprint has exactly four unique offsets.
- Monolith footprint has exactly eighteen unique offsets.
- Anchor offset is included exactly once.
- Four horizontal facings generate correct world positions.
- Reverse part-to-anchor transform returns the original anchor.
- No generated shape exceeds local cell bounds.
- Monolith render offset equals positive sixteen voxels on Y.
- Shrine variants share one geometry resource.
- Monolith variants in the same cycle group share the placement contract.
- Cycle order is deterministic.

### Placement tests

- Valid shrine placement.
- Valid monolith placement.
- Obstructed cell.
- World-height failure.
- World-border failure.
- Unloaded required chunk.
- Protection denial.
- Invalid clicked face.
- Block-entity creation failure.
- Part placement failure.
- Final verification failure.
- Rollback restores exact original states.
- Survival consumes one only after success.
- Creative consumes zero.
- No success sound on failure.

### Persistence tests

- Save/load shrine.
- Save/load monolith.
- Update-tag equality.
- Update-packet equality.
- Missing family definition.
- Missing variant definition.
- Changed current definition footprint does not resize saved placement.
- Malformed footprint fails safely.
- Missing client resource does not alter server state.

### Lifecycle tests

- Break anchor in survival.
- Break part in survival.
- Break anchor in creative.
- Break part in creative.
- Explosion cleanup.
- External replacement preservation.
- Piston movement denied.
- Missing part repair.
- Obstructed repair removes safely without overwriting.
- Orphan part cleanup.
- Duplicate-drop prevention.
- Reentrancy guard.

### Interior Decorator tests

- Authorized shrine cycle.
- Unauthorized shrine interaction.
- Part target resolves to anchor.
- Shrine cycle changes texture variant only.
- Authorized monolith cycle.
- Monolith cycle changes model variant only.
- Last variant wraps to first.
- Single enabled variant reports no alternative.
- Missing next variant fails without mutation.
- Client-provided invalid variant is ignored or rejected.
- Cycle persists through save/reload.
- Cycle synchronizes to observing client.

### Collision and adjacency tests

- Anchor and every part shape remains inside local bounds.
- Full perimeter stair ring for all four facings.
- Inner and outer stair corners.
- Adjacent slabs.
- Adjacent full blocks.
- Adjacent walls and fences.
- No adjacent placement mutates structure.
- No model bounds influence placement collision.
- No ordinary block can replace an occupied part through normal placement.

### Packaging and side tests

- Required blockstates, models, textures, and renderer resources package in the production JAR.
- No part item model is exposed.
- No anchor or part ordinary block item is registered.
- Common classes contain no client imports.
- Dedicated-server startup succeeds.
- Existing Interior Decorator interactions remain passing.
- Existing banner, farming, housing, and unrelated feature tests remain passing.

### Manual game validation

At minimum:

1. Place a shrine in all four facings.
2. Build a complete stair ring around each.
3. Test inner and outer stair corners.
4. Cycle every shrine variant with the Interior Decorator.
5. Save, exit, reload, and verify the selected variant.
6. Break one part and verify one structure item.
7. Place a monolith in all four facings.
8. Confirm the visual base sits on the world horizon.
9. Confirm the model is not one block too low or too high.
10. Build stairs, slabs, and full blocks against every perimeter side.
11. Cycle every monolith model.
12. Observe cycling from a second client.
13. Save and restart a dedicated server.
14. Test a structure crossing a chunk boundary.
15. Test obstruction and rollback.
16. Inspect logs for renderer, missing-resource, orphan, duplicate-drop, and synchronization errors.

Checks not performed must be marked unperformed. They must not be described as passed.

---

## 24. Acceptance Criteria

The feature is complete only when all of the following are true:

- [ ] Repository facts and existing related systems were inspected first.
- [ ] Work occurs on the approved feature branch.
- [ ] The branch is based on the approved integration branch.
- [ ] Shrine footprint is exactly `2 × 1 × 2`.
- [ ] Monolith footprint is exactly `3 × 3 × 2`.
- [ ] The structure uses one anchor and non-authoritative parts.
- [ ] Parts do not store complete family or variant state.
- [ ] Parts do not produce independent drops.
- [ ] Placement is atomic and rollback is exact.
- [ ] Cross-chunk placement never force-loads chunks.
- [ ] Saved footprints remain authoritative after definition changes.
- [ ] Shrine variants share geometry and change texture only.
- [ ] Monolith variants may change model while retaining the placement contract.
- [ ] Monolith rendering applies positive sixteen voxels on Y.
- [ ] Render offset does not change collision or occupied cells.
- [ ] Only the anchor renders.
- [ ] Render bounds cover the full structure.
- [ ] Collision and selection shapes are bounded to each local block cell.
- [ ] Adjacent stairs can be placed around every perimeter side.
- [ ] Adjacent ordinary blocks do not collide with oversized visual geometry.
- [ ] Occupied cells cannot be independently replaced through normal placement.
- [ ] Interior Decorator cycling is server-authoritative and administrator-only.
- [ ] Right-clicking a valid part resolves the anchor.
- [ ] Shrine cycling changes only texture variant state.
- [ ] Monolith cycling changes only model/texture variant state.
- [ ] No cross-family conversion occurs.
- [ ] Save/load preserves family, variant, facing, and placed footprint.
- [ ] Missing content preserves stable IDs and does not crash.
- [ ] Breaking any cell removes the whole structure.
- [ ] Survival removal produces exactly one configured item.
- [ ] Creative removal produces no duplicate item.
- [ ] Pistons cannot partially move the structure.
- [ ] Common code has no client-only imports.
- [ ] Dedicated-server startup passes.
- [ ] Automated tests pass.
- [ ] Clean build passes.
- [ ] Manual stair, reload, multiplayer, and horizon-alignment checks are completed or explicitly marked unperformed.
- [ ] Existing Interior Decorator behavior remains intact.
- [ ] No unrelated systems were refactored.
- [ ] No unapproved artwork, names, recipes, or gameplay systems were added.
- [ ] Merge-readiness review and rollback plan are documented.

---

## 25. Decisions Requiring Repository Discovery

The following are implementation questions, not product ambiguity. Codex must answer them from the repository before coding:

- Whether an existing anchor-and-parts service can be safely reused.
- The exact Interior Decorator item class and administrator predicate.
- Existing block registration and block-entity registration conventions.
- Existing custom item-state or data-component conventions.
- Existing Blockbench and GeckoLib renderer patterns.
- Existing protection-region hooks.
- Existing structure-drop and pick-block patterns.
- Existing test bootstrap and whether GameTests are available.
- Existing approved shrine item/block IDs.
- Exact approved shrine texture IDs.
- Exact monolith model count, IDs, names, and resource paths.
- Whether `SOLID_CELL` or an approved bounded custom collision profile best matches each final model.
- Exact explosion drop policy used by comparable placed architectural objects.

These answers belong in:

```text
docs/shrines-monoliths/PROJECT_FACTS.md
docs/shrines-monoliths/OPEN_QUESTIONS.md
docs/shrines-monoliths/IMPLEMENTATION_LOG.md
```

---

## 26. Final Design Summary

The feature is not an oversized single Minecraft block.

It is a logical multi-block structure:

```text
family item
    -> immutable placement plan
    -> one authoritative anchor
    -> bounded occupied part cells
    -> one persisted footprint
    -> one anchor renderer
    -> family/variant visual selection
```

The stair-collision fix comes from keeping every block shape inside its own sixteen-voxel cell.

The monolith horizon fix comes from a visual-only positive sixteen-voxel Y render offset.

Shrine cycling changes texture state. Monolith cycling changes model state. Both operations preserve the placed footprint and remain server-authoritative.
